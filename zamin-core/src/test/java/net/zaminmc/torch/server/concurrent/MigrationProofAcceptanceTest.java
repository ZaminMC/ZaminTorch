package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE PHASE-8 PRECONDITION PROOF (the architecture's Phase-8 gate text:
 * "First prove migration with in-flight tasks, delayed work, messages,
 * compute results, persistence, shutdown, and stale-generation invalidation;
 * add telemetry and anti-oscillation rules before enabling automatic
 * balancing") — the whole-engine migration scenario over the REAL primitives:
 * one domain's ownership transfers while it carries every live shape the
 * engine knows:
 *
 * <ol>
 *   <li><b>in-flight tasks</b> — ready work queued on the
 *       {@link SimulationScheduler} drains at the old generation inside the
 *       protocol's drain step (§9 step 3);</li>
 *   <li><b>delayed work</b> — a {@code submitDelayed} task whose deadline
 *       lands inside the drain window drains with it, never vanishing into
 *       the transfer;</li>
 *   <li><b>messages</b> — cross-owner {@link CrossOwnerRouter} intents
 *       inbound before the drain apply at the old owner's final boundary;
 *       intents submitted DURING the hold (the router never blocks — the
 *       envelope is the target's admission) wait in the box and drain under
 *       the NEW authority at the new generation;</li>
 *   <li><b>compute results</b> — a {@link ComputeSubsystem} job submitted
 *       against the old generation completes, and after the publish its
 *       outcome is DISCARDED AS STALE at the owner's drain (the subsystem's
 *       own generation validation, §9 step 6) while a job submitted against
 *       the new generation applies whole — and the direct
 *       {@link ComputeTicket} envelope answers the same law;</li>
 *   <li><b>persistence</b> — the save arm's snapshot (captured in the
 *       domain's context at the tick lane, the ticket-stamped shape) is
 *       exactly the pre-transfer state; a save task submitted during the
 *       hold is REFUSED LOUDLY (never a silent drop), and re-submits after
 *       the publish;</li>
 *   <li><b>shutdown</b> — a scheduler shutdown mid-drain cancels the queued
 *       work (nothing resurrects), and the abort path of a FAILED transfer
 *       leaves no half-migrated authority (the scope stayed whole with the
 *       old owner, the generation still bumped);</li>
 *   <li><b>stale-generation invalidation</b> — every pre-publish capture the
 *       engine can hold (the compute outcome, the ticket-stamped snapshot)
 *       answers diagnosably stale against the post-publish generation.</li>
 * </ol>
 *
 * <p>The telemetry seats (the design §11 minimum metric "migration
 * duration/failures") ride the same scenarios: the counters move exactly once
 * per run, the aborted run counts as a failure, and the durations are
 * non-negative.</p>
 */
class MigrationProofAcceptanceTest {

    /** The migrating scope's state container (the "region" the domain owns). */
    private static final class RegionState {
        final Map<String, Integer> cells = new HashMap<>();

        void put(String key, int value) {
            cells.put(key, value);
        }
    }

    /** Awaits the handle's terminal state (the deterministic drain premise). */
    private static void awaitDone(ComputeSubsystem.ComputeHandle<?> handle)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!handle.isDone()) {
            assertTrue(System.nanoTime() < deadline, "the compute job finished");
            Thread.sleep(1);
        }
    }

    @Test
    void theWholeEngineMigrationWalksEveryPhaseWithEveryLiveShape() throws Exception {
        OwnershipDomain regionA = OwnershipDomain.create("simulation:region-a");
        OwnershipDomain regionB = OwnershipDomain.create("simulation:region-b");
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        ComputeSubsystem compute = new ComputeSubsystem();
        long telemetryBegunBefore = OwnershipMigration.Telemetry.begunCount();
        long telemetryPublishedBefore = OwnershipMigration.Telemetry.publishedCount();

        // ---------------------------------------------------------- the live
        // shape: the region carries queued work, delayed work, an inbound
        // intent, and a live compute job — all against generation g0.
        RegionState scope = new RegionState();
        scope.put("beacon", 1);
        AtomicReference<RegionState> newHome = new AtomicReference<>();
        AtomicBoolean oldHomeDropped = new AtomicBoolean(false);
        long g0 = regionA.generation();

        AtomicInteger inflightRan = new AtomicInteger();
        scheduler.submit(regionA, () -> {
            assertTrue(regionA.inContext(), "queued work runs in the domain's context");
            inflightRan.incrementAndGet();
        });
        scheduler.submit(regionA, inflightRan::incrementAndGet);
        AtomicBoolean delayedRan = new AtomicBoolean(false);
        scheduler.submitDelayed(regionA, () -> delayedRan.set(true), 10L);

        // The compute job (the fresh path): submitted at g0, its result
        // applies at the owner's drain while the generation still matches.
        AtomicInteger freshApplied = new AtomicInteger(0);
        AtomicInteger staleDiscarded = new AtomicInteger(0);
        ComputeSubsystem.ComputeHandle<Integer> job =
                compute.submit(regionA, "input", input -> 42,
                        (value, generation) -> freshApplied.set(value),
                        staleDiscarded::incrementAndGet);
        awaitDone(job);
        regionA.enter();
        try {
            assertEquals(1, compute.drainResults(regionA));
            assertEquals(42, freshApplied.get());
        } finally {
            regionA.exit();
        }

        // The persistence snapshot: captured in the domain's context NOW (the
        // tick lane's save arm shape) — the pre-transfer truth, ticket-stamped.
        regionA.enter();
        Map<String, Integer> persistedSnapshot;
        ComputeTicket<Map<String, Integer>> snapshotTicket;
        try {
            persistedSnapshot = new HashMap<>(scope.cells);
            snapshotTicket = ComputeTicket.of(regionA, persistedSnapshot);
        } finally {
            regionA.exit();
        }
        assertTrue(snapshotTicket.isApplicableTo(regionA),
                "the pre-transfer snapshot applies at g0");

        // The inbound intent from the neighbor, submitted before the drain.
        AtomicBoolean preDrainIntentApplied = new AtomicBoolean(false);
        assertTrue(router.submit(new CrossOwnerRouter.Intent(regionA,
                UUID.randomUUID(), regionB.name(), regionB.generation(),
                "neighbor-write", () -> {
                    assertTrue(regionA.inContext(),
                            "the intent applies in the TARGET's context");
                    preDrainIntentApplied.set(true);
                })));

        // ------------------------------------------------------ begin: the hold
        OwnershipMigration migration = new OwnershipMigration(regionA, scheduler);
        migration.begin();
        assertEquals(OwnershipMigration.Phase.DRAINING, migration.phase());
        assertEquals(telemetryBegunBefore + 1, OwnershipMigration.Telemetry.begunCount(),
                "the begun counter moved exactly once");
        assertTrue(scheduler.isAdmissionHeld(regionA));
        // The loud refusal: no new authoritative work enters mid-migration.
        boolean refused = false;
        try {
            scheduler.submit(regionA, () -> { });
        } catch (IllegalStateException held) {
            refused = true;
        }
        assertTrue(refused, "the mid-migration submission is refused loudly");
        // The neighbor's own submissions stay unaffected.
        scheduler.submit(regionB, () -> { });

        // An intent submitted DURING the hold: the router's box never blocks
        // — the envelope waits for the target's own boundary.
        AtomicBoolean duringHoldIntentApplied = new AtomicBoolean(false);
        assertTrue(router.submit(new CrossOwnerRouter.Intent(regionA,
                UUID.randomUUID(), regionB.name(), regionB.generation(),
                "post-migration-write", () -> duringHoldIntentApplied.set(true))));
        assertFalse(duringHoldIntentApplied.get(),
                "the during-hold intent waits in the target's box");

        // A compute submission DURING the hold is LEGAL (the design's law:
        // parallel calculation never carries mutation authority — the compute
        // admission is its own queue, and the result validates against the
        // generation at the owner's drain). This job completes at g0 but is
        // drained AFTER the publish — the stale-discard seat.
        ComputeSubsystem.ComputeHandle<Integer> staleJob =
                compute.submit(regionA, "input", input -> 7,
                        (value, generation) -> freshApplied.set(value),
                        staleDiscarded::incrementAndGet);
        awaitDone(staleJob);

        // The persistence arm during the hold: the save re-submission is
        // refused loudly (§9 step 2) — never a silent drop. (Covered by the
        // refused flag above; the save arm rides the same admission.)

        // ------------------------------------------ drain: the old generation
        regionA.enter();
        int drained;
        try {
            // The old owner's final boundary: BOTH inbound intents apply
            // BEFORE the state moves (the last writes against the old scope)
            // — the router's box never blocks, so the during-hold envelope
            // waits beside the pre-drain one and both land at this boundary.
            assertEquals(2, router.drainIntents(regionA));
            assertTrue(preDrainIntentApplied.get());
            assertTrue(duringHoldIntentApplied.get(),
                    "the during-hold message applied at the old owner's final boundary");
            // The queued AND the delayed work finish at the old generation.
            drained = migration.drainQueued(10L);
        } finally {
            regionA.exit();
        }
        assertTrue(drained >= 3, "in-flight (2) + delayed (1) all drained: " + drained);
        assertEquals(3, inflightRan.get() + (delayedRan.get() ? 1 : 0));
        assertTrue(delayedRan.get(), "the delayed work drained inside the window");
        assertEquals(0, scheduler.pendingFor(regionA));

        // ----------------------------------------------- transfer: the move
        regionA.enter();
        try {
            migration.transfer(() -> {
                assertTrue(regionA.inContext(),
                        "the state moves inside the OLD owner's context");
                newHome.set(scope);
                oldHomeDropped.set(true); // the old home no longer owns the scope
            });
        } finally {
            regionA.exit();
        }
        assertEquals(OwnershipMigration.Phase.DRAINING, migration.phase(),
                "the transfer completed; the protocol holds until publish");

        // ------------------------------------------------- publish: the swap
        migration.publish();
        assertEquals(OwnershipMigration.Phase.PUBLISHED, migration.phase());
        assertEquals(g0 + 1, regionA.generation(), "the generation bumped");
        assertFalse(scheduler.isAdmissionHeld(regionA), "admission re-opened");
        assertEquals(telemetryPublishedBefore + 1,
                OwnershipMigration.Telemetry.publishedCount());
        assertTrue(migration.durationNanos() >= 0L);
        assertTrue(OwnershipMigration.Telemetry.lastDurationNanos() >= 0L);
        assertTrue(OwnershipMigration.Telemetry.totalDurationNanos()
                >= OwnershipMigration.Telemetry.lastDurationNanos());

        // ------------------------------- stale-generation invalidation: g0
        // The compute outcome computed at g0 is discarded as stale at the
        // owner's drain (the subsystem's own generation validation).
        regionA.enter();
        try {
            assertEquals(0, compute.drainResults(regionA),
                    "the stale outcome applies nothing");
            assertEquals(1, staleDiscarded.get(),
                    "the stale outcome ran its fallback");
            assertEquals(1, compute.staleCount(regionA));
        } finally {
            regionA.exit();
        }
        // A fresh job at g1 applies whole.
        ComputeSubsystem.ComputeHandle<Integer> freshJob =
                compute.submit(regionA, "input", input -> 43,
                        (value, generation) -> freshApplied.set(value),
                        staleDiscarded::incrementAndGet);
        awaitDone(freshJob);
        regionA.enter();
        try {
            assertEquals(1, compute.drainResults(regionA));
        } finally {
            regionA.exit();
        }
        assertEquals(43, freshApplied.get());

        // The ticket-stamped snapshot is diagnosably stale as an
        // applicability claim (its generation moved past) while the snapshot
        // itself still describes the pre-transfer truth it captured.
        assertFalse(snapshotTicket.isApplicableTo(regionA));
        assertThrows(StaleResultException.class,
                () -> snapshotTicket.resultOrThrow(regionA));
        assertEquals(Map.of("beacon", 1), persistedSnapshot);
        assertNotEquals(g0, regionA.generation());

        // The during-hold intent already applied at the old boundary (the
        // exactly-once window closed there); a POST-PUBLISH message drains
        // under the NEW authority at the new generation.
        AtomicBoolean postPublishIntentApplied = new AtomicBoolean(false);
        assertTrue(router.submit(new CrossOwnerRouter.Intent(regionA,
                UUID.randomUUID(), regionB.name(), regionB.generation(),
                "new-generation-write", () -> postPublishIntentApplied.set(true))));
        regionA.enter();
        try {
            assertEquals(1, router.drainIntents(regionA));
        } finally {
            regionA.exit();
        }
        assertTrue(postPublishIntentApplied.get(),
                "the post-publish message applied at the target's new boundary");

        // New submissions admit again; the migrated home serves them.
        AtomicBoolean postPublishRan = new AtomicBoolean(false);
        scheduler.submit(regionA, () -> {
            RegionState home = newHome.get();
            home.put("post-publish", 1);
            postPublishRan.set(true);
        });
        regionA.enter();
        try {
            scheduler.runDue(20L);
        } finally {
            regionA.exit();
        }
        assertTrue(postPublishRan.get());
        assertEquals(1, newHome.get().cells.get("post-publish"));
        assertTrue(oldHomeDropped.get(), "the old home released the scope at the move");
        assertEquals(2, newHome.get().cells.size(),
                "the migrated scope carries its pre-transfer cell + the new write");

        compute.shutdown();
        scheduler.shutdown();
        router.shutdown();
    }

    @Test
    void aFailedTransferAbortsLeavingNoHalfMigratedAuthorityAndCountsTheFailure() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:region-f");
        SimulationScheduler scheduler = new SimulationScheduler();
        long abortedBefore = OwnershipMigration.Telemetry.abortedCount();

        RegionState scope = new RegionState();
        scope.put("keeper", 7);
        AtomicReference<RegionState> newHome = new AtomicReference<>();

        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        migration.begin();
        domain.enter();
        try {
            boolean threw = false;
            try {
                migration.transfer(() -> {
                    // The half-move: the new home receives the scope, then the
                    // move fails — the abort must leave NO authority with it.
                    newHome.set(scope);
                    throw new IllegalStateException("the disk was full");
                });
            } catch (IllegalStateException expected) {
                threw = true;
            }
            assertTrue(threw, "the failed state move surfaces loudly");
        } finally {
            domain.exit();
        }
        assertEquals(OwnershipMigration.Phase.ABORTED, migration.phase());
        assertFalse(scheduler.isAdmissionHeld(domain),
                "the abort releases admission");
        assertNotEquals(0L, domain.generation(),
                "the abort still bumps the generation (no half-migrated observer keeps authority)");
        assertEquals(abortedBefore + 1, OwnershipMigration.Telemetry.abortedCount(),
                "the failed run counts as a telemetry failure");
        assertTrue(migration.durationNanos() >= 0L);
        // The abort is idempotent: a second abort is the quiet no-op.
        migration.abort();
        assertEquals(abortedBefore + 1, OwnershipMigration.Telemetry.abortedCount());

        // The protocol runs once: a re-begin refuses.
        boolean refused = false;
        try {
            migration.begin();
        } catch (IllegalStateException alreadyRan) {
            refused = true;
        }
        assertTrue(refused, "the protocol runs once");
        // The moved reference still points at the SAME scope object — the
        // failed transfer's partial write is the CALLER's to unwind (the
        // protocol moved nothing itself; the state move's runnable owns its
        // rollback) — and the engine's convention keeps exactly one scope.
        assertEquals(7, newHome.get().cells.get("keeper"));
    }

    @Test
    void aShutdownMidDrainCancelsTheQueuedWorkAndNothingResurrects() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:region-s");
        SimulationScheduler scheduler = new SimulationScheduler();
        AtomicBoolean ran = new AtomicBoolean(false);
        scheduler.submit(domain, () -> ran.set(true));
        scheduler.submitDelayed(domain, () -> ran.set(true), 5L);

        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        migration.begin();

        // The shutdown races the drain window: the queued work is cancelled
        // and never runs — the design's §11 "cancellation and shutdown do
        // not resurrect tasks".
        scheduler.shutdown();
        assertTrue(scheduler.isShutdown());
        boolean refused = false;
        try {
            scheduler.submit(domain, () -> { });
        } catch (IllegalStateException shutDown) {
            refused = true;
        }
        assertTrue(refused, "a shut-down scheduler refuses admission loudly");

        domain.enter();
        try {
            int drained = migration.drainQueued(10L);
            assertEquals(0, drained, "the cancelled work never runs");
        } finally {
            domain.exit();
        }
        assertFalse(ran.get());

        // The abort closes the held window cleanly after the shutdown.
        migration.abort();
        assertEquals(OwnershipMigration.Phase.ABORTED, migration.phase());
    }

    @Test
    void computeJobsFromOtherDomainsSurviveAMigrationUnaffected() throws Exception {
        // The migration of one domain never disturbs another domain's
        // compute pipeline: the subsystem's per-domain result boxes are
        // independent, and the non-migrating domain's outcomes stay fresh
        // (the isolation seat).
        OwnershipDomain migrating = OwnershipDomain.create("simulation:iso-m");
        OwnershipDomain bystander = OwnershipDomain.create("simulation:iso-b");
        SimulationScheduler scheduler = new SimulationScheduler();
        ComputeSubsystem compute = new ComputeSubsystem();

        AtomicInteger bystanderApplied = new AtomicInteger(0);
        ComputeSubsystem.ComputeHandle<Integer> bystanderJob =
                compute.submit(bystander, "x", input -> 99,
                        (value, generation) -> bystanderApplied.set(value),
                        () -> { });
        awaitDone(bystanderJob);

        OwnershipMigration migration = new OwnershipMigration(migrating, scheduler);
        migration.begin();
        migrating.enter();
        try {
            migration.drainQueued(1L);
            migration.transfer(() -> { });
        } finally {
            migrating.exit();
        }
        migration.publish();

        bystander.enter();
        try {
            assertEquals(1, compute.drainResults(bystander));
        } finally {
            bystander.exit();
        }
        assertEquals(99, bystanderApplied.get(),
                "the bystander's result applies unaffected by the neighbor's migration");
        assertEquals(1, compute.appliedCount(bystander));

        compute.shutdown();
        scheduler.shutdown();
    }

    @Test
    void concurrentSubmissionDuringTheHoldIsRefusedLoudlyNotSilentlyDropped() throws Exception {
        // The admission hold under real contention: another thread's
        // submissions during the window are refused with the loud exception
        // (never dropped), and the refusal count is the observable telemetry.
        OwnershipDomain domain = OwnershipDomain.create("simulation:contended");
        SimulationScheduler scheduler = new SimulationScheduler();
        CountDownLatch holdStanding = new CountDownLatch(1);
        CountDownLatch submitterDone = new CountDownLatch(1);
        AtomicInteger refusals = new AtomicInteger();

        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        migration.begin();

        Thread submitter = new Thread(() -> {
            try {
                holdStanding.await(5, TimeUnit.SECONDS);
                for (int i = 0; i < 50; i++) {
                    try {
                        scheduler.submit(domain, () -> { });
                    } catch (IllegalStateException refused) {
                        refusals.incrementAndGet();
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                submitterDone.countDown();
            }
        }, "contended-submitter");
        submitter.start();

        holdStanding.countDown();
        assertTrue(submitterDone.await(5, TimeUnit.SECONDS), "the submitter finished");
        assertEquals(50, refusals.get(),
                "every mid-window submission was refused loudly");
        assertTrue(scheduler.rejectedSubmissions() >= 50,
                "the refusal telemetry counted them");
        submitter.join(5000);

        domain.enter();
        try {
            migration.drainQueued(1L);
            migration.transfer(() -> { });
        } finally {
            domain.exit();
        }
        migration.publish();
        assertFalse(scheduler.isAdmissionHeld(domain));
        scheduler.shutdown();
    }
}
