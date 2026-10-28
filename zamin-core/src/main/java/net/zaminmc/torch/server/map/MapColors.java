package net.zaminmc.torch.server.map;

import net.zaminmc.torch.block.BlockType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The server's map palette (the map item family — the reference's
 * {@code block/material/MapColor.java} 1:1): the thirty-six map colors with
 * their ids and RGB values, and the block consult — the terrain scan's own
 * color resolution ({@code block.getMapColor(state)}), which in the
 * reference answers the block's MATERIAL color unless the block overrides
 * it (the per-variant planks/stones/slabs, the dye families, the handful of
 * named overrides like gravel's STONE and glowstone's SAND).
 *
 * <p>The engine's blocks are flattened per-state identifiers, so the table
 * keys on the identifier's value: the fixed families as explicit rows, the
 * state bands (facings, halves, powers, stages, ages) as family loops over
 * the same base color — every colorable family the reference lists is
 * covered, and the coverage test walks the whole registry. The famous
 * vanilla quirks are kept 1:1: the stairs carry their BASE BLOCK's own
 * consult (StairsBlock.getMapColor line 488 — the brick stairs read RED,
 * the quartz/sandstone/nether-brick stairs read their full blocks'
 * QUARTZ/SAND/NETHER), gravel reads STONE, glowstone reads SAND, TNT
 * reads LAVA, the sideways-log arm's substitutions (the engine's logs are
 * the upright axis-Y form — the Y arm's own per-wood colors), and the
 * wooden slab band reads the per-wood plank colors.</p>
 */
public final class MapColors {

    private MapColors() {
    }

    /**
     * One map color (the reference's own fields): the 0..63 id that rides
     * the color byte's high six bits ({@code id * 4 + shade}) and the RGB
     * value the client shades per brightness band.
     */
    public record Color(int id, int rgb) {

        /** The reference's own registration ceiling (BY_ID's 64 slots). */
        public static final Color[] BY_ID = new Color[64];

        public Color {
            if (id < 0 || id > 63) {
                throw new IndexOutOfBoundsException(
                        "Map colour ID must be between 0 and 63 (inclusive)");
            }
            BY_ID[id] = this;
        }

        /**
         * The client's shade walk (MapColor.getColor lines 54-76): the
         * brightness band's multiplier over the RGB channels — the server
         * never computes this (the color byte carries the shade id), it
         * rides here for the palette's own completeness.
         */
        public int shaded(int shade) {
            int brightness = switch (shade) {
                case 3 -> 135;
                case 2 -> 255;
                case 1 -> 220;
                default -> 180; // shade 0
            };
            int r = (rgb >> 16 & 0xFF) * brightness / 255;
            int g = (rgb >> 8 & 0xFF) * brightness / 255;
            int b = (rgb & 0xFF) * brightness / 255;
            return 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    // --- the thirty-six colors (MapColor.java's own registrations, 1:1) ---

    public static final Color AIR = new Color(0, 0);
    public static final Color GRASS = new Color(1, 8368696);
    public static final Color SAND = new Color(2, 16247203);
    public static final Color WEB = new Color(3, 13092807);
    public static final Color LAVA = new Color(4, 16711680);
    public static final Color ICE = new Color(5, 10526975);
    public static final Color IRON = new Color(6, 10987431);
    public static final Color FOLIAGE = new Color(7, 31744);
    public static final Color WHITE = new Color(8, 16777215);
    public static final Color CLAY = new Color(9, 10791096);
    public static final Color DIRT = new Color(10, 9923917);
    public static final Color STONE = new Color(11, 7368816);
    public static final Color WATER = new Color(12, 4210943);
    public static final Color WOOD = new Color(13, 9402184);
    public static final Color QUARTZ = new Color(14, 16776437);
    public static final Color ORANGE = new Color(15, 14188339);
    public static final Color MAGENTA = new Color(16, 11685080);
    public static final Color LIGHT_BLUE = new Color(17, 6724056);
    public static final Color YELLOW = new Color(18, 15066419);
    public static final Color LIME = new Color(19, 8375321);
    public static final Color PINK = new Color(20, 15892389);
    public static final Color GRAY = new Color(21, 5000268);
    public static final Color LIGHT_GRAY = new Color(22, 10066329);
    public static final Color CYAN = new Color(23, 5013401);
    public static final Color PURPLE = new Color(24, 8339378);
    public static final Color BLUE = new Color(25, 3361970);
    public static final Color BROWN = new Color(26, 6704179);
    public static final Color GREEN = new Color(27, 6717235);
    public static final Color RED = new Color(28, 10040115);
    public static final Color BLACK = new Color(29, 1644825);
    public static final Color GOLD = new Color(30, 16445005);
    public static final Color DIAMOND = new Color(31, 6085589);
    public static final Color LAPIS = new Color(32, 4882687);
    public static final Color EMERALD = new Color(33, 55610);
    public static final Color SPRUCE = new Color(34, 8476209);
    public static final Color NETHER = new Color(35, 7340544);

    // --- the block consult --------------------------------------------------

    /** The dye order (DyeColor's own id band, WHITE first). */
    private static final List<String> DYES = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "silver", "cyan", "purple", "blue", "brown", "green", "red", "black");

    /** The dye id -> the dye's own map color (DyeColor's registrations). */
    private static final List<Color> DYE_COLORS = List.of(
            WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK, GRAY,
            LIGHT_GRAY, CYAN, PURPLE, BLUE, BROWN, GREEN, RED, BLACK);

    /** The planks variant order (PlanksBlock.Variant's own colors). */
    private static final List<String> WOODS = List.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak");

    /** The per-wood plank colors (PlanksBlock.Variant's registrations). */
    private static final List<Color> WOOD_COLORS = List.of(
            WOOD, SPRUCE, SAND, DIRT, ORANGE, BROWN);

    private static final Map<String, Color> TABLE = buildTable();

    private static Map<String, Color> buildTable() {
        Map<String, Color> t = new LinkedHashMap<>();
        // The core terrain (Material colors + the named overrides).
        row(t, "air", AIR);
        row(t, "stone", STONE);
        row(t, "bedrock", STONE);
        row(t, "grass_block", GRASS);
        row(t, "mycelium", PURPLE); // MyceliumBlock(Material.GRASS, PURPLE)
        row(t, "dirt", DIRT);
        row(t, "coarse_dirt", DIRT);
        row(t, "podzol", SPRUCE); // DirtBlock.Variant.PODZOL
        row(t, "farmland", DIRT);
        row(t, "farmland_wet", DIRT);
        row(t, "cobblestone", STONE);
        row(t, "mossy_cobblestone", STONE);
        // The ores: OreBlock() default -> Material.STONE's color; the
        // quartz ore alone carries MapColor.NETHER (Block.java line 1334).
        for (String ore : new String[] {"coal_ore", "iron_ore", "gold_ore",
                "diamond_ore", "emerald_ore", "redstone_ore", "lit_redstone_ore"}) {
            row(t, ore, STONE);
        }
        row(t, "lapis_ore", STONE);
        row(t, "quartz_ore", NETHER);
        // The sands and the gravel quirk (GravelBlock.getMapColor -> STONE).
        row(t, "sand", SAND);
        row(t, "red_sand", ORANGE); // SandBlock.Variant.RED_SAND
        row(t, "gravel", STONE);
        // The sandstone trio (the override -> SAND) + the red family
        // (RedSandstoneBlock's ctor color RED_SAND -> ORANGE).
        row(t, "sandstone", SAND);
        row(t, "chiseled_sandstone", SAND);
        row(t, "smooth_sandstone", SAND);
        for (String red : new String[] {"red_sandstone", "chiseled_red_sandstone",
                "smooth_red_sandstone"}) {
            row(t, red, ORANGE);
        }
        // The mineral vault (Block.java's own registrations).
        row(t, "clay", CLAY);
        row(t, "lapis_block", LAPIS);       // Block(Material.IRON, LAPIS)
        row(t, "gold_block", GOLD);         // Block(Material.IRON, GOLD)
        row(t, "iron_block", IRON);         // Block(Material.IRON, IRON)
        row(t, "diamond_block", DIAMOND);   // Block(Material.IRON, DIAMOND)
        row(t, "emerald_block", EMERALD);   // Block(Material.IRON, EMERALD)
        row(t, "coal_block", BLACK);        // Block(Material.STONE, BLACK)
        row(t, "brick_block", RED);         // Block(Material.STONE, RED)
        row(t, "redstone_block", LAVA);     // RedstoneBlock(IRON, LAVA)
        row(t, "quartz_block", QUARTZ);     // QuartzBlock override
        // The nether set: netherrack/nether bricks carry the NETHER
        // override, the soul sand's ctor BROWN, the portal pair the
        // PORTAL material's AIR.
        row(t, "netherrack", NETHER);
        row(t, "nether_bricks", NETHER);
        row(t, "nether_brick_fence", NETHER); // FenceBlock(STONE, NETHER)
        row(t, "soul_sand", BROWN);
        row(t, "nether_portal", AIR);
        row(t, "nether_portal_z", AIR);
        row(t, "glowstone", SAND); // GlowstoneBlock override
        row(t, "obsidian", BLACK); // ObsidianBlock override
        row(t, "end_stone", SAND); // EndStoneBlock? — see the table note below
        row(t, "sea_lantern", QUARTZ); // SeaLanternBlock override
        // The sponge pair (Material.SPONGE -> YELLOW).
        row(t, "sponge", YELLOW);
        row(t, "wet_sponge", YELLOW);
        row(t, "slime_block", GRASS); // SlimeBlock(CLAY, GRASS)
        row(t, "tnt", LAVA);          // Material.TNT -> LAVA
        // The snow/ice pair.
        row(t, "snow", WHITE);
        row(t, "snow_layer", WHITE);
        row(t, "ice", ICE);
        row(t, "packed_ice", ICE);
        // The fluids (the water/lava material colors).
        row(t, "water", WATER);
        row(t, "lava", LAVA);
        for (int i = 1; i <= 15; i++) {
            row(t, "still_water_" + i, WATER);
            row(t, "still_lava_" + i, LAVA);
        }
        for (int i = 1; i <= 7; i++) {
            row(t, "flowing_water_" + i, WATER);
            row(t, "flowing_lava_" + i, LAVA);
            row(t, "falling_water_" + i, WATER);
            row(t, "falling_lava_" + i, LAVA);
        }
        row(t, "flowing_water", WATER);
        row(t, "flowing_lava", LAVA);
        row(t, "falling_water", WATER);
        row(t, "falling_lava", LAVA);
        // The glass family (Material.GLASS -> AIR).
        row(t, "glass", AIR);
        row(t, "glass_pane", AIR);
        // The wool/stained families (ColoredBlock's per-dye consult; the
        // plain wool/carpet members are the WHITE dye's).
        row(t, "wool", WHITE);
        row(t, "carpet_white", WHITE); // the plain carpet's own state is white
        for (int dye = 0; dye < 16; dye++) {
            Color color = DYE_COLORS.get(dye);
            row(t, DYES.get(dye) + "_wool", color);
            row(t, "carpet_" + DYES.get(dye), color);
            row(t, "stained_glass_" + DYES.get(dye), color);
            row(t, "stained_glass_pane_" + DYES.get(dye), color);
            row(t, "stained_clay_" + DYES.get(dye), color);
        }
        row(t, "hardened_clay", ORANGE); // HardenedClayBlock override
        // The wood families: the planks' per-variant colors; the logs'
        // axis-Y arm (the engine's upright form); the leaves' FOLIAGE;
        // the slabs' per-variant colors; the stairs' MATERIAL color (the
        // vanilla quirk — WOOD for every wood); the fences' material
        // color; the gates' per-variant ctor color; the doors' material
        // color.
        for (int wood = 0; wood < 6; wood++) {
            String w = WOODS.get(wood);
            Color c = WOOD_COLORS.get(wood);
            row(t, w + "_planks", c);
            row(t, w + "_log", c);
            row(t, w + "_leaves", FOLIAGE);
            row(t, w + "_leaves_check", FOLIAGE);
            row(t, w + "_leaves_persistent", FOLIAGE);
            row(t, w + "_leaves_persistent_check", FOLIAGE);
            row(t, w + "_slab", c);
            row(t, w + "_slab_top", c);
            row(t, "double_" + (wood == 0 ? "wooden" : w) + "_slab", c);
            row(t, w + "_sapling", FOLIAGE);
            row(t, w + "_sapling_ready", FOLIAGE);
            row(t, w + "_fence", WOOD);
            row(t, w + "_fence_gate", c);
            for (String facing : new String[] {"east", "north", "south", "west"}) {
                row(t, w + "_fence_gate_" + facing, c);
                row(t, w + "_fence_gate_open_" + facing, c);
                row(t, w + "_fence_gate_powered_" + facing, c);
                row(t, w + "_fence_gate_powered_open_" + facing, c);
                row(t, w + "_stairs_" + facing, c);
                row(t, w + "_stairs_" + facing + "_top", c);
            }
            row(t, w + "_fence_gate_open", c);
            row(t, w + "_fence_gate_powered", c);
            row(t, w + "_fence_gate_powered_open", c);
            row(t, w + "_door", WOOD);
            row(t, w + "_door_upper", WOOD);
            row(t, w + "_door_upper_right", WOOD);
            row(t, w + "_door_upper_powered", WOOD);
            row(t, w + "_door_upper_right_powered", WOOD);
            for (String state : new String[] {
                    "east", "north", "south", "west", "open", "open_east",
                    "open_north", "open_south", "open_west", "upper_open",
                    "upper_open_east", "upper_open_north", "upper_open_south",
                    "upper_open_west", "upper_powered", "upper_powered_open",
                    "upper_right", "upper_right_open", "upper_right_powered",
                    "upper_right_powered_open", "upper_powered_east",
                    "upper_powered_north", "upper_powered_south",
                    "upper_powered_west", "upper_open_right"}) {
                row(t, w + "_door_" + state, WOOD);
            }
        }
        // The masonry stairs (StairsBlock.getMapColor lines 488-489 — the
        // BASE BLOCK's own consult, overrides included): the cobblestone/
        // stone-brick STONE, the brick RED (the Block(STONE, RED) ctor),
        // the sandstone SAND (the override), the nether brick NETHER (the
        // override), the red sandstone ORANGE (the RED_SAND ctor color),
        // the quartz QUARTZ (the override).
        stairs(t, "cobblestone", STONE);
        stairs(t, "stone_brick", STONE);
        stairs(t, "brick", RED);
        stairs(t, "sandstone", SAND);
        stairs(t, "nether_brick", NETHER);
        stairs(t, "red_sandstone", ORANGE);
        stairs(t, "quartz", QUARTZ);
        // The stone slabs (StoneSlabBlock's per-variant consult, shared by
        // the single and the double forms).
        row(t, "stone_slab", STONE);
        row(t, "stone_slab_top", STONE);
        row(t, "cobblestone_slab", STONE);
        row(t, "cobblestone_slab_top", STONE);
        row(t, "sandstone_slab", SAND);
        row(t, "sandstone_slab_top", SAND);
        row(t, "stone_brick_slab", STONE);
        row(t, "stone_brick_slab_top", STONE);
        row(t, "double_stone_slab", STONE);
        row(t, "double_stone_slab_seamless", STONE);
        row(t, "double_cobblestone_slab", STONE);
        row(t, "double_sandstone_slab", SAND);
        row(t, "double_stone_brick_slab", STONE);
        // The red sandstone slab pair (RedSandstoneSlab's variant consult:
        // the single variant carries RED_SAND's ORANGE).
        row(t, "stone_slab2", ORANGE);
        row(t, "stone_slab2_top", ORANGE);
        row(t, "double_stone_slab2", ORANGE);
        row(t, "double_stone_slab2_seamless", ORANGE);
        // The stone bricks (Material.STONE) + the infested eggs (CLAY!).
        row(t, "stone_bricks", STONE);
        row(t, "mossy_stone_bricks", STONE);
        row(t, "cracked_stone_bricks", STONE);
        row(t, "chiseled_stone_bricks", STONE);
        for (String infested : new String[] {"infested_stone", "infested_cobblestone",
                "infested_stone_bricks", "infested_mossy_stone_bricks",
                "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks"}) {
            row(t, infested, CLAY); // InfestedBlock(Material.CLAY)
        }
        // The walls (WallBlock over the cobblestone's STONE material).
        row(t, "cobblestone_wall", STONE);
        row(t, "mossy_cobblestone_wall", STONE);
        // The functional blocks.
        row(t, "crafting_table", WOOD);
        row(t, "bookshelf", WOOD);
        row(t, "chest", WOOD);
        row(t, "trapped_chest", WOOD);
        for (String facing : new String[] {"east", "north", "south", "west"}) {
            row(t, "chest_" + facing, WOOD);
            row(t, "trapped_chest_" + facing, WOOD);
            row(t, "ender_chest_" + facing, STONE); // EnderChestBlock(STONE)
        }
        row(t, "furnace", STONE);
        row(t, "furnace_lit", STONE);
        for (String facing : new String[] {"east", "north", "south", "west"}) {
            row(t, "furnace_" + facing, STONE);
            row(t, "furnace_lit_" + facing, STONE);
        }
        row(t, "dispenser", STONE); // DispenserBlock(STONE)
        row(t, "dropper", STONE);
        row(t, "hopper", STONE); // HopperBlock(IRON, STONE)
        row(t, "brewing_stand", IRON);  // BrewingStandBlock(IRON)
        row(t, "cauldron", STONE);      // CauldronBlock(IRON, STONE)
        row(t, "anvil", IRON);          // Material.ANVIL -> IRON
        for (String state : new String[] {"east", "north", "west",
                "chipped", "chipped_east", "chipped_north", "chipped_west",
                "damaged", "damaged_east", "damaged_north", "damaged_west"}) {
            row(t, "anvil_" + state, IRON);
        }
        row(t, "enchanting_table", RED); // EnchantingTableBlock(STONE, RED)
        row(t, "beacon", DIAMOND);       // BeaconBlock(GLASS, DIAMOND)
        row(t, "mob_spawner", STONE);
        row(t, "command_block", ORANGE); // CommandBlock(IRON, ORANGE)
        row(t, "command_block_triggered", ORANGE);
        row(t, "jukebox", DIRT);  // JukeboxBlock(WOOD, DIRT) — the quirk
        row(t, "jukebox_record", DIRT);
        row(t, "noteblock", WOOD);
        row(t, "end_portal", BLACK);       // EndPortalBlock override
        // The end portal frames (STONE + GREEN).
        for (String frame : new String[] {"end_portal_frame_east",
                "end_portal_frame_north", "end_portal_frame_south",
                "end_portal_frame_west", "end_portal_frame_eye_east",
                "end_portal_frame_eye_north", "end_portal_frame_eye_south",
                "end_portal_frame_eye_west"}) {
            row(t, frame, GREEN);
        }
        // The redstone family: the lamp pair's AIR, the torch/repeater/
        // comparator/wire/tripwire DECORATION-AIR, the ore STONE.
        row(t, "redstone_lamp", AIR);
        row(t, "lit_redstone_lamp", AIR);
        row(t, "torch", AIR);
        row(t, "redstone_torch", AIR);
        row(t, "unlit_redstone_torch", AIR);
        for (String facing : new String[] {"east", "north", "south", "west"}) {
            row(t, "redstone_torch_" + facing, AIR);
            row(t, "unlit_redstone_torch_" + facing, AIR);
        }
        for (int i = 1; i <= 15; i++) {
            row(t, "redstone_wire_" + i, AIR);
        }
        row(t, "redstone_wire", AIR);
        // The repeaters/comparators (DiodeBlock's DECORATION material).
        for (String facing : new String[] {"east", "north", "south", "west"}) {
            for (int i = 1; i <= 4; i++) {
                row(t, "repeater_" + facing + "_" + i, AIR);
                row(t, "powered_repeater_" + facing + "_" + i, AIR);
            }
            for (String mode : new String[] {"compare", "subtract"}) {
                row(t, "comparator_" + facing + "_" + mode, AIR);
                row(t, "powered_comparator_" + facing + "_" + mode, AIR);
            }
        }
        // The mechanical band (the pistons' STONE, the rails' AIR).
        for (String piston : new String[] {"piston", "moving_piston"}) {
            for (String facing : new String[] {"down", "up", "east", "north",
                    "south", "west"}) {
                row(t, piston + "_" + facing, STONE);
                row(t, piston + "_" + facing + "_extended", STONE);
                row(t, piston + "_" + facing + "_sticky", STONE);
            }
        }
        row(t, "sticky_piston_down", STONE);
        row(t, "sticky_piston_up", STONE);
        row(t, "sticky_piston_east", STONE);
        row(t, "sticky_piston_north", STONE);
        row(t, "sticky_piston_south", STONE);
        row(t, "sticky_piston_west", STONE);
        for (String suffix : new String[] {"_extended", "_extended_sticky"}) {
            for (String facing : new String[] {"down", "up", "east", "north",
                    "south", "west"}) {
                row(t, "sticky_piston_" + facing + suffix, STONE);
            }
        }
        for (String head : new String[] {"piston_head_down", "piston_head_up",
                "piston_head_east", "piston_head_north", "piston_head_south",
                "piston_head_west"}) {
            row(t, head, STONE);
            row(t, head + "_sticky", STONE);
        }
        // The rails (AbstractRailBlock's DECORATION material -> AIR).
        for (String rail : new String[] {"rail", "rail_ew", "golden_rail",
                "golden_rail_powered", "detector_rail", "detector_rail_powered",
                "activator_rail", "activator_rail_powered"}) {
            row(t, rail, AIR);
        }
        for (String family : new String[] {"golden_rail", "detector_rail",
                "activator_rail"}) {
            row(t, family + "_powered", AIR);
            for (String shape : new String[] {"east_west", "powered_east_west",
                    "ascending_east", "ascending_north", "ascending_south",
                    "ascending_west", "powered_ascending_east",
                    "powered_ascending_north", "powered_ascending_south",
                    "powered_ascending_west"}) {
                row(t, family + "_" + shape, AIR);
            }
        }
        // The flora: the plants' FOLIAGE material band; the dead bush's
        // WOOD override; the lily pad's FOLIAGE.
        for (String plant : new String[] {"tall_grass", "fern",
                "dandelion", "poppy",
                "blue_orchid", "allium", "houstonia", "red_tulip", "orange_tulip",
                "pink_tulip", "white_tulip", "oxeye_daisy", "sugar_cane",
                "waterlily", "brown_mushroom", "red_mushroom", "cactus",
                "nether_wart_stage0", "nether_wart_stage1", "nether_wart_stage2",
                "nether_wart_stage3", "cocoa", "vine"}) {
            row(t, plant, FOLIAGE);
        }
        row(t, "dead_bush", WOOD); // DeadBushBlock override
        for (String suffix : new String[] {"", "_east", "_north", "_west"}) {
            for (int age = 0; age <= 2; age++) {
                if (age == 0) {
                    row(t, "cocoa" + suffix, FOLIAGE);
                } else {
                    row(t, "cocoa_age" + age + suffix, FOLIAGE);
                }
            }
        }
        // The crops (PlantBlock's FOLIAGE — the stems carry the
        // "_stem" segment in their engine names).
        for (String crop : new String[] {"wheat", "carrots", "potatoes"}) {
            for (int stage = 0; stage <= 7; stage++) {
                row(t, crop + "_stage" + stage, FOLIAGE);
            }
        }
        for (int stage = 0; stage <= 7; stage++) {
            row(t, "pumpkin_stem_stage" + stage, FOLIAGE);
            row(t, "melon_stem_stage" + stage, FOLIAGE);
        }
        row(t, "melon_block", LIME);  // MelonBlock(PUMPKIN, LIME)
        row(t, "pumpkin", ORANGE);    // PumpkinBlock(PUMPKIN, ORANGE)
        row(t, "lit_pumpkin", ORANGE);
        // The rotated states (the metadata slice): the same orange body.
        row(t, "pumpkin_west", ORANGE);
        row(t, "pumpkin_north", ORANGE);
        row(t, "pumpkin_east", ORANGE);
        row(t, "lit_pumpkin_west", ORANGE);
        row(t, "lit_pumpkin_north", ORANGE);
        row(t, "lit_pumpkin_east", ORANGE);
        row(t, "hay_block", YELLOW);  // HayBlock(GRASS, YELLOW)
        // The double plants (REPLACEABLE_PLANT -> FOLIAGE).
        for (String plant : new String[] {"double_grass", "double_fern",
                "sunflower", "lilac", "rose", "peony"}) {
            row(t, plant, FOLIAGE);
            row(t, plant + "_upper", FOLIAGE);
        }
        // The mushroom blocks: the ctor colors (brown DIRT, red RED) with
        // the three stem/inside overrides (WEB/SAND/SAND).
        for (String facing : new String[] {"east", "north", "north_east",
                "north_west", "south", "south_east", "south_west", "west",
                "center"}) {
            row(t, "brown_mushroom_block_" + facing, DIRT);
            row(t, "red_mushroom_block_" + facing, RED);
        }
        row(t, "brown_mushroom_block_all_stem", WEB);
        row(t, "red_mushroom_block_all_stem", WEB);
        row(t, "brown_mushroom_block_all_inside", SAND);
        row(t, "red_mushroom_block_all_inside", SAND);
        row(t, "brown_mushroom_block_stem", SAND);
        row(t, "red_mushroom_block_stem", SAND);
        row(t, "brown_mushroom_block", DIRT);
        row(t, "red_mushroom_block", RED);
        // The prismarine trio (the ROUGH/DIAMOND override).
        row(t, "prismarine_rough", CYAN);
        row(t, "prismarine", CYAN);
        row(t, "prismarine_bricks", DIAMOND);
        row(t, "prismarine_dark", DIAMOND);
        // The misc functional band.
        row(t, "bed", WEB); // BedBlock(WOOL) -> WEB
        for (String state : new String[] {"east", "north", "west", "head",
                "head_east", "head_north", "head_west"}) {
            row(t, "bed_" + state, WEB);
        }
        row(t, "cobweb", WEB);   // Material.COBWEB
        row(t, "iron_bars", IRON); // PaneBlock(IRON)
        row(t, "cake", AIR);
        for (int bite = 1; bite <= 6; bite++) {
            row(t, "cake_bite" + bite, AIR);
        }
        row(t, "barrier", AIR);   // Material.BARRIER -> AIR
        row(t, "fire", AIR);      // Material.FIRE -> AIR
        for (int age = 1; age <= 15; age++) {
            row(t, "fire_age" + age, AIR);
        }
        row(t, "dragon_egg", BLACK); // DragonEggBlock(EGG, BLACK)
        row(t, "skull", AIR);        // Material.DECORATION -> AIR
        row(t, "skull_down", AIR);
        for (String facing : new String[] {"east", "north", "south", "west"}) {
            row(t, "skull_" + facing, AIR);
            row(t, "sign_" + facing, WOOD); // SignBlock(WOOD)
            row(t, "wall_sign_" + facing, WOOD);
            row(t, "standing_banner_" + facing, WOOD); // BannerBlock(WOOD)
            row(t, "wall_banner_" + facing, WOOD);
            row(t, "ladder_" + facing, AIR); // LadderBlock(DECORATION)
        }
        row(t, "sign", WOOD);
        row(t, "wall_sign", WOOD);
        row(t, "standing_banner", WOOD);
        row(t, "wall_banner", WOOD);
        row(t, "ladder", AIR);
        row(t, "flower_pot", AIR); // FlowerPotBlock(DECORATION)
        for (String potted : new String[] {"potted_oak_sapling",
                "potted_spruce_sapling", "potted_birch_sapling",
                "potted_jungle_sapling", "potted_acacia_sapling",
                "potted_dark_oak_sapling", "potted_dandelion", "potted_poppy",
                "potted_red_mushroom", "potted_brown_mushroom", "potted_cactus",
                "potted_dead_bush", "potted_fern"}) {
            row(t, potted, AIR);
        }
        // The levers and buttons (DECORATION -> AIR).
        for (String lever : new String[] {"east", "west", "south", "north",
                "up_x", "up_z", "down_x", "down_z"}) {
            row(t, "lever_" + lever, AIR);
            row(t, "lever_" + lever + "_powered", AIR);
        }
        for (String button : new String[] {"down", "up", "east", "north",
                "south", "west"}) {
            row(t, "stone_button_" + button, AIR);
            row(t, "stone_button_" + button + "_powered", AIR);
            row(t, "wooden_button_" + button, AIR);
            row(t, "wooden_button_" + button + "_powered", AIR);
        }
        row(t, "stone_button_down", AIR);
        // The pressure plates: the wooden pair's WOOD material color, the
        // stone pair's STONE, the weighted pair's GOLD ctor color.
        row(t, "wooden_pressure_plate", WOOD);
        row(t, "wooden_pressure_plate_powered", WOOD);
        row(t, "stone_pressure_plate", STONE);
        row(t, "stone_pressure_plate_powered", STONE);
        for (int power = 1; power <= 15; power++) {
            row(t, "light_weighted_pressure_plate_power" + power, GOLD);
            row(t, "heavy_weighted_pressure_plate_power" + power, GOLD);
        }
        row(t, "light_weighted_pressure_plate", GOLD);
        row(t, "heavy_weighted_pressure_plate", GOLD);
        // The trapdoor pair (WOOD/IRON materials) + the iron door states.
        row(t, "trapdoor", WOOD);
        row(t, "iron_trapdoor", IRON);
        for (String state : new String[] {"east", "west", "south", "north",
                "top", "open", "open_east", "open_west", "open_south",
                "open_north", "open_top", "open_top_east", "open_top_west",
                "open_top_south", "open_top_north", "top_east", "top_west",
                "top_south", "top_north"}) {
            row(t, "trapdoor_" + state, WOOD);
            row(t, "iron_trapdoor_" + state, IRON);
        }
        for (String state : new String[] {"east", "west", "south", "north",
                "upper", "upper_open", "upper_powered", "upper_powered_open",
                "upper_right", "upper_right_open", "upper_right_powered",
                "upper_right_powered_open", "open_east", "open_west",
                "open_south", "open_north"}) {
            row(t, "iron_door_" + state, IRON);
        }
        row(t, "iron_door_open_east", IRON);
        // The tripwire band (DECORATION -> AIR).
        for (String state : new String[] {"", "_attached", "_disarmed",
                "_attached_disarmed", "_powered", "_powered_attached",
                "_powered_disarmed", "_powered_attached_disarmed",
                "_suspended", "_suspended_attached", "_suspended_disarmed",
                "_suspended_attached_disarmed", "_powered_suspended",
                "_powered_suspended_attached", "_powered_suspended_disarmed",
                "_powered_suspended_attached_disarmed"}) {
            row(t, "tripwire" + state, AIR);
        }
        for (String facing : new String[] {"east", "north", "south", "west"}) {
            for (String state : new String[] {"", "_attached", "_powered",
                    "_attached_powered"}) {
                row(t, "tripwire_hook_" + facing + state, AIR);
            }
        }
        // The daylight detectors (WOOD material).
        row(t, "daylight_detector_p0", WOOD);
        row(t, "daylight_detector_inverted_p0", WOOD);
        for (int i = 0; i <= 15; i++) {
            row(t, "daylight_detector_p" + i, WOOD);
            row(t, "daylight_detector_inverted_p" + i, WOOD);
        }
        // The cauldron's water levels.
        for (int level = 1; level <= 3; level++) {
            row(t, "cauldron_level" + level, STONE);
        }
        // The brewing stand's bottle-bit states.
        row(t, "brewing_stand_bottle0", IRON);
        row(t, "brewing_stand_bottle1", IRON);
        row(t, "brewing_stand_bottle2", IRON);
        row(t, "brewing_stand_bottle0_bottle1", IRON);
        row(t, "brewing_stand_bottle0_bottle2", IRON);
        row(t, "brewing_stand_bottle1_bottle2", IRON);
        row(t, "brewing_stand_bottle0_bottle1_bottle2", IRON);
        // The dispenser/dropper facings + triggered bits.
        for (String facing : new String[] {"down", "up", "east", "north",
                "south", "west"}) {
            row(t, "dispenser_" + facing, STONE);
            row(t, "dispenser_" + facing + "_triggered", STONE);
            row(t, "dropper_" + facing, STONE);
            row(t, "dropper_" + facing + "_triggered", STONE);
        }
        // The hopper's facings + disabled bits.
        for (String facing : new String[] {"down", "east", "north", "south",
                "west"}) {
            row(t, "hopper_" + facing, STONE);
            row(t, "hopper_" + facing + "_disabled", STONE);
        }
        return t;
    }

    private static void row(Map<String, Color> table, String name, Color color) {
        table.putIfAbsent(name, color);
    }

    /** One stair family's eight states (the four facings + the top halves). */
    private static void stairs(Map<String, Color> table, String family, Color color) {
        for (String facing : new String[] {"east", "north", "south", "west"}) {
            row(table, family + "_stairs_" + facing, color);
            row(table, family + "_stairs_" + facing + "_top", color);
        }
    }

    /**
     * The terrain scan's consult (the reference's
     * {@code blockstate.getBlock().getMapColor(blockstate)}): the block's
     * table color, AIR for an unknown block (the reference's own answer
     * for anything unregistered — nothing renders on the map).
     */
    public static Color of(BlockType type) {
        if (type == null) {
            return AIR;
        }
        return TABLE.getOrDefault(type.identifier().value(), AIR);
    }

    /**
     * Whether the table carries an explicit row for the block (the
     * coverage consult — the test walks the registry and every block must
     * be known).
     */
    public static boolean known(String blockValue) {
        return TABLE.containsKey(blockValue);
    }
}
