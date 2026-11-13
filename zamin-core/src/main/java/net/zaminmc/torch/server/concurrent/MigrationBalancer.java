package net.zaminmc.torch.server.concurrent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * THE ADAPTIVE BALANCING LOOP (Phase 8's last standing follow-on, Wave
 * MIG-3 — the arming of the pipeline MIG-1 proved and MIG-2 bridged): the
 * per-tick decision window that samples the scheduler's own §11 telemetry,
 * feeds the {@link MigrationPolicy}'s anti-oscillation law, and APPLIES
 * the answered decisions through the {@link MigrationExecutors} — every
 * boundary move a full {@link OwnershipMigration} protocol run, exactly as
 * the phase's gate text demands.
 *
 * <p>THE WINDOW LAW: the caller ticks the balancer once per engine tick;
 * every {@code windowTicks} the balancer closes a decision window — the
 * opening snapshot it took at the window's start, the closing snapshot
 * now, the actual elapsed tick count between them — and every domain in
 * the PARTITIONS' census (not the telemetry's: a freshly-split half has
 * no scheduler history yet, and its zero-load joiner contract is exactly
 * the merge signal) is sampled through {@link MigrationLoadSampler}'s
 * clamped window law and fed to the policy at the decision tick. The
 * decisions answer in the census's stable name order; the executor
 * applications run in that same order — deterministic on every JVM.</p>
 *
 * <p>THE COOLDOWN ANCHORS: a split notes the migration for BOTH sides
 * (the base and the fresh half — the settling window's samples never
 * count toward either run); a merge notes both too (the survivor and the
 * retired). A refused application (an already-subdivided region's split,
 * an undivided region's merge, a foreign domain name) notes NOTHING and
 * moves NOTHING — the policy's own sustained-run reset governs the next
 * attempt, and the refusal logs at FINE (an expected shape, never an
 * error).</p>
 *
 * <p>THE EVIDENCE GATE: the engine arms the loop behind
 * {@code torch.adaptive-balancing} (DEFAULT OFF) — the §22 discipline
 * holds: no measured load shape demands automatic balancing (the
 * attribution pass's verdict stands), and the operator who disagrees
 * carries the burden of the flag. Unarmed, nothing consults the policy,
 * nothing moves a boundary, the engine's behavior is byte-identical to
 * the pre-MIG-3 tree.</p>
 */
public final class MigrationBalancer {

    private static final Logger LOGGER = Logger.getLogger(MigrationBalancer.class.getName());

    /**
     * The default decision window: 100 ticks (five seconds at 20 TPS) —
     * long enough for a window's load to be a regime, not a burst; the
     * reference policy's 3-sample sustain then demands fifteen sustained
     * seconds of heat before a boundary moves.
     */
    public static final long DEFAULT_WINDOW_TICKS = 100L;

    private final List<RegionPartition> partitions;
    private final SimulationScheduler scheduler;
    private final CrossOwnerRouter router;
    private final MigrationPolicy policy;
    private final long windowTicks;

    /** The open window's opening snapshot (null before the first tick). */
    private MigrationLoadSampler.Snapshot opening;
    /** The open window's opening tick. */
    private long openingTick;
    /** The migrations applied (splits + merges) — the loop's own telemetry. */
    private final AtomicLong splitsApplied = new AtomicLong();
    private final AtomicLong mergesApplied = new AtomicLong();
    private final AtomicLong applicationsRefused = new AtomicLong();

    /**
     * @param partitions every dimension's partition (the census source and
     *                   the executors' routing tables; the overworld's
     *                   first)
     * @param scheduler  the shared scheduler whose §11 counters the windows
     *                   sample
     * @param router     the shared cross-owner router the protocol drains
     * @param policy     the anti-oscillation decision law
     * @param windowTicks the decision window's length in ticks (>= 1)
     */
    public MigrationBalancer(List<RegionPartition> partitions,
                             SimulationScheduler scheduler,
                             CrossOwnerRouter router,
                             MigrationPolicy policy,
                             long windowTicks) {
        this.partitions = List.copyOf(Objects.requireNonNull(partitions, "partitions"));
        if (this.partitions.isEmpty()) {
            throw new IllegalArgumentException("the balancer needs at least one partition");
        }
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.router = Objects.requireNonNull(router, "router");
        this.policy = Objects.requireNonNull(policy, "policy");
        if (windowTicks < 1) {
            throw new IllegalArgumentException("windowTicks must be >= 1");
        }
        this.windowTicks = windowTicks;
    }

    /** @return the decision window's length in ticks. */
    public long windowTicks() {
        return windowTicks;
    }

    /** @return how many splits the loop applied (telemetry). */
    public long splitsApplied() {
        return splitsApplied.get();
    }

    /** @return how many merges the loop applied (telemetry). */
    public long mergesApplied() {
        return mergesApplied.get();
    }

    /** @return how many policy decisions the loop refused to apply (telemetry). */
    public long applicationsRefused() {
        return applicationsRefused.get();
    }

    /**
     * One engine tick of the loop. Tick-thread context, called at the
     * scheduler walk's completion (the safe point — every domain gate
     * free). Non-boundary ticks answer an empty map and move nothing; the
     * window-closing tick answers every censused domain's decision and
     * applies the actionable ones (SPLIT on a whole region's base, MERGE
     * on an active subdivision's half) through the executors.
     *
     * @param nowTick the caller's tick clock (the policy's decision clock)
     * @return the window's decisions keyed by domain name (empty when the
     *         window did not close this tick) — telemetry for the caller;
     *         the applications already ran
     */
    public Map<String, MigrationPolicy.Decision> tick(long nowTick) {
        if (opening == null) {
            opening = MigrationLoadSampler.snapshotOf(scheduler);
            openingTick = nowTick;
            return Map.of();
        }
        long elapsed = nowTick - openingTick;
        if (elapsed < windowTicks) {
            return Map.of();
        }
        MigrationLoadSampler.Snapshot closing = MigrationLoadSampler.snapshotOf(scheduler);
        Map<String, MigrationPolicy.Decision> decisions = new LinkedHashMap<>();
        // The census: every domain the partitions know — the bases and
        // every active subdivision's half — in the stable name order
        // (deterministic on every JVM; the applications ride the same
        // order).
        for (RegionPartition partition : partitions) {
            for (OwnershipDomain domain : partition.domains()) {
                double load = MigrationLoadSampler.loadBetween(
                        opening, closing, domain.name(), elapsed);
                MigrationPolicy.Decision decision =
                        policy.decide(domain.name(), load, nowTick);
                decisions.put(domain.name(), decision);
                applyDecision(partition, domain.name(), decision, nowTick);
            }
        }
        // The next window opens now.
        opening = closing;
        openingTick = nowTick;
        return decisions;
    }

    /**
     * One decision's application: SPLIT resolves the base's region and
     * walks the executor; MERGE resolves the half's subdivision and walks
     * the executor; every other answer (and every unresolvable name — a
     * split on an already-subdivided region, a merge on an undivided one,
     * a foreign domain) is a refusal: nothing noted, nothing moved.
     */
    private void applyDecision(RegionPartition homePartition, String domainName,
                               MigrationPolicy.Decision decision, long nowTick) {
        switch (decision) {
            case SPLIT -> {
                Long regionId = homePartition.regionIdOfBase(domainName);
                if (regionId == null) {
                    refused(domainName, decision, "not a whole region's base");
                    return;
                }
                try {
                    MigrationExecutors.SplitOutcome outcome = MigrationExecutors.splitRegion(
                            homePartition, scheduler, router, regionId, nowTick);
                    splitsApplied.incrementAndGet();
                    // The cooldown anchors BOTH sides: the base's settling
                    // window and the fresh half's (whose zero-load joiner
                    // contract is the merge signal, not a decision input).
                    policy.noteMigration(outcome.base().name(), nowTick);
                    policy.noteMigration(outcome.half().name(), nowTick);
                } catch (IllegalStateException refused) {
                    refused(domainName, decision, refused.getMessage());
                }
            }
            case MERGE -> {
                RegionPartition.Subdivision subdivision =
                        homePartition.subdivisionOfHalf(domainName);
                if (subdivision == null) {
                    refused(domainName, decision, "not an active subdivision's half");
                    return;
                }
                try {
                    MigrationExecutors.MergeOutcome outcome = MigrationExecutors.mergeRegion(
                            homePartition, scheduler, router, subdivision.regionId(), nowTick);
                    mergesApplied.incrementAndGet();
                    // Both sides again: the survivor settles, the retired
                    // half is gone (its cooldown row is harmless history).
                    policy.noteMigration(outcome.base().name(), nowTick);
                    policy.noteMigration(outcome.retiredHalf().name(), nowTick);
                } catch (IllegalStateException refused) {
                    refused(domainName, decision, refused.getMessage());
                }
            }
            case NONE -> {
                // The dominant answer — nothing to apply.
            }
        }
    }

    /** A refused application: expected shapes log at FINE and count. */
    private void refused(String domainName, MigrationPolicy.Decision decision, String why) {
        applicationsRefused.incrementAndGet();
        LOGGER.fine(() -> "Balancer refused " + decision + " for '" + domainName
                + "': " + why);
    }

    /** @return whether a decision window is currently open (telemetry). */
    public boolean windowOpen() {
        return opening != null;
    }

    /** The policy's cooldown consult, exposed for the loop's own seats. */
    public boolean inCooldown(String domainName, long nowTick) {
        return policy.inCooldown(domainName, nowTick);
    }

    /**
     * A diagnostic line for the operator's log at arm time.
     */
    @Override
    public String toString() {
        return "MigrationBalancer[window=" + windowTicks + " splits=" + splitsApplied.get()
                + " merges=" + mergesApplied.get() + " refused=" + applicationsRefused.get()
                + " policy=" + policy.highWater + "/" + policy.lowWater
                + " sustain=" + policy.sustainSamples + " cooldown=" + policy.cooldownTicks + "]";
    }
}
