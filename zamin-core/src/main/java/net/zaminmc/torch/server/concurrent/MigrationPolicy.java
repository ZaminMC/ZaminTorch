package net.zaminmc.torch.server.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The adaptive-balancing decision policy (Phase 8's "anti-oscillation rules
 * before enabling automatic balancing" — the pure law, DELIBERATELY DISARMED:
 * nothing in the engine consults this class today; the automatic balancer
 * itself remains future work behind the Phase-8 gate, exactly as the phase
 * text demands).
 *
 * <p>The law is the classic load-shedding hysteresis triplet:</p>
 * <ol>
 *   <li><b>Sustained qualification</b> — a domain qualifies for a split only
 *       after its load sample exceeds the high-water mark for
 *       {@code sustainSamples} <em>consecutive</em> samples, and for a merge
 *       only after it sits below the low-water mark for the same sustained
 *       run. A single spike, a burst, or a lag hiccup never moves a
 *       boundary.</li>
 *   <li><b>The hysteresis band</b> — {@code lowWater < highWater}, and a
 *       load between the two waters qualifies for nothing: a domain whose
 *       load oscillates around a single threshold can never flap between
 *       split and merge, because the two decisions read disjoint bands that
 *       only a real regime change crosses.</li>
 *   <li><b>The cooldown</b> — after a domain migrates, no further decision
 *       for that domain until {@code cooldownTicks} pass. The new
 *       partition's load needs time to settle; deciding on the transient
 *       right after a migration is the primary oscillation source.</li>
 * </ol>
 *
 * <p>The policy is a pure function over the caller-supplied sample history:
 * it holds no timers, reads no clocks, and schedules nothing. The balancer
 * (when it exists) feeds it one load sample per decision tick per domain and
 * applies the returned decision through the migration protocol. Inputs are
 * per-domain normalized loads in {@code [0, 1]} (or any consistent scale the
 * harness chooses — the law only compares against the waters).</p>
 */
public final class MigrationPolicy {

    /** One domain's decision at one decision tick. */
    public enum Decision {
        /** No boundary change (the default — the disarmed answer). */
        NONE,
        /** The domain sustained above the high water: split it. */
        SPLIT,
        /** The domain sustained below the low water: merge it. */
        MERGE
    }

    /** One domain's per-decision-tick sample state (the policy's own memory). */
    private static final class DomainState {
        /** Consecutive samples above the high-water mark. */
        int hotRun;
        /** Consecutive samples below the low-water mark. */
        int coldRun;
        /** The tick of the domain's last migration (Long.MIN_VALUE = never). */
        long lastMigrationTick = Long.MIN_VALUE;
    }

    /** Above this sustained load a domain qualifies to split. */
    public final double highWater;
    /** Below this sustained load a domain qualifies to merge. */
    public final double lowWater;
    /** Consecutive samples required to qualify either direction. */
    public final int sustainSamples;
    /** Ticks after a migration before the domain may migrate again. */
    public final long cooldownTicks;

    private final Map<String, DomainState> domains = new HashMap<>();

    /**
     * @param highWater      the split qualification mark (exclusive above)
     * @param lowWater       the merge qualification mark (exclusive below)
     * @param sustainSamples the consecutive-sample run required (>= 1)
     * @param cooldownTicks  the post-migration quiet window (>= 0)
     * @throws IllegalArgumentException when the band law is violated
     *         ({@code lowWater >= highWater}) or {@code sustainSamples < 1}
     *         or {@code cooldownTicks < 0} or the waters fall outside
     *         {@code [0, 1]}
     */
    public MigrationPolicy(double highWater, double lowWater,
                           int sustainSamples, long cooldownTicks) {
        if (Double.isNaN(highWater) || Double.isNaN(lowWater)
                || highWater < 0.0 || highWater > 1.0
                || lowWater < 0.0 || lowWater > 1.0) {
            throw new IllegalArgumentException(
                    "water marks must be within [0, 1]: high=" + highWater
                            + " low=" + lowWater);
        }
        if (lowWater >= highWater) {
            throw new IllegalArgumentException(
                    "the hysteresis band requires lowWater < highWater: "
                            + lowWater + " !< " + highWater);
        }
        if (sustainSamples < 1) {
            throw new IllegalArgumentException("sustainSamples must be >= 1");
        }
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("cooldownTicks must be >= 0");
        }
        this.highWater = highWater;
        this.lowWater = lowWater;
        this.sustainSamples = sustainSamples;
        this.cooldownTicks = cooldownTicks;
    }

    /** The reference shape: 0.75/0.25 waters, 3-sample sustain, 600-tick cooldown. */
    public static MigrationPolicy reference() {
        return new MigrationPolicy(0.75, 0.25, 3, 600L);
    }

    /**
     * Feeds one decision tick's sample for one domain and answers the
     * decision. Deterministic and pure in the sample history: the same
     * sample sequence answers the same decision sequence.
     *
     * <p>The cooldown consults the caller-declared {@code currentTick}
     * against the domain's recorded {@link #noteMigration(String, long)}
     * tick — a domain inside its cooldown answers {@link Decision#NONE}
     * whatever its load, and its runs do not count while the cooldown
     * stands (the post-migration transient must re-qualify from scratch:
     * a decision after the cooldown expires needs its own sustained
     * evidence, never the samples the settling window swallowed).</p>
     *
     * @param domain      the domain's diagnostic identity
     * @param loadSample  this decision tick's load reading
     * @param currentTick the decision clock (monotonic in the caller)
     * @return the decision for this tick
     */
    public Decision decide(String domain, double loadSample, long currentTick) {
        Objects.requireNonNull(domain, "domain");
        DomainState state = domains.computeIfAbsent(domain, k -> new DomainState());
        // The cooldown gate: a migrated domain stays put until it settles,
        // and the settling window's samples never count toward a run.
        if (state.lastMigrationTick != Long.MIN_VALUE
                && currentTick - state.lastMigrationTick <= cooldownTicks) {
            return Decision.NONE;
        }
        if (loadSample > highWater) {
            state.hotRun++;
        } else {
            state.hotRun = 0;
        }
        if (loadSample < lowWater) {
            state.coldRun++;
        } else {
            state.coldRun = 0;
        }
        if (state.hotRun >= sustainSamples) {
            state.hotRun = 0;
            state.coldRun = 0;
            return Decision.SPLIT;
        }
        if (state.coldRun >= sustainSamples) {
            state.hotRun = 0;
            state.coldRun = 0;
            return Decision.MERGE;
        }
        return Decision.NONE;
    }

    /**
     * Records the domain's migration tick (the cooldown anchor). The caller
     * invokes this when it actually executes a migration for the domain —
     * the policy never migrates anything itself.
     */
    public void noteMigration(String domain, long tick) {
        Objects.requireNonNull(domain, "domain");
        DomainState state = domains.computeIfAbsent(domain, k -> new DomainState());
        state.lastMigrationTick = Math.max(state.lastMigrationTick, tick);
        // The post-migration transient must re-qualify from scratch.
        state.hotRun = 0;
        state.coldRun = 0;
    }

    /** @return whether the domain currently sits inside its cooldown window. */
    public boolean inCooldown(String domain, long currentTick) {
        Objects.requireNonNull(domain, "domain");
        DomainState state = domains.get(domain);
        if (state == null || state.lastMigrationTick == Long.MIN_VALUE) {
            return false;
        }
        return currentTick - state.lastMigrationTick <= cooldownTicks;
    }
}
