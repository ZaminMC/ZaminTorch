package net.zaminmc.torch.server.concurrent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * The simulation task scheduler (the permanent architecture's Phase 2 — the
 * design's §5 scheduling model): tasks are registered against their
 * {@link OwnershipDomain}; the tick calls {@link #runDue(long)} which moves
 * due delayed work to the ready set and executes it — fairly across domains,
 * one active task per domain (the domain's own single-writer gate is the
 * enforcement), submission order preserved inside a domain, and stable
 * sequence numbers breaking same-deadline ties (the determinism contract,
 * design §16).
 *
 * <p>Backpressure is explicit (design invariant 8): each domain's pending
 * admission is bounded; a submit past the bound throws — authoritative
 * gameplay intents are never silently dropped. Cancellation removes queued
 * work permanently; a shutdown stops admission and cancels everything
 * queued — no resurrection (design §11 test list).</p>
 *
 * <p>Phase 2 scope: the engine's tick loop drives {@link #runDue(long)}; the
 * physical worker pool that runs independent domains concurrently arrives in
 * Phase 4 — until then runDue executes serially on the caller (today's tick
 * thread), which preserves the current single-domain semantics exactly.</p>
 */
public final class SimulationScheduler {

    private static final Logger LOGGER = Logger.getLogger(SimulationScheduler.class.getName());

    /** The default per-domain pending bound (the explicit overload contract). */
    public static final int DEFAULT_MAX_PENDING_PER_DOMAIN = 4096;

    /** One domain's ready + delayed queues and its telemetry. */
    private static final class DomainQueue {
        final OwnershipDomain domain;
        /** The ready FIFO (submission order preserved within the domain). */
        final ArrayDeque<DomainTask> ready = new ArrayDeque<>();
        /** The delayed store: (dueTick, sequence) → task, the stable-order rule. */
        final ConcurrentSkipListMap<Long, DomainTask> delayed = new ConcurrentSkipListMap<>();
        final AtomicLong lastDurationNanos = new AtomicLong();
        final AtomicLong executedCount = new AtomicLong();

        DomainQueue(OwnershipDomain domain) {
            this.domain = domain;
        }

        int pending() {
            return ready.size() + delayed.size();
        }
    }

    private final Map<String, DomainQueue> queues = new ConcurrentHashMap<>();
    private final Map<String, Boolean> admissionHeld = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final int maxPendingPerDomain;
    private volatile long rejectedSubmissions;

    public SimulationScheduler() {
        this(DEFAULT_MAX_PENDING_PER_DOMAIN);
    }

    public SimulationScheduler(int maxPendingPerDomain) {
        if (maxPendingPerDomain < 1) {
            throw new IllegalArgumentException("maxPendingPerDomain must be positive");
        }
        this.maxPendingPerDomain = maxPendingPerDomain;
    }

    /**
     * Admits a task to run on the next {@link #runDue(long)}. Any thread;
     * the admission is the bounded queue's explicit backpressure point.
     *
     * @throws IllegalStateException on shutdown, or past the domain's pending
     *         bound — the caller decides retry or surface (never a silent drop)
     */
    public void submit(OwnershipDomain domain, Runnable work) {
        admit(queueOf(domain), new DomainTask(domain, work));
    }

    /**
     * Admits a pre-built task (the cancellation handle stays with the
     * caller). Same admission rules as {@link #submit(OwnershipDomain, Runnable)}.
     */
    public void submitTask(OwnershipDomain domain, DomainTask task) {
        admit(queueOf(domain), Objects.requireNonNull(task, "task"));
    }

    /**
     * Admits a delayed task: it becomes ready when the tick clock reaches
     * {@code dueTick}. Same-thread semantics as the fixed-rate engine loop's
     * logical deadlines.
     */
    public DomainTask submitDelayed(OwnershipDomain domain, Runnable work, long dueTick) {
        if (dueTick < 0) {
            throw new IllegalArgumentException("dueTick must be non-negative");
        }
        DomainQueue queue = queueOf(domain);
        DomainTask task = new DomainTask(domain, work);
        synchronized (queue) {
            checkAdmission(queue);
            queue.delayed.put(delayedKey(dueTick, sequence.incrementAndGet()), task);
        }
        return task;
    }

    private void admit(DomainQueue queue, DomainTask task) {
        synchronized (queue) {
            checkAdmission(queue);
            queue.ready.addLast(task);
        }
    }

    private void checkAdmission(DomainQueue queue) {
        if (shutdown.get()) {
            throw new IllegalStateException(
                    "Scheduler is shut down; submission refused for domain '"
                            + queue.domain.name() + "'");
        }
        if (admissionHeld.containsKey(queue.domain.name())) {
            rejectedSubmissions++;
            throw new IllegalStateException("Admission held for domain '"
                    + queue.domain.name() + "' (ownership migration in progress); "
                    + "submission refused — the design's §9 step 2");
        }
        if (queue.pending() >= maxPendingPerDomain) {
            rejectedSubmissions++;
            throw new IllegalStateException("Pending bound exceeded for domain '"
                    + queue.domain.name() + "' (" + maxPendingPerDomain
                    + "); submission refused — surface to the caller, never a silent drop");
        }
    }

    /**
     * Holds the domain's admission (the migration protocol's §9 step 2): new
     * submissions are refused loudly while the ownership transfer runs;
     * already-queued work is untouched (it drains at the old generation).
     * Releasing re-opens admission. Idempotent per hold.
     */
    public void holdAdmission(OwnershipDomain domain) {
        Objects.requireNonNull(domain, "domain");
        admissionHeld.put(domain.name(), Boolean.TRUE);
    }

    /** Releases the migration hold; admission resumes. */
    public void releaseAdmission(OwnershipDomain domain) {
        Objects.requireNonNull(domain, "domain");
        admissionHeld.remove(domain.name());
    }

    /** @return whether the domain's admission is currently held. */
    public boolean isAdmissionHeld(OwnershipDomain domain) {
        return domain != null && admissionHeld.containsKey(domain.name());
    }

    /**
     * Fully drains ONE domain: due delayed tasks move to ready, then every
     * ready task runs on the caller's thread (the caller must hold the
     * domain's context — the migration's §9 step 3 finish-in-flight point).
     * Unlike {@link #runDue(long)} this is not a fair rotation pass: it
     * drains the named domain completely, including work admitted during
     * the drain (the hold makes that set stable).
     *
     * @return the number of tasks executed
     */
    public int drainDomain(OwnershipDomain domain, long nowTick) {
        Objects.requireNonNull(domain, "domain");
        domain.checkInContext("drainDomain");
        DomainQueue queue = queues.get(domain.name());
        if (queue == null) {
            return 0;
        }
        moveDueToReady(nowTick);
        int executed = 0;
        while (true) {
            int pass;
            synchronized (queue) {
                pass = drainReady(queue);
            }
            executed += pass;
            if (pass == 0) {
                return executed;
            }
        }
    }

    private DomainQueue queueOf(OwnershipDomain domain) {
        Objects.requireNonNull(domain, "domain");
        return queues.computeIfAbsent(domain.name(), name -> new DomainQueue(domain));
    }

    private long delayedKey(long dueTick, long seq) {
        return (dueTick << 24) | (seq & 0xFFFFFF);
    }

    /**
     * Runs all due work: due delayed tasks move to ready, then every ready
     * task executes — fairly across domains (round-robin rotation, so one
     * busy domain cannot starve another), one active task per domain (the
     * gate), FIFO inside a domain (the gameplay ordering contract).
     *
     * <p>The caller runs this on the simulation authority's thread; each
     * task re-binds the domain context for its own run. A task whose run
     * throws is logged and skipped — one failing task never kills the
     * schedule (the historical tick policy).</p>
     *
     * @param nowTick the tick clock reading (delayed tasks with dueTick ≤ now run)
     * @return the number of tasks executed
     */
    public int runDue(long nowTick) {
        moveDueToReady(nowTick);
        int executed = 0;
        // The rotation walk: each pass covers every registered domain once,
        // so ready work interleaves fairly; tasks admitted during the walk
        // run next tick (no unbounded drain within one tick).
        List<DomainQueue> ordered = new ArrayList<>(queues.values());
        ordered.sort(Comparator.comparing(q -> q.domain.name()));
        for (DomainQueue queue : ordered) {
            executed += drainReady(queue);
        }
        return executed;
    }

    /**
     * The Phase 4 walk: the same due-move and fairness rules, but the
     * domains' due work runs <em>concurrently</em> — each domain bound to
     * the caller's thread drains inline (its tasks' nested enter pairs with
     * the caller's binding), every other domain's drain dispatches to the
     * shared {@link DomainWorkerPool} and the join inside the pool is the
     * tick-edge barrier: the caller returns only after every domain's due
     * work finished (the documented hybrid of design §8 — one logical tick
     * clock, enforced at the edge).
     *
     * <p>Each drain still runs one active task per domain (each
     * {@link DomainTask} holds its own gate and binding), FIFO inside a
     * domain. The dispatch order is the stable domain-name walk (the
     * deterministic start order); completion is not ordered — physical
     * worker assignment is not part of game semantics (design invariant 9).
     * With every domain caller-bound the walk degenerates to the serial
     * behavior (identical gameplay — today's single-domain live run).
     * Cross-domain intents submitted by a drain may land in the target's
     * next-tick drain when the target's drain already started — the
     * documented Folia-equivalent skew, admitted by the design (the
     * cross-domain protocol applies at the target's own boundary).</p>
     *
     * @param nowTick the tick clock reading (delayed tasks with dueTick ≤ now run)
     * @param pool the shared simulation worker pool
     * @return the number of tasks executed
     */
    public int runDueParallel(long nowTick, DomainWorkerPool pool) {
        Objects.requireNonNull(pool, "pool");
        moveDueToReady(nowTick);
        List<DomainQueue> ordered = new ArrayList<>(queues.values());
        ordered.sort(Comparator.comparing(q -> q.domain.name()));
        int executed = 0;
        List<DomainQueue> foreign = new ArrayList<>(ordered.size());
        for (DomainQueue queue : ordered) {
            if (queue.domain.inContext()) {
                // The caller's own binding: drain inline (nested enter), the
                // same-context ordering the serial walk gave this domain.
                executed += drainReady(queue);
            } else {
                foreign.add(queue);
            }
        }
        if (!foreign.isEmpty()) {
            java.util.concurrent.atomic.AtomicInteger foreignExecuted =
                    new java.util.concurrent.atomic.AtomicInteger();
            List<Runnable> jobs = new ArrayList<>(foreign.size());
            for (DomainQueue queue : foreign) {
                jobs.add(() -> foreignExecuted.addAndGet(drainReady(queue)));
            }
            pool.dispatchAndJoin(jobs);
            executed += foreignExecuted.get();
        }
        return executed;
    }

    private void moveDueToReady(long nowTick) {
        for (DomainQueue queue : queues.values()) {
            synchronized (queue) {
                // headMap is exclusive: the +1 tick bound makes tasks due
                // exactly at nowTick eligible (the same-tick deadline rule).
                Iterator<Map.Entry<Long, DomainTask>> it =
                        queue.delayed.headMap((nowTick + 1) << 24).entrySet().iterator();
                while (it.hasNext()) {
                    DomainTask task = it.next().getValue();
                    it.remove();
                    queue.ready.addLast(task);
                }
            }
        }
    }

    private int drainReady(DomainQueue queue) {
        int executed = 0;
        synchronized (queue) {
            while (!queue.ready.isEmpty()) {
                DomainTask task = queue.ready.pollFirst();
                if (task == null || task.isCancelled()) {
                    continue; // cancelled queued work: dropped, never resurrected
                }
                long start = System.nanoTime();
                try {
                    task.run(); // the domain's gate + binding are the task's own
                } catch (Throwable t) {
                    LOGGER.log(java.util.logging.Level.SEVERE,
                            "Simulation task failed in domain '" + queue.domain.name()
                                    + "'; skipping it", t);
                } finally {
                    queue.lastDurationNanos.set(System.nanoTime() - start);
                    queue.executedCount.incrementAndGet();
                    executed++;
                }
            }
        }
        return executed;
    }

    /** @return whether the scheduler accepts submissions. */
    public boolean isShutdown() {
        return shutdown.get();
    }

    /**
     * Stops admission permanently and cancels every queued and delayed task
     * — shutdown is total: nothing queued runs after this point (the design's
     * "shutdown with queued and running tasks" contract). Idempotent.
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        for (DomainQueue queue : queues.values()) {
            synchronized (queue) {
                queue.ready.forEach(DomainTask::cancel);
                queue.ready.clear();
                queue.delayed.values().forEach(DomainTask::cancel);
                queue.delayed.clear();
            }
        }
        LOGGER.fine("Simulation scheduler shut down; queued work cancelled");
    }

    /** @return the pending (ready + delayed) task count for the domain. */
    public int pendingFor(OwnershipDomain domain) {
        DomainQueue queue = queues.get(domain.name());
        return queue == null ? 0 : queue.pending();
    }

    /** @return the last executed task's duration in nanos for the domain (telemetry, design §17). */
    public long lastDurationNanos(OwnershipDomain domain) {
        DomainQueue queue = queues.get(domain.name());
        return queue == null ? 0 : queue.lastDurationNanos.get();
    }

    /** @return how many tasks the domain executed (telemetry). */
    public long executedCount(OwnershipDomain domain) {
        DomainQueue queue = queues.get(domain.name());
        return queue == null ? 0 : queue.executedCount.get();
    }

    /** @return the refused-submission count (the backpressure telemetry). */
    public long rejectedSubmissions() {
        return rejectedSubmissions;
    }
}
