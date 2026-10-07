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
 * {@code minecraft:oak_log}; the version adapter owns that translation.</p>
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

    private static final Map<Identifier, ItemType> REGISTRY = build();

    private BuiltinItems() {
    }

    private static Map<Identifier, ItemType> build() {
        Map<Identifier, ItemType> map = new TreeMap<>();
        for (EngineItemType type : new EngineItemType[] {DIRT, COBBLESTONE, OAK_PLANKS, OAK_LOG}) {
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
