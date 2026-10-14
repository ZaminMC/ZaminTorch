package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The master spec §28.8 adversarial list — the repeatable proofs that the
 * engine's own execution contracts make the forbidden patterns impossible
 * or loudly diagnosable, before any plugin ever runs on them:
 *
 * <ul>
 *   <li>case 1 — reciprocal owner requests: two owners request each other
 *       at the same time; neither may block the progress the other needs
 *       (the one-way intent model is the proof);</li>
 *   <li>case 2 — same-pool self-wait: a task that blocks waiting for work
 *       queued behind itself onto the same bounded pool is DETECTED and
 *       REJECTED, never silently entered;</li>
 *   <li>case 9 — the noisy owner: one domain floods its own admission past
 *       the bound while unrelated owners retain progress and the overload
 *       stays observable;</li>
 *   <li>case 10 — blocking I/O pressure: slow blocking work runs on the
 *       I/O lane's own threads and never consumes simulation-worker
 *       capacity;</li>
 *   <li>case 12 — shutdown: the whole stack (scheduler, router, compute,
 *       I/O lane, worker pool) terminates with in-flight and queued work in
 *       bounded time, no dependency cycle.</li>
 * </ul>
 *
 * <p>Case 6 (disable during execution) is recorded as a concrete blocker:
 * the plugin lifecycle does not exist yet (the §28.5 state machine lands
 * with the plugin loader slice), so "disable a plugin while its callback
 * runs" has no subject. The engine-side equivalents that exist and are
 * covered elsewhere: scheduler cancel-is-permanent
 * ({@code SimulationSchedulerTest}), compute/lane tryCancel-while-queued
 * ({@code ComputeSubsystemTest}, {@code BlockingIoLaneTest}), and the
 * router/lane shutdown-cancels-queued walks. Cases 3, 4, 5, 7, 8, and 11
 * are covered by their own suites ({@code OwnershipDomainTest},
 * {@code OwnershipMigrationTest}, {@code EventBusUnitTest},
 * {@code RegionBoundaryTest}, {@code DomainWorkerPoolTest}).</p>
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class AdversarialContractTest {

    private DomainWorkerPool pool;
    private BlockingIoLane lane;

    @AfterEach
    void stopInfra() {
        if (lane != null) {
            lane.shutdown();
            lane = null;
        }
        if (pool != null) {
            pool.shutdown();
            pool = null;
        }
    }

    // §28.8 case 1 — reciprocal owner requests.

    @Test
    void reciprocalOwnerRequestsNeitherBlocksTheOther() {
        pool = new DomainWorkerPool(2);
        CrossOwnerRouter router = new CrossOwnerRouter();
        OwnershipDomain a = OwnershipDomain.create("adv:recip-a");
        OwnershipDomain b = OwnershipDomain.create("adv:recip-b");
        ConcurrentLinkedQueue<String> appliedLog = new ConcurrentLinkedQueue<>();
        AtomicInteger aRequestsToB = new AtomicInteger();
        AtomicInteger bRequestsToA = new AtomicInteger();

        // Round 1: A's drain, in A's context, submits a request TO B; B's
        // drain, at the same time, submits a request TO A. Both drains run
        // concurrently on the pool; neither submission waits for the other
        // (the one-way intent admission is the whole proof).
        pool.dispatchAndJoin(List.of(
                bindDrain(a, () -> {
                    assertTrue(a.inContext());
                    router.submit(new CrossOwnerRouter.Intent(b, UUID.randomUUID(),
                            a.name(), a.generation(), "ping-a-to-b",
                            () -> {
                                assertTrue(b.inContext(),
                                        "the intent applies in the TARGET's context");
                                appliedLog.add("b-applied-a-ping");
                            }));
                    aRequestsToB.incrementAndGet();
                }),
                bindDrain(b, () -> {
                    assertTrue(b.inContext());
                    router.submit(new CrossOwnerRouter.Intent(a, UUID.randomUUID(),
                            b.name(), b.generation(), "pong-b-to-a",
                            () -> {
                                assertTrue(a.inContext(),
                                        "the intent applies in the TARGET's context");
                                appliedLog.add("a-applied-b-pong");
                            }));
                    bRequestsToA.incrementAndGet();
                })));

        assertEquals(1, aRequestsToB.get());
        assertEquals(1, bRequestsToA.get());
        assertEquals(2, router.pendingFor(a) + router.pendingFor(b),
                "both intents wait in their target queues (applied at the next drain)");

        // Round 2: each target's drain applies the other's request. The two
        // drains again run concurrently; each intent validates in its own
        // target's context.
        pool.dispatchAndJoin(List.of(
                bindDrain(a, () -> router.drainIntents(a)),
                bindDrain(b, () -> router.drainIntents(b))));

        assertEquals(2, appliedLog.size(), "both reciprocal requests applied exactly once");
        assertTrue(appliedLog.contains("b-applied-a-ping"));
        assertTrue(appliedLog.contains("a-applied-b-pong"));
        assertEquals(0, router.pendingFor(a) + router.pendingFor(b));
        assertEquals(2, router.appliedCount(a) + router.appliedCount(b));
        // No duplicates: the exactly-once window is the deduplication.
        assertEquals(0, router.duplicatesDropped());
        router.shutdown();
    }

    // §28.8 case 2 — same-pool self-wait.

    @Test
    void samePoolSelfWaitIsDetectedAndRejectedLoudly() {
        pool = new DomainWorkerPool(2);
        CountDownLatch healthyRan = new CountDownLatch(1);
        AtomicReference<IllegalStateException> refusal = new AtomicReference<>();

        // The mis-built reentrant dispatch: a job running ON a pool worker
        // calls dispatchAndJoin on the SAME pool — the waiter would park on
        // the completion latch while its waited-for work needs the very
        // capacity it occupies.
        long start = System.nanoTime();
        pool.dispatchAndJoin(List.of(
                healthyRan::countDown,
                () -> {
                    try {
                        pool.dispatchAndJoin(List.of(() -> { }));
                    } catch (IllegalStateException e) {
                        refusal.set(e);
                    }
                }));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMillis < 5_000,
                "the refusal returned in bounded time (" + elapsedMillis + "ms), no hang");
        assertNotNull(refusal.get(), "the self-wait attempt was refused");
        assertTrue(refusal.get().getMessage().contains("Same-pool self-wait refused"),
                "the diagnostic names the cycle: " + refusal.get().getMessage());
        assertEquals(0, healthyRan.getCount(),
                "the unrelated job in the same batch still ran");
        assertEquals(2, pool.dispatchedCount(),
                "the batch dispatched exactly two jobs");
        assertEquals(1, pool.refusedSelfWaitCount(),
                "the cycle-detection telemetry counted the refusal");
        // The pool stays healthy after the refusal: the next dispatch runs.
        AtomicInteger after = new AtomicInteger();
        pool.dispatchAndJoin(List.of(after::incrementAndGet));
        assertEquals(1, after.get());
        assertEquals(0, pool.failedJobCount(),
                "the refusal was caught by the job itself, not a worker failure");
    }

    // §28.8 case 9 — the noisy owner.

    @Test
    void theNoisyOwnerOverloadsItselfWhileUnrelatedOwnersRetainProgress() {
        pool = new DomainWorkerPool(2);
        // A deliberately tiny bound: the noisy owner floods past it at once.
        SimulationScheduler scheduler = new SimulationScheduler(8);
        OwnershipDomain noisy = OwnershipDomain.create("adv:noisy");
        OwnershipDomain quiet = OwnershipDomain.create("adv:quiet");
        AtomicInteger noisyRefusals = new AtomicInteger();
        AtomicInteger quietRuns = new AtomicInteger();
        AtomicReference<Long> quietCompletedAt = new AtomicReference<>();
        AtomicReference<Long> noisyDrainEndedAt = new AtomicReference<>();

        // The noisy owner floods its own admission bound. The 8th task is
        // the drain's FIFO last — it stamps the noisy drain's own end.
        for (int i = 0; i < 8; i++) {
            final boolean last = i == 7;
            scheduler.submit(noisy, () -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(30); // a busy drain: ~240ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (last) {
                    noisyDrainEndedAt.set(System.nanoTime());
                }
            });
        }
        for (int i = 0; i < 4; i++) {
            try {
                scheduler.submit(noisy, () -> { });
            } catch (IllegalStateException overBound) {
                noisyRefusals.incrementAndGet(); // the loud refusal, surfaced
            }
        }
        assertEquals(4, noisyRefusals.get(),
                "the flood past the bound refused loudly every time");
        assertEquals(4, scheduler.rejectedSubmissions(),
                "the overload stayed observable in the telemetry");

        // The quiet owner's ordinary work rides the SAME tick.
        scheduler.submit(quiet, () -> {
            quietRuns.incrementAndGet();
            quietCompletedAt.set(System.nanoTime());
        });

        // One tick: both domains' drains dispatch to the pool. The quiet
        // domain's work must complete while the noisy drain is still
        // grinding — different workers, no shared fate.
        long tickStart = System.nanoTime();
        scheduler.runDueParallel(1, pool);
        long tickMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tickStart);

        assertEquals(1, quietRuns.get(), "the quiet owner completed its work");
        assertEquals(8, scheduler.executedCount(noisy));
        assertEquals(1, scheduler.executedCount(quiet));
        assertNotNull(quietCompletedAt.get());
        assertNotNull(noisyDrainEndedAt.get(), "the noisy drain stamped its own end");
        assertTrue(tickMillis < 5_000,
                "the tick edge joined in bounded time (" + tickMillis + "ms)");
        // The quiet task completed BEFORE the noisy drain's own last task:
        // the quiet owner never waited for the noisy one's grind.
        assertTrue(quietCompletedAt.get() <= noisyDrainEndedAt.get(),
                "the quiet owner finished inside the noisy drain's own window");
    }

    // §28.8 case 10 — blocking I/O pressure.

    @Test
    void blockingIoPressureNeverConsumesSimulationWorkers() throws Exception {
        pool = new DomainWorkerPool(2);
        lane = new BlockingIoLane(16, 3);
        OwnershipDomain owner = OwnershipDomain.create("adv:io-owner");
        ConcurrentLinkedQueue<String> ioThreadNames = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> simThreadNames = new ConcurrentLinkedQueue<>();
        CountDownLatch ioHolding = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger freshApplies = new AtomicInteger();

        // Three slow blocking tasks park the I/O lane's workers — a stand-in
        // for slow database/HTTP/filesystem calls in flight.
        for (int i = 0; i < 3; i++) {
            final String label = "io-done-" + i;
            lane.submit(owner, "slow-io-" + i, () -> {
                ioThreadNames.add(Thread.currentThread().getName());
                ioHolding.countDown();
                release.await();
                return label;
            }, (value, gen) -> freshApplies.incrementAndGet(), () -> { });
        }
        assertTrue(ioHolding.await(5, TimeUnit.SECONDS),
                "the I/O lane picked up all three tasks");
        assertEquals(0, lane.queuedDepth());

        // Meanwhile the simulation runs its ticks through the worker pool.
        for (int tick = 0; tick < 3; tick++) {
            pool.dispatchAndJoin(List.of(
                    () -> simThreadNames.add(Thread.currentThread().getName()),
                    () -> simThreadNames.add(Thread.currentThread().getName())));
        }

        // The two thread families are disjoint: the blocking work never
        // ran on a simulation worker and the simulation never ran on the
        // I/O lane.
        for (String name : ioThreadNames) {
            assertTrue(name.startsWith("torch-io-"),
                    "the blocking work ran on the I/O lane: " + name);
        }
        for (String name : simThreadNames) {
            assertTrue(name.startsWith("zamin-domain-"),
                    "the simulation ran on the worker pool: " + name);
        }
        assertEquals(6, simThreadNames.size());

        // Release the pressure: the completions land at the owner's drain.
        release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (lane.pendingDeliveries(owner) < 3 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertEquals(3, lane.pendingDeliveries(owner));
        owner.enter();
        try {
            assertEquals(3, lane.drainCompletions(owner));
        } finally {
            owner.exit();
        }
        assertEquals(3, freshApplies.get());
    }

    // §28.8 case 12 — shutdown with work everywhere.

    @Test
    void theFullStackShutsDownWithWorkInFlightAndQueued() throws Exception {
        pool = new DomainWorkerPool(2);
        lane = new BlockingIoLane(16, 2);
        CrossOwnerRouter router = new CrossOwnerRouter();
        SimulationScheduler scheduler = new SimulationScheduler(64);
        ComputeSubsystem compute = new ComputeSubsystem(64, 2);
        OwnershipDomain shutdownDomain = OwnershipDomain.create("adv:shutdown");

        // Work everywhere: TWO in-flight blocking calls (one per lane
        // worker, so the third task stays QUEUED), the third task queued
        // behind them, one queued router intent, one queued scheduler
        // task, and one submitted computation.
        CountDownLatch workersParked = new CountDownLatch(2);
        AtomicReference<String> firstOutcome = new AtomicReference<>("never");
        AtomicReference<String> secondOutcome = new AtomicReference<>("never");
        CountDownLatch secondHold = new CountDownLatch(1);
        lane.submit(shutdownDomain, "in-flight-io-one", () -> {
            workersParked.countDown();
            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                firstOutcome.set("interrupted");
                throw e;
            }
            return "done";
        }, (v, g) -> { }, () -> { });
        lane.submit(shutdownDomain, "in-flight-io-two", () -> {
            workersParked.countDown();
            try {
                secondHold.await();
            } catch (InterruptedException e) {
                secondOutcome.set("interrupted");
                throw e;
            }
            return "done";
        }, (v, g) -> { }, () -> { });
        assertTrue(workersParked.await(5, TimeUnit.SECONDS),
                "both lane workers hold an in-flight call");
        BlockingIoLane.IoHandle<?> queuedIo = lane.submit(shutdownDomain, "queued-io",
                () -> "q", (v, g) -> { }, () -> { });

        OwnershipDomain source = OwnershipDomain.create("adv:shutdown-src");
        source.enter();
        try {
            router.submit(new CrossOwnerRouter.Intent(shutdownDomain, UUID.randomUUID(),
                    source.name(), source.generation(), "pending-intent", () -> { }));
        } finally {
            source.exit();
        }
        assertEquals(1, router.pendingFor(shutdownDomain));

        scheduler.submit(shutdownDomain, () -> { });
        compute.submit(shutdownDomain, "immutable-input",
                input -> "computed", (value, gen) -> { }, () -> { });

        // Shut the whole stack down, scheduler first (no new work), the
        // pool last (the tick thread's own). Every shutdown is bounded by
        // its own implementation (joins with timeouts) — this test's own
        // timeout is the cycle-free proof.
        scheduler.shutdown();
        router.shutdown();
        compute.shutdown();
        lane.shutdown();
        pool.shutdown();

        assertTrue(scheduler.isShutdown());
        assertEquals(0, scheduler.pendingFor(shutdownDomain),
                "the scheduler cancelled the queued task");
        assertEquals(0, router.pendingFor(shutdownDomain),
                "the router cancelled the queued intent");
        assertTrue(lane.isShutdown());
        assertEquals("interrupted", firstOutcome.get(),
                "the first in-flight call was interrupted in bounded time");
        assertEquals("interrupted", secondOutcome.get(),
                "the second in-flight call was interrupted in bounded time");
        assertTrue(queuedIo.isCancelled(),
                "the queued I/O task was cancelled and never runs");
        assertEquals(1, lane.cancelledAtShutdownCount());
        assertTrue(pool.isShutdown());
    }

    /** A pool job that binds the domain, runs the drain body in its context,
     *  and releases — the test's drain wrapper. */
    private static Runnable bindDrain(OwnershipDomain domain, Runnable body) {
        return () -> {
            domain.enter();
            try {
                body.run();
            } finally {
                domain.exit();
            }
        };
    }
}
