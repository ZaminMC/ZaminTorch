package net.zaminmc.torch.server.item;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemType;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The item types built into the engine. This is data, not code: the set grows as
 * gameplay slices require more items, always with its canonical identifier first.
 *
 * <p>Canonical identifiers use modern names where the concept exists
 * (the engine is not bound to 1.8 names): the 1.8 dataset entries {@code planks}
 * and {@code log} correspond to {@code minecraft:oak_planks} and
 * {@code minecraft:oak_log}; the version adapter owns that translation. Tool
 * names already match the dataset ({@code wooden_pickaxe} ...). Durability
 * limits are the dataset's {@code maxDurability} values.</p>
 */
public final class BuiltinItems {

    public static final EngineItemType DIRT = new EngineItemType(
            Identifier.parse("minecraft:dirt"), "Dirt", 64);
    public static final EngineItemType COBBLESTONE = new EngineItemType(
            Identifier.parse("minecraft:cobblestone"), "Cobblestone", 64);
    public static final EngineItemType OAK_PLANKS = new EngineItemType(
            Identifier.parse("minecraft:oak_planks"), "Oak Planks", 64);
    public static final EngineItemType OAK_LOG = new EngineItemType(
            Identifier.parse("minecraft:oak_log"), "Oak Log", 64);

    // Crafting results (community-data recipes): stick 280, crafting table 58,
    // torch 50 on the legacy wire.
    public static final EngineItemType STICK = new EngineItemType(
            Identifier.parse("minecraft:stick"), "Stick", 64);
    public static final EngineItemType CRAFTING_TABLE = new EngineItemType(
            Identifier.parse("minecraft:crafting_table"), "Crafting Table", 64);
    // The note block's item form (legacy 25, the reference's `noteblock`
    // registration — the block item shares the id).
    public static final EngineItemType NOTE_BLOCK = new EngineItemType(
            Identifier.parse("minecraft:noteblock"), "Note Block", 64);
    // The jukebox's item form (legacy 84 — the reference's Block.java
    // line-1181 registration; the block item shares the id).
    public static final EngineItemType JUKEBOX = new EngineItemType(
            Identifier.parse("minecraft:jukebox"), "Jukebox", 64);
    // The daylight detector's item form (legacy 151 — Item.java line 490's
    // register(Blocks.DAYLIGHT_DETECTOR); the block family's thirty-two
    // flattened power states share the id with the nibble carrying the
    // dial). The inverted form (178) has NO item — the use arm toggles.
    public static final EngineItemType DAYLIGHT_DETECTOR = new EngineItemType(
            Identifier.parse("minecraft:daylight_detector"), "Daylight Sensor", 64);
    // The twelve music discs (the reference's Item.java lines 735-746, ids
    // 2256-2267 in this order): MusicDiscItem's own maxStackSize 1. The
    // record name rides the identifier ("records." + the item's own record
    // field, resolved at the wire).
    public static final EngineItemType RECORD_13 = new EngineItemType(
            Identifier.parse("minecraft:record_13"), "13 Disc", 1);
    public static final EngineItemType RECORD_CAT = new EngineItemType(
            Identifier.parse("minecraft:record_cat"), "Cat Disc", 1);
    public static final EngineItemType RECORD_BLOCKS = new EngineItemType(
            Identifier.parse("minecraft:record_blocks"), "Blocks Disc", 1);
    public static final EngineItemType RECORD_CHIRP = new EngineItemType(
            Identifier.parse("minecraft:record_chirp"), "Chirp Disc", 1);
    public static final EngineItemType RECORD_FAR = new EngineItemType(
            Identifier.parse("minecraft:record_far"), "Far Disc", 1);
    public static final EngineItemType RECORD_MALL = new EngineItemType(
            Identifier.parse("minecraft:record_mall"), "Mall Disc", 1);
    public static final EngineItemType RECORD_MELLOHI = new EngineItemType(
            Identifier.parse("minecraft:record_mellohi"), "Mellohi Disc", 1);
    public static final EngineItemType RECORD_STAL = new EngineItemType(
            Identifier.parse("minecraft:record_stal"), "Stal Disc", 1);
    public static final EngineItemType RECORD_STRAD = new EngineItemType(
            Identifier.parse("minecraft:record_strad"), "Strad Disc", 1);
    public static final EngineItemType RECORD_WARD = new EngineItemType(
            Identifier.parse("minecraft:record_ward"), "Ward Disc", 1);
    public static final EngineItemType RECORD_11 = new EngineItemType(
            Identifier.parse("minecraft:record_11"), "11 Disc", 1);
    public static final EngineItemType RECORD_WAIT = new EngineItemType(
            Identifier.parse("minecraft:record_wait"), "Wait Disc", 1);
    public static final EngineItemType TORCH = new EngineItemType(
            Identifier.parse("minecraft:torch"), "Torch", 64);
    // Craftable blocks unlocked by the 3x3 crafting table (legacy 61 / 54).
    public static final EngineItemType FURNACE = new EngineItemType(
            Identifier.parse("minecraft:furnace"), "Furnace", 64);
    public static final EngineItemType CHEST = new EngineItemType(
            Identifier.parse("minecraft:chest"), "Chest", 64);

    // Materials yielded by tool-gated mining (legacy ids: coal 263, diamond 264).
    public static final EngineItemType COAL = new EngineItemType(
            Identifier.parse("minecraft:coal"), "Coal", 64);
    public static final EngineItemType DIAMOND = new EngineItemType(
            Identifier.parse("minecraft:diamond"), "Diamond", 64);
    // Smelted materials (furnace slice): iron ingot unlocks the iron tool tier.
    public static final EngineItemType IRON_INGOT = new EngineItemType(
            Identifier.parse("minecraft:iron_ingot"), "Iron Ingot", 64);
    // Gold ingot (community items.json legacy 266): the gold ore smelt.
    public static final EngineItemType GOLD_INGOT = new EngineItemType(
            Identifier.parse("minecraft:gold_ingot"), "Gold Ingot", 64);
    // Fire starter (community items.json legacy 259, durability 64):
    // ignites fire against a clicked face.
    public static final EngineItemType FLINT_AND_STEEL = new EngineItemType(
            Identifier.parse("minecraft:flint_and_steel"), "Flint and Steel", 1, 64);

    // World-completion items (community items.json): sugar cane 338 (plants
    // the reed), cactus 81 (places the cactus block), sugar 353, paper 339,
    // book 340 (the crafting chain off the cane).
    public static final EngineItemType SUGAR_CANE = new EngineItemType(
            Identifier.parse("minecraft:sugar_cane"), "Sugar Cane", 64);
    public static final EngineItemType CACTUS = new EngineItemType(
            Identifier.parse("minecraft:cactus"), "Cactus", 64);
    public static final EngineItemType SUGAR = new EngineItemType(
            Identifier.parse("minecraft:sugar"), "Sugar", 64);
    public static final EngineItemType PAPER = new EngineItemType(
            Identifier.parse("minecraft:paper"), "Paper", 64);
    public static final EngineItemType BOOK = new EngineItemType(
            Identifier.parse("minecraft:book"), "Book", 64);
    // Smelting result of cobblestone; places the stone block like any block item.
    public static final EngineItemType STONE = new EngineItemType(
            Identifier.parse("minecraft:stone"), "Stone", 64);

    // Variant-metadata items (community items.json: coal damage 1 = charcoal,
    // sand damage 1 = red sand) and the smelting result of sand. Glass places
    // the glass block like any block item (legacy ids: sand 12, glass 20).
    public static final EngineItemType SAND = new EngineItemType(
            Identifier.parse("minecraft:sand"), "Sand", 64);
    public static final EngineItemType GLASS = new EngineItemType(
            Identifier.parse("minecraft:glass"), "Glass", 64);

    // Gravity-block items (legacy ids: gravel 13; flint 318 is the historical
    // 10% gravel roll, not a placeable block).
    public static final EngineItemType GRAVEL = new EngineItemType(
            Identifier.parse("minecraft:gravel"), "Gravel", 64);
    public static final EngineItemType FLINT = new EngineItemType(
            Identifier.parse("minecraft:flint"), "Flint", 64);

    // The nether's yields (community items.json ids): the mushroom pair ride
    // their block ids (39/40), glowstone dust 348 (the cluster's 2-4 roll)
    // and nether quartz 406 (the ore's drop); the netherrack/soul-sand pair
    // drops itself (block-item shares the block id).
    public static final EngineItemType BROWN_MUSHROOM = new EngineItemType(
            Identifier.parse("minecraft:brown_mushroom"), "Brown Mushroom", 64);
    public static final EngineItemType RED_MUSHROOM = new EngineItemType(
            Identifier.parse("minecraft:red_mushroom"), "Red Mushroom", 64);
    public static final EngineItemType GLOWSTONE_DUST = new EngineItemType(
            Identifier.parse("minecraft:glowstone_dust"), "Glowstone Dust", 64);
    public static final EngineItemType QUARTZ = new EngineItemType(
            Identifier.parse("minecraft:quartz"), "Nether Quartz", 64);
    public static final EngineItemType NETHERRACK = new EngineItemType(
            Identifier.parse("minecraft:netherrack"), "Netherrack", 64);
    public static final EngineItemType SOUL_SAND = new EngineItemType(
            Identifier.parse("minecraft:soul_sand"), "Soul Sand", 64);

    // Edible items (community foods.json): raw beef smelts into steak.
    public static final EngineItemType BEEF = new EngineItemType(
            Identifier.parse("minecraft:beef"), "Beef", 64);
    public static final EngineItemType COOKED_BEEF = new EngineItemType(
            Identifier.parse("minecraft:cooked_beef"), "Steak", 64);

    // The farming slice (community items.json legacy ids: seeds 295,
    // wheat 296, bread 297, bone meal 351; the melon/pumpkin seed pair
    // 292/294 lands with the mineshaft's chest-minecart loot, Slice 21a).
    // Bread is the iconic crop food; bone meal is the instant-grow dust
    // (dye damage 15 on the wire).
    public static final EngineItemType WHEAT_SEEDS = new EngineItemType(
            Identifier.parse("minecraft:wheat_seeds"), "Wheat Seeds", 64);
    public static final EngineItemType MELON_SEEDS = new EngineItemType(
            Identifier.parse("minecraft:melon_seeds"), "Melon Seeds", 64);
    public static final EngineItemType PUMPKIN_SEEDS = new EngineItemType(
            Identifier.parse("minecraft:pumpkin_seeds"), "Pumpkin Seeds", 64);
    public static final EngineItemType WHEAT = new EngineItemType(
            Identifier.parse("minecraft:wheat"), "Wheat", 64);
    public static final EngineItemType BREAD = new EngineItemType(
            Identifier.parse("minecraft:bread"), "Bread", 64);

    // The breeding foods (the animal husbandry slice): the carrot feeds
    // pigs, the apple/golden items feed horses (the golden pair also brings
    // a tamed adult into love); hay is the block item horses heal on.
    public static final EngineItemType CARROT = new EngineItemType(
            Identifier.parse("minecraft:carrot"), "Carrot", 64);
    // The potato family (Slice 12b — the village farm's second crop, the
    // community items.json pair 392/393): the raw potato 1/0.6, the baked
    // pair 5/6.0 (the smelting row in FurnaceRecipes).
    public static final EngineItemType POTATO = new EngineItemType(
            Identifier.parse("minecraft:potato"), "Potato", 64);
    public static final EngineItemType BAKED_POTATO = new EngineItemType(
            Identifier.parse("minecraft:baked_potato"), "Baked Potato", 64);
    public static final EngineItemType APPLE = new EngineItemType(
            Identifier.parse("minecraft:apple"), "Apple", 64);
    public static final EngineItemType GOLDEN_CARROT = new EngineItemType(
            Identifier.parse("minecraft:golden_carrot"), "Golden Carrot", 64);
    public static final EngineItemType GOLDEN_APPLE = new EngineItemType(
            Identifier.parse("minecraft:golden_apple"), "Golden Apple", 64);
    public static final EngineItemType HAY_BLOCK = new EngineItemType(
            Identifier.parse("minecraft:hay_block"), "Hay Bale", 64);

    public static final EngineItemType BONE_MEAL = new EngineItemType(
            Identifier.parse("minecraft:bone_meal"), "Bone Meal", 64);
    // The sign item (community items.json: legacy 323, stack 16) — places
    // the standing sign block; the text rides Update Sign after placement.
    public static final EngineItemType SIGN = new EngineItemType(
            Identifier.parse("minecraft:sign"), "Sign", 16);
    // The building vocabulary (community items.json: oak door 324,
    // ladder 65, oak fence 85). The door item places both halves; the
    // block identifier "minecraft:oak_door" is the lower's closed state.
    public static final EngineItemType OAK_DOOR = new EngineItemType(
            Identifier.parse("minecraft:oak_door"), "Oak Door", 64);
    public static final EngineItemType LADDER = new EngineItemType(
            Identifier.parse("minecraft:ladder"), "Ladder", 64);

    // The redstone slice (community items.json legacy ids: dust 331, the
    // torch shares block id 76, the repeater item 356, the lever 69, the
    // buttons 77/143, the plates 70/72).
    public static final EngineItemType REDSTONE = new EngineItemType(
            Identifier.parse("minecraft:redstone"), "Redstone", 64);
    public static final EngineItemType REDSTONE_TORCH = new EngineItemType(
            Identifier.parse("minecraft:redstone_torch"), "Redstone Torch", 64);
    public static final EngineItemType REPEATER = new EngineItemType(
            Identifier.parse("minecraft:repeater"), "Repeater", 64);
    public static final EngineItemType COMPARATOR = new EngineItemType(
            Identifier.parse("minecraft:comparator"), "Redstone Comparator", 64);
    public static final EngineItemType PISTON = new EngineItemType(
            Identifier.parse("minecraft:piston"), "Piston", 64);
    public static final EngineItemType STICKY_PISTON = new EngineItemType(
            Identifier.parse("minecraft:sticky_piston"), "Sticky Piston", 64);
    public static final EngineItemType TNT = new EngineItemType(
            Identifier.parse("minecraft:tnt"), "Tnt", 64);
    public static final EngineItemType LEVER = new EngineItemType(
            Identifier.parse("minecraft:lever"), "Lever", 64);
    public static final EngineItemType STONE_BUTTON = new EngineItemType(
            Identifier.parse("minecraft:stone_button"), "Stone Button", 64);
    public static final EngineItemType WOODEN_BUTTON = new EngineItemType(
            Identifier.parse("minecraft:wooden_button"), "Wooden Button", 64);
    public static final EngineItemType STONE_PRESSURE_PLATE = new EngineItemType(
            Identifier.parse("minecraft:stone_pressure_plate"), "Stone Pressure Plate", 64);
    public static final EngineItemType WOODEN_PRESSURE_PLATE = new EngineItemType(
            Identifier.parse("minecraft:wooden_pressure_plate"), "Wooden Pressure Plate", 64);
    public static final EngineItemType HOPPER = new EngineItemType(
            Identifier.parse("minecraft:hopper"), "Hopper", 64);
    public static final EngineItemType TRIPWIRE_HOOK = new EngineItemType(
            Identifier.parse("minecraft:tripwire_hook"), "Tripwire Hook", 64);
    // The dispenser family (Slice 11a — community items.json: the block
    // forms share the block ids 23/125, the fire charge is item 385).
    public static final EngineItemType DISPENSER = new EngineItemType(
            Identifier.parse("minecraft:dispenser"), "Dispenser", 64);
    public static final EngineItemType DROPPER = new EngineItemType(
            Identifier.parse("minecraft:dropper"), "Dropper", 64);
    public static final EngineItemType FIRE_CHARGE = new EngineItemType(
            Identifier.parse("minecraft:fire_charge"), "Fire Charge", 64);
    // The potion family (Slice 11b — Item.java lines 659-660: the potion
    // 373 with the effect set riding the damage field, the glass bottle
    // 374; max stack 1, the reference's own PotionItem ctor).
    public static final EngineItemType POTION = new EngineItemType(
            Identifier.parse("minecraft:potion"), "Potion", 1);
    public static final EngineItemType GLASS_BOTTLE = new EngineItemType(
            Identifier.parse("minecraft:glass_bottle"), "Glass Bottle", 64);

    // The brewing family (Slice 11e — the reference's Item.java
    // registrations): the stand item 379 places the block; the
    // ingredients carry their own flag recipes (BrewIngredients' table):
    // ghast tear 370, nether wart 372, spider eye 375, fermented spider
    // eye 376, blaze powder 377, magma cream 378, glistering melon 382
    // (the reference's "speckled_melon"), rabbit foot 414. The pufferfish
    // (the fish item's metadata-3 arm) rides the fish slice.
    public static final EngineItemType GHAST_TEAR = new EngineItemType(
            Identifier.parse("minecraft:ghast_tear"), "Ghast Tear", 64);
    public static final EngineItemType NETHER_WART = new EngineItemType(
            Identifier.parse("minecraft:nether_wart"), "Nether Wart", 64);
    public static final EngineItemType SPIDER_EYE = new EngineItemType(
            Identifier.parse("minecraft:spider_eye"), "Spider Eye", 64);
    public static final EngineItemType FERMENTED_SPIDER_EYE = new EngineItemType(
            Identifier.parse("minecraft:fermented_spider_eye"), "Fermented Spider Eye", 64);
    public static final EngineItemType BLAZE_POWDER = new EngineItemType(
            Identifier.parse("minecraft:blaze_powder"), "Blaze Powder", 64);
    // The blaze's own drop (Slice 18a — the reference's Item.java line for
    // the rod, id 369): the plain material, stack 64.
    public static final EngineItemType BLAZE_ROD = new EngineItemType(
            Identifier.parse("minecraft:blaze_rod"), "Blaze Rod", 64);
    public static final EngineItemType MAGMA_CREAM = new EngineItemType(
            Identifier.parse("minecraft:magma_cream"), "Magma Cream", 64);
    public static final EngineItemType GLISTERING_MELON = new EngineItemType(
            Identifier.parse("minecraft:glistering_melon"), "Glistering Melon", 64);
    public static final EngineItemType RABBIT_FOOT = new EngineItemType(
            Identifier.parse("minecraft:rabbit_foot"), "Rabbit's Foot", 64);
    // The rabbit family (Slice 12l — the reference's Item.java lines
    // 715-719: the raw rabbit 411 FoodItem(3, 0.3F), the cooked rabbit 412
    // FoodItem(5, 0.6F), the rabbit stew 413 FoodItem(10, 0.6F) with the
    // bowl-return stack-1 rule, the hide 415 a plain material).
    public static final EngineItemType RABBIT = new EngineItemType(
            Identifier.parse("minecraft:rabbit"), "Raw Rabbit", 64);
    public static final EngineItemType COOKED_RABBIT = new EngineItemType(
            Identifier.parse("minecraft:cooked_rabbit"), "Cooked Rabbit", 64);
    public static final EngineItemType RABBIT_STEW = new EngineItemType(
            Identifier.parse("minecraft:rabbit_stew"), "Rabbit Stew", 1);
    // The bowl pair (Slice 17b — the reference's Item.java: the bowl 281
    // and the mushroom stew 282 FoodItem(6, 0.6F) with the stack-1 bowl rule).
    public static final EngineItemType BOWL = new EngineItemType(
            Identifier.parse("minecraft:bowl"), "Bowl", 64);
    public static final EngineItemType MUSHROOM_STEW = new EngineItemType(
            Identifier.parse("minecraft:mushroom_stew"), "Mushroom Stew", 1);
    // The golem family's block items (Slice 17d — the item ids share the
    // block ids for all five; the snowball 332 already lives for the
    // shard family).
    public static final EngineItemType SNOW_BLOCK = new EngineItemType(
            Identifier.parse("minecraft:snow"), "Snow", 64);
    public static final EngineItemType SNOW_LAYER = new EngineItemType(
            Identifier.parse("minecraft:snow_layer"), "Snow Layer", 64);
    // The stone-brick item (Slice 18c — Item.java's block item for the
    // brick family, legacy 98): the plain brick's own item form; the
    // mossy/cracked/chiseled variants ride the damage field (1/2/3) per
    // the reference's getSilkTouchDrop ItemStacks, and the infested eggs'
    // silk arm drops it too.
    public static final EngineItemType STONE_BRICKS = new EngineItemType(
            Identifier.parse("minecraft:stone_bricks"), "Stone Bricks", 64);
    public static final EngineItemType IRON_BLOCK = new EngineItemType(
            Identifier.parse("minecraft:iron_block"), "Block of Iron", 64);
    public static final EngineItemType PUMPKIN = new EngineItemType(
            Identifier.parse("minecraft:pumpkin"), "Pumpkin", 64);
    public static final EngineItemType LIT_PUMPKIN = new EngineItemType(
            Identifier.parse("minecraft:lit_pumpkin"), "Jack o'Lantern", 64);
    public static final EngineItemType RABBIT_HIDE = new EngineItemType(
            Identifier.parse("minecraft:rabbit_hide"), "Rabbit Hide", 64);
    // The spawn egg (Slice 20a — Item.java line 683: id 383, the reference's
    // "monsterPlacer"): the one item whose damage field names a MOB — the
    // entity registration id (50 creeper .. 120 villager). Stack 64; the
    // kind dispatch lives in SpawnEggs; the egg colors are client-side.
    public static final EngineItemType SPAWN_EGG = new EngineItemType(
            Identifier.parse("minecraft:spawn_egg"), "Spawn Egg", 64);
    // The fish family (Slice 11i — the reference's Item.java lines 635-636:
    // the raw fish 349 and the cooked fish 350, the four variants riding
    // the damage field: 0 cod, 1 salmon, 2 clownfish, 3 pufferfish).
    // The nutrition/eat-effect dispatch and the pufferfish's brewing arm
    // live in FishItems; the smelting pair rides FurnaceRecipes.
    public static final EngineItemType FISH = new EngineItemType(
            Identifier.parse("minecraft:fish"), "Raw Fish", 64);
    public static final EngineItemType COOKED_FISH = new EngineItemType(
            Identifier.parse("minecraft:cooked_fish"), "Cooked Fish", 64);
    public static final EngineItemType BREWING_STAND = new EngineItemType(
            Identifier.parse("minecraft:brewing_stand"), "Brewing Stand", 64);
    // The cauldron item (reference Item.java line 676: legacy 380, the
    // PlaceableItem of the cauldron block, the brewing tab).
    public static final EngineItemType CAULDRON = new EngineItemType(
            Identifier.parse("minecraft:cauldron"), "Cauldron", 64);
    public static final EngineItemType OAK_FENCE = new EngineItemType(
            Identifier.parse("minecraft:oak_fence"), "Oak Fence", 64);
    // The bed item (community items.json: legacy 355) — places both halves.
    public static final EngineItemType BED = new EngineItemType(
            Identifier.parse("minecraft:bed"), "Bed", 1);

    // The collision-shape slice (community items.json: stone slab 44, wooden
    // slab 126, oak stairs 53, cobble stairs 67 — stack 64 block items). The
    // slab items share the bottom slab block identifiers; the stairs items
    // keep the bare identifier while the block types carry facing suffixes
    // (the sign pattern).
    public static final EngineItemType OAK_SLAB = new EngineItemType(
            Identifier.parse("minecraft:oak_slab"), "Oak Slab", 64);
    public static final EngineItemType STONE_SLAB = new EngineItemType(
            Identifier.parse("minecraft:stone_slab"), "Stone Slab", 64);
    public static final EngineItemType COBBLESTONE_SLAB = new EngineItemType(
            Identifier.parse("minecraft:cobblestone_slab"), "Cobblestone Slab", 64);
    public static final EngineItemType SANDSTONE_SLAB = new EngineItemType(
            Identifier.parse("minecraft:sandstone_slab"), "Sandstone Slab", 64);
    public static final EngineItemType OAK_STAIRS = new EngineItemType(
            Identifier.parse("minecraft:oak_stairs"), "Oak Stairs", 64);
    public static final EngineItemType COBBLESTONE_STAIRS = new EngineItemType(
            Identifier.parse("minecraft:cobblestone_stairs"), "Cobblestone Stairs", 64);

    // The vehicle slice (community items.json: rail 66, boat 333, minecart
    // 328). The boat and the minecart are the placeable spawn items (stack
    // of one); the rail item places the flat track per the placer's look.
    public static final EngineItemType RAIL = new EngineItemType(
            Identifier.parse("minecraft:rail"), "Rail", 64);
    public static final EngineItemType BOAT = new EngineItemType(
            Identifier.parse("minecraft:boat"), "Boat", 1);
    public static final EngineItemType MINECART = new EngineItemType(
            Identifier.parse("minecraft:minecart"), "Minecart", 1);
    // The chest minecart item (community items.json 342, Slice 21a): the
    // rail-place arm spawns the chest variant; its death, 1:1, never drops
    // this item — the cart spills a minecart + a chest block instead.
    public static final EngineItemType CHEST_MINECART = new EngineItemType(
            Identifier.parse("minecraft:chest_minecart"), "Minecart with Chest", 1);

    // The mount slice (community items.json: saddle 329, iron/gold/diamond
    // horse armor 417/418/419, lead 420, carrot on a stick 398, emerald 388).
    // The saddle equips horses and pigs; the horse armors go on tamed horses
    // only (the historical armor gate); the carrot on a stick steers a
    // saddled pig; emeralds are the villager currency (the trading slice).
    public static final EngineItemType SADDLE = new EngineItemType(
            Identifier.parse("minecraft:saddle"), "Saddle", 1);
    public static final EngineItemType IRON_HORSE_ARMOR = new EngineItemType(
            Identifier.parse("minecraft:iron_horse_armor"), "Iron Horse Armor", 1);
    public static final EngineItemType GOLDEN_HORSE_ARMOR = new EngineItemType(
            Identifier.parse("minecraft:golden_horse_armor"), "Golden Horse Armor", 1);
    public static final EngineItemType DIAMOND_HORSE_ARMOR = new EngineItemType(
            Identifier.parse("minecraft:diamond_horse_armor"), "Diamond Horse Armor", 1);
    public static final EngineItemType LEAD = new EngineItemType(
            Identifier.parse("minecraft:lead"), "Lead", 64);
    public static final EngineItemType CARROT_ON_A_STICK = new EngineItemType(
            Identifier.parse("minecraft:carrot_on_a_stick"), "Carrot on a Stick", 1);
    public static final EngineItemType EMERALD = new EngineItemType(
            Identifier.parse("minecraft:emerald"), "Emerald", 64);

    // The blacksmith's chest loot pair (Slice 12b — the reference's
    // Item.byBlock rows): the obsidian item (legacy 49) and the oak
    // sapling (legacy 6) — chest contents only this slice; the plantable
    // sapling block is its own ledger row.
    public static final EngineItemType OBSIDIAN = new EngineItemType(
            Identifier.parse("minecraft:obsidian"), "Obsidian", 64);
    // The ender chest item (Slice 29a — legacy 130, the block family's own
    // id): places any of the four facing states (the placement verb's
    // look-opposite walk), stack 64.
    public static final EngineItemType ENDER_CHEST = new EngineItemType(
            Identifier.parse("minecraft:ender_chest"), "Ender Chest", 64);
    public static final EngineItemType OAK_SAPLING = new EngineItemType(
            Identifier.parse("minecraft:oak_sapling"), "Oak Sapling", 64);

    // The enchanting corner (legacy ids: lapis is the dye 351 whose damage 4
    // is the blue the table's slot gate demands, enchanted book 403 the
    // book's enchanted form at max 1; the bookshelf and table place from
    // their block forms, 47 and 116).
    public static final EngineItemType LAPIS = new EngineItemType(
            Identifier.parse("minecraft:lapis"), "Lapis Lazuli", 64);
    public static final EngineItemType ENCHANTED_BOOK = new EngineItemType(
            Identifier.parse("minecraft:enchanted_book"), "Enchanted Book", 1);

    // The villager's trade stock (Slice 21g — the reference's Item.java
    // registrations): fishing rod 346 (the tool rod, max 1), clock 347,
    // cake 354 (stack 1 — the placed block's item), cookie 357 (food 2),
    // melon 360 (food 2 — the melon block's item), ender eye 381,
    // experience bottle 384, written book 387 (stack 16), pumpkin pie 400
    // (food 8), name tag 421.
    public static final EngineItemType FISHING_ROD = new EngineItemType(
            Identifier.parse("minecraft:fishing_rod"), "Fishing Rod", 1);
    public static final EngineItemType CLOCK = new EngineItemType(
            Identifier.parse("minecraft:clock"), "Clock", 64);
    public static final EngineItemType CAKE = new EngineItemType(
            Identifier.parse("minecraft:cake"), "Cake", 1);
    public static final EngineItemType COOKIE = new EngineItemType(
            Identifier.parse("minecraft:cookie"), "Cookie", 64);
    public static final EngineItemType MELON = new EngineItemType(
            Identifier.parse("minecraft:melon"), "Melon", 64);
    public static final EngineItemType ENDER_EYE = new EngineItemType(
            Identifier.parse("minecraft:ender_eye"), "Eye of Ender", 64);
    public static final EngineItemType EXPERIENCE_BOTTLE = new EngineItemType(
            Identifier.parse("minecraft:experience_bottle"), "Bottle o' Enchanting", 64);
    public static final EngineItemType WRITTEN_BOOK = new EngineItemType(
            Identifier.parse("minecraft:written_book"), "Written Book", 16);
    public static final EngineItemType PUMPKIN_PIE = new EngineItemType(
            Identifier.parse("minecraft:pumpkin_pie"), "Pumpkin Pie", 64);
    public static final EngineItemType NAME_TAG = new EngineItemType(
            Identifier.parse("minecraft:name_tag"), "Name Tag", 64);
    // The cleric's stock (the glowstone block's item form — legacy 348).
    public static final EngineItemType GLOWSTONE = new EngineItemType(
            Identifier.parse("minecraft:glowstone"), "Glowstone", 64);
    public static final EngineItemType BOOKSHELF = new EngineItemType(
            Identifier.parse("minecraft:bookshelf"), "Bookshelf", 64);
    public static final EngineItemType ENCHANTING_TABLE = new EngineItemType(
            Identifier.parse("minecraft:enchanting_table"), "Enchantment Table", 64);

    // The decoration family (Slice 28a/28b — Item.java's DecorationItem
    // registrations): the item frame (legacy 389) and the painting
    // (legacy 321), both spawning their entity bodies on the wall the
    // use names.
    public static final EngineItemType ITEM_FRAME = new EngineItemType(
            Identifier.parse("minecraft:item_frame"), "Item Frame", 64);
    public static final EngineItemType PAINTING = new EngineItemType(
            Identifier.parse("minecraft:painting"), "Painting", 64);
    // The armor stand's item form (Slice 28c — Item.java line 720:
    // register(416, "armor_stand", ...).setMaxStackSize(16)).
    public static final EngineItemType ARMOR_STAND = new EngineItemType(
            Identifier.parse("minecraft:armor_stand"), "Armor Stand", 16);

    // The stronghold's chest loot family (Slice 12c — the reference's
    // ChestCorridor/Library/RoomCrossing LootEntry tables): the ender pearl
    // (legacy 368, max 16), the empty map (legacy 395) and the compass
    // (legacy 329 — the reference's own Items.COMPASS registry name "compass")
    // from the library's table.
    public static final EngineItemType ENDER_PEARL = new EngineItemType(
            Identifier.parse("minecraft:ender_pearl"), "Ender Pearl", 16);
    public static final EngineItemType EMPTY_MAP = new EngineItemType(
            Identifier.parse("minecraft:empty_map"), "Empty Map", 64);
    public static final EngineItemType COMPASS = new EngineItemType(
            Identifier.parse("minecraft:compass"), "Compass", 64);
    // The iron door item (legacy 330 — the prison's pair, the block's
    // drop form).
    public static final EngineItemType IRON_DOOR = new EngineItemType(
            Identifier.parse("minecraft:iron_door"), "Iron Door", 64);
    // The fortress's block item family (Slice 12d — legacy 405/113/114).
    public static final EngineItemType NETHER_BRICKS = new EngineItemType(
            Identifier.parse("minecraft:nether_bricks"), "Nether Bricks", 64);
    public static final EngineItemType NETHER_BRICK_FENCE = new EngineItemType(
            Identifier.parse("minecraft:nether_brick_fence"), "Nether Brick Fence", 64);
    public static final EngineItemType NETHER_BRICK_STAIRS = new EngineItemType(
            Identifier.parse("minecraft:nether_brick_stairs"), "Nether Brick Stairs", 64);
    // The monument's item family (Slice 12e — legacy 168/410/409/169/19/41):
    // the prismarine item (the block trio's damage variants ride the stack's
    // damage field — the plain item is the drop stand-in), the crystals +
    // the shard (the lantern's drop), the sea lantern, the sponge, the gold
    // block.
    public static final EngineItemType PRISMARINE = new EngineItemType(
            Identifier.parse("minecraft:prismarine"), "Prismarine", 64);
    public static final EngineItemType PRISMARINE_CRYSTALS = new EngineItemType(
            Identifier.parse("minecraft:prismarine_crystals"), "Prismarine Crystals", 64);
    public static final EngineItemType PRISMARINE_SHARD = new EngineItemType(
            Identifier.parse("minecraft:prismarine_shard"), "Prismarine Shard", 64);
    public static final EngineItemType SEA_LANTERN = new EngineItemType(
            Identifier.parse("minecraft:sea_lantern"), "Sea Lantern", 64);
    public static final EngineItemType SPONGE = new EngineItemType(
            Identifier.parse("minecraft:sponge"), "Sponge", 64);
    public static final EngineItemType GOLD_BLOCK = new EngineItemType(
            Identifier.parse("minecraft:gold_block"), "Block of Gold", 64);
    // The beacon pyramid's minerals (Slice 29b — legacy 57 and 133) + the
    // beacon item itself (legacy 138, the block's own id).
    public static final EngineItemType DIAMOND_BLOCK = new EngineItemType(
            Identifier.parse("minecraft:diamond_block"), "Block of Diamond", 64);
    public static final EngineItemType EMERALD_BLOCK = new EngineItemType(
            Identifier.parse("minecraft:emerald_block"), "Block of Emerald", 64);
    public static final EngineItemType BEACON = new EngineItemType(
            Identifier.parse("minecraft:beacon"), "Beacon", 64);

    // Mob loot (legacy ids from the community dataset: porkchop 319, cooked
    // porkchop 320, raw chicken 365, cooked chicken 366, feather 288,
    // leather 334, rotten flesh 367). Cooked variants smelt from the raw
    // ones; raw chicken and rotten flesh are edible (historical risk-free
    // slice: no poison effects yet).
    public static final EngineItemType PORKCHOP = new EngineItemType(
            Identifier.parse("minecraft:porkchop"), "Raw Porkchop", 64);
    public static final EngineItemType COOKED_PORKCHOP = new EngineItemType(
            Identifier.parse("minecraft:cooked_porkchop"), "Cooked Porkchop", 64);
    public static final EngineItemType RAW_CHICKEN = new EngineItemType(
            Identifier.parse("minecraft:chicken"), "Raw Chicken", 64);
    public static final EngineItemType COOKED_CHICKEN = new EngineItemType(
            Identifier.parse("minecraft:cooked_chicken"), "Cooked Chicken", 64);
    public static final EngineItemType FEATHER = new EngineItemType(
            Identifier.parse("minecraft:feather"), "Feather", 64);
    public static final EngineItemType LEATHER = new EngineItemType(
            Identifier.parse("minecraft:leather"), "Leather", 64);
    public static final EngineItemType ROTTEN_FLESH = new EngineItemType(
            Identifier.parse("minecraft:rotten_flesh"), "Rotten Flesh", 64);
    /** The pigman's drop (ZombiePigmanEntity.dropItems; community id 371). */
    public static final EngineItemType GOLD_NUGGET = new EngineItemType(
            Identifier.parse("minecraft:gold_nugget"), "Gold Nugget", 64);
    /** The squid's drop and the first dye member (SquidEntity.dropItems:
     * the black dye, metadata 0; community id 351 — the full dye family
     * rides its own slice). */
    public static final EngineItemType INK_SAC = new EngineItemType(
            Identifier.parse("minecraft:dye"), "Ink Sac", 64);

    // Ore blocks that drop themselves (iron ore historically needs smelting).
    public static final EngineItemType COAL_ORE = new EngineItemType(
            Identifier.parse("minecraft:coal_ore"), "Coal Ore", 64);
    public static final EngineItemType IRON_ORE = new EngineItemType(
            Identifier.parse("minecraft:iron_ore"), "Iron Ore", 64);
    public static final EngineItemType DIAMOND_ORE = new EngineItemType(
            Identifier.parse("minecraft:diamond_ore"), "Diamond Ore", 64);
    public static final EngineItemType GOLD_ORE = new EngineItemType(
            Identifier.parse("minecraft:gold_ore"), "Gold Ore", 64);
    public static final EngineItemType REDSTONE_ORE = new EngineItemType(
            Identifier.parse("minecraft:redstone_ore"), "Redstone Ore", 64);

    // Ranged combat (legacy ids from the community dataset: bow 261, arrow
    // 262, snowball 332, egg 344). The bow wears one durability per full
    // draw (the historical 384); shards stack to 16.
    public static final EngineItemType BOW = new EngineItemType(
            Identifier.parse("minecraft:bow"), "Bow", 1, 384);
    public static final EngineItemType ARROW = new EngineItemType(
            Identifier.parse("minecraft:arrow"), "Arrow", 64);
    public static final EngineItemType SNOWBALL = new EngineItemType(
            Identifier.parse("minecraft:snowball"), "Snowball", 16);
    public static final EngineItemType EGG = new EngineItemType(
            Identifier.parse("minecraft:egg"), "Egg", 16);

    // Tools: stack of one, dataset durability. Pickaxes (legacy 270/274/257/278/285).
    public static final EngineItemType WOODEN_PICKAXE = tool("wooden_pickaxe", "Wooden Pickaxe",
            ToolClass.PICKAXE, ToolMaterial.WOOD);
    public static final EngineItemType STONE_PICKAXE = tool("stone_pickaxe", "Stone Pickaxe",
            ToolClass.PICKAXE, ToolMaterial.STONE);
    public static final EngineItemType IRON_PICKAXE = tool("iron_pickaxe", "Iron Pickaxe",
            ToolClass.PICKAXE, ToolMaterial.IRON);
    public static final EngineItemType DIAMOND_PICKAXE = tool("diamond_pickaxe", "Diamond Pickaxe",
            ToolClass.PICKAXE, ToolMaterial.DIAMOND);
    public static final EngineItemType GOLDEN_PICKAXE = tool("golden_pickaxe", "Golden Pickaxe",
            ToolClass.PICKAXE, ToolMaterial.GOLD);

    // Axes (legacy 271/275/258/279/286).
    public static final EngineItemType WOODEN_AXE = tool("wooden_axe", "Wooden Axe",
            ToolClass.AXE, ToolMaterial.WOOD);
    public static final EngineItemType STONE_AXE = tool("stone_axe", "Stone Axe",
            ToolClass.AXE, ToolMaterial.STONE);
    public static final EngineItemType IRON_AXE = tool("iron_axe", "Iron Axe",
            ToolClass.AXE, ToolMaterial.IRON);
    public static final EngineItemType DIAMOND_AXE = tool("diamond_axe", "Diamond Axe",
            ToolClass.AXE, ToolMaterial.DIAMOND);
    public static final EngineItemType GOLDEN_AXE = tool("golden_axe", "Golden Axe",
            ToolClass.AXE, ToolMaterial.GOLD);

    // Shovels (legacy 269/273/256/277/284).
    public static final EngineItemType WOODEN_SHOVEL = tool("wooden_shovel", "Wooden Shovel",
            ToolClass.SHOVEL, ToolMaterial.WOOD);
    public static final EngineItemType STONE_SHOVEL = tool("stone_shovel", "Stone Shovel",
            ToolClass.SHOVEL, ToolMaterial.STONE);
    public static final EngineItemType IRON_SHOVEL = tool("iron_shovel", "Iron Shovel",
            ToolClass.SHOVEL, ToolMaterial.IRON);
    public static final EngineItemType DIAMOND_SHOVEL = tool("diamond_shovel", "Diamond Shovel",
            ToolClass.SHOVEL, ToolMaterial.DIAMOND);
    public static final EngineItemType GOLDEN_SHOVEL = tool("golden_shovel", "Golden Shovel",
            ToolClass.SHOVEL, ToolMaterial.GOLD);

    // Swords (legacy 268/272/267/276/283): registered for completeness; combat
    // and their plant/web/leaves speed roles arrive with later slices.
    public static final EngineItemType WOODEN_SWORD = tool("wooden_sword", "Wooden Sword",
            ToolClass.SWORD, ToolMaterial.WOOD);
    public static final EngineItemType STONE_SWORD = tool("stone_sword", "Stone Sword",
            ToolClass.SWORD, ToolMaterial.STONE);
    public static final EngineItemType IRON_SWORD = tool("iron_sword", "Iron Sword",
            ToolClass.SWORD, ToolMaterial.IRON);
    public static final EngineItemType DIAMOND_SWORD = tool("diamond_sword", "Diamond Sword",
            ToolClass.SWORD, ToolMaterial.DIAMOND);
    public static final EngineItemType GOLDEN_SWORD = tool("golden_sword", "Golden Sword",
            ToolClass.SWORD, ToolMaterial.GOLD);

    // Shears (legacy 359).
    public static final EngineItemType SHEARS = tool("shears", "Shears",
            ToolClass.SHEARS, ToolMaterial.IRON, 238);
    // The tillers (community items.json ids 290-294, maxDurability per
    // material — the same values the other tool families carry).
    public static final EngineItemType WOODEN_HOE = tool("wooden_hoe", "Wooden Hoe",
            ToolClass.HOE, ToolMaterial.WOOD);
    public static final EngineItemType STONE_HOE = tool("stone_hoe", "Stone Hoe",
            ToolClass.HOE, ToolMaterial.STONE);
    public static final EngineItemType IRON_HOE = tool("iron_hoe", "Iron Hoe",
            ToolClass.HOE, ToolMaterial.IRON);
    public static final EngineItemType DIAMOND_HOE = tool("diamond_hoe", "Diamond Hoe",
            ToolClass.HOE, ToolMaterial.DIAMOND);
    public static final EngineItemType GOLDEN_HOE = tool("golden_hoe", "Golden Hoe",
            ToolClass.HOE, ToolMaterial.GOLD);

    // Mob loot of the living-night slice (legacy ids from the community
    // dataset: bone 352, string 287, gunpowder 289; wool is the sheep's
    // block-item 35; mutton smelts into the cooked form, 423/424).
    public static final EngineItemType BONE = new EngineItemType(
            Identifier.parse("minecraft:bone"), "Bone", 64);
    public static final EngineItemType STRING = new EngineItemType(
            Identifier.parse("minecraft:string"), "String", 64);
    public static final EngineItemType GUNPOWDER = new EngineItemType(
            Identifier.parse("minecraft:gunpowder"), "Gunpowder", 64);
    /** The small slime's drop (SlimeEntity.getDropItem: size 1 only; community id 341). */
    public static final EngineItemType SLIME_BALL = new EngineItemType(
            Identifier.parse("minecraft:slime_ball"), "Slimeball", 64);
    /**
     * The wither skeleton's rare head (SkeletonEntity.dropRareItem line
     * 204: {@code new ItemStack(Items.SKULL, 1, 1)} — community id 397;
     * the stack's damage field carries the variant, 1 = wither).
     */
    public static final EngineItemType SKULL = new EngineItemType(
            Identifier.parse("minecraft:skull"), "Skull", 64);
    /** The ghast's rare drop and the magma cube's drop ride the brewing
     * family's registrations (GHAST_TEAR / MAGMA_CREAM — the wire rows
     * landed with Slice 16c). */
    public static final EngineItemType WOOL = new EngineItemType(
            Identifier.parse("minecraft:wool"), "Wool", 64);
    public static final EngineItemType MUTTON = new EngineItemType(
            Identifier.parse("minecraft:mutton"), "Raw Mutton", 64);
    public static final EngineItemType COOKED_MUTTON = new EngineItemType(
            Identifier.parse("minecraft:cooked_mutton"), "Cooked Mutton", 64);

    // Fluid handling (legacy ids: bucket 325, water bucket 326, lava bucket
    // 327). A filled bucket carries its fluid as the identity — the 1.8 way —
    // and swaps for the empty one on use; the empty bucket fills from any
    // source block it targets.
    public static final EngineItemType BUCKET = new EngineItemType(
            Identifier.parse("minecraft:bucket"), "Bucket", 1);
    public static final EngineItemType WATER_BUCKET = new EngineItemType(
            Identifier.parse("minecraft:water_bucket"), "Water Bucket", 1);
    public static final EngineItemType LAVA_BUCKET = new EngineItemType(
            Identifier.parse("minecraft:lava_bucket"), "Lava Bucket", 1);

    // Decoration flora + desert stone (the placeable block-item forms; the
    // flowers drop themselves, tall grass/dead bush are /give-reachable).
    public static final EngineItemType TALL_GRASS = new EngineItemType(
            Identifier.parse("minecraft:tall_grass"), "Tall Grass", 64);
    public static final EngineItemType DEAD_BUSH = new EngineItemType(
            Identifier.parse("minecraft:dead_bush"), "Dead Bush", 64);
    public static final EngineItemType DANDELION = new EngineItemType(
            Identifier.parse("minecraft:dandelion"), "Dandelion", 64);
    public static final EngineItemType POPPY = new EngineItemType(
            Identifier.parse("minecraft:poppy"), "Poppy", 64);
    public static final EngineItemType SANDSTONE = new EngineItemType(
            Identifier.parse("minecraft:sandstone"), "Sandstone", 64);

    // The dye-coloring world families' items (Slice 31 — the reference's
    // Item registrations over the block ids): each family is ONE item whose
    // damage field carries the DyeColor id (0 white .. 15 black), the same
    // domain as the wool item's own damage model (the sheep's fleece).
    // Legacy ids: stained glass 95, stained hardened clay 159, the stained
    // pane 160, the carpet 171, hardened clay 172.
    public static final EngineItemType STAINED_GLASS = new EngineItemType(
            Identifier.parse("minecraft:stained_glass"), "Stained Glass", 64);
    public static final EngineItemType STAINED_GLASS_PANE = new EngineItemType(
            Identifier.parse("minecraft:stained_glass_pane"), "Stained Glass Pane", 64);
    public static final EngineItemType CARPET = new EngineItemType(
            Identifier.parse("minecraft:carpet"), "Carpet", 64);
    public static final EngineItemType HARDENED_CLAY = new EngineItemType(
            Identifier.parse("minecraft:hardened_clay"), "Hardened Clay", 64);
    public static final EngineItemType STAINED_HARDENED_CLAY = new EngineItemType(
            Identifier.parse("minecraft:stained_hardened_clay"), "Stained Clay", 64);

    // Armor (community items.json legacy ids + maxDurability): the five
    // historical tiers across the four slots. Per-piece armor points ride the
    // Armor registry; a full set totals 7/12/15/11/20 on the 20-point bar.
    public static final EngineItemType LEATHER_HELMET = armor("leather_helmet", "Leather Cap", Armor.Slot.HEAD, 1, 56);
    public static final EngineItemType LEATHER_CHESTPLATE = armor("leather_chestplate", "Leather Tunic", Armor.Slot.CHEST, 3, 81);
    public static final EngineItemType LEATHER_LEGGINGS = armor("leather_leggings", "Leather Pants", Armor.Slot.LEGS, 2, 76);
    public static final EngineItemType LEATHER_BOOTS = armor("leather_boots", "Leather Boots", Armor.Slot.FEET, 1, 66);

    public static final EngineItemType CHAINMAIL_HELMET = armor("chainmail_helmet", "Chain Helmet", Armor.Slot.HEAD, 2, 166);
    public static final EngineItemType CHAINMAIL_CHESTPLATE = armor("chainmail_chestplate", "Chain Chestplate", Armor.Slot.CHEST, 5, 241);
    public static final EngineItemType CHAINMAIL_LEGGINGS = armor("chainmail_leggings", "Chain Leggings", Armor.Slot.LEGS, 4, 226);
    public static final EngineItemType CHAINMAIL_BOOTS = armor("chainmail_boots", "Chain Boots", Armor.Slot.FEET, 1, 196);

    public static final EngineItemType IRON_HELMET = armor("iron_helmet", "Iron Helmet", Armor.Slot.HEAD, 2, 166);
    public static final EngineItemType IRON_CHESTPLATE = armor("iron_chestplate", "Iron Chestplate", Armor.Slot.CHEST, 6, 241);
    public static final EngineItemType IRON_LEGGINGS = armor("iron_leggings", "Iron Leggings", Armor.Slot.LEGS, 5, 226);
    public static final EngineItemType IRON_BOOTS = armor("iron_boots", "Iron Boots", Armor.Slot.FEET, 2, 196);

    public static final EngineItemType GOLDEN_HELMET = armor("golden_helmet", "Golden Helmet", Armor.Slot.HEAD, 2, 78);
    public static final EngineItemType GOLDEN_CHESTPLATE = armor("golden_chestplate", "Golden Chestplate", Armor.Slot.CHEST, 5, 113);
    public static final EngineItemType GOLDEN_LEGGINGS = armor("golden_leggings", "Golden Leggings", Armor.Slot.LEGS, 3, 106);
    public static final EngineItemType GOLDEN_BOOTS = armor("golden_boots", "Golden Boots", Armor.Slot.FEET, 1, 92);

    public static final EngineItemType DIAMOND_HELMET = armor("diamond_helmet", "Diamond Helmet", Armor.Slot.HEAD, 3, 364);
    public static final EngineItemType DIAMOND_CHESTPLATE = armor("diamond_chestplate", "Diamond Chestplate", Armor.Slot.CHEST, 8, 529);
    public static final EngineItemType DIAMOND_LEGGINGS = armor("diamond_leggings", "Diamond Leggings", Armor.Slot.LEGS, 6, 496);
    public static final EngineItemType DIAMOND_BOOTS = armor("diamond_boots", "Diamond Boots", Armor.Slot.FEET, 3, 430);

    private static final Map<Identifier, ItemType> REGISTRY = build();

    private BuiltinItems() {
    }

    private static EngineItemType tool(String name, String displayName,
                                       ToolClass toolClass, ToolMaterial material) {
        return tool(name, displayName, toolClass, material, material.maxDurability());
    }

    private static EngineItemType tool(String name, String displayName,
                                       ToolClass toolClass, ToolMaterial material, int maxDurability) {
        Tools.define(Identifier.parse("minecraft:" + name), toolClass, material);
        return new EngineItemType(Identifier.parse("minecraft:" + name), displayName, 1, maxDurability);
    }

    /** Declares one armor piece: registry item + the Armor gameplay spec. */
    private static EngineItemType armor(String name, String displayName,
                                        Armor.Slot slot, int armorPoints, int maxDurability) {
        Identifier identifier = Identifier.parse("minecraft:" + name);
        Armor.define(identifier, slot, armorPoints);
        return new EngineItemType(identifier, displayName, 1, maxDurability);
    }

    private static Map<Identifier, ItemType> build() {
        Map<Identifier, ItemType> map = new TreeMap<>();
        for (EngineItemType type : new EngineItemType[] {
                DIRT, COBBLESTONE, OAK_PLANKS, OAK_LOG,
                STICK, CRAFTING_TABLE, NOTE_BLOCK, TORCH, FURNACE, CHEST,
                COAL, DIAMOND, IRON_INGOT, STONE, SAND, GLASS, GRAVEL, FLINT, BEEF, COOKED_BEEF,
                PORKCHOP, COOKED_PORKCHOP, RAW_CHICKEN, COOKED_CHICKEN,
                BOW, ARROW, SNOWBALL, EGG,
                FEATHER, LEATHER, ROTTEN_FLESH, GOLD_NUGGET, INK_SAC,
                COAL_ORE, IRON_ORE, DIAMOND_ORE, GOLD_ORE, REDSTONE_ORE,
                WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, DIAMOND_PICKAXE, GOLDEN_PICKAXE,
                WOODEN_AXE, STONE_AXE, IRON_AXE, DIAMOND_AXE, GOLDEN_AXE,
                WOODEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL, DIAMOND_SHOVEL, GOLDEN_SHOVEL,
                WOODEN_SWORD, STONE_SWORD, IRON_SWORD, DIAMOND_SWORD, GOLDEN_SWORD,
                SHEARS,
                WOODEN_HOE, STONE_HOE, IRON_HOE, DIAMOND_HOE, GOLDEN_HOE,
                WHEAT_SEEDS, MELON_SEEDS, PUMPKIN_SEEDS, WHEAT, BREAD, BONE_MEAL, SIGN,
                CARROT, POTATO, BAKED_POTATO, APPLE, GOLDEN_CARROT, GOLDEN_APPLE, HAY_BLOCK,
                OAK_DOOR, LADDER, OAK_FENCE, BED,
                REDSTONE, REDSTONE_TORCH, REPEATER, COMPARATOR, PISTON, STICKY_PISTON, TNT,
                LEVER, STONE_BUTTON, WOODEN_BUTTON,
                STONE_PRESSURE_PLATE, WOODEN_PRESSURE_PLATE, HOPPER, TRIPWIRE_HOOK,
                DISPENSER, DROPPER, FIRE_CHARGE, POTION, GLASS_BOTTLE,
                GHAST_TEAR, NETHER_WART, SPIDER_EYE, FERMENTED_SPIDER_EYE,
                BLAZE_POWDER, MAGMA_CREAM, GLISTERING_MELON, RABBIT_FOOT,
                BLAZE_ROD,
                RABBIT, COOKED_RABBIT, RABBIT_STEW, RABBIT_HIDE,
                SPAWN_EGG,
                BOWL, MUSHROOM_STEW,
                JUKEBOX,
                DAYLIGHT_DETECTOR,
                RECORD_13, RECORD_CAT, RECORD_BLOCKS, RECORD_CHIRP, RECORD_FAR,
                RECORD_MALL, RECORD_MELLOHI, RECORD_STAL, RECORD_STRAD,
                RECORD_WARD, RECORD_11, RECORD_WAIT,
                SNOW_BLOCK, SNOW_LAYER, IRON_BLOCK, PUMPKIN, LIT_PUMPKIN,
                STONE_BRICKS,
                FISH, COOKED_FISH,
                BREWING_STAND,
                CAULDRON,
                OAK_SLAB, STONE_SLAB, COBBLESTONE_SLAB, SANDSTONE_SLAB,
                OAK_STAIRS, COBBLESTONE_STAIRS,
                RAIL, BOAT, MINECART, CHEST_MINECART,
                SADDLE, IRON_HORSE_ARMOR, GOLDEN_HORSE_ARMOR, DIAMOND_HORSE_ARMOR,
                OBSIDIAN, OAK_SAPLING,
                ENDER_CHEST,
                LEAD, CARROT_ON_A_STICK, EMERALD,
                LAPIS, ENCHANTED_BOOK, BOOKSHELF, ENCHANTING_TABLE,
                ITEM_FRAME, PAINTING, ARMOR_STAND,
                ENDER_PEARL, EMPTY_MAP, COMPASS, IRON_DOOR,
                NETHER_BRICKS, NETHER_BRICK_FENCE, NETHER_BRICK_STAIRS,
                PRISMARINE, PRISMARINE_CRYSTALS, PRISMARINE_SHARD, SEA_LANTERN,
                SPONGE, GOLD_BLOCK,
                DIAMOND_BLOCK, EMERALD_BLOCK, BEACON,
                BONE, STRING, GUNPOWDER, WOOL, MUTTON, COOKED_MUTTON, SLIME_BALL,
                SKULL,
                BUCKET, WATER_BUCKET, LAVA_BUCKET,
                FLINT_AND_STEEL, GOLD_INGOT,
                SUGAR_CANE, CACTUS, SUGAR, PAPER, BOOK,
                TALL_GRASS, DEAD_BUSH, DANDELION, POPPY, SANDSTONE,
                STAINED_GLASS, STAINED_GLASS_PANE, CARPET,
                HARDENED_CLAY, STAINED_HARDENED_CLAY,
                BROWN_MUSHROOM, RED_MUSHROOM, GLOWSTONE_DUST, QUARTZ, NETHERRACK, SOUL_SAND,
                LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS,
                CHAINMAIL_HELMET, CHAINMAIL_CHESTPLATE, CHAINMAIL_LEGGINGS, CHAINMAIL_BOOTS,
                IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS,
                GOLDEN_HELMET, GOLDEN_CHESTPLATE, GOLDEN_LEGGINGS, GOLDEN_BOOTS,
                DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS}) {
            ItemType existing = map.put(type.identifier(), type);
            if (existing != null) {
                throw new IllegalStateException("Duplicate item type registration: " + type.identifier());
            }
        }
        return Map.copyOf(map);
    }

    public static Optional<ItemType> lookup(Identifier identifier) {
        return Optional.ofNullable(REGISTRY.get(identifier));
    }

    /** @return all registered item types (diagnostics and future client sync). */
    public static Collection<ItemType> all() {
        return REGISTRY.values();
    }
}
