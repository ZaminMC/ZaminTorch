package net.zamin.api;

import java.util.Collection;
import java.util.Optional;

/**
 * Read access to the block registry. Registries are frozen before the server
 * accepts gameplay, so lookups are always safe and never mutate state.
 */
public interface BlockRegistry {

    /** @return the registered block type, or empty if unknown. */
    Optional<BlockType> lookup(Identifier identifier);

    /** @return the registered block type, or throws if unknown (programming error). */
    default BlockType require(Identifier identifier) {
        return lookup(identifier).orElseThrow(
                () -> new IllegalArgumentException("Unknown block type: " + identifier));
    }

    /** @return all registered block types. The collection is read-only. */
    Collection<BlockType> all();
}
