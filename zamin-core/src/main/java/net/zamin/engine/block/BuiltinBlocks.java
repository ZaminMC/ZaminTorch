package net.zamin.engine.block;

import net.zamin.api.Identifier;

/**
 * The block types built into the engine. This is data, not code: the set grows as
 * gameplay slices require more blocks, always with its canonical identifier first.
 */
public final class BuiltinBlocks {

    public static final EngineBlockType AIR = new EngineBlockType(
            Identifier.parse("minecraft:air"), "Air");
    public static final EngineBlockType STONE = new EngineBlockType(
            Identifier.parse("minecraft:stone"), "Stone");
    public static final EngineBlockType GRASS_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:grass_block"), "Grass Block");
    public static final EngineBlockType DIRT = new EngineBlockType(
            Identifier.parse("minecraft:dirt"), "Dirt");
    public static final EngineBlockType BEDROCK = new EngineBlockType(
            Identifier.parse("minecraft:bedrock"), "Bedrock");

    // Ore ladder for harvest gating: coal (tier 1) -> iron (tier 2) -> diamond (tier 3).
    public static final EngineBlockType COAL_ORE = new EngineBlockType(
            Identifier.parse("minecraft:coal_ore"), "Coal Ore");
    public static final EngineBlockType IRON_ORE = new EngineBlockType(
            Identifier.parse("minecraft:iron_ore"), "Iron Ore");
    public static final EngineBlockType DIAMOND_ORE = new EngineBlockType(
            Identifier.parse("minecraft:diamond_ore"), "Diamond Ore");

    // Craftable blocks (community-data recipes place their results here).
    public static final EngineBlockType CRAFTING_TABLE = new EngineBlockType(
            Identifier.parse("minecraft:crafting_table"), "Crafting Table");
    public static final EngineBlockType TORCH = new EngineBlockType(
            Identifier.parse("minecraft:torch"), "Torch");
    // 3x3 crafting-table unlocks (community-data recipes): the furnace and the
    // chest are the two ring-pattern results whose ingredients the registry has.
    public static final EngineBlockType FURNACE = new EngineBlockType(
            Identifier.parse("minecraft:furnace"), "Furnace");
    public static final EngineBlockType CHEST = new EngineBlockType(
            Identifier.parse("minecraft:chest"), "Chest");

    private BuiltinBlocks() {
    }

    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        return builder
                .register(AIR)
                .register(STONE)
                .register(GRASS_BLOCK)
                .register(DIRT)
                .register(BEDROCK)
                .register(COAL_ORE)
                .register(IRON_ORE)
                .register(DIAMOND_ORE)
                .register(CRAFTING_TABLE)
                .register(TORCH)
                .register(FURNACE)
                .register(CHEST);
    }
}
