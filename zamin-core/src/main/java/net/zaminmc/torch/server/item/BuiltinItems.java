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

    // Edible items (community foods.json): raw beef smelts into steak.
    public static final EngineItemType BEEF = new EngineItemType(
            Identifier.parse("minecraft:beef"), "Beef", 64);
    public static final EngineItemType COOKED_BEEF = new EngineItemType(
            Identifier.parse("minecraft:cooked_beef"), "Steak", 64);

    // The farming slice (community items.json legacy ids: seeds 295,
    // wheat 296, bread 297, bone meal 351). Bread is the iconic crop food;
    // bone meal is the instant-grow dust (dye damage 15 on the wire).
    public static final EngineItemType WHEAT_SEEDS = new EngineItemType(
            Identifier.parse("minecraft:wheat_seeds"), "Wheat Seeds", 64);
    public static final EngineItemType WHEAT = new EngineItemType(
            Identifier.parse("minecraft:wheat"), "Wheat", 64);
    public static final EngineItemType BREAD = new EngineItemType(
            Identifier.parse("minecraft:bread"), "Bread", 64);

    // The breeding foods (the animal husbandry slice): the carrot feeds
    // pigs, the apple/golden items feed horses (the golden pair also brings
    // a tamed adult into love); hay is the block item horses heal on.
    public static final EngineItemType CARROT = new EngineItemType(
            Identifier.parse("minecraft:carrot"), "Carrot", 64);
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
                STICK, CRAFTING_TABLE, TORCH, FURNACE, CHEST,
                COAL, DIAMOND, IRON_INGOT, STONE, SAND, GLASS, GRAVEL, FLINT, BEEF, COOKED_BEEF,
                PORKCHOP, COOKED_PORKCHOP, RAW_CHICKEN, COOKED_CHICKEN,
                BOW, ARROW, SNOWBALL, EGG,
                FEATHER, LEATHER, ROTTEN_FLESH,
                COAL_ORE, IRON_ORE, DIAMOND_ORE, GOLD_ORE, REDSTONE_ORE,
                WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, DIAMOND_PICKAXE, GOLDEN_PICKAXE,
                WOODEN_AXE, STONE_AXE, IRON_AXE, DIAMOND_AXE, GOLDEN_AXE,
                WOODEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL, DIAMOND_SHOVEL, GOLDEN_SHOVEL,
                WOODEN_SWORD, STONE_SWORD, IRON_SWORD, DIAMOND_SWORD, GOLDEN_SWORD,
                SHEARS,
                WOODEN_HOE, STONE_HOE, IRON_HOE, DIAMOND_HOE, GOLDEN_HOE,
                WHEAT_SEEDS, WHEAT, BREAD, BONE_MEAL, SIGN,
                CARROT, APPLE, GOLDEN_CARROT, GOLDEN_APPLE, HAY_BLOCK,
                OAK_DOOR, LADDER, OAK_FENCE, BED,
                OAK_SLAB, STONE_SLAB, COBBLESTONE_SLAB, SANDSTONE_SLAB,
                OAK_STAIRS, COBBLESTONE_STAIRS,
                RAIL, BOAT, MINECART,
                SADDLE, IRON_HORSE_ARMOR, GOLDEN_HORSE_ARMOR, DIAMOND_HORSE_ARMOR,
                LEAD, CARROT_ON_A_STICK, EMERALD,
                BONE, STRING, GUNPOWDER, WOOL, MUTTON, COOKED_MUTTON,
                BUCKET, WATER_BUCKET, LAVA_BUCKET,
                FLINT_AND_STEEL, GOLD_INGOT,
                SUGAR_CANE, CACTUS, SUGAR, PAPER, BOOK,
                TALL_GRASS, DEAD_BUSH, DANDELION, POPPY, SANDSTONE,
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
