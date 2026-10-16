package net.zaminmc.torch.block;

import net.zaminmc.torch.util.Identifier;

import java.util.Collection;
import java.util.Optional;

/**
 * Read access to the block registry. Registries are frozen before the server
 * accepts gameplay, so lookups are always safe and never mutate state.
 */
public interface BlockRegistry {

    /** @return the registered block type, or empty if unknown. */
    Optional<BlockType> lookup(Identifier identifier);

    /**
     * @param key the canonical form ({@code "minecraft:stone"}) or the
     *            short form ({@code "stone"} — the minecraft namespace)
     * @return the registered block type, or empty if unknown (the item
     *         registry's own short-form shape)
     */
    default Optional<BlockType> byKey(String key) {
        return lookup(Identifier.parse(key.contains(":") ? key : "minecraft:" + key));
    }

    /** @return the registered block type, or throws if unknown (programming error). */
    default BlockType require(Identifier identifier) {
        return lookup(identifier).orElseThrow(
                () -> new IllegalArgumentException("Unknown block type: " + identifier));
    }

    /** @return all registered block types. The collection is read-only. */
    Collection<BlockType> all();

    /**
     * @return the block's survival data (hardness, tool gates, material,
     *         light behavior, drops) — never null; the
     *         {@link BlockData#UNREGISTERED} default when the engine
     *         carries no behavior row for the type. Frozen data, safe
     *         from any thread.
     */
    BlockData dataOf(BlockType type);

    /**
     * @param type the block being mined
     * @param held the held item, or null for the empty hand
     * @return whether the held item can harvest DROPS from the block —
     *         blocks without a tool requirement are harvestable by hand;
     *         tool-gated blocks need a tool of the matching class whose
     *         material tier satisfies the block's harvest level
     *         (breaking works regardless, it just yields nothing and
     *         takes the slow timing — the historical rule). Frozen
     *         data; safe from any thread.
     */
    default boolean canHarvest(BlockType type, net.zaminmc.torch.item.ItemType held) {
        return !dataOf(type).requiresTool();
    }

    /**
     * @param type the block being mined
     * @param held the held item, or null for the empty hand
     * @return the mining speed multiplier the held item contributes:
     *         the tool material's speed when its class matches the
     *         block's material family, 1.0 otherwise (the empty hand or
     *         a mismatched tool). Frozen data; safe from any thread.
     */
    default double miningSpeedMultiplier(BlockType type, net.zaminmc.torch.item.ItemType held) {
        return 1.0;
    }
}
