package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockData;
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

        /**
         * The §27.3 block-state read (Slice 14b): the behavior table's
         * row joined with the light table's pair, mapped into the public
         * {@link BlockData} shape — the {@code UNREGISTERED} default for
         * types without a row (frozen data, thread-safe by construction).
         */
        @Override
        public BlockData dataOf(BlockType type) {
            Identifier id = type.identifier();
            return BlockBehaviorTable.of(id).map(behavior -> new BlockData(
                    behavior.hardness(),
                    behavior.diggable(),
                    behavior.requiresTool(),
                    behavior.material(),
                    behavior.harvestLevel(),
                    BlockLightTable.emission(type),
                    BlockLightTable.filter(type),
                    BlockBehaviorTable.isSilkTouchable(id),
                    behavior.drops().stream()
                            .map(drop -> new BlockData.Drop(drop.item(), drop.count(), drop.chance()))
                            .toList()))
                    .orElseGet(() -> new BlockData(
                            0.0, true, false, null, 0,
                            BlockLightTable.emission(type), BlockLightTable.filter(type),
                            false, List.of()));
        }

        @Override
        public Collection<BlockType> all() {
            return all;
        }

        /**
         * The exact engine verdict (§27.3's tool-interaction read): the
         * behavior table's own class/tier matching over the reference's
         * {@code canHarvest} shape.
         */
        @Override
        public boolean canHarvest(BlockType type, net.zaminmc.torch.item.ItemType held) {
            return BlockBehaviorTable.of(type.identifier())
                    .map(behavior -> BlockBehaviorTable.canHarvest(behavior, held))
                    .orElse(false);
        }

        /**
         * The exact engine multiplier: the reference's per-item-class speed
         * law (the mining-speed parity audit — BlockBehaviorTable's own
         * table), 1.0 otherwise.
         */
        @Override
        public double miningSpeedMultiplier(BlockType type, net.zaminmc.torch.item.ItemType held) {
            return BlockBehaviorTable.of(type.identifier())
                    .map(behavior -> BlockBehaviorTable.speedMultiplier(behavior, type, held))
                    .orElse(1.0);
        }
    }
}
