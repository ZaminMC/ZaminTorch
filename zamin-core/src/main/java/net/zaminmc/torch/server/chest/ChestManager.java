package net.zaminmc.torch.server.chest;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns every placed chest's block-entity state of one world, keyed by block
 * position. Simulation-thread confined like {@link ItemEntityManager}: state
 * creation, click application and spill-on-break all run on the world's
 * owner, so no locks are needed and ordering matches the tick.
 *
 * <p>Placement creates state lazily on first open (an untouched chest has no
 * state worth storing); the per-tick sweep discards state whose block is no
 * longer a chest (the creative-break path removes the block without the
 * survival spill); a survival break spills all 27 slots into the world (the
 * historical container drop) and forgets the state. Persistence goes through
 * {@link ChestDataStore} (ZCD v1) on the engine's save points.</p>
 */
public final class ChestManager {

    private final Map<BlockPosition, ChestBlockEntity> chests = new LinkedHashMap<>();

    /**
     * The contents-changed wake (Slice 9d): position -> the comparator
     * re-evaluation fan-out. Wired by the engine after the redstone system
     * boots; runs on the tick thread inside every slot mutation.
     */
    private java.util.function.Consumer<BlockPosition> contentsChanged;

    /** Wires the contents-changed wake (the redstone comparator fan-out). */
    public void setContentsChanged(java.util.function.Consumer<BlockPosition> listener) {
        this.contentsChanged = listener;
    }

    /** Binds the per-position wake to a block entity. */
    private void wire(BlockPosition position, ChestBlockEntity entity) {
        entity.setContentsListener(() -> {
            java.util.function.Consumer<BlockPosition> fan = contentsChanged;
            if (fan != null) {
                fan.accept(position);
            }
        });
    }

    /** @return the chest state at the position, creating it on first use. */
    public ChestBlockEntity getOrCreate(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        ChestBlockEntity existing = chests.get(position);
        if (existing != null) {
            wire(position, existing); // restored entries wire lazily too
            return existing;
        }
        ChestBlockEntity created = new ChestBlockEntity();
        wire(position, created);
        chests.put(position, created);
        return created;
    }

    /** @return the live state at the position, or null when the chest has none. */
    public ChestBlockEntity peek(BlockPosition position) {
        return chests.get(position);
    }

    /** @return a defensive copy of the whole chest map (consistent save snapshot). */
    public Map<BlockPosition, ChestBlockEntity> snapshot() {
        return new LinkedHashMap<>(chests);
    }

    /** Restores persisted state after boot (world deltas already applied). */
    public void restoreAll(Map<BlockPosition, ChestBlockEntity> restored) {
        restored.forEach((position, entity) -> wire(position, entity));
        chests.putAll(restored);
    }

    /**
     * Discards state whose block is no longer a chest (creative break removes
     * the block without the survival spill path). Runs once per tick on the
     * world owner; a chest has no advancing state of its own.
     */
    public void tick(EngineWorld world) {
        chests.entrySet().removeIf(entry ->
                !isChestBlock(world, entry.getKey()));
    }

    private static boolean isChestBlock(EngineWorld world, BlockPosition position) {
        return world.getBlock(position).identifier()
                .equals(BuiltinBlocks.CHEST.identifier());
    }

    /**
     * A survival break committed on the chest block: spill every slot into the
     * world at the block (nothing is lost, the historical container drop) and
     * forget the state.
     */
    public void onBlockBroken(BlockPosition position, ItemEntityManager items) {
        ChestBlockEntity chest = chests.remove(position);
        if (chest == null || items == null) {
            return;
        }
        Position origin = new Position(position.x(), position.y(), position.z());
        for (ItemStack stack : chest.snapshotSlots()) {
            if (!stack.isEmpty()) {
                items.spawnDropAtBlock(origin, stack, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
        }
    }
}
