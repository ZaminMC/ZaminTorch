package net.zaminmc.torch.server.concurrent;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The parallel compute subsystem (the permanent architecture's Phase 3 — the
 * design's §6-A "parallel calculation, serialized authoritative application"
 * and §12's compute contract): workers compute over immutable inputs with
 * <em>no authority over live state</em>; results ship with the ownership
 * generation they were computed against; the owning domain validates and
 * applies them at its own boundary. Neither side ever waits on the other —
 * the simulation owner never blocks on the pool, the pool never blocks on
 * the owner (the design §12 deadlock rule).
 *
 * <p>The task contract (§12): the input is the caller's immutable snapshot
 * (captured on the owner before submit); the compute function is pure over
 * that input; the outcome lands in the domain's result mailbox; the owner's
 * {@link #drainResults(OwnershipDomain)} applies fresh results through the
 * registered applier and discards stale ones through the registered
 * fallback — a stale result is never blindly applied. Admission is bounded
 * and loud; a compute failure surfaces on the handle and the telemetry, and
 * never kills a worker.</p>
 *
 * <p>The workers share one bounded pool sized conservatively below the
 * machine's reported cores (the design §12 oversubscription rule); blocking
 * I/O does not belong here — that is the storage lane's own bounded pool in
 * a later phase.</p>
 */
public final class ComputeSubsystem {

    private static final Logger LOGGER = Logger.getLogger(ComputeSubsystem.class.getName());

    /** The default admission bound (the explicit backpressure point). */
    public static final int DEFAULT_ADMISSION_CAPACITY = 256;
    /** The worker thread count cap: compute must not oversubscribe the machine. */
    private static final int MAX_WORKERS = 4;

    /**
     * One completed computation's delivery envelope: the apply/stale arms
     * are closures built inside the typed handle, so the drain needs no
     * wildcard casts. Immutable and safe to cross threads.
     */
    private record Outcome(ComputeHandle<?> handle, Runnable applyIfFresh, Runnable markStale) {
    }

    /** One submitted computation's lifecycle handle. */
    public static final class ComputeHandle<R> {
        private enum State { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

        private final OwnershipDomain domain;
        private final long generation;
        private final AtomicReference<State> state = new AtomicReference<>(State.QUEUED);
        private volatile R result;
        private volatile Throwable failure;
        private final java.util.function.BiConsumer<R, Long> onFresh;
        private final Runnable onStale;

        ComputeHandle(OwnershipDomain domain, long generation,
                      java.util.function.BiConsumer<R, Long> onFresh, Runnable onStale) {
            this.domain = domain;
            this.generation = generation;
            this.onFresh = onFresh;
            this.onStale = onStale;
        }

        /** The generation the computation was submitted against. */
        public long generation() {
            return generation;
        }

        /** @return true once the handle reached a terminal state. */
        public boolean isDone() {
            State current = state.get();
            return current == State.COMPLETED || current == State.FAILED
                    || current == State.CANCELLED;
        }

        public boolean isCancelled() {
            return state.get() == State.CANCELLED;
        }

        public boolean isFailed() {
            return state.get() == State.FAILED;
        }

        /** The failure a failed computation threw (diagnostics; never applied). */
        public Throwable failure() {
            return failure;
        }

        /**
         * Cancels a queued task. A running task cannot be cancelled — it
         * completes and its result flows through the usual fresh/stale
         * validation at the owner's drain.
         *
         * @return true when the task will never run
         */
        public boolean tryCancel() {
            return state.compareAndSet(State.QUEUED, State.CANCELLED);
        }

        void markRunning() {
            state.compareAndSet(State.QUEUED, State.RUNNING);
        }

        @SuppressWarnings("unchecked")
        void markCompleted(Object value) {
            this.result = (R) value;
            state.set(State.COMPLETED);
        }

        void markFailed(Throwable t) {
            this.failure = t;
            state.set(State.FAILED);
        }

        /** The typed delivery envelope (the apply arms close over R). */
        Outcome outcome() {
            return new Outcome(this,
                    () -> onFresh.accept(result, generation),
                    () -> {
                        if (onStale != null) {
                            onStale.run();
                        }
                    });
        }
    }

    /** One domain's result mailbox: outcomes waiting for the owner's drain. */
    private static final class ResultBox {
        final Queue<Outcome> outcomes = new ConcurrentLinkedQueue<>();
        final AtomicLong staleCount = new AtomicLong();
        final AtomicLong appliedCount = new AtomicLong();
    }

    private final LinkedBlockingQueue<Job> admission;
    private final ConcurrentHashMap<String, ResultBox> results = new ConcurrentHashMap<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final Thread[] workers;
    private final int admissionCapacity;

    /**
     * Creates the subsystem with the default admission capacity and a
     * conservative worker count ({@code max(1, cores / 4)} capped at
     * {@value #MAX_WORKERS}).
     */
    public ComputeSubsystem() {
        this(DEFAULT_ADMISSION_CAPACITY,
                Math.max(1, Math.min(MAX_WORKERS,
                        Runtime.getRuntime().availableProcessors() / 4)));
    }

    public ComputeSubsystem(int admissionCapacity, int workerCount) {
        if (admissionCapacity < 1 || workerCount < 1) {
            throw new IllegalArgumentException("capacity and workerCount must be positive");
        }
        this.admissionCapacity = admissionCapacity;
        this.admission = new LinkedBlockingQueue<>(admissionCapacity);
        this.workers = new Thread[workerCount];
        for (int i = 0; i < workerCount; i++) {
            Thread worker = new Thread(this::workerLoop, "torch-compute-" + i);
            worker.setDaemon(true);
            workers[i] = worker;
            worker.start();
        }
    }

    /** The untyped job envelope riding the admission queue. */
    private record Job(OwnershipDomain domain, Object input,
                       java.util.function.UnaryOperator<Object> compute,
                       ComputeHandle<?> handle) {
    }

    /**
     * Submits a pure computation over an immutable input. The caller runs on
     * the owning authority (the generation captured here is the snapshot's);
     * the outcome reaches the owner through the next drain.
     *
     * @param domain  the owning domain (the result validates against it)
     * @param input   the immutable snapshot (the worker never touches live state)
     * @param compute the pure function over the input
     * @param onFresh runs on the owner's drain inside its context with the
     *                fresh result and its generation — the apply arm
     * @param onStale runs on the owner's drain when the generation moved —
     *                the documented fallback (recompute is the caller's move)
     * @return the lifecycle handle (cancellation while queued)
     * @throws IllegalStateException on shutdown or a full admission queue —
     *         the loud refusal, never a silent drop
     */
    public <I, R> ComputeHandle<R> submit(OwnershipDomain domain, I input,
                                          java.util.function.Function<I, R> compute,
                                          java.util.function.BiConsumer<R, Long> onFresh,
                                          Runnable onStale) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(compute, "compute");
        Objects.requireNonNull(onFresh, "onFresh");
        Objects.requireNonNull(onStale, "onStale");
        if (shutdown.get()) {
            rejected.incrementAndGet();
            throw new IllegalStateException("Compute subsystem is shut down; submission refused");
        }
        ComputeHandle<R> handle = new ComputeHandle<>(domain, domain.generation(), onFresh, onStale);
        Job job = new Job(domain, input,
                value -> compute.apply(castInput(value)), handle);
        if (!admission.offer(job)) {
            rejected.incrementAndGet();
            throw new IllegalStateException("Compute admission queue full ("
                    + admission.size() + " queued, capacity " + admissionCapacity
                    + "); submission refused");
        }
        submitted.incrementAndGet();
        return handle;
    }

    @SuppressWarnings("unchecked")
    private static <I> I castInput(Object value) {
        return (I) value;
    }

    private void workerLoop() {
        while (!shutdown.get()) {
            Job job;
            try {
                job = admission.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (job == null) {
                continue;
            }
            ComputeHandle<?> handle = job.handle();
            if (handle.isCancelled()) {
                continue; // cancelled while queued: never runs, never resurrects
            }
            handle.markRunning();
            try {
                Object result = job.compute().apply(job.input());
                handle.markCompleted(result);
                resultBox(job.domain()).outcomes.offer(handle.outcome());
            } catch (Throwable t) {
                handle.markFailed(t);
                failed.incrementAndGet();
                LOGGER.log(Level.SEVERE, "Compute task failed in domain '"
                        + job.domain().name() + "'; the result is discarded", t);
            }
        }
    }

    private ResultBox resultBox(OwnershipDomain domain) {
        return results.computeIfAbsent(domain.name(), k -> new ResultBox());
    }

    /**
     * The owner's boundary call: applies every completed outcome whose
     * generation still matches the domain, discards the stale ones through
     * their registered fallback, and reports the count. The appliers run on
     * the CALLING thread — the owner's authority — so the drain is itself an
     * in-context operation (the design's "the owner validates … and
     * applies").
     *
     * @return the number of fresh results applied
     * @throws OwnershipViolationException when the caller is not the domain's
     *         bound executor (STRICT) — the drain is an authoritative apply
     */
    public int drainResults(OwnershipDomain domain) {
        domain.checkInContext("drainResults");
        ResultBox box = results.get(domain.name());
        if (box == null) {
            return 0;
        }
        int applied = 0;
        Outcome outcome;
        while ((outcome = box.outcomes.poll()) != null) {
            if (outcome.handle().generation() != domain.generation()) {
                box.staleCount.incrementAndGet();
                outcome.markStale().run();
                continue;
            }
            box.appliedCount.incrementAndGet();
            applied++;
            outcome.applyIfFresh().run();
        }
        return applied;
    }

    /**
     * Stops admission permanently; the workers drain what is already queued
     * and exit (the outcomes of already-queued work still flow to the
     * owner's drain). Idempotent.
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        for (Thread worker : workers) {
            worker.interrupt();
        }
        for (Thread worker : workers) {
            try {
                worker.join(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.fine("Compute subsystem shut down");
    }

/** @return whether admission has stopped (the shutdown state). */
    public boolean isShutdownAdmissionStopped() {
        return shutdown.get();
    }

    /** @return the submitted count (telemetry, the design §17 floor). */
    public long submittedCount() {
        return submitted.get();
    }

    /** @return the failed computation count. */
    public long failedCount() {
        return failed.get();
    }

    /** @return the refused-submission count (the backpressure telemetry). */
    public long rejectedCount() {
        return rejected.get();
    }

    /** @return how many fresh results the domain applied (telemetry). */
    public long appliedCount(OwnershipDomain domain) {
        ResultBox box = results.get(domain.name());
        return box == null ? 0 : box.appliedCount.get();
    }

    /** @return how many stale results the domain discarded (telemetry). */
    public long staleCount(OwnershipDomain domain) {
        ResultBox box = results.get(domain.name());
        return box == null ? 0 : box.staleCount.get();
    }

    /** @return the queued-but-not-yet-running job depth. */
    public int queuedDepth() {
        return admission.size();
    }

    /** @return whether any submitted job has failed (the test probe). */
    public boolean isFailedVisible() {
        return failed.get() > 0;
    }
}
