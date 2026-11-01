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

    /**
     * The public §27.7 burn + smelt gates (Slice 82): fires the
     * FurnaceBurnEvent / FurnaceSmeltEvent at the entity's own decision
     * points. The world name is captured at the tick's entry (the gates
     * run on the tick thread inside the same tick call — the single
     * writer's own context).
     */
    private volatile net.zaminmc.torch.server.event.EngineEventBus eventBus;
    private String tickWorldName; // tick-thread only (captured at tick entry)

    /** Wires the public §27.7 gates (the burn + smelt observe hooks). */
    public void setEventBus(net.zaminmc.torch.server.event.EngineEventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    /** The entity-side gate adapter: the events over the captured world name. */
    private final FurnaceBlockEntity.Gates publicGates = new FurnaceBlockEntity.Gates() {
        @Override
        public boolean allowIgnite(net.zaminmc.torch.item.ItemStack fuel, int burnTicks) {
            var bus = eventBus;
            if (bus == null) {
                return true;
            }
            var event = new net.zaminmc.torch.event.block.FurnaceBurnEvent(
                    tickWorldName, gatePosition, fuel, burnTicks);
            bus.fire(event);
            return !event.cancelled();
        }

        @Override
        public boolean allowSmelt(net.zaminmc.torch.item.ItemStack input,
                                  net.zaminmc.torch.item.ItemStack result) {
            var bus = eventBus;
            if (bus == null) {
                return true;
            }
            var event = new net.zaminmc.torch.event.block.FurnaceSmeltEvent(
                    tickWorldName, gatePosition, input, result);
            bus.fire(event);
            return !event.cancelled();
        }
    };

    /** The firing entity's position (tick-thread only, set before each gate). */
    private BlockPosition gatePosition;

    /** Binds the per-position wake + the public gates to a block entity. */
    private void wire(BlockPosition position, FurnaceBlockEntity entity) {
        entity.setContentsListener(() -> {
            java.util.function.Consumer<BlockPosition> fan = contentsChanged;
            if (fan != null) {
                fan.accept(position);
            }
        });
        entity.setGates(publicGates);
    }

    /** @return the furnace state at the position, creating it on first use. */
    public FurnaceBlockEntity getOrCreate(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        FurnaceBlockEntity existing = furnaces.get(position);
        if (existing != null) {
            wire(position, existing); // restored entries wire lazily too
            return existing;
        }
        FurnaceBlockEntity created = new FurnaceBlockEntity();
        wire(position, created);
        furnaces.put(position, created);
        return created;
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
        restored.forEach((position, entity) -> wire(position, entity));
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
        tickWorldName = world.name(); // the gates' world row (this tick's own)
        furnaces.entrySet().removeIf(entry -> {
            BlockPosition position = entry.getKey();
            gatePosition = position; // the firing entity's own row
            FurnaceBlockEntity furnace = entry.getValue();
            if (!isFurnaceBlock(world, position)) {
                // The block-gone discard only decides on a LOADED chunk —
                // an unloaded chunk reads as air, but the vanilla block
                // entity freezes there (the restored state of a furnace
                // outside the spawn area survives the restart).
                return world.isChunkLoadedAt(position);
            }
            if (!world.isChunkLoadedAt(position)) {
                return false; // frozen: an unloaded chunk's furnace does not smelt
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

    /**
     * Swaps the furnace block to its lit (or unlit) variant, the historical
     * visual. Slice L3: the swap keeps the FACING whole
     * (AbstractFurnaceBlock.updateState's own write — the partner state
     * over the same Direction-id nibble).
     */
    private void syncLitBlock(EngineWorld world, BlockPosition position, boolean burning) {
        var swap = net.zaminmc.torch.server.furnace.FurnaceFacingBlocks.partnerOf(
                world.getBlock(position), burning);
        if (swap != null) {
            world.setBlock(position, swap);
        }
    }

    private static boolean isFurnaceBlock(EngineWorld world, BlockPosition position) {
        return net.zaminmc.torch.server.block.WorldSolidity.isFurnaceBlock(
                world.getBlock(position).identifier());
    }

    /**
     * A survival break committed on the furnace block: spill every slot into
     * the world at the block (nothing is lost, the historical container drop)
     * and forget the state. The legacy form stamps the overworld — the
     * per-dimension break path passes its own world's stamp.
     */
    public void onBlockBroken(BlockPosition position, ItemEntityManager items) {
        onBlockBroken(position, items, 0);
    }

    /**
     * The dimension-stamped break spill (Wave FL4-GAP2 Slice B): the drops
     * land in the SAME world the furnace stood in — a nether furnace's
     * spill never falls into the overworld.
     */
    public void onBlockBroken(BlockPosition position, ItemEntityManager items, int dimension) {
        FurnaceBlockEntity furnace = furnaces.remove(position);
        if (furnace == null || items == null) {
            return;
        }
        Position origin = new Position(position.x(), position.y(), position.z());
        for (ItemStack stack : furnace.snapshotSlots()) {
            if (!stack.isEmpty()) {
                items.spawnDropAtBlock(origin, stack, ItemEntity.PICKUP_DELAY_DROP_TICKS,
                        dimension);
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
                && (FurnaceRecipes.resultOf(peek.type(), peek.damage()).isPresent()
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
