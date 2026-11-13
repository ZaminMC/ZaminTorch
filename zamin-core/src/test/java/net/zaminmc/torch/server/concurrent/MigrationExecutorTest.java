package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE SPLIT/MERGE EXECUTOR SEATS (Phase 8, Wave MIG-3 — the executors over
 * the REAL partition + scheduler + router, the MIG-1-proven protocol
 * applied to the region routing table):
 *
 * <ol>
 *   <li><b>the split's whole walk</b> — the base's in-flight tasks, delayed
 *       work and inbound cross-owner intents drain at the old generation
 *       inside the old owner's context, the subdivision installs as the
 *       transfer payload, the publish bumps the base's generation and
 *       re-opens admission, and the ROUTING answers the new law (lower-half
 *       chunks → base, upper-half chunks → the fresh half) — with the
 *       post-split submissions landing on the half's own authority;</li>
 *   <li><b>the refusals</b> — an already-subdivided region, an atomic
 *       (1-chunk) grid: nothing noted, nothing moved, the telemetry
 *       counters unchanged;</li>
 *   <li><b>the merge's whole walk</b> — the half's queued work and intents
 *       drain at its final boundary, the routing returns to the base, the
 *       retired half's generation bumps (stale forever), and the base's
 *       own authority is never held;</li>
 *   <li><b>the boundary scope's census law</b> — the subdivided region
 *       contributes each half the box actually touches, base first (the
 *       global acquisition order stays deadlock-free).</li>
 * </ol>
 */
class MigrationExecutorTest {

    /** Burns exactly the given nanos (the load seats' busy shape). */
    private static void busyNanos(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }

    @Test
    void aSplitWalksTheWholeProtocolAndMovesTheUpperHalfsRouting() {
        RegionPartition partition = new RegionPartition("w", 8);
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        long regionId = partition.regionId(0, 0); // region (0,0): chunks [0..7]×[0..7]
        OwnershipDomain base = partition.domainFor(0, 0);
        long generationBefore = base.generation();
        long publishedBefore = OwnershipMigration.Telemetry.publishedCount();

        // The live shape: in-flight (2), delayed (due 10), one inbound intent.
        AtomicInteger inflightRan = new AtomicInteger();
        scheduler.submit(base, inflightRan::incrementAndGet);
        scheduler.submit(base, inflightRan::incrementAndGet);
        AtomicBoolean delayedRan = new AtomicBoolean(false);
        scheduler.submitDelayed(base, () -> delayedRan.set(true), 10L);
        OwnershipDomain neighbor = partition.domainFor(100, 100); // another region
        AtomicBoolean intentApplied = new AtomicBoolean(false);
        assertTrue(router.submit(new CrossOwnerRouter.Intent(base,
                UUID.randomUUID(), neighbor.name(), neighbor.generation(),
                "boundary-write", () -> {
                    assertTrue(base.inContext(),
                            "the intent applies at the base's final boundary");
                    intentApplied.set(true);
                })));

        MigrationExecutors.SplitOutcome outcome = MigrationExecutors.splitRegion(
                partition, scheduler, router, regionId, 10L);

        // The whole protocol walked: everything drained at the old generation.
        assertEquals(2, inflightRan.get());
        assertTrue(delayedRan.get(), "the delayed work drained inside the window");
        assertTrue(intentApplied.get(), "the inbound intent applied at the old boundary");
        assertTrue(outcome.drainedTasks() >= 3, "in-flight + delayed drained: "
                + outcome.drainedTasks());
        assertEquals(1, outcome.drainedIntents());
        assertEquals(0, scheduler.pendingFor(base));

        // The publish: the base's generation bumped, admission re-opened,
        // the telemetry counted exactly one publication.
        assertEquals(generationBefore + 1, base.generation());
        assertFalse(scheduler.isAdmissionHeld(base));
        assertEquals(publishedBefore + 1, OwnershipMigration.Telemetry.publishedCount());

        // THE ROUTING LAW: the deterministic split along chunk X at the
        // region's midpoint (origin 0, R=8 → boundary 4; the base keeps
        // [0..3], the half takes [4..7]).
        assertEquals(4, outcome.atChunk());
        assertEquals(RegionPartition.Subdivision.AXIS_X, outcome.axis());
        assertSame(base, outcome.base());
        assertSame(outcome.half(), partition.domainFor(4, 0));
        assertSame(base, partition.domainFor(3, 0));
        assertSame(outcome.half(), partition.domainFor(7, 7));
        assertSame(base, partition.domainFor(0, 7));
        // The neighborhood region is untouched.
        assertSame(neighbor, partition.domainFor(100, 100));

        // The subdivision is the partition's active record.
        RegionPartition.Subdivision sub = partition.subdivisionOf(regionId);
        assertSame(outcome.half(), sub.half());
        assertEquals(1, partition.subdivisionCount());

        // The ownership consult answers the new law.
        assertTrue(partition.owns(3, 0, base));
        assertFalse(partition.owns(4, 0, base));
        assertTrue(partition.owns(4, 0, outcome.half()));

        // Post-split submissions land on the half's own authority and run
        // in ITS context (the fresh half serves its own queue).
        AtomicBoolean halfTaskRan = new AtomicBoolean(false);
        scheduler.submit(partition.domainFor(5, 5), () -> {
            assertTrue(outcome.half().inContext(),
                    "the upper half's task runs in the half's context");
            halfTaskRan.set(true);
        });
        scheduler.runDue(11L);
        assertTrue(halfTaskRan.get());

        // The domains census carries both halves in the stable name order.
        List<OwnershipDomain> census = partition.domains();
        assertTrue(census.contains(base));
        assertTrue(census.contains(outcome.half()));

        scheduler.shutdown();
        router.shutdown();
    }

    @Test
    void aSplitRefusesTheAlreadySubdividedRegionAndTheAtomicGrid() {
        RegionPartition partition = new RegionPartition("w", 8);
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        long regionId = partition.regionId(0, 0);
        MigrationExecutors.splitRegion(partition, scheduler, router, regionId, 1L);

        long publishedBefore = OwnershipMigration.Telemetry.publishedCount();
        long abortedBefore = OwnershipMigration.Telemetry.abortedCount();
        assertThrows(IllegalStateException.class,
                () -> MigrationExecutors.splitRegion(partition, scheduler, router, regionId, 2L),
                "the single-level law refuses the second split");
        assertEquals(publishedBefore, OwnershipMigration.Telemetry.publishedCount(),
                "the refusal moved nothing");
        assertEquals(abortedBefore, OwnershipMigration.Telemetry.abortedCount());

        // The atomic grid: a 1-chunk region has no halves.
        RegionPartition atomic = new RegionPartition("atomic", 1);
        assertThrows(IllegalStateException.class, () -> MigrationExecutors.splitRegion(
                atomic, scheduler, router, atomic.regionId(0, 0), 1L));
        assertEquals(publishedBefore, OwnershipMigration.Telemetry.publishedCount());

        scheduler.shutdown();
        router.shutdown();
    }

    @Test
    void aMergeRetiresTheHalfWholeAndReturnsTheRoutingToTheBase() {
        RegionPartition partition = new RegionPartition("w", 8);
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        long regionId = partition.regionId(0, 0);
        OwnershipDomain base = partition.domainFor(0, 0);
        MigrationExecutors.SplitOutcome split = MigrationExecutors.splitRegion(
                partition, scheduler, router, regionId, 1L);
        OwnershipDomain half = split.half();
        long halfGeneration = half.generation();
        long publishedBefore = OwnershipMigration.Telemetry.publishedCount();

        // The half carries its own live shape: queued work + one intent.
        AtomicBoolean halfWorkRan = new AtomicBoolean(false);
        scheduler.submit(half, () -> {
            assertTrue(half.inContext());
            halfWorkRan.set(true);
        });
        AtomicBoolean halfIntentApplied = new AtomicBoolean(false);
        assertTrue(router.submit(new CrossOwnerRouter.Intent(half,
                UUID.randomUUID(), base.name(), base.generation(),
                "half-write", () -> halfIntentApplied.set(true))));

        MigrationExecutors.MergeOutcome outcome = MigrationExecutors.mergeRegion(
                partition, scheduler, router, regionId, 2L);

        assertTrue(halfWorkRan.get(), "the half's queued work drained at its final boundary");
        assertTrue(halfIntentApplied.get(), "the half's intent applied at its final boundary");
        assertEquals(1, outcome.drainedTasks());
        assertEquals(1, outcome.drainedIntents());
        assertEquals(halfGeneration + 1, half.generation(),
                "the retired half's generation bumped — stale forever");
        assertSame(base, outcome.base());
        assertSame(half, outcome.retiredHalf());
        assertEquals(publishedBefore + 1, OwnershipMigration.Telemetry.publishedCount());

        // The routing returned to the base whole; the subdivision is gone.
        assertNull(partition.subdivisionOf(regionId));
        assertEquals(0, partition.subdivisionCount());
        assertSame(base, partition.domainFor(4, 0));
        assertSame(base, partition.domainFor(7, 7));
        assertSame(base, partition.domainFor(0, 0));

        // The base's own authority was never held, and the post-merge
        // submissions against the returned chunks run on the base's queue.
        assertFalse(scheduler.isAdmissionHeld(base));
        assertFalse(scheduler.isAdmissionHeld(half));
        AtomicBoolean postMergeRan = new AtomicBoolean(false);
        scheduler.submit(partition.domainFor(5, 5), () -> {
            assertTrue(base.inContext(), "the returned chunk's task runs in the base's context");
            postMergeRan.set(true);
        });
        scheduler.runDue(3L);
        assertTrue(postMergeRan.get());

        // A later re-split mints a FRESH half (the serial law: the retired
        // authority never aliases a queue or a ticket).
        MigrationExecutors.SplitOutcome resplit = MigrationExecutors.splitRegion(
                partition, scheduler, router, regionId, 4L);
        assertNotEquals(half.name(), resplit.half().name(),
                "the re-split's half is a fresh domain, never the retired one");

        scheduler.shutdown();
        router.shutdown();
    }

    @Test
    void aMergeRefusesTheUndividedRegion() {
        RegionPartition partition = new RegionPartition("w", 8);
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        long regionId = partition.regionId(0, 0);
        partition.domainFor(0, 0); // the base exists

        long publishedBefore = OwnershipMigration.Telemetry.publishedCount();
        assertThrows(IllegalStateException.class,
                () -> MigrationExecutors.mergeRegion(partition, scheduler, router, regionId, 1L));
        assertEquals(publishedBefore, OwnershipMigration.Telemetry.publishedCount());

        scheduler.shutdown();
        router.shutdown();
    }

    @Test
    void theBoundaryScopeCensusCoversExactlyTheHalvesTheBoxTouches() {
        RegionPartition partition = new RegionPartition("w", 8);
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        long regionId = partition.regionId(0, 0);
        OwnershipDomain base = partition.domainFor(0, 0);
        MigrationExecutors.SplitOutcome split = MigrationExecutors.splitRegion(
                partition, scheduler, router, regionId, 1L);
        OwnershipDomain half = split.half();

        // A lower-half box (chunks x 0..3) enters ONLY the base.
        List<OwnershipDomain> lower = partition.domainsBetween(0, 0, 3, 7);
        assertEquals(List.of(base), lower);
        // An upper-half box (chunks x 4..7) enters ONLY the half.
        List<OwnershipDomain> upper = partition.domainsBetween(4, 0, 7, 7);
        assertEquals(List.of(half), upper);
        // A seam-spanning box enters BOTH, base first (the global
        // (region, base-before-half) acquisition order — deadlock-free).
        List<OwnershipDomain> seam = partition.domainsBetween(2, 0, 5, 7);
        assertEquals(List.of(base, half), seam);
        // A negative-coordinate seam in another region answers the same law.
        MigrationExecutors.SplitOutcome negSplit = MigrationExecutors.splitRegion(
                partition, scheduler, router, partition.regionId(-1, 0), 1L);
        List<OwnershipDomain> negSeam = partition.domainsBetween(-6, 0, -3, 0);
        assertEquals(List.of(partition.baseDomainFor(partition.regionId(-1, 0)),
                negSplit.half()), negSeam);

        scheduler.shutdown();
        router.shutdown();
    }

    @Test
    void theRoutingTableSurvivesConcurrentReadersAcrossTheSplit() throws Exception {
        RegionPartition partition = new RegionPartition("w", 8);
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        long regionId = partition.regionId(0, 0);
        OwnershipDomain base = partition.domainFor(0, 0);

        // A concurrent reader hammers the routing table while the split
        // walks its protocol: every read answers SOME consistent authority
        // (the pre-split base or the post-split half — never an exception,
        // never a third domain).
        java.util.concurrent.CountDownLatch spinning =
                new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicBoolean sawHalf = new AtomicBoolean(false);
        Thread reader = new Thread(() -> {
            spinning.countDown(); // the reader is IN the loop before the split
            while (!stopped.get()) {
                OwnershipDomain owner = partition.domainFor(6, 6);
                if (owner != null && owner != base) {
                    sawHalf.set(true);
                }
            }
        }, "routing-reader");
        reader.start();
        assertTrue(spinning.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "the reader entered its loop");
        try {
            MigrationExecutors.SplitOutcome outcome = MigrationExecutors.splitRegion(
                    partition, scheduler, router, regionId, 1L);
            // Wait for the observation (a deadline, never a fixed spin — a
            // loaded machine may schedule the reader late).
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit
                    .SECONDS.toNanos(5);
            while (!sawHalf.get() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertTrue(sawHalf.get(), "the reader observed the half's routing");
        } finally {
            stopped.set(true);
            reader.join(5_000);
        }
        assertFalse(reader.isAlive(), "the reader terminated");

        scheduler.shutdown();
        router.shutdown();
    }
}
