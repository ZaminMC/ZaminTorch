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

    private BuiltinBlocks() {
    }

    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        return builder
                .register(AIR)
                .register(STONE)
                .register(GRASS_BLOCK)
                .register(DIRT)
                .register(BEDROCK);
    }
}
