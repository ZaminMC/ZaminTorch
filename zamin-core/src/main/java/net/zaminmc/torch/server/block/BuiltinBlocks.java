package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

import java.util.List;

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
            Identifier.parse("minecraft:chest"), "Chest");    // The trapped chest (Slice 49b — ChestBlock(1): setStrength(2.5F)
    // WOOD_SOUNDS over Material.WOOD — the same container, the
    // viewer-count power source).
    public static final EngineBlockType TRAPPED_CHEST = new EngineBlockType(
            Identifier.parse("minecraft:trapped_chest"), "Trapped Chest");
    // The red sandstone family (Slice 52 — 1.8's own four, 1:1): the three
    // TYPE states over 179 (RedSandstoneBlock: DEFAULT 0, CHISELED 1,
    // SMOOTH 2 — the TYPE id whole in the nibble), the four facing states
    // over 180 (the Bukkit band E=0 W=1 S=2 N=3 like the masonry stairs),
    // the slab pair over 182 (the HALF bit 8 — the RED_SANDSTONE variant
    // id 0 rides the nibble whole like the reference's
    // getDropItemMetadata) and the double 181 (the SEAMLESS bit never
    // settable by gameplay — the plain form registers alone).
    public static final EngineBlockType RED_SAND = RedSandstoneBlocks.redSand();
    public static final EngineBlockType RED_SANDSTONE = RedSandstoneBlocks.state(0);
    public static final EngineBlockType CHISELED_RED_SANDSTONE = RedSandstoneBlocks.state(1);
    public static final EngineBlockType SMOOTH_RED_SANDSTONE = RedSandstoneBlocks.state(2);
    public static final EngineBlockType RED_SANDSTONE_STAIRS_EAST = RedSandstoneBlocks.stairsState(0);
    public static final EngineBlockType RED_SANDSTONE_STAIRS_WEST = RedSandstoneBlocks.stairsState(1);
    public static final EngineBlockType RED_SANDSTONE_STAIRS_SOUTH = RedSandstoneBlocks.stairsState(2);
    public static final EngineBlockType RED_SANDSTONE_STAIRS_NORTH = RedSandstoneBlocks.stairsState(3);
    public static final EngineBlockType DOUBLE_STONE_SLAB2 = new EngineBlockType(
            Identifier.parse("minecraft:double_stone_slab2"), "Double Red Sandstone Slab");
    public static final EngineBlockType STONE_SLAB2 = new EngineBlockType(
            Identifier.parse("minecraft:stone_slab2"), "Red Sandstone Slab");
    public static final EngineBlockType STONE_SLAB2_TOP = new EngineBlockType(
            Identifier.parse("minecraft:stone_slab2_top"), "Red Sandstone Slab", 8);
    // The anvil family (Slice 53 — AnvilBlock 1:1): the twelve states over
    // 145 (the FACING horizontal id S=0 W=1 N=2 E=3 in the low pair, the
    // DAMAGE << 2 in the high pair — the reference's own
    // getStateFromMetadata band), the falling family (the two-tick
    // schedule joins the sand band) with the landing walk (the hurt + the
    // progression roll).
    public static final EngineBlockType ANVIL = AnvilBlocks.state(0, 0);
    public static final EngineBlockType ANVIL_WEST = AnvilBlocks.state(1, 0);
    public static final EngineBlockType ANVIL_NORTH = AnvilBlocks.state(2, 0);
    public static final EngineBlockType ANVIL_EAST = AnvilBlocks.state(3, 0);
    public static final EngineBlockType ANVIL_CHIPPED = AnvilBlocks.state(0, 1);
    public static final EngineBlockType ANVIL_CHIPPED_WEST = AnvilBlocks.state(1, 1);
    public static final EngineBlockType ANVIL_CHIPPED_NORTH = AnvilBlocks.state(2, 1);
    public static final EngineBlockType ANVIL_CHIPPED_EAST = AnvilBlocks.state(3, 1);
    public static final EngineBlockType ANVIL_DAMAGED = AnvilBlocks.state(0, 2);
    public static final EngineBlockType ANVIL_DAMAGED_WEST = AnvilBlocks.state(1, 2);
    public static final EngineBlockType ANVIL_DAMAGED_NORTH = AnvilBlocks.state(2, 2);
    public static final EngineBlockType ANVIL_DAMAGED_EAST = AnvilBlocks.state(3, 2);
    // The end portal (Slice 54 — EndPortalBlock 1:1): the 1/16 film, the
    // unbreakable portal block (setStrength(-1.0F)) — the single state
    // (the reference has no metadata band; the contact arm rides the End
    // dimension's own slice).
    public static final EngineBlockType END_PORTAL = EndPortalBlocks.endPortal();
    // The dragon egg (Slice 55 — DragonEggBlock 1:1): the 1/16-inset
    // obelisk (setStrength(3.0F) blast 15, the setLight(0.125F) emission
    // 1), no item form, the own 5-tick fall schedule, and the click
    // teleport.
    public static final EngineBlockType DRAGON_EGG = DragonEggBlocks.dragonEgg();
    // The barrier (Slice 56 — BarrierBlock 1:1): the invisible indestructible
    // solid (Material.BARRIER, setUnbreakable blast 6000001, the -1 render —
    // the client draws nothing; the translucent block passes light whole).
    // The item exists (the /give route) but never joins a creative tab.
    public static final EngineBlockType BARRIER = new EngineBlockType(
            Identifier.parse("minecraft:barrier"), "Barrier");
    // The double wooden slab family (Slice 58 — DoubleWoodenSlabBlock 1:1):
    // the six planks variants over 125 (the variant id whole in the nibble;
    // the oak carries the wire's own double_wooden_slab name). Breaking a
    // double yields TWO single slabs (getBaseDropCount's isDouble ? 2 : 1).
    public static final EngineBlockType DOUBLE_WOODEN_SLAB = WoodShapeBlocks.doubleSlabState(0);
    public static final EngineBlockType DOUBLE_SPRUCE_SLAB = WoodShapeBlocks.doubleSlabState(1);
    public static final EngineBlockType DOUBLE_BIRCH_SLAB = WoodShapeBlocks.doubleSlabState(2);
    public static final EngineBlockType DOUBLE_JUNGLE_SLAB = WoodShapeBlocks.doubleSlabState(3);
    public static final EngineBlockType DOUBLE_ACACIA_SLAB = WoodShapeBlocks.doubleSlabState(4);
    public static final EngineBlockType DOUBLE_DARK_OAK_SLAB = WoodShapeBlocks.doubleSlabState(5);

    // The TNT block (legacy 46, Slice 9f): the ignition system's family.
    public static final EngineBlockType TNT = new EngineBlockType(
            Identifier.parse("minecraft:tnt"), "Tnt");
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
    // The skull family (Slice 60 — SkullBlock 1:1, block 144): the six
    // facing states, the FACING nibble the reference's Direction ids (0
    // down .. 5 east). The floor mount (FACING UP) carries the base
    // minecraft:skull name; the TYPE (skeleton 0 .. dragon 5) and the
    // floor rotation ride the tile, not the block.
    public static final EngineBlockType SKULL = new EngineBlockType(
            Identifier.parse("minecraft:skull"), "Skull", 1);
    public static final EngineBlockType SKULL_DOWN = new EngineBlockType(
            Identifier.parse("minecraft:skull_down"), "Skull", 0);
    public static final EngineBlockType SKULL_NORTH = new EngineBlockType(
            Identifier.parse("minecraft:skull_north"), "Skull", 2);
    public static final EngineBlockType SKULL_SOUTH = new EngineBlockType(
            Identifier.parse("minecraft:skull_south"), "Skull", 3);
    public static final EngineBlockType SKULL_WEST = new EngineBlockType(
            Identifier.parse("minecraft:skull_west"), "Skull", 4);
    public static final EngineBlockType SKULL_EAST = new EngineBlockType(
            Identifier.parse("minecraft:skull_east"), "Skull", 5);
    // The command-block family (Slice 72 — CommandBlock 1:1, block 137,
    // Block.java line 1299's setUnbreakable + 6000000 blast): the two
    // TRIGGERED states over the shared id — the plain block (meta 0) and
    // the triggered one (meta 1, the reference's TRIGGERED bit — the
    // rising redstone edge's own mark, cleared on the falling edge).
    public static final EngineBlockType COMMAND_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:command_block"), "Command Block", 0);
    public static final EngineBlockType COMMAND_BLOCK_TRIGGERED = new EngineBlockType(
            Identifier.parse("minecraft:command_block_triggered"), "Command Block", 1);
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
    // The open upper (the pre-family engine's own state): the reference's
    // upper metadata is 8 | hinge 1 | powered 2, the engine's open flag
    // bit 4 — a manually opened door is open and UNPOWERED (metadata 12,
    // DoorBlock.use never writes POWERED). The historical byte claimed a
    // right hinge (9); worlds saved by the pre-family engine load
    // unchanged and render strictly closer to vanilla (the old doors were
    // all left-hinged, and the redstone edge memory reads unpowered —
    // exactly the manual-open state those doors were in).
    public static final EngineBlockType OAK_DOOR_UPPER_OPEN = new EngineBlockType(
            Identifier.parse("minecraft:oak_door_upper_open"), "Oak Door", 12);

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
    // The stairs' upside-down forms (StairsBlock's HALF bit 4 — the
    // reference's (HALF == TOP ? 4 : 0) | (5 - FACING.getId()) fold).
    public static final EngineBlockType OAK_STAIRS_EAST_TOP = new EngineBlockType(
            Identifier.parse("minecraft:oak_stairs_east_top"), "Oak Stairs", 4);
    public static final EngineBlockType OAK_STAIRS_WEST_TOP = new EngineBlockType(
            Identifier.parse("minecraft:oak_stairs_west_top"), "Oak Stairs", 5);
    public static final EngineBlockType OAK_STAIRS_SOUTH_TOP = new EngineBlockType(
            Identifier.parse("minecraft:oak_stairs_south_top"), "Oak Stairs", 6);
    public static final EngineBlockType OAK_STAIRS_NORTH_TOP = new EngineBlockType(
            Identifier.parse("minecraft:oak_stairs_north_top"), "Oak Stairs", 7);
    public static final EngineBlockType COBBLESTONE_STAIRS_EAST = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_east"), "Cobblestone Stairs");
    public static final EngineBlockType COBBLESTONE_STAIRS_WEST = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_west"), "Cobblestone Stairs", 1);
    public static final EngineBlockType COBBLESTONE_STAIRS_SOUTH = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_south"), "Cobblestone Stairs", 2);
    public static final EngineBlockType COBBLESTONE_STAIRS_NORTH = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_north"), "Cobblestone Stairs", 3);
    public static final EngineBlockType COBBLESTONE_STAIRS_EAST_TOP = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_east_top"), "Cobblestone Stairs", 4);
    public static final EngineBlockType COBBLESTONE_STAIRS_WEST_TOP = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_west_top"), "Cobblestone Stairs", 5);
    public static final EngineBlockType COBBLESTONE_STAIRS_SOUTH_TOP = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_south_top"), "Cobblestone Stairs", 6);
    public static final EngineBlockType COBBLESTONE_STAIRS_NORTH_TOP = new EngineBlockType(
            Identifier.parse("minecraft:cobblestone_stairs_north_top"), "Cobblestone Stairs", 7);

    // The rail (community blocks.json: block 66, the flat orientation in
    // the metadata nibble: 0 = north-south, 1 = east-west). Walk-through
    // (the vanilla rail never blocks a body); the minecart reads the axis.
    public static final EngineBlockType RAIL = new EngineBlockType(
            Identifier.parse("minecraft:rail"), "Rail");
    public static final EngineBlockType RAIL_EW = new EngineBlockType(
            Identifier.parse("minecraft:rail_ew"), "Rail", 1);
    // The cobweb (Slice 12a — community blocks.json legacy 30): the
    // mineshaft corridor's 1-in-23 resident, walk-through (the vanilla
    // web slows, never blocks; the slow effect ledger with its own slice).
    public static final EngineBlockType COBWEB = new EngineBlockType(
            Identifier.parse("minecraft:cobweb"), "Cobweb");

    // The village slice (community blocks.json ids): the glass pane (102)
    // and the iron bars (101) — thin solids the engine models full-cube
    // (the flat-solid stand-in, like the fence before the shape slice);
    // the double stone slab (43, the blacksmith's table block); the
    // smooth sandstone skin (24 metadata 2 — the desert swap for planks);
    // the sandstone stairs (128, the ascending band E/W/S/N = 0..3 like
    // the oak/cobble pair); and the two extra farm crops — carrots (141)
    // and potatoes (142) with the age 0..7 in the metadata nibble exactly
    // like the wheat (each stage its own per-state type).
    public static final EngineBlockType GLASS_PANE = new EngineBlockType(
            Identifier.parse("minecraft:glass_pane"), "Glass Pane");
    public static final EngineBlockType IRON_BARS = new EngineBlockType(
            Identifier.parse("minecraft:iron_bars"), "Iron Bars");
    public static final EngineBlockType DOUBLE_STONE_SLAB = new EngineBlockType(
            Identifier.parse("minecraft:double_stone_slab"), "Double Stone Slab");
    public static final EngineBlockType SMOOTH_SANDSTONE = new EngineBlockType(
            Identifier.parse("minecraft:smooth_sandstone"), "Sandstone", 2);
    public static final EngineBlockType SANDSTONE_STAIRS_EAST = new EngineBlockType(
            Identifier.parse("minecraft:sandstone_stairs_east"), "Sandstone Stairs");
    public static final EngineBlockType SANDSTONE_STAIRS_WEST = new EngineBlockType(
            Identifier.parse("minecraft:sandstone_stairs_west"), "Sandstone Stairs", 1);
    public static final EngineBlockType SANDSTONE_STAIRS_SOUTH = new EngineBlockType(
            Identifier.parse("minecraft:sandstone_stairs_south"), "Sandstone Stairs", 2);
    public static final EngineBlockType SANDSTONE_STAIRS_NORTH = new EngineBlockType(
            Identifier.parse("minecraft:sandstone_stairs_north"), "Sandstone Stairs", 3);
    public static final EngineBlockType SANDSTONE_STAIRS_EAST_TOP = new EngineBlockType(
            Identifier.parse("minecraft:sandstone_stairs_east_top"), "Sandstone Stairs", 4);
    public static final EngineBlockType SANDSTONE_STAIRS_WEST_TOP = new EngineBlockType(
            Identifier.parse("minecraft:sandstone_stairs_west_top"), "Sandstone Stairs", 5);
    public static final EngineBlockType SANDSTONE_STAIRS_SOUTH_TOP = new EngineBlockType(
            Identifier.parse("minecraft:sandstone_stairs_south_top"), "Sandstone Stairs", 6);
    public static final EngineBlockType SANDSTONE_STAIRS_NORTH_TOP = new EngineBlockType(
            Identifier.parse("minecraft:sandstone_stairs_north_top"), "Sandstone Stairs", 7);
    public static final EngineBlockType CARROTS_STAGE0 = new EngineBlockType(
            Identifier.parse("minecraft:carrots_stage0"), "Carrots");
    public static final EngineBlockType CARROTS_STAGE1 = new EngineBlockType(
            Identifier.parse("minecraft:carrots_stage1"), "Carrots", 1);
    public static final EngineBlockType CARROTS_STAGE2 = new EngineBlockType(
            Identifier.parse("minecraft:carrots_stage2"), "Carrots", 2);
    public static final EngineBlockType CARROTS_STAGE3 = new EngineBlockType(
            Identifier.parse("minecraft:carrots_stage3"), "Carrots", 3);
    public static final EngineBlockType CARROTS_STAGE4 = new EngineBlockType(
            Identifier.parse("minecraft:carrots_stage4"), "Carrots", 4);
    public static final EngineBlockType CARROTS_STAGE5 = new EngineBlockType(
            Identifier.parse("minecraft:carrots_stage5"), "Carrots", 5);
    public static final EngineBlockType CARROTS_STAGE6 = new EngineBlockType(
            Identifier.parse("minecraft:carrots_stage6"), "Carrots", 6);
    public static final EngineBlockType CARROTS_STAGE7 = new EngineBlockType(
            Identifier.parse("minecraft:carrots_stage7"), "Carrots", 7);
    public static final EngineBlockType POTATOES_STAGE0 = new EngineBlockType(
            Identifier.parse("minecraft:potatoes_stage0"), "Potatoes");
    public static final EngineBlockType POTATOES_STAGE1 = new EngineBlockType(
            Identifier.parse("minecraft:potatoes_stage1"), "Potatoes", 1);
    public static final EngineBlockType POTATOES_STAGE2 = new EngineBlockType(
            Identifier.parse("minecraft:potatoes_stage2"), "Potatoes", 2);
    public static final EngineBlockType POTATOES_STAGE3 = new EngineBlockType(
            Identifier.parse("minecraft:potatoes_stage3"), "Potatoes", 3);
    public static final EngineBlockType POTATOES_STAGE4 = new EngineBlockType(
            Identifier.parse("minecraft:potatoes_stage4"), "Potatoes", 4);
    public static final EngineBlockType POTATOES_STAGE5 = new EngineBlockType(
            Identifier.parse("minecraft:potatoes_stage5"), "Potatoes", 5);
    public static final EngineBlockType POTATOES_STAGE6 = new EngineBlockType(
            Identifier.parse("minecraft:potatoes_stage6"), "Potatoes", 6);
    public static final EngineBlockType POTATOES_STAGE7 = new EngineBlockType(
            Identifier.parse("minecraft:potatoes_stage7"), "Potatoes", 7);

    // The stronghold set (Slice 12c — the reference's StrongholdPieces):
    // the stone-brick family over legacy 98 (default 0, mossy 1, cracked 2
    // — the StoneBrickPicker's own three rolls), the silverfish stone-brick
    // monster egg (legacy 97 metadata 4), the smooth stone-brick slab
    // (legacy 44 metadata 5 — StoneSlabBlock.Variant.SMOOTHBRICK), and the
    // mob spawner (legacy 52) whose block entity ledgers with its own slice.
    public static final EngineBlockType STONE_BRICKS = new EngineBlockType(
            Identifier.parse("minecraft:stone_bricks"), "Stone Bricks");
    public static final EngineBlockType MOSSY_STONE_BRICKS = new EngineBlockType(
            Identifier.parse("minecraft:mossy_stone_bricks"), "Mossy Stone Bricks", 1);
    public static final EngineBlockType CRACKED_STONE_BRICKS = new EngineBlockType(
            Identifier.parse("minecraft:cracked_stone_bricks"), "Cracked Stone Bricks", 2);
    public static final EngineBlockType CHISELED_STONE_BRICKS = new EngineBlockType(
            Identifier.parse("minecraft:chiseled_stone_bricks"), "Chiseled Stone Bricks", 3);
    // The infested family (Slice 18c — the reference's InfestedBlock: the
    // single MONSTER_EGG block 97 with the SIX host variants): the
    // stonebrick egg is the stronghold's own pick (StoneBrickPicker's
    // < 0.55 band — the wire metadata 2, the reference's Variant.
    // STONEBRICK); the stone and cobblestone eggs are the InfestStone
    // goal's own writes; the mossy/cracked/chiseled eggs complete the
    // silk-drop family (the host ItemStack's damage 1/2/3).
    public static final EngineBlockType INFESTED_STONE_BRICKS = new EngineBlockType(
            Identifier.parse("minecraft:infested_stone_bricks"), "Infested Stone Bricks", 2);
    public static final EngineBlockType INFESTED_STONE = new EngineBlockType(
            Identifier.parse("minecraft:infested_stone"), "Infested Stone", 0);
    public static final EngineBlockType INFESTED_COBBLESTONE = new EngineBlockType(
            Identifier.parse("minecraft:infested_cobblestone"), "Infested Cobblestone", 1);
    public static final EngineBlockType INFESTED_MOSSY_STONE_BRICKS = new EngineBlockType(
            Identifier.parse("minecraft:infested_mossy_stone_bricks"),
            "Infested Mossy Stone Bricks", 3);
    public static final EngineBlockType INFESTED_CRACKED_STONE_BRICKS = new EngineBlockType(
            Identifier.parse("minecraft:infested_cracked_stone_bricks"),
            "Infested Cracked Stone Bricks", 4);
    public static final EngineBlockType INFESTED_CHISELED_STONE_BRICKS = new EngineBlockType(
            Identifier.parse("minecraft:infested_chiseled_stone_bricks"),
            "Infested Chiseled Stone Bricks", 5);
    public static final EngineBlockType STONE_BRICK_SLAB = new EngineBlockType(
            Identifier.parse("minecraft:stone_brick_slab"), "Stone Brick Slab", 5);
    public static final EngineBlockType MOB_SPAWNER = new EngineBlockType(
            Identifier.parse("minecraft:mob_spawner"), "Mob Spawner");
    // The note block (legacy 25, the reference's `noteblock` registration,
    // strength 0.8, Material.WOOD): the music block with its own tile.
    public static final EngineBlockType NOTE_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:noteblock"), "Note Block");
    // The jukebox (legacy 84, the reference's Block.java line-1181
    // registration — strength 2.0, blast resistance 10.0, STONE sounds):
    // the plain block and its HAS_RECORD variant share the id; the
    // metadata nibble's bit 0 is JukeboxBlock.HAS_RECORD
    // (getMetadataFromState: has_record ? 1 : 0).
    public static final EngineBlockType JUKEBOX = new EngineBlockType(
            Identifier.parse("minecraft:jukebox"), "Jukebox");
    public static final EngineBlockType JUKEBOX_RECORD = new EngineBlockType(
            Identifier.parse("minecraft:jukebox_record"), "Jukebox", 1);
    // The iron door (legacy 71, the reference's Prison): the lower's four
    // facings in the engine's W/N/E/S band (the oak door's own shape) plus
    // the plain upper half.
    public static final EngineBlockType IRON_DOOR_LOWER_W = new EngineBlockType(
            Identifier.parse("minecraft:iron_door_west"), "Iron Door");
    public static final EngineBlockType IRON_DOOR_LOWER_N = new EngineBlockType(
            Identifier.parse("minecraft:iron_door_north"), "Iron Door", 1);
    public static final EngineBlockType IRON_DOOR_LOWER_E = new EngineBlockType(
            Identifier.parse("minecraft:iron_door_east"), "Iron Door", 2);
    public static final EngineBlockType IRON_DOOR_LOWER_S = new EngineBlockType(
            Identifier.parse("minecraft:iron_door_south"), "Iron Door", 3);
    public static final EngineBlockType IRON_DOOR_UPPER = new EngineBlockType(
            Identifier.parse("minecraft:iron_door_upper"), "Iron Door", 8);
    // The end portal frame (legacy 120): the reference's metadata = the
    // horizontal facing id (S=0 W=1 N=2 E=3) | the eye bit 4 — the eight
    // flattened states over the facing band, the eye pair per facing.
    public static final EngineBlockType END_PORTAL_FRAME_S = new EngineBlockType(
            Identifier.parse("minecraft:end_portal_frame_south"), "End Portal Frame");
    public static final EngineBlockType END_PORTAL_FRAME_W = new EngineBlockType(
            Identifier.parse("minecraft:end_portal_frame_west"), "End Portal Frame", 1);
    public static final EngineBlockType END_PORTAL_FRAME_N = new EngineBlockType(
            Identifier.parse("minecraft:end_portal_frame_north"), "End Portal Frame", 2);
    public static final EngineBlockType END_PORTAL_FRAME_E = new EngineBlockType(
            Identifier.parse("minecraft:end_portal_frame_east"), "End Portal Frame", 3);
    public static final EngineBlockType END_PORTAL_FRAME_EYE_S = new EngineBlockType(
            Identifier.parse("minecraft:end_portal_frame_eye_south"), "End Portal Frame", 4);
    public static final EngineBlockType END_PORTAL_FRAME_EYE_W = new EngineBlockType(
            Identifier.parse("minecraft:end_portal_frame_eye_west"), "End Portal Frame", 5);
    public static final EngineBlockType END_PORTAL_FRAME_EYE_N = new EngineBlockType(
            Identifier.parse("minecraft:end_portal_frame_eye_north"), "End Portal Frame", 6);
    public static final EngineBlockType END_PORTAL_FRAME_EYE_E = new EngineBlockType(
            Identifier.parse("minecraft:end_portal_frame_eye_east"), "End Portal Frame", 7);

    // The fortress set (Slice 12d — the reference's FortressPieces): the
    // nether-brick family over legacy 112, the nether-brick fence 113, the
    // nether-brick stairs 114 (the ascending band E/W/S/N = 0..3), and the
    // nether wart's four age states over 115 (the farm plants age 0).
    public static final EngineBlockType NETHER_BRICKS = new EngineBlockType(
            Identifier.parse("minecraft:nether_bricks"), "Nether Bricks");
    public static final EngineBlockType NETHER_BRICK_FENCE = new EngineBlockType(
            Identifier.parse("minecraft:nether_brick_fence"), "Nether Brick Fence");
    public static final EngineBlockType NETHER_BRICK_STAIRS_EAST = new EngineBlockType(
            Identifier.parse("minecraft:nether_brick_stairs_east"), "Nether Brick Stairs");
    public static final EngineBlockType NETHER_BRICK_STAIRS_WEST = new EngineBlockType(
            Identifier.parse("minecraft:nether_brick_stairs_west"), "Nether Brick Stairs", 1);
    public static final EngineBlockType NETHER_BRICK_STAIRS_SOUTH = new EngineBlockType(
            Identifier.parse("minecraft:nether_brick_stairs_south"), "Nether Brick Stairs", 2);
    public static final EngineBlockType NETHER_BRICK_STAIRS_NORTH = new EngineBlockType(
            Identifier.parse("minecraft:nether_brick_stairs_north"), "Nether Brick Stairs", 3);
    public static final EngineBlockType NETHER_BRICK_STAIRS_EAST_TOP = new EngineBlockType(
            Identifier.parse("minecraft:nether_brick_stairs_east_top"), "Nether Brick Stairs", 4);
    public static final EngineBlockType NETHER_BRICK_STAIRS_WEST_TOP = new EngineBlockType(
            Identifier.parse("minecraft:nether_brick_stairs_west_top"), "Nether Brick Stairs", 5);
    public static final EngineBlockType NETHER_BRICK_STAIRS_SOUTH_TOP = new EngineBlockType(
            Identifier.parse("minecraft:nether_brick_stairs_south_top"), "Nether Brick Stairs", 6);
    public static final EngineBlockType NETHER_BRICK_STAIRS_NORTH_TOP = new EngineBlockType(
            Identifier.parse("minecraft:nether_brick_stairs_north_top"), "Nether Brick Stairs", 7);
    public static final EngineBlockType NETHER_WART_STAGE0 = new EngineBlockType(
            Identifier.parse("minecraft:nether_wart_stage0"), "Nether Wart");
    public static final EngineBlockType NETHER_WART_STAGE1 = new EngineBlockType(
            Identifier.parse("minecraft:nether_wart_stage1"), "Nether Wart", 1);
    public static final EngineBlockType NETHER_WART_STAGE2 = new EngineBlockType(
            Identifier.parse("minecraft:nether_wart_stage2"), "Nether Wart", 2);
    public static final EngineBlockType NETHER_WART_STAGE3 = new EngineBlockType(
            Identifier.parse("minecraft:nether_wart_stage3"), "Nether Wart", 3);

    // The monument set (Slice 12e — the reference's OceanMonumentPieces):
    // the prismarine trio over legacy 168 (rough 0, bricks 1, dark 2), the
    // sea lantern 169, the sponge pair over 19 (dry 0, wet 1 — the top
    // rooms plant wet), and the gold block 41 (the core room's treasure
    // block).
    public static final EngineBlockType PRISMARINE_ROUGH = new EngineBlockType(
            Identifier.parse("minecraft:prismarine_rough"), "Prismarine");
    public static final EngineBlockType PRISMARINE_BRICKS = new EngineBlockType(
            Identifier.parse("minecraft:prismarine_bricks"), "Prismarine Bricks", 1);
    public static final EngineBlockType PRISMARINE_DARK = new EngineBlockType(
            Identifier.parse("minecraft:prismarine_dark"), "Dark Prismarine", 2);
    public static final EngineBlockType SEA_LANTERN = new EngineBlockType(
            Identifier.parse("minecraft:sea_lantern"), "Sea Lantern");
    public static final EngineBlockType SPONGE = new EngineBlockType(
            Identifier.parse("minecraft:sponge"), "Sponge");
    public static final EngineBlockType WET_SPONGE = new EngineBlockType(
            Identifier.parse("minecraft:wet_sponge"), "Wet Sponge", 1);
    public static final EngineBlockType GOLD_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:gold_block"), "Block of Gold");
    // The beacon pyramid's remaining minerals (Slice 29b — updateLevels
    // line 145's four: diamond block legacy 57, emerald block 133).
    public static final EngineBlockType DIAMOND_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:diamond_block"), "Block of Diamond");
    public static final EngineBlockType EMERALD_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:emerald_block"), "Block of Emerald");

    // The mineral vault family (Slice 32 — the reference's own Block.java
    // registrations): the two missing ores (lapis 21, emerald 129 —
    // OreBlock rows 1025/1267) and the storage/compressed blocks
    // (MineralRecipes' nine-of-a-kind set + the 2x2 masonries). The
    // behavior rows live in the table; the drop walks (lapis's 4-8 base,
    // redstone's own additive fortune) ride the DropService's family arms.
    public static final EngineBlockType LAPIS_ORE = new EngineBlockType(
            Identifier.parse("minecraft:lapis_ore"), "Lapis Lazuli Ore");
    public static final EngineBlockType EMERALD_ORE = new EngineBlockType(
            Identifier.parse("minecraft:emerald_ore"), "Emerald Ore");
    public static final EngineBlockType LAPIS_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:lapis_block"), "Lapis Lazuli Block");
    public static final EngineBlockType REDSTONE_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:redstone_block"), "Block of Redstone");
    public static final EngineBlockType COAL_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:coal_block"), "Block of Coal");
    public static final EngineBlockType QUARTZ_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:quartz_block"), "Block of Quartz");
    public static final EngineBlockType BRICK_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:brick_block"), "Bricks");
    public static final EngineBlockType CLAY = new EngineBlockType(
            Identifier.parse("minecraft:clay"), "Clay");
    public static final EngineBlockType HAY_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:hay_block"), "Hay Bale");
    public static final EngineBlockType SLIME_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:slime_block"), "Slime Block");
    public static final EngineBlockType END_STONE = new EngineBlockType(
            Identifier.parse("minecraft:end_stone"), "End Stone");
    public static final EngineBlockType PACKED_ICE = new EngineBlockType(
            Identifier.parse("minecraft:packed_ice"), "Packed Ice");

    // The redstone power family (Slice 33 — the reference's own
    // registrations): the lamp pair (123 the dark, 124 the lit — one item,
    // the dark form, both states dropping it; RedstoneLampBlock's
    // neighbor-signal flip) and the ore's glowing state (74 — the click/
    // step swap's lit twin whose random tick reverts it).
    public static final EngineBlockType REDSTONE_LAMP = new EngineBlockType(
            Identifier.parse("minecraft:redstone_lamp"), "Redstone Lamp");
    public static final EngineBlockType LIT_REDSTONE_LAMP = new EngineBlockType(
            Identifier.parse("minecraft:lit_redstone_lamp"), "Redstone Lamp");
    public static final EngineBlockType LIT_REDSTONE_ORE = new EngineBlockType(
            Identifier.parse("minecraft:lit_redstone_ore"), "Redstone Ore");

    // The temple set (Slice 12f — the reference's TemplePieces): the
    // chiseled sandstone (legacy 24/3), the stained hardened clay pair over
    // 159 (the pyramid's blueprint floor — the reference's
    // ~DyeColor.ORANGE & 15 = 1 and ~DyeColor.BLUE & 15 = 4), the mossy
    // cobblestone (48 — the jungle picker's 60% pair), and the vine (106,
    // the all-attached metadata 15).
    public static final EngineBlockType CHISELED_SANDSTONE = new EngineBlockType(
            Identifier.parse("minecraft:chiseled_sandstone"), "Sandstone", 3);
    public static final EngineBlockType STAINED_CLAY_ORANGE = new EngineBlockType(
            Identifier.parse("minecraft:stained_clay_orange"), "Orange Stained Clay", 1);
    public static final EngineBlockType STAINED_CLAY_YELLOW = new EngineBlockType(
            Identifier.parse("minecraft:stained_clay_yellow"), "Yellow Stained Clay", 4);
    public static final EngineBlockType MOSSY_COBBLESTONE = new EngineBlockType(
            Identifier.parse("minecraft:mossy_cobblestone"), "Mossy Cobblestone");
    public static final EngineBlockType VINE = new EngineBlockType(
            Identifier.parse("minecraft:vine"), "Vine", 15);

    // The golem family's blocks (Slice 17d — the reference's
    // PumpkinBlock.trySpawnGolem patterns + the snow golem's trail): the
    // full snow block (legacy 80, the 4-snowball drop), the thin snow
    // layer (78 — a walk-through ground cover), the iron block (42, the
    // golem body + the reference's own material), the pumpkin pair (86/
    // 91 — the plain block and the lit lantern, the golem head; the
    // historical FACING rotation ledgers with the metadata slice — the
    // engine registers the unrotated form).
    public static final EngineBlockType SNOW = new EngineBlockType(
            Identifier.parse("minecraft:snow"), "Snow");
    public static final EngineBlockType SNOW_LAYER = new EngineBlockType(
            Identifier.parse("minecraft:snow_layer"), "Snow Layer");
    // The ice (legacy 79, Slice 21c — the precipitation freeze arm's write;
    // the reference's Material.ICE translucent block, slipperiness 0.98).
    public static final EngineBlockType ICE = new EngineBlockType(
            Identifier.parse("minecraft:ice"), "Ice");
    public static final EngineBlockType IRON_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:iron_block"), "Block of Iron");
    public static final EngineBlockType PUMPKIN = new EngineBlockType(
            Identifier.parse("minecraft:pumpkin"), "Pumpkin");
    public static final EngineBlockType MELON_BLOCK = new EngineBlockType(
            Identifier.parse("minecraft:melon_block"), "Melon", 0);
    public static final EngineBlockType MYCELIUM = new EngineBlockType(
            Identifier.parse("minecraft:mycelium"), "Mycelium", 0);
    public static final EngineBlockType WATERLILY = new EngineBlockType(
            Identifier.parse("minecraft:waterlily"), "Lily Pad", 0);
    public static final EngineBlockType LIT_PUMPKIN = new EngineBlockType(
            Identifier.parse("minecraft:lit_pumpkin"), "Jack o'Lantern");

    // The dye-coloring world families (Slice 31 — the reference's
    // DyeRecipes.java + the Block registrations 35/95/159/160/171/172):
    // every family is ONE vanilla item whose damage field carries the
    // DyeColor id, expanded here into flattened per-color blocks whose
    // legacy metadata nibble IS the color. The white wool keeps the plain
    // minecraft:wool id (every placed white wool in existing worlds stays
    // valid); the stained clay family folds the temple slice's
    // orange/yellow pair in as its own two members (the loop skips them —
    // the constants above ARE the family's registrations for those
    // colors).
    public static final EngineBlockType HARDENED_CLAY = new EngineBlockType(
            Identifier.parse("minecraft:hardened_clay"), "Hardened Clay");

    /** The flattened dye-family blocks (77 entries; see the slice note above). */
    public static final List<EngineBlockType> DYE_FAMILY_BLOCKS = buildDyeFamilyBlocks();

    private static List<EngineBlockType> buildDyeFamilyBlocks() {
        List<EngineBlockType> blocks = new java.util.ArrayList<>();
        for (int color = 0; color < 16; color++) {
            if (color != 0) { // white wool keeps the plain minecraft:wool id
                blocks.add(new EngineBlockType(
                        DyeFamilyBlocks.blockOf("wool", color),
                        DyeFamilyBlocks.displayName("Wool", color), color));
            }
            blocks.add(new EngineBlockType(
                    DyeFamilyBlocks.stainedGlassBlock(color),
                    DyeFamilyBlocks.displayName("Stained Glass", color), color));
            blocks.add(new EngineBlockType(
                    DyeFamilyBlocks.stainedGlassPaneBlock(color),
                    DyeFamilyBlocks.displayName("Stained Glass Pane", color), color));
            blocks.add(new EngineBlockType(
                    DyeFamilyBlocks.carpetBlock(color),
                    DyeFamilyBlocks.displayName("Carpet", color), color));
            // the temple slice's orange (1) / yellow (4) constants are the
            // family's own registrations for those colors
            if (color != 1 && color != 4) {
                blocks.add(new EngineBlockType(
                        DyeFamilyBlocks.stainedClayBlock(color),
                        DyeFamilyBlocks.displayName("Stained Clay", color), color));
            }
        }
        return List.copyOf(blocks);
    }

    private BuiltinBlocks() {
    }

    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        builder = builder
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
                .register(TRAPPED_CHEST)
                .register(TNT)
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
                .register(SKULL)
                .register(COMMAND_BLOCK)
                .register(COMMAND_BLOCK_TRIGGERED)
                .register(SKULL_DOWN)
                .register(SKULL_NORTH)
                .register(SKULL_SOUTH)
                .register(SKULL_WEST)
                .register(SKULL_EAST)
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
                .register(OAK_STAIRS_EAST_TOP)
                .register(OAK_STAIRS_WEST_TOP)
                .register(OAK_STAIRS_SOUTH_TOP)
                .register(OAK_STAIRS_NORTH_TOP)
                .register(COBBLESTONE_STAIRS_EAST)
                .register(COBBLESTONE_STAIRS_WEST)
                .register(COBBLESTONE_STAIRS_SOUTH)
                .register(COBBLESTONE_STAIRS_NORTH)
                .register(COBBLESTONE_STAIRS_EAST_TOP)
                .register(COBBLESTONE_STAIRS_WEST_TOP)
                .register(COBBLESTONE_STAIRS_SOUTH_TOP)
                .register(COBBLESTONE_STAIRS_NORTH_TOP)
                .register(RAIL)
                .register(RAIL_EW)
                .register(COBWEB)
                .register(GLASS_PANE)
                .register(IRON_BARS)
                .register(DOUBLE_STONE_SLAB)
                .register(SMOOTH_SANDSTONE)
                .register(SANDSTONE_STAIRS_EAST)
                .register(SANDSTONE_STAIRS_WEST)
                .register(SANDSTONE_STAIRS_SOUTH)
                .register(SANDSTONE_STAIRS_NORTH)
                .register(SANDSTONE_STAIRS_EAST_TOP)
                .register(SANDSTONE_STAIRS_WEST_TOP)
                .register(SANDSTONE_STAIRS_SOUTH_TOP)
                .register(SANDSTONE_STAIRS_NORTH_TOP)
                .register(CARROTS_STAGE0)
                .register(CARROTS_STAGE1)
                .register(CARROTS_STAGE2)
                .register(CARROTS_STAGE3)
                .register(CARROTS_STAGE4)
                .register(CARROTS_STAGE5)
                .register(CARROTS_STAGE6)
                .register(CARROTS_STAGE7)
                .register(STONE_BRICKS)
                .register(MOSSY_STONE_BRICKS)
                .register(CRACKED_STONE_BRICKS)
                .register(CHISELED_STONE_BRICKS)
                .register(INFESTED_STONE_BRICKS)
                .register(INFESTED_STONE)
                .register(INFESTED_COBBLESTONE)
                .register(INFESTED_MOSSY_STONE_BRICKS)
                .register(INFESTED_CRACKED_STONE_BRICKS)
                .register(INFESTED_CHISELED_STONE_BRICKS)
                .register(STONE_BRICK_SLAB)
                .register(MOB_SPAWNER)
                .register(NOTE_BLOCK)
                .register(JUKEBOX)
                .register(JUKEBOX_RECORD)
                .register(IRON_DOOR_LOWER_W)
                .register(IRON_DOOR_LOWER_N)
                .register(IRON_DOOR_LOWER_E)
                .register(IRON_DOOR_LOWER_S)
                .register(IRON_DOOR_UPPER)
                .register(END_PORTAL_FRAME_S)
                .register(END_PORTAL_FRAME_W)
                .register(END_PORTAL_FRAME_N)
                .register(END_PORTAL_FRAME_E)
                .register(END_PORTAL_FRAME_EYE_S)
                .register(END_PORTAL_FRAME_EYE_W)
                .register(END_PORTAL_FRAME_EYE_N)
                .register(END_PORTAL_FRAME_EYE_E)
                .register(NETHER_BRICKS)
                .register(NETHER_BRICK_FENCE)
                .register(NETHER_BRICK_STAIRS_EAST)
                .register(NETHER_BRICK_STAIRS_WEST)
                .register(NETHER_BRICK_STAIRS_SOUTH)
                .register(NETHER_BRICK_STAIRS_NORTH)
                .register(NETHER_BRICK_STAIRS_EAST_TOP)
                .register(NETHER_BRICK_STAIRS_WEST_TOP)
                .register(NETHER_BRICK_STAIRS_SOUTH_TOP)
                .register(NETHER_BRICK_STAIRS_NORTH_TOP)
                .register(NETHER_WART_STAGE0)
                .register(NETHER_WART_STAGE1)
                .register(NETHER_WART_STAGE2)
                .register(NETHER_WART_STAGE3)
                .register(PRISMARINE_ROUGH)
                .register(PRISMARINE_BRICKS)
                .register(PRISMARINE_DARK)
                .register(SEA_LANTERN)
                .register(SPONGE)
                .register(WET_SPONGE)
                .register(GOLD_BLOCK)
                .register(DIAMOND_BLOCK)
                .register(EMERALD_BLOCK)
                .register(LAPIS_ORE)
                .register(EMERALD_ORE)
                .register(LAPIS_BLOCK)
                .register(REDSTONE_BLOCK)
                .register(COAL_BLOCK)
                .register(QUARTZ_BLOCK)
                .register(BRICK_BLOCK)
                .register(CLAY)
                .register(HAY_BLOCK)
                .register(SLIME_BLOCK)
                .register(END_STONE)
                .register(PACKED_ICE)
                .register(REDSTONE_LAMP)
                .register(LIT_REDSTONE_LAMP)
                .register(LIT_REDSTONE_ORE)
                .register(CHISELED_SANDSTONE)
                .register(STAINED_CLAY_ORANGE)
                .register(STAINED_CLAY_YELLOW)
                .register(MOSSY_COBBLESTONE)
                .register(VINE)
                .register(SNOW)
                .register(SNOW_LAYER)
                .register(ICE)
                .register(IRON_BLOCK)
                .register(PUMPKIN)
                .register(MELON_BLOCK)
                .register(MYCELIUM)
                .register(WATERLILY)
                .register(LIT_PUMPKIN)
                .register(POTATOES_STAGE0)
                .register(POTATOES_STAGE1)
                .register(POTATOES_STAGE2)
                .register(POTATOES_STAGE3)
                .register(POTATOES_STAGE4)
                .register(POTATOES_STAGE5)
                .register(POTATOES_STAGE6)
                .register(POTATOES_STAGE7)
                .register(HARDENED_CLAY);
        for (EngineBlockType family : DYE_FAMILY_BLOCKS) {
            builder = builder.register(family);
        }
        // The wood-variant foundation (Slice 35 — PlanksBlock.Variant's own
        // domain): the five new woods' planks/logs/leaves + all six
        // saplings. The shipped oak planks/log/leaves keep their own
        // registration rows above.
        for (EngineBlockType family : WoodVariantBlocks.familyBlocks()) {
            builder = builder.register(family);
        }
        // The building-shape families (Slice 36 — the eight new stair
        // families, the five new wood fences, the wooden-slab pairs).
        for (EngineBlockType shape : WoodShapeBlocks.familyBlocks()) {
            builder = builder.register(shape);
        }
        // The chest pair's facing states (Slice L2 — the four plain + the
        // four trapped, the Direction-id nibble band 2-5).
        builder = net.zaminmc.torch.server.chest.ChestFacingBlocks.registerAll(builder);
        // The furnace pair's facing states (Slice L3 — the four plain + the
        // four lit, the same Direction-id nibble band).
        builder = net.zaminmc.torch.server.furnace.FurnaceFacingBlocks.registerAll(builder);
        // The complete door families (Slice 34 — DoorBlock/DoorItem 1:1):
        // the five new woods' twelve states each, the oak hinge pair, and
        // the iron door's open + hinge states. The shipped oak/iron
        // constants above keep their own registration rows.
        builder = WoodDoorFamily.registerAll(builder);
        // The fence-gate families (Slice 37 — FenceGateBlock 1:1): the six
        // woods' sixteen facing/open/powered states each (the bare meta-0
        // form shares the family item's own identifier).
        for (EngineBlockType gate : FenceGateBlocks.familyBlocks()) {
            builder = builder.register(gate);
        }
        // The rail trio (Slice 38 — PoweredRailBlock/DetectorRailBlock 1:1):
        // the golden/detector/activator families' twelve shape/powered
        // states each (the bare north_south form shares the family item's
        // own identifier).
        for (EngineBlockType rail : RailBlocks.familyBlocks()) {
            builder = builder.register(rail);
        }
        // The trapdoor pair (Slice 39 — TrapdoorBlock 1:1): the wood 96 and
        // iron 167 families' sixteen facing/open/half states each.
        for (EngineBlockType trapdoor : TrapdoorBlocks.familyBlocks()) {
            builder = builder.register(trapdoor);
        }
        // The huge-mushroom pair (Slice 40 — MushroomBlock 1:1): the brown
        // 99 and red 100 families' fourteen variant states each (the bare
        // form carries the all_outside default).
        for (EngineBlockType mushroom : MushroomBlocks.familyBlocks()) {
            builder = builder.register(mushroom);
        }
        // The melon family (Slice 41 — MelonBlock/StemBlock 1:1): the two
        // stems' eight AGE states each (the melon block's own constant
        // rides the plain rows above).
        for (EngineBlockType stem : MelonBlocks.familyBlocks()) {
            builder = builder.register(stem);
        }
        // The cocoa family (Slice 43 — CocoaBlock 1:1): the twelve
        // facing/age states.
        for (EngineBlockType pod : CocoaBlocks.familyBlocks()) {
            builder = builder.register(pod);
        }
        // The flower family (Slice 44 — FlowerBlock 1:1): the red flower's
        // eight new variant states (the poppy 0 keeps its shipped plain
        // rows above).
        for (EngineBlockType flower : FlowerBlocks.familyBlocks()) {
            builder = builder.register(flower);
        }
        // The double-plant family (Slice 45 — DoublePlantBlock 1:1): the
        // six variants' lower + upper states.
        for (EngineBlockType plant : DoublePlantBlocks.familyBlocks()) {
            builder = builder.register(plant);
        }
        // The cake family (Slice 46 — CakeBlock 1:1): the seven BITES
        // states (the bare bites-0 form is the placed block).
        for (EngineBlockType slice : CakeBlocks.familyBlocks()) {
            builder = builder.register(slice);
        }
        // The wall family (Slice 47 — WallBlock 1:1): the two VARIANT
        // states (the connections are the client's own virtual reads).
        for (EngineBlockType wall : WallBlocks.familyBlocks()) {
            builder = builder.register(wall);
        }
        // The flower-pot family (Slice 48 — FlowerPotBlock 1:1): the empty
        // pot + the thirteen potted contents over the LEGACY_DATA band.
        for (EngineBlockType pot : FlowerPotBlocks.familyBlocks()) {
            builder = builder.register(pot);
        }
        // The weighted pressure-plate pair (Slice 49a —
        // WeightedPressurePlateBlock 1:1): the sixteen POWER states each.
        for (EngineBlockType plate : net.zaminmc.torch.server.redstone
                .WeightedPlateBlocks.familyBlocks()) {
            builder = builder.register(plate);
        }
        // The red sandstone family (Slice 52 — 1.8's own four, 1:1): the
        // red sand + the three TYPE states + the four stairs through the
        // family hub, the slab pair + the double directly (the single
        // registration forms).
        for (EngineBlockType member : RedSandstoneBlocks.familyBlocks()) {
            builder = builder.register(member);
        }
        builder = builder.register(DOUBLE_STONE_SLAB2)
                .register(STONE_SLAB2)
                .register(STONE_SLAB2_TOP);
        // The anvil family (Slice 53 — AnvilBlock 1:1): the twelve states
        // through the family hub.
        for (EngineBlockType anvil : AnvilBlocks.familyBlocks()) {
            builder = builder.register(anvil);
        }
        // The end portal (Slice 54 — EndPortalBlock 1:1): the single state.
        builder = builder.register(END_PORTAL);
        // The dragon egg (Slice 55 — DragonEggBlock 1:1): the single state.
        builder = builder.register(DRAGON_EGG);
        // The barrier (Slice 56 — BarrierBlock 1:1): the single state.
        builder = builder.register(BARRIER);
        // The double wooden slab family (Slice 58 — the six variants over 125).
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            builder = builder.register(WoodShapeBlocks.doubleSlabState(variant));
        }
        return builder;
    }

    /**
     * @return whether the type is a command-block state (either TRIGGERED
     *         bit — Slice 72's family membership, the redstone walk's gate).
     */
    public static boolean isCommandBlock(BlockType type) {
        if (type == null) {
            return false;
        }
        String value = type.identifier().value();
        return value.equals("command_block")
                || value.equals("command_block_triggered");
    }
}
