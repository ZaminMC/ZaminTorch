package net.zaminmc.torch.item;

import net.zaminmc.torch.util.Identifier;

import java.util.Collection;
import java.util.Optional;

/**
 * The frozen item-type registry (§27.3's registry family): the engine's
 * own item set — the identities a plugin may name, enumerate, and build
 * stacks from. Frozen before gameplay begins; every call is safe from
 * any thread.
 */
public interface ItemRegistry {

    /** @return all registered item types. The collection is read-only. */
    Collection<ItemType> all();

    /** @return the registered item type, or empty if unknown. */
    Optional<ItemType> byId(Identifier identifier);

    /**
     * @param key the canonical form ({@code "minecraft:dirt"}) or the
     *            short form ({@code "dirt"} — the minecraft namespace)
     * @return the registered item type, or empty if unknown
     */
    default Optional<ItemType> byKey(String key) {
        return byId(Identifier.parse(key));
    }
}
