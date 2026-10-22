package net.zaminmc.torch.server.stat;

import net.zaminmc.torch.util.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The statistics vocabulary (the stats slice — Stats.java 1:1): every stat
 * key the 1.8.8 client's statistics screen resolves, exactly the
 * reference's own registration rows.
 *
 * <p>The general stats carry their {@code stat.*} keys verbatim; the item
 * and block families compose theirs over the registry identifier with the
 * colon flattened to a dot ({@code stat.mineBlock.minecraft.stone} —
 * Stats.translationKey's own scheme); the entity kill pair composes over
 * the reference's Entities key band ({@code stat.killEntity.Zombie} — the
 * old capitalized names). The reference's mergeBlockStats rows keep the
 * literal forms' stats on the family's base member: the flowing fluids,
 * the lit variants and the double slabs all count on their plain form's
 * key, so a client never sees two counters for one material.</p>
 */
public final class EngineStats {

    private EngineStats() {
    }

    // --- the general band (Stats.java's own rows, in registration order) ---

    public static final String GAMES_LEFT = "stat.leaveGame";
    public static final String MINUTES_PLAYED = "stat.playOneMinute";
    public static final String TIME_SINCE_DEATH = "stat.timeSinceDeath";
    public static final String CM_WALKED = "stat.walkOneCm";
    public static final String CROUCH_ONE_CM = "stat.crouchOneCm";
    public static final String SPRINT_ONE_CM = "stat.sprintOneCm";
    public static final String CM_SWUM = "stat.swimOneCm";
    public static final String CM_FALLEN = "stat.fallOneCm";
    public static final String CM_CLIMB = "stat.climbOneCm";
    public static final String CM_FLOWN = "stat.flyOneCm";
    public static final String CM_DIVEN = "stat.diveOneCm";
    public static final String CM_MINECART = "stat.minecartOneCm";
    public static final String CM_SAILED = "stat.boatOneCm";
    public static final String CM_PIG = "stat.pigOneCm";
    public static final String CM_HORSE = "stat.horseOneCm";
    public static final String JUMPS = "stat.jump";
    public static final String DROPS = "stat.drop";
    public static final String DAMAGE_DEALT = "stat.damageDealt";
    public static final String DAMAGE_TAKEN = "stat.damageTaken";
    public static final String DEATHS = "stat.deaths";
    public static final String MOBS_KILLED = "stat.mobKills";
    public static final String ANIMALS_BRED = "stat.animalsBred";
    public static final String PLAYERS_KILLED = "stat.playerKills";
    public static final String FISH_CAUGHT = "stat.fishCaught";
    public static final String JUNK_FISHED = "stat.junkFished";
    public static final String TREASURE_FISHED = "stat.treasureFished";
    public static final String TALKED_TO_VILLAGER = "stat.talkedToVillager";
    public static final String TRADED_WITH_VILLAGER = "stat.tradedWithVillager";
    public static final String CAKE_SLICES_EATEN = "stat.cakeSlicesEaten";
    public static final String CAULDRONS_FILLED = "stat.cauldronFilled";
    public static final String CAULDRONS_USED = "stat.cauldronUsed";
    public static final String ARMOR_CLEANED = "stat.armorCleaned";
    public static final String BANNER_CLEANED = "stat.bannerCleaned";
    public static final String BREWING_STAND_INTERACTIONS = "stat.brewingstandInteraction";
    public static final String BEACON_INTERACTIONS = "stat.beaconInteraction";
    public static final String DROPPERS_INSPECTED = "stat.dropperInspected";
    public static final String HOPPERS_INSPECTED = "stat.hopperInspected";
    public static final String DISPENSERS_INSPECTED = "stat.dispenserInspected";
    public static final String NOTE_BLOCKS_PLAYED = "stat.noteblockPlayed";
    public static final String NOTE_BLOCKS_TUNED = "stat.noteblockTuned";
    public static final String FLOWERS_POTTED = "stat.flowerPotted";
    public static final String TRAPPED_CHESTS_TRIGGERED = "stat.trappedChestTriggered";
    public static final String ENDER_CHESTS_OPENED = "stat.enderchestOpened";
    public static final String ITEMS_ENCHANTED = "stat.itemEnchanted";
    public static final String RECORDS_PLAYED = "stat.recordPlayed";
    public static final String FURNACE_INTERACTIONS = "stat.furnaceInteraction";
    public static final String CRAFTING_TABLE_INTERACTIONS = "stat.workbenchInteraction";
    public static final String CHESTS_OPENED = "stat.chestOpened";

    /** The general band in the reference's registration order. */
    public static final List<String> GENERAL = List.of(
            GAMES_LEFT, MINUTES_PLAYED, TIME_SINCE_DEATH, CM_WALKED, CROUCH_ONE_CM,
            SPRINT_ONE_CM, CM_SWUM, CM_FALLEN, CM_CLIMB, CM_FLOWN, CM_DIVEN,
            CM_MINECART, CM_SAILED, CM_PIG, CM_HORSE, JUMPS, DROPS,
            DAMAGE_DEALT, DAMAGE_TAKEN, DEATHS, MOBS_KILLED, ANIMALS_BRED,
            PLAYERS_KILLED, FISH_CAUGHT, JUNK_FISHED, TREASURE_FISHED,
            TALKED_TO_VILLAGER, TRADED_WITH_VILLAGER, CAKE_SLICES_EATEN,
            CAULDRONS_FILLED, CAULDRONS_USED, ARMOR_CLEANED, BANNER_CLEANED,
            BREWING_STAND_INTERACTIONS, BEACON_INTERACTIONS, DROPPERS_INSPECTED,
            HOPPERS_INSPECTED, DISPENSERS_INSPECTED, NOTE_BLOCKS_PLAYED,
            NOTE_BLOCKS_TUNED, FLOWERS_POTTED, TRAPPED_CHESTS_TRIGGERED,
            ENDER_CHESTS_OPENED, ITEMS_ENCHANTED, RECORDS_PLAYED,
            FURNACE_INTERACTIONS, CRAFTING_TABLE_INTERACTIONS, CHESTS_OPENED);

    // --- the item and block families ---------------------------------------

    /**
     * The mined-block stat key (Slice L4 — StatList.initMineStats' own
     * scheme: one row per registry Block, keyed by the 1.8.8 registry
     * name). The engine's state families fold onto their registry
     * Block's row through the strip consult; the name divergences ride
     * the block-name table (never the item table — the melon block 103
     * and the melon item 362 share an id and resolve apart).
     */
    public static String mineBlockOf(Identifier block) {
        return "stat.mineBlock." + dottedBlock(blockMerged(block));
    }

    /** The crafted-item stat key ({@code stat.craftItem.<id>}). */
    public static String craftItemOf(Identifier item) {
        return "stat.craftItem." + dotted(item);
    }

    /** The used-item stat key ({@code stat.useItem.<id>}). */
    public static String useItemOf(Identifier item) {
        return "stat.useItem." + dotted(item);
    }

    /** The broken-item stat key ({@code stat.breakItem.<id>}). */
    public static String breakItemOf(Identifier item) {
        return "stat.breakItem." + dotted(item);
    }

    // --- the entity kill pair ------------------------------------------------

    /** The kill stat key ({@code stat.killEntity.<EntityKey>}). */
    public static String killEntityOf(String entityKey) {
        return "stat.killEntity." + entityKey;
    }

    /** The killed-by stat key ({@code stat.entityKilledBy.<EntityKey>}). */
    public static String killedByEntityOf(String entityKey) {
        return "stat.entityKilledBy." + entityKey;
    }

    // --- the vanilla name band -------------------------------------------------

    /**
     * The engine identifiers whose names differ from the 1.8.8 registry's
     * own (the modern dataset names the engine registered — the same
     * divergences LegacyBlockIds documents at the wire boundary): the stat
     * keys must carry the registry names the 1.8.8 client resolves, so the
     * families fold here — the planks onto {@code planks}, the classic
     * woods' logs/leaves onto {@code log}/{@code leaves} with the 1.7 pair
     * on {@code log2}/{@code leaves2}, the grass, the oak door's
     * {@code wooden_door}, the fence's bare {@code fence}, the reed's own
     * {@code reeds}, the portal, the flora's old spellings, the crop and
     * stem stages onto their base forms, and the dye families' colored
     * members onto their single-registry ids.
     */
    private static final Map<String, String> VANILLA_NAMES = buildVanillaNames();

    private static Map<String, String> buildVanillaNames() {
        Map<String, String> names = new LinkedHashMap<>();
        // The planks: one registry id (5) with the wood damage.
        for (String wood : new String[] {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            names.put(wood + "_planks", "planks");
            names.put(wood + "_sapling", "sapling");
        }
        // The logs and leaves: 17/18 for the classic woods, 162/161 for the pair.
        for (String wood : new String[] {"oak", "spruce", "birch", "jungle"}) {
            names.put(wood + "_log", "log");
            names.put(wood + "_leaves", "leaves");
        }
        names.put("acacia_log", "log2");
        names.put("dark_oak_log", "log2");
        names.put("acacia_leaves", "leaves2");
        names.put("dark_oak_leaves", "leaves2");
        // The grass (the registry's own name) and the oak door (64 is
        // "wooden_door" — the 1.7 woods keep their own ids).
        names.put("grass_block", "grass");
        names.put("oak_door", "wooden_door");
        // The bare fence (85), the oak gate (107), the oak slab (126),
        // and the 44-variant slabs (the sandstone/cobblestone/brick
        // riders on the stone_slab item's damage band).
        names.put("oak_fence", "fence");
        names.put("oak_fence_gate", "fence_gate");
        names.put("oak_slab", "wooden_slab");
        names.put("sandstone_slab", "stone_slab");
        names.put("cobblestone_slab", "stone_slab");
        // The stone-brick/nether-brick block items (98 is "stonebrick",
        // 112 is "nether_brick"), the cobblestone stairs item (67 is
        // "stone_stairs"), and the slime block's item (165 is "slime").
        names.put("stone_bricks", "stonebrick");
        names.put("nether_bricks", "nether_brick");
        names.put("cobblestone_stairs", "stone_stairs");
        names.put("slime_block", "slime");
        // The pure items whose registry names diverge: the lapis and the
        // bone meal (351 is "dye" — the damage band), the glistering
        // melon (382 is "speckled_melon"), the empty map (395 is "map" —
        // 358 alone is the filled form).
        names.put("lapis", "dye");
        names.put("bone_meal", "dye");
        names.put("glistering_melon", "speckled_melon");
        names.put("empty_map", "map");
        // The reed (83's own name) and the portal (90's own name).
        names.put("sugar_cane", "reeds");
        names.put("nether_portal", "portal");
        names.put("nether_portal_z", "portal");
        // The flora's old spellings (31/32/37/38's own names).
        names.put("tall_grass", "tallgrass");
        // The fern (31's variant 2 — the VIII14 decorators slice's row):
        // the family's own id keeps the tallgrass row.
        names.put("fern", "tallgrass");
        // The dirt variants (3's damage band — the VIII14 surface slice's
        // register-only pair): the family's own id keeps the dirt row.
        names.put("coarse_dirt", "dirt");
        names.put("podzol", "dirt");
        names.put("dead_bush", "deadbush");
        names.put("dandelion", "yellow_flower");
        names.put("poppy", "red_flower");
        // The crop and stem stages onto their base forms.
        for (int stage = 0; stage <= 7; stage++) {
            names.put("wheat_stage" + stage, "wheat");
            names.put("pumpkin_stem_stage" + stage, "pumpkin_stem");
            names.put("melon_stem_stage" + stage, "melon_stem");
        }
        for (int stage = 0; stage <= 3; stage++) {
            names.put("nether_wart_stage" + stage, "nether_wart");
        }
        // The red flower's variant states onto the family id (38).
        for (String variant : new String[] {"blue_orchid", "allium", "houstonia",
                "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"}) {
            names.put(variant, "red_flower");
        }
        // The double-plant variants onto 175's own id.
        for (String variant : new String[] {"sunflower", "lilac", "double_grass",
                "double_fern", "rose", "peony"}) {
            names.put(variant, "double_plant");
            names.put(variant + "_upper", "double_plant");
        }
        // The dye families' colored members onto their single-registry ids.
        for (String color : new String[] {"orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red",
                "black"}) {
            names.put(color + "_wool", "wool");
            names.put("carpet_" + color, "carpet");
            names.put("stained_clay_" + color, "stained_hardened_clay");
            names.put("stained_glass_" + color, "stained_glass");
            names.put("stained_glass_pane_" + color, "stained_glass_pane");
        }
        return names;
    }

    // --- the block registry oracle (Slice L4) -------------------------------

    /**
     * The 1.8.8 block registry's own names (reference Block.java's
     * registrations, ids 1-197 in order — id 0 is air, unregistered):
     * the mine-stat rows are keyed by these names alone
     * (StatList.initMineStats walks Block.REGISTRY, one row per Block —
     * the states never carry their own rows). Every engine block's
     * mine key must resolve into this set; the completeness test walks
     * the whole registry and asserts it.
     */
    private static final List<String> BLOCK_REGISTRY = List.of(
            "stone", "grass", "dirt", "cobblestone", "planks", "sapling",
            "bedrock", "flowing_water", "water", "flowing_lava", "lava", "sand",
            "gravel", "gold_ore", "iron_ore", "coal_ore", "log", "leaves",
            "sponge", "glass", "lapis_ore", "lapis_block", "dispenser", "sandstone",
            "noteblock", "bed", "golden_rail", "detector_rail", "sticky_piston", "web",
            "tallgrass", "deadbush", "piston", "piston_head", "wool", "piston_extension",
            "yellow_flower", "red_flower", "brown_mushroom", "red_mushroom", "gold_block", "iron_block",
            "double_stone_slab", "stone_slab", "brick_block", "tnt", "bookshelf", "mossy_cobblestone",
            "obsidian", "torch", "fire", "mob_spawner", "oak_stairs", "chest",
            "redstone_wire", "diamond_ore", "diamond_block", "crafting_table", "wheat", "farmland",
            "furnace", "lit_furnace", "standing_sign", "wooden_door", "ladder", "rail",
            "stone_stairs", "wall_sign", "lever", "stone_pressure_plate", "iron_door", "wooden_pressure_plate",
            "redstone_ore", "lit_redstone_ore", "unlit_redstone_torch", "redstone_torch", "stone_button", "snow_layer",
            "ice", "snow", "cactus", "clay", "reeds", "jukebox",
            "fence", "pumpkin", "netherrack", "soul_sand", "glowstone", "portal",
            "lit_pumpkin", "cake", "unpowered_repeater", "powered_repeater", "stained_glass", "trapdoor",
            "monster_egg", "stonebrick", "brown_mushroom_block", "red_mushroom_block", "iron_bars", "glass_pane",
            "melon_block", "pumpkin_stem", "melon_stem", "vine", "fence_gate", "brick_stairs",
            "stone_brick_stairs", "mycelium", "waterlily", "nether_brick", "nether_brick_fence", "nether_brick_stairs",
            "nether_wart", "enchanting_table", "brewing_stand", "cauldron", "end_portal", "end_portal_frame",
            "end_stone", "dragon_egg", "redstone_lamp", "lit_redstone_lamp", "double_wooden_slab", "wooden_slab",
            "cocoa", "sandstone_stairs", "emerald_ore", "ender_chest", "tripwire_hook", "tripwire",
            "emerald_block", "spruce_stairs", "birch_stairs", "jungle_stairs", "command_block", "beacon",
            "cobblestone_wall", "flower_pot", "carrots", "potatoes", "wooden_button", "skull",
            "anvil", "trapped_chest", "light_weighted_pressure_plate", "heavy_weighted_pressure_plate", "unpowered_comparator", "powered_comparator",
            "daylight_detector", "redstone_block", "quartz_ore", "hopper", "quartz_block", "quartz_stairs",
            "activator_rail", "dropper", "stained_hardened_clay", "stained_glass_pane", "leaves2", "log2",
            "acacia_stairs", "dark_oak_stairs", "slime", "barrier", "iron_trapdoor", "prismarine",
            "sea_lantern", "hay_block", "carpet", "hardened_clay", "coal_block", "packed_ice",
            "double_plant", "standing_banner", "wall_banner", "daylight_detector_inverted", "red_sandstone", "red_sandstone_stairs",
            "double_stone_slab2", "stone_slab2", "spruce_fence_gate", "birch_fence_gate", "jungle_fence_gate", "dark_oak_fence_gate",
            "acacia_fence_gate", "spruce_fence", "birch_fence", "jungle_fence", "dark_oak_fence", "acacia_fence",
            "spruce_door", "birch_door", "jungle_door", "acacia_door", "dark_oak_door"
    );

    /** The registry names as the O(1) oracle (the list keeps the id order). */
    private static final java.util.Set<String> BLOCK_REGISTRY_SET =
            new java.util.LinkedHashSet<>(BLOCK_REGISTRY);

    /** @return the 1.8.8 registry's own names in id order (the oracle). */
    static List<String> blockRegistryNames() {
        return BLOCK_REGISTRY;
    }

    /**
     * The 1.8.8 item registry's own PURE items (reference Item.java's
     * registrations, ids 256-2267 — the block items below 256 share the
     * BLOCK registry's names, so the item rows resolve over the UNION:
     * every useItem/craftItem/breakItem key must land in it).
     */
    private static final List<String> ITEM_REGISTRY = List.of(
            "iron_shovel", "iron_pickaxe", "iron_axe", "flint_and_steel", "apple", "bow",
            "arrow", "coal", "diamond", "iron_ingot", "gold_ingot", "iron_sword",
            "wooden_sword", "wooden_shovel", "wooden_pickaxe", "wooden_axe", "stone_sword", "stone_shovel",
            "stone_pickaxe", "stone_axe", "diamond_sword", "diamond_shovel", "diamond_pickaxe", "diamond_axe",
            "stick", "bowl", "mushroom_stew", "golden_sword", "golden_shovel", "golden_pickaxe",
            "golden_axe", "string", "feather", "gunpowder", "wooden_hoe", "stone_hoe",
            "iron_hoe", "diamond_hoe", "golden_hoe", "wheat_seeds", "wheat", "bread",
            "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "chainmail_helmet", "chainmail_chestplate",
            "chainmail_leggings", "chainmail_boots", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots",
            "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots", "golden_helmet", "golden_chestplate",
            "golden_leggings", "golden_boots", "flint", "porkchop", "cooked_porkchop", "painting",
            "golden_apple", "sign", "wooden_door", "bucket", "water_bucket", "lava_bucket",
            "minecart", "saddle", "iron_door", "redstone", "snowball", "boat",
            "leather", "milk_bucket", "brick", "clay_ball", "reeds", "paper",
            "book", "slime_ball", "chest_minecart", "furnace_minecart", "egg", "compass",
            "fishing_rod", "clock", "glowstone_dust", "fish", "cooked_fish", "dye",
            "bone", "sugar", "cake", "bed", "repeater", "cookie",
            "filled_map", "shears", "melon", "pumpkin_seeds", "melon_seeds", "beef",
            "cooked_beef", "chicken", "cooked_chicken", "rotten_flesh", "ender_pearl", "blaze_rod",
            "ghast_tear", "gold_nugget", "nether_wart", "potion", "glass_bottle", "spider_eye",
            "fermented_spider_eye", "blaze_powder", "magma_cream", "brewing_stand", "cauldron", "ender_eye",
            "speckled_melon", "spawn_egg", "experience_bottle", "fire_charge", "writable_book", "written_book",
            "emerald", "item_frame", "flower_pot", "carrot", "potato", "baked_potato",
            "poisonous_potato", "map", "golden_carrot", "skull", "carrot_on_a_stick", "nether_star",
            "pumpkin_pie", "fireworks", "firework_charge", "enchanted_book", "comparator", "netherbrick",
            "quartz", "tnt_minecart", "hopper_minecart", "prismarine_shard", "prismarine_crystals", "rabbit",
            "cooked_rabbit", "rabbit_stew", "rabbit_foot", "rabbit_hide", "armor_stand", "iron_horse_armor",
            "golden_horse_armor", "diamond_horse_armor", "lead", "name_tag", "command_block_minecart", "mutton",
            "cooked_mutton", "banner", "spruce_door", "birch_door", "jungle_door", "acacia_door",
            "dark_oak_door", "record_13", "record_cat", "record_blocks", "record_chirp", "record_far",
            "record_mall", "record_mellohi", "record_stal", "record_strad", "record_ward", "record_11",
            "record_wait"
    );

    /** The item rows: the pure items over the union with the block names. */
    private static final java.util.Set<String> ITEM_ROW_SET = buildItemRowSet();

    private static java.util.Set<String> buildItemRowSet() {
        java.util.Set<String> rows = new java.util.LinkedHashSet<>(ITEM_REGISTRY);
        rows.addAll(BLOCK_REGISTRY);
        return rows;
    }

    /** @return the 1.8.8 item rows (the pure items over the block union). */
    static java.util.Set<String> itemRowSet() {
        return ITEM_ROW_SET;
    }

    /**
     * The block-name divergences (Slice L4): the engine's block ids
     * whose 1.8.8 registry names differ, plus the PREFIX families —
     * the variant members whose family base is not a suffix strip
     * (blue_orchid onto red_flower, infested_stone onto monster_egg,
     * the dye bands). This table is the MINE path's own — the item
     * path keeps VANILLA_NAMES, because the registries diverge apart
     * (the melon block is melon_block, the melon item stays melon).
     */
    private static final Map<String, String> BLOCK_NAMES = buildBlockNames();

    private static Map<String, String> buildBlockNames() {
        Map<String, String> names = new LinkedHashMap<>();
        // The planks: one registry id (5) with the wood damage.
        for (String wood : new String[] {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            names.put(wood + "_planks", "planks");
            names.put(wood + "_sapling", "sapling");
        }
        // The logs and leaves: 17/18 for the classic woods, 162/161 for the pair.
        for (String wood : new String[] {"oak", "spruce", "birch", "jungle"}) {
            names.put(wood + "_log", "log");
            names.put(wood + "_leaves", "leaves");
        }
        names.put("acacia_log", "log2");
        names.put("dark_oak_log", "log2");
        names.put("acacia_leaves", "leaves2");
        names.put("dark_oak_leaves", "leaves2");
        // The grass (the registry's own name), the oak door (64 is
        // "wooden_door" — the 1.7 woods keep their own ids), the oak
        // fence (85), the oak slab (158) and the oak gate (107).
        names.put("grass_block", "grass");
        names.put("oak_door", "wooden_door");
        names.put("oak_fence", "fence");
        names.put("oak_slab", "wooden_slab");
        names.put("oak_fence_gate", "fence_gate");
        // The reed (83's own name) and the portal (90's own name).
        names.put("sugar_cane", "reeds");
        names.put("nether_portal", "portal");
        names.put("nether_portal_z", "portal");
        // The flora's old spellings (31/32/37/38's own names).
        names.put("tall_grass", "tallgrass");
        // The fern (31's variant 2 — the VIII14 decorators slice's row):
        // the family's own id keeps the tallgrass row.
        names.put("fern", "tallgrass");
        // The dirt variants (3's damage band — the VIII14 surface slice's
        // register-only pair): the family's own id keeps the dirt row.
        names.put("coarse_dirt", "dirt");
        names.put("podzol", "dirt");
        names.put("dead_bush", "deadbush");
        names.put("dandelion", "yellow_flower");
        names.put("poppy", "red_flower");
        // The red flower's variant states onto the family id (38).
        for (String variant : new String[] {"blue_orchid", "allium", "houstonia",
                "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"}) {
            names.put(variant, "red_flower");
        }
        // The five 1.7 woods' wooden slabs (126/125 are the registry's
        // own wooden ids — the woods ride the variant nibble; the tops
        // fold through the strip onto these entries).
        for (String wood : new String[] {"spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            names.put(wood + "_slab", "wooden_slab");
            names.put("double_" + wood + "_slab", "double_wooden_slab");
        }
        // The red sand (12 is "sand" with the damage-1 variant) and the
        // red sandstone's chiseled member (179's variant nibble).
        names.put("red_sand", "sand");
        names.put("chiseled_red_sandstone", "red_sandstone");
        names.put("smooth_red_sandstone", "red_sandstone");
        // The double-plant variants onto 175's own id (the uppers ride
        // the strip: sunflower_upper -> sunflower -> double_plant).
        for (String variant : new String[] {"sunflower", "lilac", "double_grass",
                "double_fern", "rose", "peony"}) {
            names.put(variant, "double_plant");
        }
        // The dye families' colored members onto their single-registry ids.
        for (String color : new String[] {"white", "orange", "magenta", "light_blue", "yellow",
                "lime", "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green",
                "red", "black"}) {
            names.put(color + "_wool", "wool");
            names.put("carpet_" + color, "carpet");
            names.put("stained_clay_" + color, "stained_hardened_clay");
            names.put("stained_glass_" + color, "stained_glass");
            names.put("stained_glass_pane_" + color, "stained_glass_pane");
        }
        // The literal divergences (Slice L4 — the registry spellings the
        // engine's modern names never carried): the lit furnace (62 is
        // lit_furnace), the cobweb (30 is web), the melon block (103 is
        // melon_block — the ITEM keeps the melon name), the nether
        // brick block (112 is nether_brick, singular), the quartz block
        // (155 is quartz_block), the repeater/comparator bases (93/149
        // are the unpowered_ forms), the standing sign (63), the wet
        // sponge (19's own item variant — the block row is the sponge's).
        names.put("furnace_lit", "lit_furnace");
        // The stone bricks (98 is "stonebrick", one word — the engine's
        // plural never existed in the registry).
        names.put("stone_bricks", "stonebrick");
        // The cobblestone stairs (67 is "stone_stairs" — THE STRIP'S OWN
        // TRAP: the engine id's cobblestone- prefix folds onto the
        // cobblestone row, the WRONG family; the explicit entry keeps the
        // stairs states on the stone_stairs row).
        names.put("cobblestone_stairs", "stone_stairs");
        // The 44-family slab variants (the sandstone/cobblestone/stone-
        // brick riders on the stone_slab item's damage band — THE STRIP'S
        // TRAP again: each would fold onto its prefix block, the wrong
        // family; the explicit entries keep the band on stone_slab).
        names.put("sandstone_slab", "stone_slab");
        names.put("cobblestone_slab", "stone_slab");
        names.put("stone_brick_slab", "stone_slab");
        // The 43-family variant doubles + the seamless skins (Slice L5 —
        // 43's VARIANT nibble and the SEAMLESS bit ride the double's own
        // registry row; 181's skin likewise).
        names.put("double_sandstone_slab", "double_stone_slab");
        names.put("double_cobblestone_slab", "double_stone_slab");
        names.put("double_stone_brick_slab", "double_stone_slab");
        names.put("double_stone_slab_seamless", "double_stone_slab");
        names.put("double_stone_slab2_seamless", "double_stone_slab2");
        // The slime block (165 is "slime" — the engine's modern name).
        names.put("slime_block", "slime");
        names.put("cobweb", "web");
        names.put("melon", "melon_block");
        names.put("nether_bricks", "nether_brick");
        names.put("quartz", "quartz_block");
        names.put("repeater", "unpowered_repeater");
        names.put("comparator", "unpowered_comparator");
        names.put("sign", "standing_sign");
        names.put("wet_sponge", "sponge");
        // The sandstone and stone-brick variant members (the registry's
        // single ids with the variant nibble — prefix families, never
        // suffix strips).
        names.put("chiseled_sandstone", "sandstone");
        names.put("smooth_sandstone", "sandstone");
        names.put("chiseled_stone_bricks", "stonebrick");
        names.put("cracked_stone_bricks", "stonebrick");
        names.put("mossy_stone_bricks", "stonebrick");
        // The infested family (97 is monster_egg — the variant nibble).
        for (String variant : new String[] {"stone", "cobblestone", "stone_bricks",
                "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks"}) {
            names.put("infested_" + variant, "monster_egg");
        }
        return names;
    }

    /**
     * The mine path's fold (Slice L4 — the pure state fold): the
     * engine's state families fold onto their registry Block's row by
     * stripping the trailing variant/facing/half segments until a
     * resolvable base lands (oak_stairs_east_top onto oak_stairs,
     * furnace_lit_north onto furnace_lit, wheat_stage3 onto wheat).
     * The literal merges of the old scheme are GONE — vanilla keeps
     * its own rows for the grass, the farmland, the doubles, the lit
     * and powered forms, and the flowing fluids (registry 8/10 exist).
     */
    private static Identifier blockMerged(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return id;
        }
        String value = id.value();
        if (blockRowOf(value) != null) {
            return id; // the base resolves directly — never stripped
        }
        String[] parts = value.split("_");
        for (int k = parts.length - 1; k >= 1; k--) {
            String candidate = String.join("_",
                    java.util.Arrays.asList(parts).subList(0, k));
            if (blockRowOf(candidate) != null) {
                return Identifier.parse("minecraft:" + candidate);
            }
        }
        return id; // unresolvable — the completeness test's own catch
    }

    /**
     * @return the registry row a block name counts on (the block-name
     * table's divergence first, else the registry's own name), or null
     * when neither resolves.
     */
    static String blockRowOf(String engineName) {
        String mapped = BLOCK_NAMES.get(engineName);
        if (mapped != null) {
            return mapped;
        }
        if (engineName.startsWith("potted_")) {
            // The flower-pot family (140 is "flower_pot" — every potted
            // member rides the metadata band, the prefix rule).
            return "flower_pot";
        }
        return BLOCK_REGISTRY_SET.contains(engineName) ? engineName : null;
    }

    /**
     * The mine path's dotted form: the registry row over the block-name
     * consult (VANILLA_NAMES never runs here — the item divergences
     * would corrupt the block rows).
     */
    private static String dottedBlock(Identifier id) {
        String value = id.value();
        String row = blockRowOf(value);
        return "minecraft." + (row != null ? row : value);
    }

    /**
     * Stats.translationKey 1:1 — the registry identifier with the colon
     * flattened, the engine's modern names carried onto the 1.8.8
     * registry's own through {@link #VANILLA_NAMES}.
     */
    private static String dotted(Identifier id) {
        if (id.namespace().equals("minecraft")) {
            String vanilla = VANILLA_NAMES.get(id.value());
            if (vanilla != null) {
                return "minecraft." + vanilla;
            }
        }
        return id.toString().replace(':', '.');
    }

    // --- the entity key band ---------------------------------------------------

    /**
     * The reference's Entities key band (the old capitalized names) over
     * the engine's mob types — the keys the kill pair composes with
     * ({@code stat.killEntity.PigZombie}, the zombie pigman's own quirky
     * key; the ocelot's "Ozelot", the mooshroom's "MushroomCow", the
     * horse's "EntityHorse"). The iron and snow golems carry no kill pair
     * at all — the reference registers the pair only for the spawn-egg
     * band.
     */
    private static final Map<String, String> ENTITY_KEYS = buildEntityKeys();

    private static Map<String, String> buildEntityKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("PIG", "Pig");
        keys.put("COW", "Cow");
        keys.put("CHICKEN", "Chicken");
        keys.put("SHEEP", "Sheep");
        keys.put("RABBIT", "Rabbit");
        keys.put("WOLF", "Wolf");
        keys.put("OCELOT", "Ozelot");
        keys.put("ZOMBIE", "Zombie");
        keys.put("SKELETON", "Skeleton");
        keys.put("CREEPER", "Creeper");
        keys.put("SPIDER", "Spider");
        keys.put("HORSE", "EntityHorse");
        keys.put("VILLAGER", "Villager");
        keys.put("CAVE_SPIDER", "CaveSpider");
        keys.put("SILVERFISH", "Silverfish");
        keys.put("BLAZE", "Blaze");
        keys.put("ENDERMAN", "Enderman");
        keys.put("WITCH", "Witch");
        keys.put("SLIME", "Slime");
        keys.put("MAGMA_CUBE", "LavaSlime");
        keys.put("GHAST", "Ghast");
        keys.put("ZOMBIE_PIGMAN", "PigZombie");
        keys.put("SQUID", "Squid");
        keys.put("BAT", "Bat");
        keys.put("GUARDIAN", "Guardian");
        keys.put("MOOSHROOM", "MushroomCow");
        keys.put("ENDERMITE", "Endermite");
        keys.put("WITHER", "Wither");
        return keys;
    }

    /**
     * @return the reference's entity key for a mob type name (the engine's
     * enum name), null when the type carries no kill pair (the golem pair
     * — the reference's spawn-egg-only registration band).
     */
    public static String entityKeyOf(String mobTypeName) {
        return ENTITY_KEYS.get(mobTypeName);
    }

    /**
     * The entity key band itself (the reference's spawn-egg keys, the old
     * capitalized names) — the kill-pair criteria walk and the
     * {@code /achievement} resolution consult it.
     */
    public static java.util.Collection<String> entityKeyBand() {
        return java.util.Collections.unmodifiableCollection(ENTITY_KEYS.values());
    }
}
