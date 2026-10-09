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

    // The oak door (community blocks.json: block 64). The lower half's
    // metadata carries the facing (historical order W/N/E/S = 0..3) with bit
    // 2 (value 4) as the open flag; the upper half's metadata 8 (right
    // hinge) with the open mirror at 9. Each state is its own block type —
    // the per-state fluid model again.
    public static final EngineBlockType OAK_DOOR_LOWER_CLOSED_W = new EngineBlockType(
            Identifier.parse("minecraft:oak_door"), "Oak Door");
    public static final EngineBlockType OAK_DOOR_LOWER_CLOSED_N = new EngineBlockType(
            Identifier.parse("minecraft:oak_door_north"), "Oak Door", 1);
    public static final EngineBlockType OAK_DOOR_LOWER_CLOSED_E = new EngineBlockType(
            Identifier.parse("minecraft:oak_door_east"), "Oak Door", 2);
    public static final EngineBlockType OAK_DOOR_LOWER_CLOSED_S = new EngineBlockType(
            Identifier.parse("minecraft:oak_door_south"), "Oak Door", 3);
    public static final EngineBlockType OAK_DOOR_LOWER_OPEN_W = new EngineBlockType(
            Identifier.parse("minecraft:oak_door_open"), "Oak Door", 4);
    public static final EngineBlockType OAK_DOOR_LOWER_OPEN_N = new EngineBlockType(
            Identifier.parse("minecraft:oak_door_open_north"), "Oak Door", 5);
    public static final EngineBlockType OAK_DOOR_LOWER_OPEN_E = new EngineBlockType(
            Identifier.parse("minecraft:oak_door_open_east"), "Oak Door", 6);
    public static final EngineBlockType OAK_DOOR_LOWER_OPEN_S = new EngineBlockType(
            Identifier.parse("minecraft:oak_door_open_south"), "Oak Door", 7);
    public static final EngineBlockType OAK_DOOR_UPPER = new EngineBlockType(
            Identifier.parse("minecraft:oak_door_upper"), "Oak Door", 8);
    public static final EngineBlockType OAK_DOOR_UPPER_OPEN = new EngineBlockType(
            Identifier.parse("minecraft:oak_door_upper_open"), "Oak Door", 9);

    // The ladders (block 65, the facing in the metadata: N=2, S=3, W=4, E=5)
    // and the fence (block 85 — the engine's flat-solid model stands in for
    // the 1.5-block collision until the collision-shape slice).
    public static final EngineBlockType LADDER_NORTH = new EngineBlockType(
            Identifier.parse("minecraft:ladder"), "Ladder", 2);
    public static final EngineBlockType LADDER_SOUTH = new EngineBlockType(
            Identifier.parse("minecraft:ladder_south"), "Ladder", 3);
    public static final EngineBlockType LADDER_WEST = new EngineBlockType(
            Identifier.parse("minecraft:ladder_west"), "Ladder", 4);
    public static final EngineBlockType LADDER_EAST = new EngineBlockType(
            Identifier.parse("minecraft:ladder_east"), "Ladder", 5);
    public static final EngineBlockType FENCE = new EngineBlockType(
            Identifier.parse("minecraft:oak_fence"), "Oak Fence");

    // The bed (community blocks.json: block 26). The foot's metadata is the
    // foot-to-head facing (S/W/N/E = 0..3, the sign's band); the head's
    // metadata adds bit 3 (value 8). Two block types per facing.
    public static final EngineBlockType BED_FOOT_SOUTH = new EngineBlockType(
            Identifier.parse("minecraft:bed"), "Bed");
    public static final EngineBlockType BED_FOOT_WEST = new EngineBlockType(
            Identifier.parse("minecraft:bed_west"), "Bed", 1);
    public static final EngineBlockType BED_FOOT_NORTH = new EngineBlockType(
            Identifier.parse("minecraft:bed_north"), "Bed", 2);
    public static final EngineBlockType BED_FOOT_EAST = new EngineBlockType(
            Identifier.parse("minecraft:bed_east"), "Bed", 3);
    public static final EngineBlockType BED_HEAD_SOUTH = new EngineBlockType(
            Identifier.parse("minecraft:bed_head"), "Bed", 8);
    public static final EngineBlockType BED_HEAD_WEST = new EngineBlockType(
            Identifier.parse("minecraft:bed_head_west"), "Bed", 9);
    public static final EngineBlockType BED_HEAD_NORTH = new EngineBlockType(
            Identifier.parse("minecraft:bed_head_north"), "Bed", 10);
    public static final EngineBlockType BED_HEAD_EAST = new EngineBlockType(
            Identifier.parse("minecraft:bed_head_east"), "Bed", 11);

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
                .register(SIGN_EAST)
                .register(OAK_DOOR_LOWER_CLOSED_W)
                .register(OAK_DOOR_LOWER_CLOSED_N)
                .register(OAK_DOOR_LOWER_CLOSED_E)
                .register(OAK_DOOR_LOWER_CLOSED_S)
                .register(OAK_DOOR_LOWER_OPEN_W)
                .register(OAK_DOOR_LOWER_OPEN_N)
                .register(OAK_DOOR_LOWER_OPEN_E)
                .register(OAK_DOOR_LOWER_OPEN_S)
                .register(OAK_DOOR_UPPER)
                .register(OAK_DOOR_UPPER_OPEN)
                .register(LADDER_NORTH)
                .register(LADDER_SOUTH)
                .register(LADDER_WEST)
                .register(LADDER_EAST)
                .register(FENCE)
                .register(BED_FOOT_SOUTH)
                .register(BED_FOOT_WEST)
                .register(BED_FOOT_NORTH)
                .register(BED_FOOT_EAST)
                .register(BED_HEAD_SOUTH)
                .register(BED_HEAD_WEST)
                .register(BED_HEAD_NORTH)
                .register(BED_HEAD_EAST);
    }
}
