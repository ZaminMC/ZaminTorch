package net.zamin.engine.block;

import net.zamin.api.Identifier;

import java.util.List;

/**
 * Survival-relevant behavior of one block type: how long it takes to break and
 * what it yields (§424 "drops" capability, §434 "drops generated from gameplay
 * state"). Capability-model scoped: only the behaviors the current slice needs;
 * fluid/redstone/etc. capabilities are intentionally absent.
 *
 * @param hardness      vanilla hardness value; negative encodes "unbreakable"
 * @param diggable      whether a player can break it at all
 * @param requiresTool  whether a harvest-appropriate tool is required for DROPS
 *                      (breaking by hand still works historically, it just
 *                      yields nothing and takes the slow timing)
 * @param material      vanilla material category ("rock", "dirt", "wood"), the
 *                      input for tool-class matching and speed multipliers
 * @param harvestLevel  the harvest tier required for drops when
 *                      {@code requiresTool} is set (1 = any pickaxe material,
 *                      2 = stone tier, 3 = iron tier); 0 when no tool is needed
 * @param drops         what this block yields when broken with a valid harvest
 *                      tool (or by hand when no tool is required)
 */
public record BlockBehavior(
        double hardness,
        boolean diggable,
        boolean requiresTool,
        String material,
        int harvestLevel,
        List<Drop> drops) {

    /** One yielded item: canonical item identifier and unit count. */
    public record Drop(Identifier item, int count) {
        public Drop {
            if (count < 1) {
                throw new IllegalArgumentException("Drop count must be positive: " + count);
            }
        }
    }

    public static BlockBehavior unbreakable() {
        return new BlockBehavior(-1.0, false, false, null, 0, List.of());
    }

    /**
     * @return the historical break duration in ticks (20 ticks/second) at the
     *         given mining speed (1.0 = bare hand, or a tool whose class does
     *         not match the block's material).
     */
    public int breakTicks(double speedMultiplier, boolean canHarvest) {
        if (!diggable || hardness < 0) {
            return Integer.MAX_VALUE; // never completes
        }
        // Vanilla per-tick aggregate: harvestable hardness*1.5s, otherwise
        // hardness*5s, divided by the speed multiplier; a break completes on the
        // first tick the accumulated damage reaches 1, so round up.
        // (matches wiki timings: dirt 0.5 -> 15 ticks (0.75s), stone by hand
        // 1.5 -> 150 ticks (7.5s), stone with a wooden pickaxe 1.5*30/2 -> 23)
        double ticks = hardness * (canHarvest ? 30.0 : 100.0) / speedMultiplier;
        return (int) Math.ceil(ticks - 1e-9);
    }
}
