package net.zaminmc.torch.block;

import net.zaminmc.torch.util.Identifier;

import java.util.List;

/**
 * The survival-relevant data of one block type (Slice 14b — the §27.3
 * block-state family read): how long it takes to break, whether a tool
 * is required for drops, its material family, its light behavior, and
 * its drop table. Read-only data; the engine's own tables are the
 * authority behind it.
 *
 * <p>Instances are immutable and safe to share across threads. A block
 * the engine carries no behavior row for answers the
 * {@link #UNREGISTERED} default (transparent, instantly breakable, no
 * drops).</p>
 *
 * @param hardness     the vanilla hardness value; negative encodes
 *                     "unbreakable"
 * @param diggable     whether a player can break it at all
 * @param requiresTool whether a harvest-appropriate tool is required
 *                     for DROPS (breaking by hand still works, it just
 *                     yields nothing and takes the slow timing)
 * @param material     the material family ("rock", "dirt", "wood") —
 *                     the tool-class matching input, or null when
 *                     unknown
 * @param harvestLevel the harvest tier required for drops when
 *                     {@code requiresTool} is set (1 = any pickaxe
 *                     material, 2 = stone tier, 3 = iron tier); 0 when
 *                     no tool is needed
 * @param lightEmission the block's own light output (0-15)
 * @param lightFilter   how much light the block absorbs when passed
 *                     through (0-15; 15 = fully opaque)
 * @param silkTouchable whether silk touch drops the block itself (the
 *                     reference's {@code isCube() && !hasBlockEntity}
 *                     gate)
 * @param drops        the drop table (first successful roll wins); an
 *                     empty list yields nothing
 */
public record BlockData(
        double hardness,
        boolean diggable,
        boolean requiresTool,
        String material,
        int harvestLevel,
        int lightEmission,
        int lightFilter,
        boolean silkTouchable,
        List<Drop> drops) {

    /**
     * One yielded item: the item identifier, the unit count, and the
     * probability the entry yields at all. Entries are rolled in order
     * and the first successful roll wins.
     *
     * @param item   the canonical item identifier
     * @param count  units yielded when this entry wins
     * @param chance probability in [0,1] that the roll stops here
     */
    public record Drop(Identifier item, int count, double chance) {
        public Drop {
            if (count < 1) {
                throw new IllegalArgumentException("Drop count must be positive: " + count);
            }
            if (chance < 0.0 || chance > 1.0) {
                throw new IllegalArgumentException("Drop chance must be in [0,1]");
            }
        }
    }

    /** The default for types the engine carries no behavior row for. */
    public static final BlockData UNREGISTERED = new BlockData(
            0.0, true, false, null, 0, 0, 0, false, List.of());

    /** @return whether the block emits any light of its own. */
    public boolean emitsLight() {
        return lightEmission > 0;
    }

    /** @return whether the block fully blocks passing light. */
    public boolean opaque() {
        return lightFilter >= 15;
    }

    /**
     * @return the historical break duration in ticks (20 per second) at
     *         the given mining speed (1.0 = bare hand or a tool whose
     *         class does not match): harvestable hardness takes
     *         {@code hardness * 1.5s}, otherwise {@code hardness * 5s},
     *         both divided by the speed; a break completes on the first
     *         tick the accumulated damage reaches 1, so the value rounds
     *         up. Unbreakable answers {@link Integer#MAX_VALUE}.
     */
    public int breakTicks(double speedMultiplier, boolean canHarvest) {
        if (!diggable || hardness < 0) {
            return Integer.MAX_VALUE;
        }
        double ticks = hardness * (canHarvest ? 30.0 : 100.0) / speedMultiplier;
        return (int) Math.ceil(ticks - 1e-9);
    }
}
