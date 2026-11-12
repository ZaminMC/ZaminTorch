package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE POLICY-TO-TELEMETRY BRIDGE'S OWN SEATS (Phase 8's first follow-on —
 * {@link MigrationLoadSampler} over the REAL scheduler's §11 busy-nanos
 * telemetry, feeding the REAL {@link MigrationPolicy}; the bridge stays
 * DISARMED: nothing in the engine's tick loop consults it, the automatic
 * balancer stays future work behind the phase's own gate).
 */
class MigrationLoadSamplerTest {

    @Test
    void theBusyNanosAccumulatePerTaskAcrossTheWindow() {
        // The §11 per-domain tick-execution metric: every executed task's
        // duration accumulates into the domain's counter — two snapshots
        // difference the window's whole busy time, not the last task's.
        OwnershipDomain domain = OwnershipDomain.create("simulation:sampler");
        SimulationScheduler scheduler = new SimulationScheduler();
        long before = scheduler.busyNanos(domain);
        assertEquals(0L, before, "a fresh domain has accumulated nothing");

        scheduler.submit(domain, () -> { });
        scheduler.submit(domain, () -> { });
        domain.enter();
        try {
            scheduler.runDue(1L);
        } finally {
            domain.exit();
        }
        long after = scheduler.busyNanos(domain);
        assertTrue(after >= 0L, "two executed tasks accumulated their durations");
        assertEquals(2L, scheduler.executedCount(domain));
        // The snapshot copy matches the accessor's read.
        assertEquals(after, scheduler.busyNanosSnapshot().getOrDefault(
                domain.name(), 0L).longValue());
        scheduler.shutdown();
    }

    @Test
    void theWindowLawNormalizesAgainstTheTickBudget() {
        // The window law, exact: busy delta / (ticks × 50ms), clamped to
        // [0, 1]. The snapshots are value records — the same pair always
        // answers the same load.
        MigrationLoadSampler.Snapshot before = snapshot(0L);
        // A window of 10 ticks at 50ms each: 500ms budget. A 375ms busy
        // delta reads exactly 0.75 — the reference policy's high water.
        MigrationLoadSampler.Snapshot after = snapshot(375_000_000L);
        assertEquals(0.75, MigrationLoadSampler.loadBetween(
                before, after, "a", 10L), 1.0E-9);
        // The saturation: a past-budget window clamps at 1.0.
        MigrationLoadSampler.Snapshot overloaded = snapshot(900_000_000L);
        assertEquals(1.0, MigrationLoadSampler.loadBetween(
                before, overloaded, "a", 10L), 0.0);
        // The idle window: no delta, no load.
        assertEquals(0.0, MigrationLoadSampler.loadBetween(
                before, before, "a", 10L), 0.0);
        // The zero-tick window: no time passed, no load.
        assertEquals(0.0, MigrationLoadSampler.loadBetween(
                before, overloaded, "a", 0L), 0.0);
        // A domain the opening snapshot never saw (a joiner): its opening
        // read is 0 by contract, the whole closing counter is the delta.
        assertEquals(0.5, MigrationLoadSampler.loadBetween(
                before, snapshot("b", 250_000_000L), "b", 10L), 1.0E-9);
    }

    @Test
    void theDecisionWindowFeedsEveryDomainThePolicySaw() {
        // The bridge's end-to-end shape: two snapshots difference a
        // window, every domain the closing snapshot saw is sampled and
        // fed to the policy, and the answer maps each to its decision.
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 2, 600L);
        MigrationLoadSampler.Snapshot before = snapshot(
                Map.of("hot", 0L, "cold", 0L, "band", 0L));
        // hot: 0.9 — above the high water; cold: 0.1 — below the low;
        // band: 0.5 — inside the band.
        MigrationLoadSampler.Snapshot after = snapshot(Map.of(
                "hot", 450_000_000L,
                "cold", 50_000_000L,
                "band", 250_000_000L));
        Map<String, MigrationPolicy.Decision> first =
                MigrationLoadSampler.decisionWindow(policy, before, after, 10L, 1L);
        assertEquals(MigrationPolicy.Decision.NONE, first.get("hot"),
                "one hot sample is not a sustained run (sustain 2)");
        assertEquals(MigrationPolicy.Decision.NONE, first.get("cold"),
                "one cold sample is not a sustained run");
        assertEquals(MigrationPolicy.Decision.NONE, first.get("band"),
                "the band qualifies nothing");
        assertFalse(first.containsKey("unseen"), "the unsampled domain is absent");

        // The second identical window: hot reaches its sustained run and
        // splits; cold reaches its own and merges; band still nothing.
        Map<String, MigrationPolicy.Decision> second =
                MigrationLoadSampler.decisionWindow(policy, after,
                        snapshot(Map.of(
                                "hot", 900_000_000L,
                                "cold", 100_000_000L,
                                "band", 500_000_000L)),
                        10L, 2L);
        assertEquals(MigrationPolicy.Decision.SPLIT, second.get("hot"));
        assertEquals(MigrationPolicy.Decision.MERGE, second.get("cold"));
        assertEquals(MigrationPolicy.Decision.NONE, second.get("band"));
    }

    @Test
    void theBridgeEndToEndOverTheRealScheduler() throws Exception {
        // THE END-TO-END SEAT: the REAL scheduler's own telemetry drives a
        // sustained-split decision — a domain whose tasks burn past 75% of
        // the tick budget for two consecutive decision windows makes the
        // policy split it, through busyNanosSnapshot and nothing else.
        OwnershipDomain busy = OwnershipDomain.create("simulation:busy");
        OwnershipDomain idle = OwnershipDomain.create("simulation:idle");
        SimulationScheduler scheduler = new SimulationScheduler();
        MigrationPolicy policy = new MigrationPolicy(0.75, 0.25, 2, 600L);

        // Window 1: the busy domain burns ~40ms of its 50ms budget (a
        // sleep-backed task), the idle domain nothing.
        MigrationLoadSampler.Snapshot before =
                MigrationLoadSampler.snapshotOf(scheduler);
        scheduler.submit(busy, () -> {
            try {
                Thread.sleep(40L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        scheduler.submit(idle, () -> { });
        busy.enter();
        try {
            scheduler.runDue(1L);
        } finally {
            busy.exit();
        }
        // The busy domain's task ran on the caller's binding; the idle
        // domain's task needs its own drain.
        idle.enter();
        try {
            scheduler.runDue(1L);
        } finally {
            idle.exit();
        }
        MigrationLoadSampler.Snapshot middle =
                MigrationLoadSampler.snapshotOf(scheduler);
        Map<String, MigrationPolicy.Decision> first =
                MigrationLoadSampler.decisionWindow(policy, before, middle, 1L, 1L);
        assertEquals(MigrationPolicy.Decision.NONE, first.get(busy.name()),
                "the first hot window is not yet sustained");

        // Window 2: the same burn again.
        scheduler.submit(busy, () -> {
            try {
                Thread.sleep(40L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        busy.enter();
        try {
            scheduler.runDue(2L);
        } finally {
            busy.exit();
        }
        MigrationLoadSampler.Snapshot after =
                MigrationLoadSampler.snapshotOf(scheduler);
        Map<String, MigrationPolicy.Decision> second =
                MigrationLoadSampler.decisionWindow(policy, middle, after, 1L, 2L);
        assertEquals(MigrationPolicy.Decision.SPLIT, second.get(busy.name()),
                "two sustained 80%-of-budget windows split the busy domain");
        assertEquals(MigrationPolicy.Decision.MERGE, second.get(idle.name()),
                "the idle domain's own sustained cold run qualifies its merge "
                        + "(the law's other arm — the DISARMED assertion below "
                        + "is the point: nothing actually migrated)");
        // The disarmed law: the bridge answered a decision; nothing
        // migrated — the domains' generations never moved.
        assertEquals(0L, busy.generation());
        assertEquals(0L, idle.generation());
        scheduler.shutdown();
    }

    /** A snapshot over one domain's counter (the test's value factory). */
    private static MigrationLoadSampler.Snapshot snapshot(long busyNanos) {
        return MigrationLoadSampler.Snapshot.of(Map.of("a", busyNanos));
    }

    private static MigrationLoadSampler.Snapshot snapshot(String domain, long busyNanos) {
        return MigrationLoadSampler.Snapshot.of(Map.of(domain, busyNanos));
    }

    private static MigrationLoadSampler.Snapshot snapshot(Map<String, Long> busyNanos) {
        return MigrationLoadSampler.Snapshot.of(busyNanos);
    }
}
