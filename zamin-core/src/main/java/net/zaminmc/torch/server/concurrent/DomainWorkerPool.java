package net.zaminmc.torch.server.concurrent;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The physical simulation worker pool (the permanent architecture's Phase 4
 * second unit — the design's §5 scheduling model and the master spec's §18
 * "workers are shared execution resources"): independent ownership domains
 * dispatch one drain job per domain per tick here and the workers run the
 * different domains' due work <em>concurrently</em> — one active task per
 * domain is still enforced by each task's own gate ({@link DomainTask}),
 * so a domain never gains a second writer by having a worker; the workers
 * are resources, not owners.
 *
 * <p>The dispatch is a batch with a join: {@link #dispatchAndJoin(List)}
 * submits every job and waits for all of them before returning. The engine's
 * tick calls it from the tick thread, so the join is the tick-edge barrier
 * — the documented hybrid of the design's §8 (all domains share the one
 * logical tick clock and the clock advances only after every domain's due
 * work finished; no domain is ever left running behind silently). This is
 * an <em>enforced</em> barrier, not a hidden one: the design forbids
 * claiming "all domains are on one perfectly synchronized tick unless the
 * implementation actually enforces it" — this class is that enforcement.</p>
 *
 * <p>Sizing (the design's §12 oversubscription rule, shared with the compute
 * subsystem's convention): conservatively below the machine's reported cores
 * — {@code max(1, cores / 2)} capped at 4 — because GC threads, the network
 * event loops, and the compute pool share the same machine and the reported
 * logical CPU count is not the same as the available simulation budget. Idle
 * workers park on the dispatch queue and consume no CPU (the master spec's
 * idle-worker rule).</p>
 *
 * <p>Backpressure and shutdown (design invariants 8 and 11): the pool is
 * sized for the per-tick job batch (one job per domain), so admission is
 * naturally bounded by the domain count; {@link #shutdown()} stops
 * admission and joins in-flight jobs — idempotent, and a job failure is
 * isolated (logged, counted, never kills a worker or the other jobs).</p>
 */
public final class DomainWorkerPool {

    private static final Logger LOGGER = Logger.getLogger(DomainWorkerPool.class.getName());

    /** The worker count cap: simulation workers must not oversubscribe the machine. */
    static final int MAX_WORKERS = 4;

    private final Thread[] workers;
    private final java.util.concurrent.BlockingQueue<Runnable> dispatch =
            new java.util.concurrent.LinkedBlockingQueue<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicLong dispatched = new AtomicLong();
    private final AtomicLong failedJobs = new AtomicLong();

    public DomainWorkerPool() {
        this(defaultWorkerCount());
    }

    public DomainWorkerPool(int workerCount) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        this.workers = new Thread[workerCount];
        for (int i = 0; i < workerCount; i++) {
            Thread worker = new Thread(this::workerLoop, "zamin-domain-" + i);
            worker.setDaemon(false);
            this.workers[i] = worker;
            worker.start();
        }
        LOGGER.fine("Domain worker pool started with " + workerCount + " workers");
    }

    /**
     * The conservative default: half the reported cores, capped — the
     * simulation pool shares the machine with GC, network event loops, and
     * the compute pool (which already takes cores/4).
     */
    public static int defaultWorkerCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(MAX_WORKERS, cores / 2));
    }

    private void workerLoop() {
        while (true) {
            Runnable job;
            try {
                job = dispatch.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // the shutdown interrupts the workers
            }
            if (job == POISON) {
                return; // the total shutdown's exit marker
            }
            try {
                job.run();
            } catch (Throwable t) {
                // A job's own failure is isolated: logged, counted, and the
                // worker stays live for the next dispatch (the same policy
                // the tick loop and the scheduler ride — one failure never
                // kills the executor).
                failedJobs.incrementAndGet();
                LOGGER.log(Level.SEVERE, "Domain worker job failed; isolated", t);
            }
        }
    }

    private static final Runnable POISON = () -> { };

    /**
     * Dispatches every job and waits for all of them (the tick-edge join).
     * Any thread may call it; the engine's tick thread is the intended
     * caller. A job throwing is isolated by the worker loop — the join
     * still completes for the whole batch (a failing domain never blocks
     * the other domains' progress, and the tick edge stays defined).
     *
     * @throws IllegalStateException when the pool is shut down
     */
    public void dispatchAndJoin(List<Runnable> jobs) {
        Objects.requireNonNull(jobs, "jobs");
        if (shutdown.get()) {
            throw new IllegalStateException("Domain worker pool is shut down; dispatch refused");
        }
        if (jobs.isEmpty()) {
            return;
        }
        CountDownLatch done = new CountDownLatch(jobs.size());
        for (Runnable job : jobs) {
            Objects.requireNonNull(job, "job");
            dispatched.incrementAndGet();
            dispatch.offer(() -> {
                try {
                    job.run();
                } finally {
                    done.countDown();
                }
            });
        }
        awaitAll(done);
    }

    private void awaitAll(CountDownLatch done) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    done.await();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true; // keep joining: the tick edge waits
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Stops admission and exits the workers — the total, idempotent
     * shutdown. A job already running completes (its latch waiter is the
     * dispatcher, which by contract has returned or is returning); queued
     * jobs submitted after this point are refused loudly.
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        for (Thread worker : workers) {
            dispatch.offer(POISON);
        }
        for (Thread worker : workers) {
            try {
                worker.join(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        dispatch.clear();
        LOGGER.fine("Domain worker pool shut down");
    }

    /** @return whether the pool accepts dispatches. */
    public boolean isShutdown() {
        return shutdown.get();
    }

    /** @return the configured worker count. */
    public int workerCount() {
        return workers.length;
    }

    /** @return the total dispatched job count (telemetry, design §17). */
    public long dispatchedCount() {
        return dispatched.get();
    }

    /** @return the isolated-failure count (telemetry). */
    public long failedJobCount() {
        return failedJobs.get();
    }
}
