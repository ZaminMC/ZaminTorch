package net.zaminmc.torch.server.furnace;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.player.PlayerInventory;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns every placed furnace's block-entity state of one world, keyed by block
 * position. Simulation-thread confined like {@link ItemEntityManager}: state
 * creation, ticking, click application and spill-on-break all run on the
 * world's owner, so no locks are needed and ordering matches the tick.
 *
 * <p>Placement creates state lazily on first open (an untouched furnace has no
 * state worth storing); breaking the furnace spills all three slots into the
 * world (the historical container drop) and forgets the state. Persistence
 * goes through {@link FurnaceDataStore} (ZFD v1) on the engine's save points.
 * The lit visual state (block 62 in 1.8) is a later slice's concern — the GUI
 * flame carries the burn state on the wire.</p>
 */
public final class FurnaceManager {

    private final Map<BlockPosition, FurnaceBlockEntity> furnaces = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    /** Observers the protocol adapter translates into wire sync. */
    public interface Listener {
        /** A furnace's slots changed outside the viewing player's own click. */
        void onFurnaceSlotsChanged(BlockPosition position, FurnaceBlockEntity furnace);
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** @return the furnace state at the position, creating it on first use. */
    public FurnaceBlockEntity getOrCreate(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        return furnaces.computeIfAbsent(position, p -> new FurnaceBlockEntity());
    }

    /** @return the live state at the position, or null when the furnace has none. */
    public FurnaceBlockEntity peek(BlockPosition position) {
        return furnaces.get(position);
    }

    /** @return an immutable snapshot of all tracked positions (persistence). */
    public List<BlockPosition> positions() {
        return List.copyOf(furnaces.keySet());
    }

    /** @return the live state at a tracked position (persistence + tests). */
    public FurnaceBlockEntity at(BlockPosition position) {
        return furnaces.get(position);
    }

    /** Restores persisted state after boot (world deltas already applied). */
    public void restoreAll(Map<BlockPosition, FurnaceBlockEntity> restored) {
        furnaces.putAll(restored);
    }

    /** @return a defensive copy of the whole furnace map (consistent save snapshot). */
    public Map<BlockPosition, FurnaceBlockEntity> snapshot() {
        return new LinkedHashMap<>(furnaces);
    }

    /**
     * Advances every furnace one tick, then discards state whose block is no
     * longer a furnace (creative break removes the block without the survival
     * spill path; the state has nowhere to live anymore). The burn state
     * drives the historical block swap: a burning furnace reads as the lit
     * variant (62) and cools back to the plain furnace (61) when the fire
     * dies — the block change also feeds the light engine its 13-level glow.
     */
    public void tick(EngineWorld world, ItemEntityManager items) {
        furnaces.entrySet().removeIf(entry -> {
            BlockPosition position = entry.getKey();
            FurnaceBlockEntity furnace = entry.getValue();
            if (!isFurnaceBlock(world, position)) {
                return true; // block gone: state discarded (creative break path)
            }
            int beforeSlots = furnace.slotsSerial();
            boolean wasBurning = furnace.burning();
            furnace.tick();
            if (furnace.slotsSerial() != beforeSlots) {
                for (Listener listener : listeners) {
                    listener.onFurnaceSlotsChanged(position, furnace);
                }
            }
            if (wasBurning != furnace.burning()) {
                syncLitBlock(world, position, furnace.burning());
            }
            return false;
        });
    }

    /** Swaps the furnace block to its lit (or unlit) variant, the historical visual. */
    private void syncLitBlock(EngineWorld world, BlockPosition position, boolean burning) {
        var expected = burning
                ? net.zaminmc.torch.server.block.BuiltinBlocks.FURNACE
                : net.zaminmc.torch.server.block.BuiltinBlocks.FURNACE_LIT;
        if (world.getBlock(position).equals(expected)) {
            world.setBlock(position, burning
                    ? net.zaminmc.torch.server.block.BuiltinBlocks.FURNACE_LIT
                    : net.zaminmc.torch.server.block.BuiltinBlocks.FURNACE);
        }
    }

    private static boolean isFurnaceBlock(EngineWorld world, BlockPosition position) {
        return net.zaminmc.torch.server.block.WorldSolidity.isFurnaceBlock(
                world.getBlock(position).identifier());
    }

    /**
     * A survival break committed on the furnace block: spill every slot into
     * the world at the block (nothing is lost, the historical container drop)
     * and forget the state.
     */
    public void onBlockBroken(BlockPosition position, ItemEntityManager items) {
        FurnaceBlockEntity furnace = furnaces.remove(position);
        if (furnace == null || items == null) {
            return;
        }
        Position origin = new Position(position.x(), position.y(), position.z());
        for (ItemStack stack : furnace.snapshotSlots()) {
            if (!stack.isEmpty()) {
                items.spawnDropAtBlock(origin, stack, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
        }
    }

    // ------------------------------------------------------------------ click routing

    /** Semantic left/right click (mode 0) on a furnace slot, sharing the cursor. */
    public void clickSlot(FurnaceBlockEntity furnace, int slot, int button, PlayerInventory inventory) {
        furnace.clickSlot(slot, button, inventory);
    }

    /**
     * Semantic shift-click (mode 1): furnace slots quick-move out to the
     * inventory; player-inventory slots quick-move in (smeltable to input,
     * fuel to fuel, the historical routing). Non-furnace items are refused
     * before leaving the inventory (a shift-click must never shuffle them).
     * @return whether anything moved.
     */
    public boolean quickMove(FurnaceBlockEntity furnace, int furnaceSlot, boolean fromFurnace,
                             int engineSlot, PlayerInventory inventory) {
        if (fromFurnace) {
            int before = furnace.slotsSerial();
            furnace.quickMoveToInventory(furnaceSlot, inventory);
            return furnace.slotsSerial() != before;
        }
        ItemStack peek = inventory.snapshot().get(engineSlot);
        boolean useful = !peek.isEmpty()
                && (FurnaceRecipes.resultOf(peek.type()).isPresent()
                    || FurnaceRecipes.burnTicksOf(peek.type()) > 0);
        if (!useful) {
            return false; // neither smeltable nor fuel: the click does nothing
        }
        ItemStack moving = inventory.dropFromSlot(engineSlot, true);
        ItemStack remainder = furnace.quickMoveIn(moving);
        if (!remainder.isEmpty()) {
            inventory.pickUp(remainder); // target slot full: put the rest back
        }
        return true;
    }

    /** Semantic drop-click (mode 4) on a furnace slot. @return what left the slot. */
    public ItemStack dropFromSlot(FurnaceBlockEntity furnace, int slot, boolean entireStack) {
        return furnace.dropFromSlot(slot, entireStack);
    }
}
