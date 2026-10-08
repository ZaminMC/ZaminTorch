package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A registry with an explicit lifecycle: build phase (register) then freeze phase
 * (runtime lookups only). Freezing produces a {@link FrozenBlockRegistry}; the builder
 * cannot be used afterwards, so gameplay can never mutate the registry by accident.
 */
public final class BlockRegistryBuilder {

    private final Map<Identifier, BlockType> registered = new TreeMap<>();
    private boolean frozen = false;

    public BlockRegistryBuilder register(BlockType type) {
        if (frozen) {
            throw new IllegalStateException("Registry already frozen; registration is closed");
        }
        BlockType existing = registered.put(type.identifier(), type);
        if (existing != null) {
            throw new IllegalStateException("Duplicate block type registration: " + type.identifier());
        }
        return this;
    }

    public FrozenBlockRegistry freeze() {
        frozen = true;
        return new FrozenBlockRegistry(List.copyOf(registered.values()), registered.keySet().size());
    }

    /** Immutable, frozen registry used during runtime. */
    public static final class FrozenBlockRegistry implements BlockRegistry {

        private final Map<Identifier, BlockType> byId;
        private final Collection<BlockType> all;

        private FrozenBlockRegistry(Collection<BlockType> types, int expectedSize) {
            Map<Identifier, BlockType> map = new TreeMap<>();
            for (BlockType type : types) {
                map.put(type.identifier(), type);
            }
            if (map.size() != expectedSize) {
                throw new IllegalStateException("Registry integrity check failed");
            }
            this.byId = Collections.unmodifiableMap(map);
            this.all = Collections.unmodifiableList(new ArrayList<>(types));
        }

        @Override
        public Optional<BlockType> lookup(Identifier identifier) {
            return Optional.ofNullable(byId.get(identifier));
        }

        @Override
        public Collection<BlockType> all() {
            return all;
        }
    }
}
