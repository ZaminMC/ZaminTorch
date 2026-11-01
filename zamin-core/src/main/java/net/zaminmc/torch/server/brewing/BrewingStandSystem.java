package net.zaminmc.torch.server.brewing;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Position;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns every placed brewing stand's block-entity state of one world, keyed
 * by block position (Slice 11e — the same ownership model as the furnace
 * and the dispenser families). Simulation-thread confined: state creation,
 * the per-tick brew walk, click application and spill-on-break all run on
 * the world's owner.
 *
 * <h2>The per-tick walk</h2>
 * <p>Every stand advances its own {@link BrewingStandBlockEntity#tick()}
 * (the 400-tick cycle with the reference's cancellation gates); the
 * bottle-bits change performs the world write through the flattened
 * family's state swap (the reference's flag-2 setBlockState — the engine's
 * ordinary commit carrying the neighbors through the listener path). A
 * stand whose block is gone discards its state (the creative-break path,
 * the furnace's own rule).</p>
 *
 * <h2>The wire seam</h2>
 * <p>The slots-changed listener fans the viewing player's window resync
 * (the engine's authoritative Window Items), the same seam the furnace
 * carries; the brew timer rides the window's property 0.</p>
 */
public final class BrewingStandSystem {

    private final Map<BlockPosition, BrewingStandBlockEntity> stands = new LinkedHashMap<>();
    private final List<Listener> listeners = new java.util.ArrayList<>();

    /** Observers the protocol adapter translates into wire sync. */
    public interface Listener {
        /** A stand's slots changed outside the viewing player's own click. */
        void onBrewingSlotsChanged(BlockPosition position, BrewingStandBlockEntity stand);
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * The contents-changed wake (the comparator re-evaluation fan-out —
     * the reference's TileEntity.markDirty -> updateNeighborComparators).
     * Wired by the engine after the redstone system boots; runs on the
     * tick thread inside every slot mutation.
     */
    private java.util.function.Consumer<BlockPosition> contentsChanged;

    /** Wires the contents-changed wake (the redstone comparator fan-out). */
    public void setContentsChanged(java.util.function.Consumer<BlockPosition> listener) {
        this.contentsChanged = listener;
    }

    /** Binds the per-position wake to a block entity. */
    private void wire(BlockPosition position, BrewingStandBlockEntity entity) {
        entity.setContentsListener(() -> {
            java.util.function.Consumer<BlockPosition> fan = contentsChanged;
            if (fan != null) {
                fan.accept(position);
            }
        });
    }

    /** @return the stand state at the position, creating it on first use. */
    public BrewingStandBlockEntity getOrCreate(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        BrewingStandBlockEntity existing = stands.get(position);
        if (existing != null) {
            return existing;
        }
        BrewingStandBlockEntity created = new BrewingStandBlockEntity();
        wire(position, created);
        stands.put(position, created);
        return created;
    }

    /** @return the live state at the position, or null when the stand has none. */
    public BrewingStandBlockEntity peek(BlockPosition position) {
        return stands.get(position);
    }

    /** @return the whole container map (the save snapshot). */
    public Map<BlockPosition, BrewingStandBlockEntity> snapshot() {
        return new LinkedHashMap<>(stands);
    }

    /** Restores persisted state after boot. */
    public void restoreAll(Map<BlockPosition, BrewingStandBlockEntity> restored) {
        restored.forEach((position, entity) -> wire(position, entity));
        stands.putAll(restored);
    }

    // ------------------------------------------------------------------
    // The per-tick walk
    // ------------------------------------------------------------------

    /**
     * Advances every stand one tick (the reference's TileEntity.tick at
     * the world's own cadence): the brew cycle, then the bottle-bits world
     * write when the occupancy moved, then the viewer fan-out. State whose
     * block is no longer a brewing stand is discarded (the creative-break
     * path).
     */
    public void tick(EngineWorld world) {
        stands.entrySet().removeIf(entry -> {
            BlockPosition position = entry.getKey();
            BrewingStandBlockEntity stand = entry.getValue();
            if (!BrewingStandBlocks.isBrewingStand(world.getBlock(position))) {
                // The block-gone discard only decides on a LOADED chunk —
                // an unloaded chunk reads as air, but the vanilla block
                // entity freezes there (the restored state of a stand
                // outside the spawn area survives the restart).
                return world.isChunkLoadedAt(position);
            }
            if (!world.isChunkLoadedAt(position)) {
                return false; // frozen: an unloaded chunk's stand does not brew
            }
            int beforeSlots = stand.slotsSerial();
            stand.tick();
            if (stand.consumeBottleBitsChange()) {
                syncBottleBits(world, position, stand);
            }
            if (stand.slotsSerial() != beforeSlots) {
                for (Listener listener : listeners) {
                    listener.onBrewingSlotsChanged(position, stand);
                }
            }
            return false;
        });
    }

    /**
     * The bottle-bits world write (tick lines 76-91): the flattened state
     * swap carrying the three HAS_BOTTLE bits — the same commit path every
     * block change rides, so the neighbors hear it and the clients sync.
     */
    private void syncBottleBits(EngineWorld world, BlockPosition position,
                                BrewingStandBlockEntity stand) {
        var current = world.getBlock(position);
        if (!BrewingStandBlocks.isBrewingStand(current)) {
            return;
        }
        boolean[] occupancy = stand.findHasPotion();
        var next = BrewingStandBlocks.of(occupancy[0], occupancy[1], occupancy[2]);
        if (!current.equals(next)) {
            world.setBlock(position, next);
        }
    }

    /**
     * A survival break committed on the stand block: spill every slot into
     * the world at the block (nothing is lost, the historical container
     * drop through InventoryUtils.dropItems) and forget the state. The
     * legacy form stamps the overworld — the per-dimension break path
     * passes its own world's stamp.
     */
    public void onBlockBroken(BlockPosition position, ItemEntityManager items) {
        onBlockBroken(position, items, 0);
    }

    /**
     * The dimension-stamped break spill (Wave FL4-GAP2 Slice B): the drops
     * land in the SAME world the stand stood in.
     */
    public void onBlockBroken(BlockPosition position, ItemEntityManager items, int dimension) {
        BrewingStandBlockEntity stand = stands.remove(position);
        if (stand == null || items == null) {
            return;
        }
        Position origin = new Position(position.x(), position.y(), position.z());
        for (ItemStack stack : stand.snapshotSlots()) {
            if (!stack.isEmpty()) {
                items.spawnDropAtBlock(origin, stack, ItemEntity.PICKUP_DELAY_DROP_TICKS,
                        dimension);
            }
        }
    }

    // ------------------------------------------------------------------
    // The hopper surface (the SidedInventory port)
    // ------------------------------------------------------------------

    /**
     * The sided slot array (getSlots lines 280-282): the UP face feeds the
     * ingredient slot alone; every other face (including the hopper's own
     * pull from below) walks the three potion slots.
     */
    public static int[] slotsForFace(int face) {
        return face == 1 // UP
                ? new int[]{BrewingStandBlockEntity.SLOT_INGREDIENT}
                : new int[]{0, 1, 2};
    }

    /**
     * The push gate (canPushItem lines 284-287 over isItemAllowed): the
     * ingredient slot takes ingredients, the potion slots potions and
     * bottles — the same filter the menu's slots carry.
     */
    public static boolean canPushItem(int slot, ItemStack stack) {
        return BrewingStandBlockEntity.isItemAllowed(slot, stack);
    }
}
