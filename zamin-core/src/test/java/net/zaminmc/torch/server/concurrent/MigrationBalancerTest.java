package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE ADAPTIVE BALANCING LOOP'S SEATS (Phase 8, Wave MIG-3 — the arming of
 * the pipeline MIG-1 proved and MIG-2 bridged, end to end over the REAL
 * scheduler's own §11 telemetry):
 *
 * <ol>
 *   <li><b>the sustained-hot split</b> — a region's base burning past the
 *       high water for the policy's sustained run SPLITS through the
 *       executor, the fresh half entering the next window's census with
 *       the zero-load joiner contract;</li>
 *   <li><b>the cold merge-back</b> — once both halves settle cold past the
 *       cooldown, the HALF's sustained cold run MERGES it back into the
 *       base (the base's own MERGE answer is the refusal shape — a base
 *       IS the whole region);</li>
 *   <li><b>the burst never moves a boundary</b> — hot/idle alternating
 *       windows (the square wave) never accumulate the sustained run;</li>
 *   <li><b>the cooldown holds the windows after a migration</b> — the
 *       settling window's samples answer NONE whatever the load;</li>
 *   <li><b>the cold loop moves nothing</b> — an idle world answers NONE
 *       forever.</li>
 * </ol>
 */
class MigrationBalancerTest {

    /** Burns exactly the given nanos (the busy-load shape). */
    private static void busyNanos(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }

    /**
     * The drive loop's one tick: submit the tick's load against the domain,
     * run the scheduler's due work (the busy time lands in the §11
     * counters), then feed the balancer — the engine loop's own order
     * (the safe point after the scheduler walk).
     */
    private static void driveTick(SimulationScheduler scheduler,
                                  MigrationBalancer balancer,
                                  OwnershipDomain loadDomain,
                                  long busyNanos,
                                  long tick) {
        if (loadDomain != null && busyNanos > 0) {
            scheduler.submit(loadDomain, () -> busyNanos(busyNanos));
        }
        scheduler.runDue(tick);
        balancer.tick(tick);
    }

    @Test
    void aSustainedHotRegionSplitsThroughTheRealTelemetry() {
        RegionPartition partition = new RegionPartition("w", 8);
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        OwnershipDomain base = partition.domainFor(0, 0);
        // window 4 ticks, waters 0.75/0.25, sustain 2, cooldown 8.
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 2, 8L);
        MigrationBalancer balancer = new MigrationBalancer(
                java.util.List.of(partition), scheduler, router, policy, 4L);

        // The hot shape: ~48ms of real busy work per tick against the base
        // — a 4-tick window reads ~192ms/200ms ≈ 0.96, past the high water.
        for (long tick = 0; tick <= 8; tick++) {
            driveTick(scheduler, balancer, base, 48_000_000L, tick);
        }

        // Two sustained hot windows (the boundary closes at ticks 4 and 8)
        // → the split applied at the second.
        assertEquals(1, balancer.splitsApplied(),
                "the sustained hot run split the region");
        assertEquals(0, balancer.mergesApplied());
        assertEquals(1, partition.subdivisionCount());
        RegionPartition.Subdivision sub = partition.subdivisionOf(
                partition.regionId(0, 0));
        assertNotNull(sub, "the subdivision is active");
        assertTrue(sub.half().name().startsWith(base.name() + "#h"),
                "the fresh half mints the serial name: " + sub.half().name());

        // The cooldown anchors BOTH sides at the split tick.
        assertTrue(balancer.inCooldown(base.name(), 9L));
        assertTrue(balancer.inCooldown(sub.half().name(), 9L));
        assertFalse(balancer.inCooldown(base.name(), 17L),
                "the cooldown expired 8+ ticks later");

        scheduler.shutdown();
        router.shutdown();
    }

    @Test
    void theColdHalvesMergeBackPastTheCooldown() {
        RegionPartition partition = new RegionPartition("w", 8);
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        OwnershipDomain base = partition.domainFor(0, 0);
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 2, 8L);
        MigrationBalancer balancer = new MigrationBalancer(
                java.util.List.of(partition), scheduler, router, policy, 4L);

        // Hot through the split (tick 8), then idle: the halves settle.
        for (long tick = 0; tick <= 8; tick++) {
            driveTick(scheduler, balancer, base, 48_000_000L, tick);
        }
        assertEquals(1, balancer.splitsApplied());
        RegionPartition.Subdivision sub = partition.subdivisionOf(
                partition.regionId(0, 0));
        assertNotNull(sub);

        // Idle windows: the cooldown (noted at tick 8) holds ticks 12 and
        // 16 (NONE whatever the load); tick 20 re-opens the law and the
        // half's zero-load joiner contract starts its cold run; the SECOND
        // cold window (tick 24) answers MERGE for the half — applied.
        for (long tick = 9; tick <= 24; tick++) {
            driveTick(scheduler, balancer, null, 0, tick);
        }

        assertEquals(1, balancer.mergesApplied(),
                "the sustained cold half merged back into the base");
        assertEquals(0, partition.subdivisionCount(),
                "the subdivision retired whole");
        assertNull(partition.subdivisionOf(partition.regionId(0, 0)));
        // The routing returned to the base.
        assertEquals(base, partition.domainFor(4, 0));
        assertEquals(base, partition.domainFor(7, 7));
        // The base's own MERGE answers were the refusal shape (a base IS
        // the whole region) — the refusal counter carries them.
        assertTrue(balancer.applicationsRefused() >= 1,
                "the base's own merge answers refused: "
                        + balancer.applicationsRefused());

        scheduler.shutdown();
        router.shutdown();
    }

    @Test
    void aBurstNeverMovesABoundary() {
        RegionPartition partition = new RegionPartition("w", 8);
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        OwnershipDomain base = partition.domainFor(0, 0);
        // window 2 ticks, sustain 2: the square wave (hot window, idle
        // window, hot, idle ...) never accumulates two consecutive hots.
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 2, 0L);
        MigrationBalancer balancer = new MigrationBalancer(
                java.util.List.of(partition), scheduler, router, policy, 2L);

        for (long tick = 0; tick <= 16; tick++) {
            // The square wave: windows (0,2] and (4,6] run both ticks hot
            // (~96ms/100ms ≈ 0.96, past the high water), windows (2,4] and
            // (6,8] idle — the alternation the policy's own square-wave
            // seat pins: the hot run resets before it ever sustains.
            boolean hotTick = tick % 4 == 1 || tick % 4 == 2;
            driveTick(scheduler, balancer, base,
                    hotTick ? 48_000_000L : 0L, tick);
        }

        assertEquals(0, balancer.splitsApplied(),
                "the square wave never split the region");
        assertEquals(0, partition.subdivisionCount());

        scheduler.shutdown();
        router.shutdown();
    }

    @Test
    void theCooldownHoldsTheWindowsAfterAMigration() {
        RegionPartition partition = new RegionPartition("w", 8);
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        OwnershipDomain base = partition.domainFor(0, 0);
        // A long cooldown (100 ticks): every window after the split sits
        // inside it, whatever the load.
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 2, 100L);
        MigrationBalancer balancer = new MigrationBalancer(
                java.util.List.of(partition), scheduler, router, policy, 4L);

        // Hot through the split, then STAY hot — the settling window's
        // samples must answer NONE.
        for (long tick = 0; tick <= 8; tick++) {
            driveTick(scheduler, balancer, base, 48_000_000L, tick);
        }
        assertEquals(1, balancer.splitsApplied());

        for (long tick = 9; tick <= 20; tick++) {
            driveTick(scheduler, balancer, base, 48_000_000L, tick);
        }

        assertEquals(1, balancer.splitsApplied(),
                "the cooldown held every post-split window");
        assertEquals(1, partition.subdivisionCount(),
                "no second migration moved the boundary");
        // The post-split hot windows inside the cooldown answered NONE —
        // the policy's settling law (the balancer's own consult).
        assertTrue(balancer.inCooldown(base.name(), 20L));

        scheduler.shutdown();
        router.shutdown();
    }

    @Test
    void theColdLoopMovesNothing() {
        RegionPartition partition = new RegionPartition("w", 8);
        SimulationScheduler scheduler = new SimulationScheduler();
        CrossOwnerRouter router = new CrossOwnerRouter();
        partition.domainFor(0, 0);
        partition.domainFor(64, 64);
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 2, 0L);
        MigrationBalancer balancer = new MigrationBalancer(
                java.util.List.of(partition), scheduler, router, policy, 4L);

        // An idle world: the bases' loads read 0.0 and the policy answers
        // MERGE for them at every second cold window (the answer consumes
        // the run — NONE between) — the REFUSAL shape (a base IS the whole
        // region; only an active subdivision's half merges). The loop's
        // own law: the answers arrive, the applications refuse, NOTHING
        // moves, and a SPLIT never appears.
        boolean sawRefusedMerge = false;
        for (long tick = 0; tick <= 20; tick++) {
            Map<String, MigrationPolicy.Decision> decisions =
                    driveTickCollecting(scheduler, balancer, tick);
            for (Map.Entry<String, MigrationPolicy.Decision> entry : decisions.entrySet()) {
                assertNotEquals(MigrationPolicy.Decision.SPLIT, entry.getValue(),
                        "an idle world never answers SPLIT: " + entry.getKey());
                if (entry.getValue() == MigrationPolicy.Decision.MERGE) {
                    sawRefusedMerge = true;
                }
            }
        }
        assertTrue(sawRefusedMerge, "the idle world produced its MERGE answers");
        assertTrue(balancer.applicationsRefused() >= 1,
                "every base MERGE refused: " + balancer.applicationsRefused());
        assertEquals(0, balancer.splitsApplied());
        assertEquals(0, balancer.mergesApplied());
        assertEquals(0, partition.subdivisionCount(),
                "an idle world moves no boundary");

        scheduler.shutdown();
        router.shutdown();
    }

    /** The drive loop's tick, answering the window's decisions. */
    private static Map<String, MigrationPolicy.Decision> driveTickCollecting(
            SimulationScheduler scheduler, MigrationBalancer balancer, long tick) {
        scheduler.runDue(tick);
        return balancer.tick(tick);
    }
}
