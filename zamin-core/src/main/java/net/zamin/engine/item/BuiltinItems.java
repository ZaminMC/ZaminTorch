package net.zamin.engine.item;

import net.zamin.api.Identifier;
import net.zamin.api.ItemType;

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

    private static Map<Identifier, ItemType> build() {
        Map<Identifier, ItemType> map = new TreeMap<>();
        for (EngineItemType type : new EngineItemType[] {
                DIRT, COBBLESTONE, OAK_PLANKS, OAK_LOG,
                STICK, CRAFTING_TABLE, TORCH, FURNACE, CHEST,
                COAL, DIAMOND, IRON_INGOT, STONE, SAND, GLASS, GRAVEL, FLINT, BEEF, COOKED_BEEF,
                PORKCHOP, COOKED_PORKCHOP, RAW_CHICKEN, COOKED_CHICKEN,
                BOW, ARROW, SNOWBALL, EGG,
                FEATHER, LEATHER, ROTTEN_FLESH,
                COAL_ORE, IRON_ORE, DIAMOND_ORE,
                WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, DIAMOND_PICKAXE, GOLDEN_PICKAXE,
                WOODEN_AXE, STONE_AXE, IRON_AXE, DIAMOND_AXE, GOLDEN_AXE,
                WOODEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL, DIAMOND_SHOVEL, GOLDEN_SHOVEL,
                WOODEN_SWORD, STONE_SWORD, IRON_SWORD, DIAMOND_SWORD, GOLDEN_SWORD,
                SHEARS}) {
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
