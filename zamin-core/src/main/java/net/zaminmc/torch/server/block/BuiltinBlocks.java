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
    // The placeable forms of two craftable items that previously existed only
    // as inventory stacks (legacy ids: planks 5, wool 35) — the crafting loop
    // reaches the building table through these.
    public static final EngineBlockType OAK_PLANKS = new EngineBlockType(
            Identifier.parse("minecraft:oak_planks"), "Oak Planks");
    public static final EngineBlockType WOOL = new EngineBlockType(
            Identifier.parse("minecraft:wool"), "Wool");

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

    // The fire block (community blocks.json: legacy 51). Instant-break,
    // no drops, non-solid; the spread/burnout clock lives in the scheduled
    // block-update system (the historical BlockFire.updateTick shape).
    public static final EngineBlockType FIRE = new EngineBlockType(
            Identifier.parse("minecraft:fire"), "Fire");

    // The nether portal (community blocks.json: legacy 90, the metadata
    // nibble carries the axis — 1 the X plane, 2 the Z plane, the reference
    // PortalBlock.getMetadata). No collision, no drops, unbreakable by hand;
    // the frame re-validation kills a cell whose frame broke (the historical
    // PortalBlock.neighborChanged) and the stand clock lives with the player
    // session (the Entity portal tick). The two identifiers name the two
    // axis planes the way the metadata-variant slices do.
    public static final EngineBlockType NETHER_PORTAL = new EngineBlockType(
            Identifier.parse("minecraft:nether_portal"), "Nether Portal", 1);
    public static final EngineBlockType NETHER_PORTAL_Z = new EngineBlockType(
            Identifier.parse("minecraft:nether_portal_z"), "Nether Portal", 2);

    // The world-completion flora (community blocks.json: reed 83, cactus 81)
    // and the lit furnace variant (legacy 62, the historical block swap when
    // the burn starts). The reed is walk-through flora; the cactus is a
    // solid (slightly inset in vanilla — the full-cube stand-in) that breaks
    // when anything solid settles beside it and damages bodies touching it.
    public static final EngineBlockType SUGAR_CANE = new EngineBlockType(
            Identifier.parse("minecraft:sugar_cane"), "Sugar Cane");
    public static final EngineBlockType CACTUS = new EngineBlockType(
            Identifier.parse("minecraft:cactus"), "Cactus");
    public static final EngineBlockType FURNACE_LIT = new EngineBlockType(
            Identifier.parse("minecraft:furnace_lit"), "Furnace");

    // The nether's materials (community blocks.json ids): netherrack 87, soul
    // sand 88, glowstone 89, quartz ore 153, the two mushrooms 39/40. The
    // nether generator (the 8b slice) fills the dimension with them; glowstone
    // emits 15 (the light table row), the mushrooms walk through like flora.
    public static final EngineBlockType NETHERRACK = new EngineBlockType(
            Identifier.parse("minecraft:netherrack"), "Netherrack");
    public static final EngineBlockType SOUL_SAND = new EngineBlockType(
            Identifier.parse("minecraft:soul_sand"), "Soul Sand");
    public static final EngineBlockType GLOWSTONE = new EngineBlockType(
            Identifier.parse("minecraft:glowstone"), "Glowstone");
    public static final EngineBlockType QUARTZ_ORE = new EngineBlockType(
            Identifier.parse("minecraft:quartz_ore"), "Quartz Ore");
    public static final EngineBlockType BROWN_MUSHROOM = new EngineBlockType(
            Identifier.parse("minecraft:brown_mushroom"), "Brown Mushroom");
    public static final EngineBlockType RED_MUSHROOM = new EngineBlockType(
            Identifier.parse("minecraft:red_mushroom"), "Red Mushroom");

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

    // The wall signs (community blocks.json: block 68, the metadata 2-5
    // naming the direction the sign faces — away from the wall it hangs
    // on). A side-face placement attaches one; the text rides the same
    // sign store (the manager keys by position, block-agnostic).
    public static final EngineBlockType WALL_SIGN_NORTH = new EngineBlockType(
            Identifier.parse("minecraft:wall_sign"), "Wall Sign", 2);
    public static final EngineBlockType WALL_SIGN_SOUTH = new EngineBlockType(
            Identifier.parse("minecraft:wall_sign_south"), "Wall Sign", 3);
    public static final EngineBlockType WALL_SIGN_WEST = new EngineBlockType(
            Identifier.parse("minecraft:wall_sign_west"), "Wall Sign", 4);
    public static final EngineBlockType WALL_SIGN_EAST = new EngineBlockType(
            Identifier.parse("minecraft:wall_sign_east"), "Wall Sign", 5);

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

    // The enchanting corner (vanilla 1.8.8): the bookshelf (47) whose count
    // feeds the table's power scan, and the enchanting table (116, the
    // 1.0x0.75x1.0 stone block whose right-click opens the menu).
    public static final EngineBlockType BOOKSHELF = new EngineBlockType(
            Identifier.parse("minecraft:bookshelf"), "Bookshelf");
    public static final EngineBlockType ENCHANTING_TABLE = new EngineBlockType(
            Identifier.parse("minecraft:enchanting_table"), "Enchantment Table");

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

    // The collision-shape slice (community blocks.json ids): slabs ride the
    // historical ids 44 (stone family) and 126 (wooden) with the half in the
    // metadata nibble (bit 0x8 = top; 44's material 0=stone 1=sandstone
    // 3=cobble), stairs ride 53 (oak) and 67 (cobble) with the ascending
    // direction in the metadata (Bukkit's Stairs band E=0 W=1 S=2 N=3).
    // Each half/facing is its own per-state block type (the fluid model);
    // the shapes live in WorldSolidity (bottom half, top half, fence 1.5).
    public static final EngineBlockType OAK_SLAB = new EngineBlockType(
            Identifier.parse("minecraft:oak_slab"), "Oak Slab");
    public static final EngineBlockType OAK_SLAB_TOP = new EngineBlockType(
            Identifier.parse("minecraft:oak_slab_top"), "Oak Slab", 8);
    public static final EngineBlockType STONE_SLAB = new EngineBlockType(
            Identifier.parse("minecraft:stone_slab"), "Stone Slab");
    public static final EngineBlockType STONE_SLAB_TOP = new EngineBlockType(
            Identifier.parse("minecraft:stone_slab_top"), "Stone Slab", 8);
    public static final EngineBlockType COBBLESTONE_SLAB = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_slab"), "Cobblestone Slab", 3);
    public static final EngineBlockType COBBLESTONE_SLAB_TOP = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_slab_top"), "Cobblestone Slab", 11);
    public static final EngineBlockType SANDSTONE_SLAB = new EngineBlockType(
            Identifier.parse("minecraft:sandstone_slab"), "Sandstone Slab", 1);
    public static final EngineBlockType SANDSTONE_SLAB_TOP = new EngineBlockType(
            Identifier.parse("minecraft:sandstone_slab_top"), "Sandstone Slab", 9);
    public static final EngineBlockType OAK_STAIRS_EAST = new EngineBlockType(
            Identifier.parse("minecraft:oak_stairs_east"), "Oak Stairs");
    public static final EngineBlockType OAK_STAIRS_WEST = new EngineBlockType(
            Identifier.parse("minecraft:oak_stairs_west"), "Oak Stairs", 1);
    public static final EngineBlockType OAK_STAIRS_SOUTH = new EngineBlockType(
            Identifier.parse("minecraft:oak_stairs_south"), "Oak Stairs", 2);
    public static final EngineBlockType OAK_STAIRS_NORTH = new EngineBlockType(
            Identifier.parse("minecraft:oak_stairs_north"), "Oak Stairs", 3);
    public static final EngineBlockType COBBLESTONE_STAIRS_EAST = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_east"), "Cobblestone Stairs");
    public static final EngineBlockType COBBLESTONE_STAIRS_WEST = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_west"), "Cobblestone Stairs", 1);
    public static final EngineBlockType COBBLESTONE_STAIRS_SOUTH = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_south"), "Cobblestone Stairs", 2);
    public static final EngineBlockType COBBLESTONE_STAIRS_NORTH = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_north"), "Cobblestone Stairs", 3);

    // The rail (community blocks.json: block 66, the flat orientation in
    // the metadata nibble: 0 = north-south, 1 = east-west). Walk-through
    // (the vanilla rail never blocks a body); the minecart reads the axis.
    public static final EngineBlockType RAIL = new EngineBlockType(
            Identifier.parse("minecraft:rail"), "Rail");
    public static final EngineBlockType RAIL_EW = new EngineBlockType(
            Identifier.parse("minecraft:rail_ew"), "Rail", 1);

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
                .register(OAK_PLANKS)
                .register(WOOL)
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
                .register(FIRE)
                .register(NETHERRACK)
                .register(SOUL_SAND)
                .register(GLOWSTONE)
                .register(QUARTZ_ORE)
                .register(BROWN_MUSHROOM)
                .register(RED_MUSHROOM)
                .register(NETHER_PORTAL)
                .register(NETHER_PORTAL_Z)
                .register(SUGAR_CANE)
                .register(CACTUS)
                .register(FURNACE_LIT)
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
                .register(WALL_SIGN_NORTH)
                .register(WALL_SIGN_SOUTH)
                .register(WALL_SIGN_WEST)
                .register(WALL_SIGN_EAST)
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
                .register(BOOKSHELF)
                .register(ENCHANTING_TABLE)
                .register(BED_FOOT_SOUTH)
                .register(BED_FOOT_WEST)
                .register(BED_FOOT_NORTH)
                .register(BED_FOOT_EAST)
                .register(BED_HEAD_SOUTH)
                .register(BED_HEAD_WEST)
                .register(BED_HEAD_NORTH)
                .register(BED_HEAD_EAST)
                .register(OAK_SLAB)
                .register(OAK_SLAB_TOP)
                .register(STONE_SLAB)
                .register(STONE_SLAB_TOP)
                .register(COBBLESTONE_SLAB)
                .register(COBBLESTONE_SLAB_TOP)
                .register(SANDSTONE_SLAB)
                .register(SANDSTONE_SLAB_TOP)
                .register(OAK_STAIRS_EAST)
                .register(OAK_STAIRS_WEST)
                .register(OAK_STAIRS_SOUTH)
                .register(OAK_STAIRS_NORTH)
                .register(COBBLESTONE_STAIRS_EAST)
                .register(COBBLESTONE_STAIRS_WEST)
                .register(COBBLESTONE_STAIRS_SOUTH)
                .register(COBBLESTONE_STAIRS_NORTH)
                .register(RAIL)
                .register(RAIL_EW);
    }
}
