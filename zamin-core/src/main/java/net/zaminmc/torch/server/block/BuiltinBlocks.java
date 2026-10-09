package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

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
    // The deep ladder for the full-world generator: gold and redstone ride
    // the same pickaxe gate as iron (community blocks.json), the oak trunk
    // and canopy fill the surface forests.
    public static final EngineBlockType GOLD_ORE = new EngineBlockType(
            Identifier.parse("minecraft:gold_ore"), "Gold Ore");
    public static final EngineBlockType REDSTONE_ORE = new EngineBlockType(
            Identifier.parse("minecraft:redstone_ore"), "Redstone Ore");
    public static final EngineBlockType OAK_LOG = new EngineBlockType(
            Identifier.parse("minecraft:oak_log"), "Oak Log");
    public static final EngineBlockType OAK_LEAVES = new EngineBlockType(
            Identifier.parse("minecraft:oak_leaves"), "Oak Leaves");

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
    // Smelting and placement blocks (community blocks.json: sand 0.5 dirt
    // material, glass 0.3 with no drops — the historical shatter).
    public static final EngineBlockType SAND = new EngineBlockType(
            Identifier.parse("minecraft:sand"), "Sand");
    public static final EngineBlockType GLASS = new EngineBlockType(
            Identifier.parse("minecraft:glass"), "Glass");
    // Gravity block (community blocks.json: gravel 0.6 dirt material, drops
    // itself; the 10% flint roll is the behavior table's chance model).
    public static final EngineBlockType GRAVEL = new EngineBlockType(
            Identifier.parse("minecraft:gravel"), "Gravel");
    // Fluid-contact products (the historical outcomes): a lava source touched
    // by water hardens into obsidian (legacy 49), a flowing stream into
    // cobblestone (legacy 4 — the item registry already carries the item form).
    public static final EngineBlockType OBSIDIAN = new EngineBlockType(
            Identifier.parse("minecraft:obsidian"), "Obsidian");
    public static final EngineBlockType COBBLESTONE = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone"), "Cobblestone");

    // Decoration flora (community blocks.json: 1.8 block ids 31/32/37/38).
    // The plants walk-through like torches (WorldSolidity) and break
    // instantly; the desert's dead bush shades the biome variety slice.
    public static final EngineBlockType TALL_GRASS = new EngineBlockType(
            Identifier.parse("minecraft:tall_grass"), "Tall Grass");
    public static final EngineBlockType DEAD_BUSH = new EngineBlockType(
            Identifier.parse("minecraft:dead_bush"), "Dead Bush");
    public static final EngineBlockType DANDELION = new EngineBlockType(
            Identifier.parse("minecraft:dandelion"), "Dandelion");
    public static final EngineBlockType POPPY = new EngineBlockType(
            Identifier.parse("minecraft:poppy"), "Poppy");
    // The desert's stone band (legacy 24): the sandstone skin under the sand.
    public static final EngineBlockType SANDSTONE = new EngineBlockType(
            Identifier.parse("minecraft:sandstone"), "Sandstone");

    // The farming slice (community blocks.json ids): farmland 60 (the wet
    // variant carries metadata 1) and the wheat crop 59 with the growth age
    // 0..7 in the metadata nibble — the historical age encoding. Each stage
    // is its own block type (the engine's fluid-level model: per-state types
    // with a wire metadata nibble, no second metadata system).
    public static final EngineBlockType FARMLAND = new EngineBlockType(
            Identifier.parse("minecraft:farmland"), "Farmland");
    public static final EngineBlockType FARMLAND_WET = new EngineBlockType(
            Identifier.parse("minecraft:farmland_wet"), "Farmland", 1);
    public static final EngineBlockType WHEAT_STAGE0 = new EngineBlockType(
            Identifier.parse("minecraft:wheat_stage0"), "Wheat Crop");
    public static final EngineBlockType WHEAT_STAGE1 = new EngineBlockType(
            Identifier.parse("minecraft:wheat_stage1"), "Wheat Crop", 1);
    public static final EngineBlockType WHEAT_STAGE2 = new EngineBlockType(
            Identifier.parse("minecraft:wheat_stage2"), "Wheat Crop", 2);
    public static final EngineBlockType WHEAT_STAGE3 = new EngineBlockType(
            Identifier.parse("minecraft:wheat_stage3"), "Wheat Crop", 3);
    public static final EngineBlockType WHEAT_STAGE4 = new EngineBlockType(
            Identifier.parse("minecraft:wheat_stage4"), "Wheat Crop", 4);
    public static final EngineBlockType WHEAT_STAGE5 = new EngineBlockType(
            Identifier.parse("minecraft:wheat_stage5"), "Wheat Crop", 5);
    public static final EngineBlockType WHEAT_STAGE6 = new EngineBlockType(
            Identifier.parse("minecraft:wheat_stage6"), "Wheat Crop", 6);
    public static final EngineBlockType WHEAT_STAGE7 = new EngineBlockType(
            Identifier.parse("minecraft:wheat_stage7"), "Wheat Crop", 7);

    // The standing signs (community blocks.json: block 63, the rotation in
    // the metadata nibble's 45-degree band 0/4/8/12 = S/W/N/E). The sign
    // faces its placer, the historical placement rule.
    public static final EngineBlockType SIGN_SOUTH = new EngineBlockType(
            Identifier.parse("minecraft:sign"), "Sign");
    public static final EngineBlockType SIGN_WEST = new EngineBlockType(
            Identifier.parse("minecraft:sign_west"), "Sign", 4);
    public static final EngineBlockType SIGN_NORTH = new EngineBlockType(
            Identifier.parse("minecraft:sign_north"), "Sign", 8);
    public static final EngineBlockType SIGN_EAST = new EngineBlockType(
            Identifier.parse("minecraft:sign_east"), "Sign", 12);

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
                .register(GOLD_ORE)
                .register(REDSTONE_ORE)
                .register(OAK_LOG)
                .register(OAK_LEAVES)
                .register(CRAFTING_TABLE)
                .register(TORCH)
                .register(FURNACE)
                .register(CHEST)
                .register(SAND)
                .register(GLASS)
                .register(GRAVEL)
                .register(OBSIDIAN)
                .register(COBBLESTONE)
                .register(TALL_GRASS)
                .register(DEAD_BUSH)
                .register(DANDELION)
                .register(POPPY)
                .register(SANDSTONE)
                .register(FARMLAND)
                .register(FARMLAND_WET)
                .register(WHEAT_STAGE0)
                .register(WHEAT_STAGE1)
                .register(WHEAT_STAGE2)
                .register(WHEAT_STAGE3)
                .register(WHEAT_STAGE4)
                .register(WHEAT_STAGE5)
                .register(WHEAT_STAGE6)
                .register(WHEAT_STAGE7)
                .register(SIGN_SOUTH)
                .register(SIGN_WEST)
                .register(SIGN_NORTH)
                .register(SIGN_EAST);
    }
}
