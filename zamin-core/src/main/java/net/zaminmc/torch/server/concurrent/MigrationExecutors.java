package net.zaminmc.torch.server.concurrent;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * THE SPLIT/MERGE EXECUTORS (Phase 8's standing follow-on, Wave MIG-3): the
 * components that APPLY a {@link MigrationPolicy} decision through the
 * {@link OwnershipMigration} protocol — the phase's own law that a boundary
 * moves only as a protocol run, never a map update. The protocol itself is
 * the MIG-1-proven walk (in-flight tasks, delayed work, cross-owner
 * messages, stale-generation invalidation, telemetry); the executors bind
 * it to the {@link RegionPartition}'s subdivision layer, the routing table
 * the design names as the migrating scope:
 *
 * <p><b>THE SPLIT</b> — one sustainedly-hot region's base domain walks the
 * whole protocol while carrying its live shapes: the safe-point check and
 * the admission hold (begin), the already-queued tasks and the inbound
 * cross-owner intents draining at the old generation inside the old
 * owner's context (drainQueued + the router's final boundary), the routing
 * change itself as the transfer payload (the subdivision installs — the
 * base keeps the lower half, a fresh half domain takes the upper), and the
 * atomic publish (the base's generation bumps, admission re-opens, every
 * pre-split capture becomes diagnosably stale). The split law is
 * deterministic: along the chunk-X axis at the region's midpoint boundary
 * (the lower half the larger when the region edge is odd) — the square
 * grid carries no longer axis to prefer, and one fixed law keeps every
 * JVM's answer identical.</p>
 *
 * <p><b>THE MERGE</b> — a sustainedly-cold half walks the same protocol on
 * ITS authority: the half's queued work and inbound intents drain at the
 * half's final boundary, the subdivision removal is the transfer payload
 * (the upper half's chunks return to the base), and the publish bumps the
 * HALF's generation — the retired authority is diagnosably stale forever
 * after, and the partition's serial law guarantees a later re-split mints
 * a fresh domain, never an alias. The base's own authority is untouched:
 * its admission never held, its generation never bumped, its in-flight
 * work never interrupted — the merge strictly WIDENS what the base may
 * write (the §9 invariant holds: no two authorities ever own the same
 * chunk, because the routing flips at a single point inside the half's
 * transfer).</p>
 *
 * <p>Both executors refuse loudly on the shape's own law: a split on an
 * already-subdivided region (the layer is single-level), a split on an
 * atomic region (a 1-chunk grid has no halves), a merge on an undivided
 * region, a merge whose half is not the subdivision's own. The refusals
 * are {@link IllegalStateException} — the caller (the balancer) treats
 * them as "no migration happened": no cooldown is noted, no telemetry
 * moves, the policy's own sustained-run reset governs the next
 * attempt.</p>
 *
 * <p>Threading: the executors run on the tick thread at a defined safe
 * point (the scheduler walk's completion — every domain gate free); the
 * protocol's begin() is the belt-and-braces refusal if a gate is held.</p>
 */
public final class MigrationExecutors {

    private static final Logger LOGGER = Logger.getLogger(MigrationExecutors.class.getName());

    /** A split's terminal outcome (the balancer's cooldown anchors). */
    public record SplitOutcome(
            /** The region's base domain — the lower half's continuing owner. */
            OwnershipDomain base,
            /** The fresh half domain — the upper half's new owner. */
            OwnershipDomain half,
            /** The subdivided region's packed id. */
            long regionId,
            /** The split axis (always {@link RegionPartition.Subdivision#AXIS_X} today). */
            int axis,
            /** The boundary chunk coordinate (the base owns coord &lt; atChunk). */
            int atChunk,
            /** How many tasks drained at the old generation. */
            int drainedTasks,
            /** How many cross-owner intents applied at the old boundary. */
            int drainedIntents,
            /** The base's generation before the publish (stale-capture proof). */
            long baseGenerationBefore) {
    }

    /** A merge's terminal outcome. */
    public record MergeOutcome(
            /** The region's base domain — the re-unified owner. */
            OwnershipDomain base,
            /** The retired half domain — never an authority again. */
            OwnershipDomain retiredHalf,
            /** The re-unified region's packed id. */
            long regionId,
            /** How many tasks drained at the half's final boundary. */
            int drainedTasks,
            /** How many cross-owner intents applied at the half's final boundary. */
            int drainedIntents,
            /** The retired half's generation before the publish. */
            long halfGenerationBefore) {
    }

    private MigrationExecutors() {
    }

    /**
     * THE SPLIT: one region's base domain divides its scope along the
     * chunk-X axis at the region's midpoint, the fresh half taking the
     * upper half's chunks — the whole {@link OwnershipMigration} protocol
     * over the routing change.
     *
     * @param partition the region's partition (the routing table that moves)
     * @param scheduler the scheduler whose queues drain at the old generation
     * @param router    the cross-owner router whose intents apply at the
     *                  old boundary
     * @param regionId  the region to split
     * @param nowTick   the tick clock for the delayed-work drain
     * @return the terminal outcome (never partial — a failure aborts whole)
     * @throws IllegalStateException when the region is already subdivided,
     *         the grid is atomic, or the protocol's own safe-point refuses
     */
    public static SplitOutcome splitRegion(RegionPartition partition,
                                           SimulationScheduler scheduler,
                                           CrossOwnerRouter router,
                                           long regionId,
                                           long nowTick) {
        Objects.requireNonNull(partition, "partition");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(router, "router");
        if (partition.subdivisionOf(regionId) != null) {
            throw new IllegalStateException("region " + regionId + " is already subdivided — "
                    + "the split layer is single-level (merge before re-splitting)");
        }
        if (partition.regionSizeChunks() < 2) {
            throw new IllegalStateException("region " + regionId + " is atomic (a "
                    + partition.regionSizeChunks() + "-chunk grid has no halves)");
        }
        // The deterministic split law: along chunk X at the region's
        // midpoint boundary — the lower half (the base's) the larger when
        // the region edge is odd.
        int originX = RegionPartition.regionXOf(regionId) * partition.regionSizeChunks();
        int atChunk = originX + (partition.regionSizeChunks() + 1) / 2;
        OwnershipDomain base = partition.baseDomainFor(regionId);
        OwnershipDomain half = partition.createHalfDomain(regionId);
        long generationBefore = base.generation();

        OwnershipMigration migration = new OwnershipMigration(base, scheduler);
        migration.begin(); // the safe point + the admission hold (§9 steps 1-2)
        int drainedIntents;
        int drainedTasks;
        try {
            base.enter();
            try {
                // §9 step 3 + the §7 target-side apply: the queued work and
                // the inbound intents finish at the old generation, inside
                // the old owner's context — the base's FINAL boundary over
                // the whole region.
                drainedIntents = router.drainIntents(base);
                drainedTasks = migration.drainQueued(nowTick);
                // §9 step 4: the state move IS the routing change — the
                // subdivision installs here and only here.
                migration.transfer(() -> partition.installSubdivision(
                        regionId, RegionPartition.Subdivision.AXIS_X, atChunk, half));
            } finally {
                base.exit();
            }
        } catch (Throwable t) {
            // A failed transfer already aborted inside the protocol; a
            // failed drain leaves the protocol mid-window — abort loud
            // (idempotent), the scope stays whole, the routing never
            // moved, admission releases, the generation still bumps.
            migration.abort();
            throw t;
        }
        // §9 steps 5-7: the atomic publish — the base's generation bumps
        // (every pre-split capture is stale), admission re-opens.
        migration.publish();
        LOGGER.info(() -> "Region " + regionId + " split at chunk x=" + atChunk
                + ": base '" + base.name() + "' keeps the lower half, '"
                + half.name() + "' owns the upper (" + drainedTasks + " tasks, "
                + drainedIntents + " intents drained at the old boundary)");
        return new SplitOutcome(base, half, regionId,
                RegionPartition.Subdivision.AXIS_X, atChunk,
                drainedTasks, drainedIntents, generationBefore);
    }

    /**
     * THE MERGE: a region's active subdivision retires — the sustainedly-cold
     * half walks the whole {@link OwnershipMigration} protocol on its own
     * authority and the upper half's chunks return to the base.
     *
     * @param partition the region's partition (the routing table that moves)
     * @param scheduler the scheduler whose queues drain at the half's final
     *                  boundary
     * @param router    the cross-owner router whose intents apply at the
     *                  half's final boundary
     * @param regionId  the region whose subdivision retires
     * @param nowTick   the tick clock for the delayed-work drain
     * @return the terminal outcome (never partial)
     * @throws IllegalStateException when the region carries no subdivision,
     *         or the protocol's own safe-point refuses
     */
    public static MergeOutcome mergeRegion(RegionPartition partition,
                                           SimulationScheduler scheduler,
                                           CrossOwnerRouter router,
                                           long regionId,
                                           long nowTick) {
        Objects.requireNonNull(partition, "partition");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(router, "router");
        RegionPartition.Subdivision subdivision = partition.subdivisionOf(regionId);
        if (subdivision == null) {
            throw new IllegalStateException("region " + regionId
                    + " carries no subdivision — nothing to merge");
        }
        OwnershipDomain base = partition.baseDomainFor(regionId);
        OwnershipDomain half = subdivision.half();
        long generationBefore = half.generation();

        OwnershipMigration migration = new OwnershipMigration(half, scheduler);
        migration.begin(); // the HALF's safe point + admission hold
        int drainedIntents;
        int drainedTasks;
        try {
            half.enter();
            try {
                // The half's final boundary: its queued work and inbound
                // intents finish at its old generation. The base's own
                // authority is untouched — its admission never held, its
                // in-flight work never interrupted.
                drainedIntents = router.drainIntents(half);
                drainedTasks = migration.drainQueued(nowTick);
                // The state move: the subdivision removes — the upper
                // half's chunks return to the base at this single point.
                migration.transfer(() -> partition.removeSubdivision(regionId, half));
            } finally {
                half.exit();
            }
        } catch (Throwable t) {
            migration.abort();
            throw t;
        }
        // The publish bumps the HALF's generation — the retired authority
        // is diagnosably stale forever; the partition's serial law
        // guarantees a later re-split mints a fresh domain.
        migration.publish();
        LOGGER.info(() -> "Region " + regionId + " merged: '" + half.name()
                + "' retired into '" + base.name() + "' (" + drainedTasks + " tasks, "
                + drainedIntents + " intents drained at the half's final boundary)");
        return new MergeOutcome(base, half, regionId,
                drainedTasks, drainedIntents, generationBefore);
    }
}
