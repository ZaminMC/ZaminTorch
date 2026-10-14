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
 * The blocking-I/O lane (the master spec §28.1's fourth execution category,
 * the design's deferred "storage lane"): database, filesystem, HTTP, and
 * other potentially blocking work runs on its OWN bounded pool — never on
 * a simulation worker, never on a compute worker — and every completion
 * returns to the owning domain through a defined handoff, exactly the
 * compute subsystem's mailbox contract (master spec §28.2: no hidden
 * blocking wait anywhere; the owner never parks for the lane).
 *
 * <p>Task contract: the submission captures the owning domain and its
 * generation; the blocking work itself owns NO mutation authority (a
 * reference is not permission, §28.3); the completion lands in the domain's
 * mailbox and the owner's {@link #drainCompletions(OwnershipDomain)} applies
 * fresh ones inside its own validated context and discards stale ones
 * through the registered fallback — a result computed against generation G1
 * never mutates state at G2 (the §6.3 stale-result rule).</p>
 *
 * <p>Admission is bounded and loud ({@code IllegalStateException}, never a
 * silent drop). Shutdown refuses new work, CANCELS everything still queued
 * (a cancelled task never runs, never resurrects), and INTERRUPTS the
 * in-flight calls — the lane's contract asks blocking work to be
 * interruptible; a task that swallows interruption simply completes and
 * its completion still flows through the drain, so shutdown joins in
 * bounded time either way.</p>
 */
public final class BlockingIoLane {

    private static final Logger LOGGER = Logger.getLogger(BlockingIoLane.class.getName());

    /** The default admission bound (the explicit backpressure point). */
    public static final int DEFAULT_ADMISSION_CAPACITY = 128;
    /** The worker thread count cap: the I/O lane waits on the world, not the CPU. */
    private static final int MAX_WORKERS = 4;

    /**
     * One submitted blocking task's lifecycle handle. Cancellation works
     * while queued; a running task cannot be cancelled — it completes (or
     * fails) and its completion flows through the usual fresh/stale
     * validation at the owner's drain.
     */
    public static final class IoHandle<R> {
        private enum State { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

        private final OwnershipDomain domain;
        private final long generation;
        private final AtomicReference<State> state = new AtomicReference<>(State.QUEUED);
        private volatile R result;
        private volatile Throwable failure;
        private final java.util.function.BiConsumer<R, Long> onFresh;
        private final Runnable onStale;

        IoHandle(OwnershipDomain domain, long generation,
                 java.util.function.BiConsumer<R, Long> onFresh, Runnable onStale) {
            this.domain = domain;
            this.generation = generation;
            this.onFresh = onFresh;
            this.onStale = onStale;
        }

        /** The generation the task was submitted against. */
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

        /** The failure a failed task threw (diagnostics; never applied). */
        public Throwable failure() {
            return failure;
        }

        /** Cancels a queued task; a running one is out of reach by contract. */
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

        /** The typed delivery envelope (the arms close over R). */
        Delivery delivery() {
            return new Delivery(this,
                    () -> onFresh.accept(result, generation),
                    () -> {
                        if (onStale != null) {
                            onStale.run();
                        }
                    });
        }
    }

    /** One completed task's delivery envelope. Immutable and safe to cross threads. */
    private record Delivery(IoHandle<?> handle, Runnable applyIfFresh, Runnable markStale) {
    }

    /** One domain's completion mailbox: deliveries waiting for the owner's drain. */
    private static final class CompletionBox {
        final Queue<Delivery> deliveries = new ConcurrentLinkedQueue<>();
        final AtomicLong staleCount = new AtomicLong();
        final AtomicLong appliedCount = new AtomicLong();
    }

    private record Task(OwnershipDomain domain, String operation,
                        java.util.concurrent.Callable<?> work, IoHandle<?> handle) {
    }

    private final LinkedBlockingQueue<Task> admission;
    private final ConcurrentHashMap<String, CompletionBox> completions = new ConcurrentHashMap<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong cancelledAtShutdown = new AtomicLong();
    private final Thread[] workers;
    private final int admissionCapacity;

    /**
     * Creates the lane with the default admission capacity and a small
     * worker count ({@code max(1, cores / 4)} capped at {@value #MAX_WORKERS}).
     */
    public BlockingIoLane() {
        this(DEFAULT_ADMISSION_CAPACITY,
                Math.max(1, Math.min(MAX_WORKERS,
                        Runtime.getRuntime().availableProcessors() / 4)));
    }

    public BlockingIoLane(int admissionCapacity, int workerCount) {
        if (admissionCapacity < 1 || workerCount < 1) {
            throw new IllegalArgumentException("capacity and workerCount must be positive");
        }
        this.admissionCapacity = admissionCapacity;
        this.admission = new LinkedBlockingQueue<>(admissionCapacity);
        this.workers = new Thread[workerCount];
        for (int i = 0; i < workerCount; i++) {
            Thread worker = new Thread(this::workerLoop, "torch-io-" + i);
            worker.setDaemon(true);
            workers[i] = worker;
            worker.start();
        }
    }

    /**
     * Submits a blocking task. Any thread may submit; the completion never
     * runs here — it lands in the owner's mailbox and the owner applies it
     * at its own drain (the §28.1 defined handoff).
     *
     * @param domain    the owning domain (the completion validates against it)
     * @param operation the diagnostic name ("save-chunk 12,34")
     * @param work      the blocking call; interruptible by contract
     * @param onFresh   runs on the owner's drain inside its context with the
     *                  fresh result and its generation
     * @param onStale   runs on the owner's drain when the generation moved
     * @return the lifecycle handle (cancellation while queued)
     * @throws IllegalStateException on shutdown or a full admission queue —
     *         the loud refusal, never a silent drop
     */
    public <R> IoHandle<R> submit(OwnershipDomain domain, String operation,
                                  java.util.concurrent.Callable<R> work,
                                  java.util.function.BiConsumer<R, Long> onFresh,
                                  Runnable onStale) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(onFresh, "onFresh");
        if (shutdown.get()) {
            rejected.incrementAndGet();
            throw new IllegalStateException("Blocking I/O lane is shut down; task '"
                    + operation + "' refused");
        }
        IoHandle<R> handle = new IoHandle<>(domain, domain.generation(), onFresh, onStale);
        Task task = new Task(domain, operation, work, handle);
        if (!admission.offer(task)) {
            rejected.incrementAndGet();
            throw new IllegalStateException("Blocking I/O admission queue full ("
                    + admission.size() + " queued, capacity " + admissionCapacity
                    + "); task '" + operation + "' refused");
        }
        submitted.incrementAndGet();
        return handle;
    }

    private void workerLoop() {
        while (!shutdown.get()) {
            Task task;
            try {
                task = admission.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // the shutdown interrupts the workers
            }
            if (task == null) {
                continue;
            }
            IoHandle<?> handle = task.handle();
            if (handle.isCancelled()) {
                continue; // cancelled while queued: never runs, never resurrects
            }
            handle.markRunning();
            try {
                Object result = task.work().call();
                handle.markCompleted(result);
                completionBox(task.domain()).deliveries.offer(handle.delivery());
            } catch (Throwable t) {
                handle.markFailed(t);
                failed.incrementAndGet();
                LOGGER.log(Level.SEVERE, "Blocking I/O task '" + task.operation()
                        + "' failed in domain '" + task.domain().name()
                        + "'; the result is discarded", t);
            }
        }
    }

    private CompletionBox completionBox(OwnershipDomain domain) {
        return completions.computeIfAbsent(domain.name(), k -> new CompletionBox());
    }

    /**
     * The owner's boundary call: applies every completed delivery whose
     * generation still matches the domain, discards the stale ones through
     * their registered fallback, and reports the count. The appliers run on
     * the CALLING thread — the owner's authority.
     *
     * @return the number of fresh completions applied
     * @throws OwnershipViolationException when the caller is not the domain's
     *         bound executor (STRICT) — the drain is an authoritative apply
     */
    public int drainCompletions(OwnershipDomain domain) {
        domain.checkInContext("drainCompletions");
        CompletionBox box = completions.get(domain.name());
        if (box == null) {
            return 0;
        }
        int applied = 0;
        Delivery delivery;
        while ((delivery = box.deliveries.poll()) != null) {
            if (delivery.handle().generation() != domain.generation()) {
                box.staleCount.incrementAndGet();
                delivery.markStale().run();
                continue;
            }
            box.appliedCount.incrementAndGet();
            applied++;
            delivery.applyIfFresh().run();
        }
        return applied;
    }

    /**
     * Stops admission permanently, cancels everything still queued, and
     * interrupts the in-flight calls. Idempotent.
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        Task task;
        while ((task = admission.poll()) != null) {
            if (task.handle().tryCancel()) {
                cancelledAtShutdown.incrementAndGet();
            }
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
        LOGGER.fine("Blocking I/O lane shut down; queued tasks cancelled");
    }

    /** @return whether admission has stopped. */
    public boolean isShutdown() {
        return shutdown.get();
    }

    /** @return the submitted count (telemetry). */
    public long submittedCount() {
        return submitted.get();
    }

    /** @return the failed task count (telemetry). */
    public long failedCount() {
        return failed.get();
    }

    /** @return the refused-submission count (the backpressure telemetry). */
    public long rejectedCount() {
        return rejected.get();
    }

    /** @return how many queued tasks the shutdown cancelled (telemetry). */
    public long cancelledAtShutdownCount() {
        return cancelledAtShutdown.get();
    }

    /** @return how many fresh completions the domain applied (telemetry). */
    public long appliedCount(OwnershipDomain domain) {
        CompletionBox box = completions.get(domain.name());
        return box == null ? 0 : box.appliedCount.get();
    }

    /** @return how many stale completions the domain discarded (telemetry). */
    public long staleCount(OwnershipDomain domain) {
        CompletionBox box = completions.get(domain.name());
        return box == null ? 0 : box.staleCount.get();
    }

    /** @return how many completed tasks wait for the owner's drain right now
     *         (the §28.9 observability read the drains and the tests ride). */
    public int pendingDeliveries(OwnershipDomain domain) {
        CompletionBox box = completions.get(domain.name());
        return box == null ? 0 : box.deliveries.size();
    }

    /** @return the queued-but-not-yet-running task depth. */
    public int queuedDepth() {
        return admission.size();
    }
}
