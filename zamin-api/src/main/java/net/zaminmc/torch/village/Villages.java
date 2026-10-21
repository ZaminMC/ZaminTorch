package net.zaminmc.torch.village;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.block.Direction;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The village-census query surface (§27.3's world-state family): the
 * reference 1.8.8's own {@code Village}/{@code VillageDoorInfo} reads —
 * the door census, the center/radius band, the population and golem
 * counts, the per-player reputation table, and the mating window —
 * reached through {@link Server#villages()}.
 *
 * <p>The engine behind it is {@code server/village/} (the VIII10 slice:
 * {@code VillageSystem} over the door census with the inside-facing sky
 * walk, the 1200-tick staleness, the founding/merge bands, the iron-golem
 * spawn, and the reputation clamps). This surface exposes the READS a
 * plugin needs to answer "where are the villages and what do they know?" —
 * the census's own mutation arms (the door adds, the reputation updates)
 * stay engine-owned, driven by the live door/mob walks.</p>
 *
 * <p><b>Threading (§27.6):</b> every answer is an immutable point-in-time
 * snapshot — the census copies the village list and each village's door
 * rows and reputation table at query time, so the result is freely
 * consumable from any thread. A snapshot taken off the simulation
 * context is best-effort: it reflects the census at some instant between
 * ticks, never a torn mid-tick state (the census mutates on the tick
 * lane). The record types carry no engine references —
 * {@link Village#doors()} answers the copied rows, not a live
 * collection.</p>
 */
public interface Villages {

    /** The empty answer (no villages founded — an unbooted world's view). */
    Villages EMPTY = new Villages() {
        @Override public List<Village> villages() {
            return List.of();
        }

        @Override public Optional<Village> nearest(int x, int y, int z, int range) {
            return Optional.empty();
        }
    };

    /**
     * @return a point-in-time copy of every founded village (the census's
     *         own order), each with its center/radius band, its door rows,
     *         its population and golem counts, and its reputation table.
     */
    List<Village> villages();

    /**
     * The reference's own village lookup ({@code getNearestVillage}): the
     * closest village whose radius band contains the position within the
     * extra range.
     *
     * @param x the query's x
     * @param y the query's y
     * @param z the query's z
     * @param range the extra band beyond the radius (the mating gate's
     *              own consult reads 0)
     * @return the nearest containing village, or empty when none holds
     */
    Optional<Village> nearest(int x, int y, int z, int range);

    /**
     * One village's point-in-time census row (the reference's
     * {@code Village} reads): the center is the door average, the radius
     * the 32-minimum over the farthest door, the reputation table the
     * per-player clamped scores.
     *
     * @param centerX      the center's x (the doors' average)
     * @param centerY      the center's y
     * @param centerZ      the center's z
     * @param radius       the 32-minimum band over the farthest door
     * @param doorCount    the census's own door rows
     * @param populationSize the villager census (the %20-tick walk)
     * @param golems       the iron-golem census (the %30-tick walk)
     * @param canMate      the mating window (the 3600-tick cooldown's
     *                     own consult — {@code stopMating}'s inverse)
     * @param doors        the copied door rows (immutable)
     * @param reputations  the copied per-player reputation table
     *                     (immutable, the -30..10 clamp's own values)
     */
    record Village(int centerX, int centerY, int centerZ, int radius,
                   int doorCount, int populationSize, int golems,
                   boolean canMate,
                   List<Door> doors,
                   Map<String, Integer> reputations) {

        /**
         * @param x a world x
         * @param y a world y
         * @param z a world z
         * @return whether the position sits inside the radius band (the
         *         squared-distance consult, the reference's
         *         {@code isAnnexed})
         */
        public boolean contains(int x, int y, int z) {
            long dx = x - centerX;
            long dy = y - centerY;
            long dz = z - centerZ;
            return dx * dx + dy * dy + dz * dz <= (long) radius * radius;
        }

        /**
         * @param playerName the player's own name (the reputation table's
         *                    key — the reference's TreeMap by name)
         * @return the clamped reputation score (0 for an unknown player —
         *         the reference's absent-row default)
         */
        public int reputationOf(String playerName) {
            return reputations.getOrDefault(playerName, 0);
        }

        /**
         * @param playerName the player's own name
         * @return whether the player sits in the bad band (at or below
         *         the reference's {@code -15} threshold)
         */
        public boolean hasBadReputation(String playerName) {
            return reputationOf(playerName) <= -15;
        }
    }

    /**
     * One door row (the reference's {@code VillageDoorInfo} reads): the
     * cell, the INSIDE facing the census's sky walk derived, the last
     * census tick, and the staleness band's own rows.
     *
     * @param x               the door cell's x
     * @param y               the door cell's y
     * @param z               the door cell's z
     * @param facing          the inside facing (the smaller-sky side)
     * @param indoorsOffsetX  the indoors position's x offset (two cells
     *                        along the facing — the isIndoors consult)
     * @param indoorsOffsetZ  the indoors position's z offset
     * @param lastUpdateTick  the tick the door last joined a census walk
     * @param outside         the outside flag (the unsheltered door's
     *                        own row)
     * @param restrictedTicks the restricted-opening counter (the siege
     *                        family's own row)
     */
    record Door(int x, int y, int z, Direction facing,
                int indoorsOffsetX, int indoorsOffsetZ,
                long lastUpdateTick, boolean outside, int restrictedTicks) {
    }
}
