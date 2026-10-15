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

    /** The mined-block stat key ({@code stat.mineBlock.<id>} over the dotted form). */
    public static String mineBlockOf(Identifier block) {
        return "stat.mineBlock." + dotted(merged(block));
    }

    /** The crafted-item stat key ({@code stat.craftItem.<id>}). */
    public static String craftItemOf(Identifier item) {
        return "stat.craftItem." + dotted(merged(item));
    }

    /** The used-item stat key ({@code stat.useItem.<id>}). */
    public static String useItemOf(Identifier item) {
        return "stat.useItem." + dotted(merged(item));
    }

    /** The broken-item stat key ({@code stat.breakItem.<id>}). */
    public static String breakItemOf(Identifier item) {
        return "stat.breakItem." + dotted(merged(item));
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
        // The bare fence (85).
        names.put("oak_fence", "fence");
        // The reed (83's own name) and the portal (90's own name).
        names.put("sugar_cane", "reeds");
        names.put("nether_portal", "portal");
        names.put("nether_portal_z", "portal");
        // The flora's old spellings (31/32/37/38's own names).
        names.put("tall_grass", "tallgrass");
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

    // --- the merge table -----------------------------------------------------

    /**
     * Stats.mergeBlockStats 1:1 — the literal forms count on their family's
     * base member: the flowing fluids on the source form, the lit
     * furnaces/ores/lamps and the powered repeaters/comparators on the
     * plain form, the redstone torch on the unlit form, the grass and the
     * farmland on dirt, the jack o'lantern on the pumpkin, and the double
     * slabs on their single form. The engine's state-identifier families
     * (the fluid levels, the lit furnace's state band) fold onto the same
     * targets through the prefix rules.
     */
    private static final Map<String, String> MERGES = buildMerges();

    private static Map<String, String> buildMerges() {
        Map<String, String> merges = new LinkedHashMap<>();
        // The fluids: every flowing level counts on the source form.
        merges.put("flowing_water", "water");
        merges.put("flowing_lava", "lava");
        // The lit and powered literals.
        merges.put("lit_furnace", "furnace");
        merges.put("lit_redstone_ore", "redstone_ore");
        merges.put("powered_repeater", "repeater");
        merges.put("powered_comparator", "comparator");
        merges.put("redstone_torch", "unlit_redstone_torch");
        merges.put("lit_redstone_lamp", "redstone_lamp");
        merges.put("lit_pumpkin", "pumpkin");
        merges.put("grass_block", "dirt");
        merges.put("farmland", "dirt");
        merges.put("double_stone_slab", "stone_slab");
        merges.put("double_wooden_slab", "wooden_slab");
        // The double wooden slabs' per-wood forms (Slice 58 — the vanilla
        // per-block-id scheme: every 125 state counts on the 126 row).
        merges.put("double_spruce_slab", "wooden_slab");
        merges.put("double_birch_slab", "wooden_slab");
        merges.put("double_jungle_slab", "wooden_slab");
        merges.put("double_acacia_slab", "wooden_slab");
        merges.put("double_dark_oak_slab", "wooden_slab");
        merges.put("double_stone_slab2", "stone_slab2");
        // The red sand (Slice 52 — the SandBlock family's RED_SAND variant)
        // counts on the sand row exactly like the reference's single sand
        // block id. The TYPE states fold onto the block's base name (the
        // vanilla per-block-id scheme: mining any of 179's states counts on
        // stat.mineBlock.minecraft.red_sandstone), the stair facings onto
        // the stairs' base, the top slab onto the single — the same fold
        // the doubles take.
        merges.put("red_sand", "sand");
        merges.put("chiseled_red_sandstone", "red_sandstone");
        merges.put("smooth_red_sandstone", "red_sandstone");
        merges.put("red_sandstone_stairs_east", "red_sandstone_stairs");
        merges.put("red_sandstone_stairs_west", "red_sandstone_stairs");
        merges.put("red_sandstone_stairs_south", "red_sandstone_stairs");
        merges.put("red_sandstone_stairs_north", "red_sandstone_stairs");
        merges.put("stone_slab2_top", "stone_slab2");
        // The anvil family (Slice 53 — the vanilla per-block-id scheme:
        // mining any of 145's twelve states counts on the anvil row).
        merges.put("anvil_west", "anvil");
        merges.put("anvil_north", "anvil");
        merges.put("anvil_east", "anvil");
        merges.put("anvil_chipped", "anvil");
        merges.put("anvil_chipped_west", "anvil");
        merges.put("anvil_chipped_north", "anvil");
        merges.put("anvil_chipped_east", "anvil");
        merges.put("anvil_damaged", "anvil");
        merges.put("anvil_damaged_west", "anvil");
        merges.put("anvil_damaged_north", "anvil");
        merges.put("anvil_damaged_east", "anvil");
        return merges;
    }

    /** @return the identifier the stats count on (the merge table's own target). */
    private static Identifier merged(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return id;
        }
        String value = id.value();
        String merged = MERGES.get(value);
        if (merged != null) {
            return Identifier.parse("minecraft:" + merged);
        }
        // The engine's state families: the flowing fluids' level suffixes
        // and the lit furnace's state band fold onto their base form.
        if (value.startsWith("flowing_water")) {
            return Identifier.parse("minecraft:water");
        }
        if (value.startsWith("flowing_lava")) {
            return Identifier.parse("minecraft:lava");
        }
        if (value.equals("furnace_lit")) {
            return Identifier.parse("minecraft:furnace");
        }
        return id;
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
}
