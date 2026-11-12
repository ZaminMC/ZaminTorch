package net.zaminmc.torch.server.concurrent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The policy-to-telemetry bridge (Phase 8's first follow-on, still
 * DELIBERATELY DISARMED — nothing in the engine's tick loop consults it;
 * the automatic balancer stays future work behind the phase's own gate):
 * the load-sample source the {@link MigrationPolicy} needs, read from the
 * scheduler's own §11 telemetry (the per-domain cumulative busy-nanos —
 * "per-domain tick execution"), normalized against the tick budget.
 *
 * <p>THE WINDOW LAW: the caller takes two {@link Snapshot}s of the
 * scheduler's per-domain busy-nanos counters (any number of ticks apart —
 * the decision cadence is the caller's), and the sampler answers each
 * domain's normalized load: the window's busy nanos divided by the
 * window's tick count times the per-tick budget, clamped to {@code [0, 1]}.
 * A domain that spends the whole budget every tick of the window reads
 * 1.0; an idle domain reads 0.0; a domain past the budget (the oversub-
 * scribed shape) clamps at 1.0 — the load signal saturates, never
 * overflows the policy's water marks.</p>
 *
 * <p>Snapshots are immutable value records: the same two snapshots always
 * answer the same loads (the determinism contract the policy's own seats
 * pin). The sampler never reads clocks, never mutates the scheduler, and
 * holds no state — it is a pure function over its inputs.</p>
 */
public final class MigrationLoadSampler {

    /** The 20-TPS tick budget: 50ms per tick (the design's own constant). */
    public static final long TICK_BUDGET_NANOS = 50_000_000L;

    /**
     * One immutable read of the scheduler's per-domain busy-nanos counters
     * (the §11 metric's window source). Two snapshots difference a window.
     */
    public static final class Snapshot {
        private final Map<String, Long> busyNanos;

        private Snapshot(Map<String, Long> busyNanos) {
            this.busyNanos = Map.copyOf(busyNanos);
        }

        /**
         * The value factory: a snapshot over any telemetry source's
         * name → cumulative-busy-nanos map (the tests' pure-window seats;
         * a non-scheduler source the future balancer may carry).
         */
        public static Snapshot of(Map<String, Long> busyNanos) {
            Objects.requireNonNull(busyNanos, "busyNanos");
            return new Snapshot(busyNanos);
        }

        /** The captured cumulative busy nanos for the domain (0 if unseen). */
        public long busyNanosOf(String domain) {
            Objects.requireNonNull(domain, "domain");
            return busyNanos.getOrDefault(domain, 0L);
        }

        /** The captured domain names (the census the snapshot saw). */
        public java.util.Set<String> domains() {
            return busyNanos.keySet();
        }
    }

    private MigrationLoadSampler() {
    }

    /** Captures the scheduler's per-domain busy-nanos counters, immutably. */
    public static Snapshot snapshotOf(SimulationScheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        return new Snapshot(scheduler.busyNanosSnapshot());
    }

    /**
     * The window's normalized load for one domain: the busy-nanos delta
     * between the two snapshots divided by the window's budget (ticks ×
     * per-tick budget), clamped to {@code [0, 1]}. A window of zero ticks
     * answers 0.0 (no time passed — no load).
     *
     * @param before the window's opening snapshot
     * @param after  the window's closing snapshot
     * @param domain the domain's diagnostic identity
     * @param ticks  the window's tick count (the decision cadence)
     * @return the normalized load in [0, 1]
     */
    public static double loadBetween(Snapshot before, Snapshot after,
                                     String domain, long ticks) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(domain, "domain");
        if (ticks <= 0L) {
            return 0.0;
        }
        long windowBudget = ticks * TICK_BUDGET_NANOS;
        long delta = after.busyNanosOf(domain) - before.busyNanosOf(domain);
        if (delta <= 0L) {
            return 0.0;
        }
        double load = (double) delta / (double) windowBudget;
        return Math.min(1.0, Math.max(0.0, load));
    }

    /**
     * The decision window: every domain the closing snapshot saw, sampled
     * and fed to the policy at the caller's decision tick. The answer maps
     * each domain to the policy's decision — the caller (the future
     * balancer) applies them through the migration protocol; this bridge
     * never migrates anything.
     *
     * @param policy   the disarmed decision law
     * @param before   the window's opening snapshot
     * @param after    the window's closing snapshot
     * @param ticks    the window's tick count
     * @param decisionTick the decision clock reading fed to the policy
     * @return each sampled domain's decision (never null; NONE dominates)
     */
    public static Map<String, MigrationPolicy.Decision> decisionWindow(
            MigrationPolicy policy, Snapshot before, Snapshot after,
            long ticks, long decisionTick) {
        Objects.requireNonNull(policy, "policy");
        Map<String, MigrationPolicy.Decision> decisions = new LinkedHashMap<>();
        for (String domain : after.domains()) {
            double load = loadBetween(before, after, domain, ticks);
            decisions.put(domain, policy.decide(domain, load, decisionTick));
        }
        return decisions;
    }
}
