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

    // Materials yielded by tool-gated mining (legacy ids: coal 263, diamond 264).
    public static final EngineItemType COAL = new EngineItemType(
            Identifier.parse("minecraft:coal"), "Coal", 64);
    public static final EngineItemType DIAMOND = new EngineItemType(
            Identifier.parse("minecraft:diamond"), "Diamond", 64);

    // Ore blocks that drop themselves (iron ore historically needs smelting).
    public static final EngineItemType COAL_ORE = new EngineItemType(
            Identifier.parse("minecraft:coal_ore"), "Coal Ore", 64);
    public static final EngineItemType IRON_ORE = new EngineItemType(
            Identifier.parse("minecraft:iron_ore"), "Iron Ore", 64);
    public static final EngineItemType DIAMOND_ORE = new EngineItemType(
            Identifier.parse("minecraft:diamond_ore"), "Diamond Ore", 64);

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
                COAL, DIAMOND, COAL_ORE, IRON_ORE, DIAMOND_ORE,
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
