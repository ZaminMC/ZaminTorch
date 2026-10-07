package net.zamin.engine.block;

import net.zamin.api.Identifier;

import java.util.List;

/**
 * Survival-relevant behavior of one block type: how long it takes to break and
 * what it yields (§424 "drops" capability, §434 "drops generated from gameplay
 * state"). Capability-model scoped: only the behaviors the current slice needs;
 * fluid/redstone/etc. capabilities are intentionally absent.
 *
 * @param hardness    vanilla hardness value; negative encodes "unbreakable"
 * @param diggable    whether a player can break it at all
 * @param requiresTool whether a harvest-appropriate tool is required for DROPS
 *                    (breaking by hand still works historically, it just
 *                    yields nothing and takes the slow timing)
 * @param material    vanilla material category ("rock", "dirt", ...), future
 *                    tool-speed input; part of the dataset snapshot
 * @param drops       what this block yields when broken with a valid harvest
 *                    tool (or by hand when no tool is required)
 */
public record BlockBehavior(
        double hardness,
        boolean diggable,
        boolean requiresTool,
        String material,
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
        return new BlockBehavior(-1.0, false, false, null, List.of());
    }

    /** @return the historical break duration in ticks at hand speed (20 ticks/second). */
    public int breakTicks(boolean canHarvest) {
        if (!diggable || hardness < 0) {
            return Integer.MAX_VALUE; // never completes
        }
        // Vanilla per-tick aggregate: harvestable hardness*1.5s, otherwise hardness*5s.
        // At 20 ticks/second: hardness*30 / hardness*100 ticks (matches wiki timings,
        // e.g. dirt 0.5 -> 15 ticks (0.75s), stone by hand 1.5 -> 150 ticks (7.5s)).
        return (int) Math.round(hardness * (canHarvest ? 30.0 : 100.0));
    }
}
