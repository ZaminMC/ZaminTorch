package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

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

    /**
     * One yielded item: canonical item identifier, unit count, and the
     * probability the entry yields at all. Entries are rolled in order and the
     * <b>first successful roll wins</b> — the model of the historical
     * {@code quantityDropped(Random)} overrides (gravel: 10% flint, otherwise
     * gravel). Chance 1.0 first entries reproduce the fixed-drop behavior.
     *
     * <p>The damage field carries the vanilla data value when the dropped
     * item is a single damage-domain family member (Slice 31 — the dye
     * families: a lime wool block yields {@code minecraft:wool} damage 5,
     * the reference's {@code getDropItemMetadata}).</p>
     *
     * @param item   canonical item identifier
     * @param count  units yielded when this entry wins
     * @param chance probability in [0,1] that the roll stops here
     */
    public record Drop(Identifier item, int count, double chance, int damage) {
        public Drop {
            if (count < 1) {
                throw new IllegalArgumentException("Drop count must be positive: " + count);
            }
            if (chance < 0.0 || chance > 1.0) {
                throw new IllegalArgumentException("Drop chance must be in [0,1]: " + chance);
            }
            if (damage < 0) {
                throw new IllegalArgumentException("Drop damage must be non-negative: " + damage);
            }
        }

        /** The common fixed drop (always yields, damage 0). */
        public Drop(Identifier item, int count) {
            this(item, count, 1.0, 0);
        }

        /** The chanced drop (the historical quantityDropped rolls). */
        public Drop(Identifier item, int count, double chance) {
            this(item, count, chance, 0);
        }

        /** The fixed drop of a damage-domain family member (Slice 31). */
        public Drop(Identifier item, int count, int damage) {
            this(item, count, 1.0, damage);
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
