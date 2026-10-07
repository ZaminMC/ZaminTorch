package net.zamin.engine.item;

/**
 * The material of a tool: its mining speed multiplier when the tool class
 * matches the block's material, the harvest tier it satisfies, and the
 * durability limit of items made of it. All values are the community
 * dataset's (PrismarineJS/minecraft-data pc/1.8, MIT; ADR-0002):
 * materials.json speeds, blocks.json harvestTools tiers, items.json
 * maxDurability.
 *
 * <p>Historical examples verified against the dataset: a wooden pickaxe mines
 * stone at 2x (tier 1 satisfies stone's requirement), a stone pickaxe at 4x
 * (tier 2 satisfies iron ore), an iron pickaxe at 6x (tier 3 satisfies
 * diamond ore), a diamond pickaxe at 8x (tier 4), a golden pickaxe at 12x
 * (tier 1 - fast but weak, as historically).</p>
 */
public enum ToolMaterial {
    WOOD(2.0, 1, 59),
    STONE(4.0, 2, 131),
    IRON(6.0, 3, 250),
    DIAMOND(8.0, 4, 1561),
    GOLD(12.0, 1, 32);

    private final double speedMultiplier;
    private final int harvestLevel;
    private final int maxDurability;

    ToolMaterial(double speedMultiplier, int harvestLevel, int maxDurability) {
        this.speedMultiplier = speedMultiplier;
        this.harvestLevel = harvestLevel;
        this.maxDurability = maxDurability;
    }

    /** Mining speed multiplier when the tool class matches the block material. */
    public double speedMultiplier() {
        return speedMultiplier;
    }

    /** Highest harvest tier this material satisfies. */
    public int harvestLevel() {
        return harvestLevel;
    }

    /** Damage value at which an item of this material breaks. */
    public int maxDurability() {
        return maxDurability;
    }
}
