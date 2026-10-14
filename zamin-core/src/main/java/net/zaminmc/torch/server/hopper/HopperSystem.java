package net.zaminmc.torch.server.hopper;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.chest.ChestBlockEntity;
import net.zaminmc.torch.server.chest.ChestManager;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.furnace.FurnaceBlockEntity;
import net.zaminmc.torch.server.furnace.FurnaceManager;
import net.zaminmc.torch.server.furnace.FurnaceRecipes;
import net.zaminmc.torch.server.player.WindowClicks;
import net.zaminmc.torch.server.redstone.RedstoneSystem;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;
import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The hopper transfer system (Slice 10b — the reference port of
 * block/entity/HopperBlockEntity.java's transfer walk + block/HopperBlock's
 * enabled/spill arms): the 8-tick cooldown cadence, the push toward the
 * FACING side's container, the pull from the container above (or the
 * item-entity suck), the powered disable, and the break spill. Every
 * transfer is one same-tick unit under the boundary scope covering the
 * hopper and its target (the Phase 4 spatial activation — a hopper feeding
 * a chest across a region seam stays vanilla-exact).
 *
 * <p>The container adapter walks the engine's three containers with the
 * reference's own sided rules (FurnaceBlockEntity.getSlots lines 326-333:
 * the top face reads the input slot, the bottom face the output then the
 * fuel, the sides the fuel; the chest and the hopper expose every slot on
 * every face). The pull-from-below furnace arm keeps the reference's
 * bucket-only fuel gate (canPullItem lines 340-348).</p>
 */
public final class HopperSystem implements WorldChangeListener {

    /** The six directions in the reference's Direction id order. */
    private static final int[][] OFFSETS = {
            {0, -1, 0}, // 0 DOWN
            {0, 1, 0},  // 1 UP
            {0, 0, -1}, // 2 NORTH
            {0, 0, 1},  // 3 SOUTH
            {-1, 0, 0}, // 4 WEST
            {1, 0, 0},  // 5 EAST
    };

    private final EngineWorld world;
    private final RedstoneSystem redstone;
    private final ItemEntityManager itemEntities;
    private final ChestManager chests;
    private final FurnaceManager furnaces;
    private final Map<BlockPosition, HopperBlockEntity> hoppers = new LinkedHashMap<>();
    /**
     * The dispenser family's container state (wired after construction —
     * the DispenserSystem itself needs this system for the dropper push,
     * so the cycle resolves through a late lookup).
     */
    private java.util.function.Function<BlockPosition,
            net.zaminmc.torch.server.dispenser.DispenserBlockEntity> dispenserLookup =
            position -> null;

    /** Wires the dispenser family's container lookup (the engine's boot order). */
    public void setDispenserLookup(
            java.util.function.Function<BlockPosition,
                    net.zaminmc.torch.server.dispenser.DispenserBlockEntity> lookup) {
        this.dispenserLookup = Objects.requireNonNull(lookup, "lookup");
    }

    /**
     * The brewing stands' container state (wired after construction like
     * the dispenser family's — the same late-lookup cycle).
     */
    private java.util.function.Function<BlockPosition,
            net.zaminmc.torch.server.brewing.BrewingStandBlockEntity> brewingLookup =
            position -> null;

    /** Wires the brewing stand family's container lookup. */
    public void setBrewingLookup(
            java.util.function.Function<BlockPosition,
                    net.zaminmc.torch.server.brewing.BrewingStandBlockEntity> lookup) {
        this.brewingLookup = Objects.requireNonNull(lookup, "lookup");
    }

    /**
     * The rolling containers' scan (Slice 25e — the reference's entity
     * fallback in getInventoryAt lines 466-472: when no block container
     * sits at the cell, the 1x1x1 box around the cell's center picks a
     * random INVENTORY entity — the chest minecart). Wired after
     * construction (the vehicle manager's boot order).
     */
    private java.util.function.Function<BlockPosition,
            java.util.List<net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity>>
            cartLookup = position -> java.util.List.of();

    /** Wires the rolling containers' scan (the cart arm). */
    public void setCartLookup(
            java.util.function.Function<BlockPosition,
                    java.util.List<net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity>>
                    lookup) {
        this.cartLookup = Objects.requireNonNull(lookup, "lookup");
    }

    /** The cart arm's own pick (getInventoryAt line 470: a random one from the box). */
    private final Random random = new Random();

    public HopperSystem(EngineWorld world, RedstoneSystem redstone,
                        ItemEntityManager itemEntities, ChestManager chests,
                        FurnaceManager furnaces) {
        this.world = Objects.requireNonNull(world, "world");
        this.redstone = Objects.requireNonNull(redstone, "redstone");
        this.itemEntities = Objects.requireNonNull(itemEntities, "itemEntities");
        this.chests = Objects.requireNonNull(chests, "chests");
        this.furnaces = Objects.requireNonNull(furnaces, "furnaces");
    }

    /** @return the block type the family registers at boot. */
    public static net.zaminmc.torch.server.block.BlockRegistryBuilder registerBlocks(
            net.zaminmc.torch.server.block.BlockRegistryBuilder builder) {
        return HopperBlocks.registerAll(builder);
    }

    /** @return the hopper state at the position, creating it on first use. */
    public HopperBlockEntity getOrCreate(BlockPosition position) {
        HopperBlockEntity existing = hoppers.get(position);
        if (existing != null) {
            return existing;
        }
        HopperBlockEntity created = new HopperBlockEntity();
        hoppers.put(position, created);
        return created;
    }

    /** @return the live state at the position, or null when the hopper has none. */
    public HopperBlockEntity peek(BlockPosition position) {
        return hoppers.get(position);
    }

    /**
     * @return whether a container sits at the position (the dropper's push
     * walk — DropperBlock.dispense's getInventoryAt: the chest, the hopper,
     * the furnace and the dispenser family all expose slots).
     */
    public boolean containerAt(BlockPosition position) {
        return !containerSlotsAt(position, 0).isEmpty();
    }

    /**
     * The dropper's push (Slice 11a — DropperBlock.dispense lines 41-63):
     * one item into the container at the position through its receiving
     * face. @return whether the item fit (the whole stack stays when it
     * does not — the reference's copy-restore arm).
     */
    public boolean pushOneIntoContainer(BlockPosition at, int containerFace, ItemStack moving) {
        List<ContainerSlot> slots = containerSlotsAt(at, containerFace);
        if (moving.isEmpty()) {
            return false;
        }
        ItemStack remainder = insertInto(slots, moving, at);
        if (remainder.isEmpty()) {
            noteReceivedByHopper(at);
            return true;
        }
        return false;
    }

    /** @return the whole hopper map (the comparator walk + the save snapshot). */
    public Map<BlockPosition, HopperBlockEntity> snapshot() {
        return new LinkedHashMap<>(hoppers);
    }

    /** Restores persisted state after boot. */
    public void restoreAll(Map<BlockPosition, HopperBlockEntity> restored) {
        hoppers.putAll(restored);
    }

    // ------------------------------------------------------------------
    // The transfer cycle (HopperBlockEntity.tick + transferItems)
    // ------------------------------------------------------------------

    /**
     * The per-tick walk (HopperBlockEntity.tick lines 166-174): every
     * hopper's cooldown runs down, and a hopper off cooldown zeroes it and
     * attempts the transfer. State whose block is no longer a hopper is
     * discarded (the creative-break path leaves no spill).
     */
    public void tick() {
        // The creative-break sweep: discard state only when its chunk is
        // LOADED and the block there is genuinely not a hopper. An unloaded
        // chunk reads as air, but the vanilla block-entity contract keeps
        // the state frozen until the chunk returns (the restored state of
        // a hopper outside the spawn area must survive the restart — the
        // first-tick eviction closed).
        hoppers.entrySet().removeIf(entry ->
                world.isChunkLoadedAt(entry.getKey())
                        && !HopperBlocks.isHopper(world.getBlock(entry.getKey())));
        for (Map.Entry<BlockPosition, HopperBlockEntity> entry : List.copyOf(
                new ArrayList<>(hoppers.entrySet()))) {
            BlockPosition position = entry.getKey();
            HopperBlockEntity hopper = entry.getValue();
            if (!world.isChunkLoadedAt(position)) {
                continue; // frozen: an unloaded chunk's hopper neither cools nor transfers
            }
            hopper.tickCooldown();
            if (!hopper.isOnCooldown()) {
                transferItems(position, hopper);
            }
        }
    }

    /**
     * One transfer attempt (HopperBlockEntity.transferItems lines 176-199):
     * a hopper holding anything pushes first; a hopper with room pulls
     * after; success restarts the 8-tick cooldown. The whole attempt is
     * one same-tick unit under the boundary scope spanning the hopper, its
     * push target, and the pull source above (the Phase 4 spatial
     * activation — a transfer across a region seam is vanilla-exact: no
     * latency, no split write).
     */
    private void transferItems(BlockPosition pos, HopperBlockEntity hopper) {
        BlockType type = world.getBlock(pos);
        if (!HopperBlocks.isHopper(type) || !HopperBlocks.hopperEnabled(type)) {
            return; // the powered-off pair never transfers
        }
        int facing = HopperBlocks.hopperFacing(type);
        BlockPosition target = pos.offset(OFFSETS[facing][0], OFFSETS[facing][1],
                OFFSETS[facing][2]);
        BlockPosition above = pos.offset(0, 1, 0);
        world.executeInRegions(
                min(pos, target, above), max(pos, target, above),
                () -> transferItemsInScope(pos, hopper, facing, target));
    }

    private void transferItemsInScope(BlockPosition pos, HopperBlockEntity hopper, int facing,
                                       BlockPosition target) {
        boolean moved = false;
        if (!hopper.isEmpty()) {
            moved = pushItem(hopper, facing, target);
        }
        if (!hopper.isFull()) {
            moved = pullItem(pos, hopper) || moved;
        }
        if (moved) {
            hopper.setCooldown(HopperBlockEntity.TRANSFER_COOLDOWN);
        }
    }

    private static BlockPosition min(BlockPosition a, BlockPosition b, BlockPosition c) {
        return new BlockPosition(Math.min(a.x(), Math.min(b.x(), c.x())),
                Math.min(a.y(), Math.min(b.y(), c.y())), Math.min(a.z(), Math.min(b.z(), c.z())));
    }

    private static BlockPosition max(BlockPosition a, BlockPosition b, BlockPosition c) {
        return new BlockPosition(Math.max(a.x(), Math.max(b.x(), c.x())),
                Math.max(a.y(), Math.max(b.y(), c.y())), Math.max(a.z(), Math.max(b.z(), c.z())));
    }

    /**
     * The push (HopperBlockEntity.pushItem lines 221-246): one item from
     * the first occupied slot toward the FACING side's container. The
     * receiving-hopper arm resets its cooldown (the reference's
     * canTransferNextTick inside the slot push).
     */
    private boolean pushItem(HopperBlockEntity hopper, int facing, BlockPosition target) {
        int targetFace = opposite(facing); // the face of the TARGET receiving the item
        List<ContainerSlot> slots = containerSlotsAt(target, targetFace);
        if (slots.isEmpty()) {
            return false;
        }
        if (isFullInventory(slots)) {
            return false;
        }
        for (int slot = 0; slot < HopperBlockEntity.SLOT_COUNT; slot++) {
            ItemStack moving = hopper.getItem(slot);
            if (moving.isEmpty()) {
                continue;
            }
            // ONE item per transfer (pushItem lines 234-243: removeItem(i, 1)
            // with the whole-stack copy held back for the restore).
            ItemStack held = moving;
            ItemStack one = ItemStack.of(moving.type(), 1);
            if (moving.count() > 1) {
                hopper.setItem(slot, moving.withCount(moving.count() - 1));
            } else {
                hopper.setItem(slot, ItemStack.EMPTY);
            }
            ItemStack remainder = insertInto(slots, one, target);
            if (remainder.isEmpty()) {
                noteReceivedByHopper(target);
                return true;
            }
            hopper.setItem(slot, held); // nothing fit: the original returns
        }
        return false;
    }

    /**
     * The pull (HopperBlockEntity.pullItem lines 296-331): one item from
     * the container above (its DOWN face); with no container there, the
     * item-entity suck from the box above the hopper.
     */
    private boolean pullItem(BlockPosition pos, HopperBlockEntity hopper) {
        BlockPosition above = pos.offset(0, 1, 0);
        List<ContainerSlot> slots = containerSlotsAt(above, 0 /* the container's DOWN face */);
        if (!slots.isEmpty()) {
            if (isEmptyInventory(slots)) {
                return false;
            }
            for (ContainerSlot source : slots) {
                if (!source.canPullFromBelow()) {
                    continue; // the furnace's bucket-only fuel gate
                }
                ItemStack moving = source.get();
                if (moving.isEmpty()) {
                    continue;
                }
                // ONE item per transfer (pullItems lines 333-347:
                // removeItem(slot, 1) with the whole-stack copy held back
                // for the restore).
                ItemStack held = moving;
                ItemStack one = ItemStack.of(moving.type(), 1);
                if (moving.count() > 1) {
                    source.set(moving.withCount(moving.count() - 1));
                } else {
                    source.set(ItemStack.EMPTY);
                }
                ItemStack remainder = insertIntoHopper(hopper, one);
                if (remainder.isEmpty()) {
                    return true;
                }
                source.set(held); // nothing fit: the original returns
            }
            return false;
        }
        // No container above: the item-entity suck (HopperBlockEntity
        // lines 323-327 — the box centered on the block above, one half
        // block out).
        for (ItemEntity item : itemEntities.all()) {
            Position at = item.position();
            if (Math.abs(at.x() - (pos.x() + 0.5)) <= 0.5
                    && Math.abs(at.z() - (pos.z() + 0.5)) <= 0.5
                    && Math.abs(at.y() - (pos.y() + 1.0)) <= 0.5) {
                ItemStack stack = item.stack();
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack remainder = insertIntoHopper(hopper, stack);
                if (remainder.isEmpty()) {
                    itemEntities.remove(item, "hopper-suck");
                    return true;
                }
                if (remainder.count() != stack.count()) {
                    item.setStack(remainder); // a partial suck keeps the rest
                    return true;
                }
            }
        }
        return false;
    }

    /** The stack-push walk (HopperBlockEntity.pushItem lines 367-429). */
    private static ItemStack insertInto(List<ContainerSlot> slots, ItemStack moving,
                                        BlockPosition containerAt) {
        for (ContainerSlot slot : slots) {
            if (moving.isEmpty()) {
                break;
            }
            ItemStack current = slot.get();
            if (!slot.allows(moving)) {
                continue;
            }
            if (current.isEmpty()) {
                slot.set(moving);
                moving = ItemStack.EMPTY;
            } else if (WindowClicks.stacksMergeable(current, moving)) {
                int capacity = current.type().maxStackSize() - current.count();
                int transfer = Math.min(moving.count(), capacity);
                if (transfer > 0) {
                    slot.set(current.withCount(current.count() + transfer));
                    moving = moving.withCount(moving.count() - transfer);
                }
            }
        }
        return moving;
    }

    /** The pull's target-side walk: the hopper itself accepts every item. */
    private static ItemStack insertIntoHopper(HopperBlockEntity hopper, ItemStack moving) {
        for (int slot = 0; slot < HopperBlockEntity.SLOT_COUNT && !moving.isEmpty(); slot++) {
            ItemStack current = hopper.getItem(slot);
            if (current.isEmpty()) {
                hopper.setItem(slot, moving);
                return ItemStack.EMPTY;
            }
            if (WindowClicks.stacksMergeable(current, moving)) {
                int capacity = current.type().maxStackSize() - current.count();
                int transfer = Math.min(moving.count(), capacity);
                if (transfer > 0) {
                    hopper.setItem(slot, current.withCount(current.count() + transfer));
                    moving = moving.withCount(moving.count() - transfer);
                }
            }
        }
        return moving;
    }

    /** The receiving-hopper cooldown reset (the slot push's arm). */
    private void noteReceivedByHopper(BlockPosition target) {
        HopperBlockEntity receiving = hoppers.get(target);
        if (receiving != null) {
            receiving.noteReceived();
        }
    }

    private static boolean isFullInventory(List<ContainerSlot> slots) {
        for (ContainerSlot slot : slots) {
            ItemStack current = slot.get();
            if (current.isEmpty() || current.count() < current.type().maxStackSize()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmptyInventory(List<ContainerSlot> slots) {
        for (ContainerSlot slot : slots) {
            if (!slot.get().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // The container adapter (the engine's chest/hopper/furnace BEs with
    // the reference's sided slot rules)
    // ------------------------------------------------------------------

    /** One addressable slot of a container at a position. */
    private interface ContainerSlot {
        ItemStack get();

        void set(ItemStack stack);

        boolean allows(ItemStack moving);

        /** The furnace's pull gate from below (canPullItem lines 340-348). */
        default boolean canPullFromBelow() {
            return true;
        }
    }

    /**
     * The container slots at the position for the given face of that
     * container (the reference's {@code getSlots(side)}: the furnace's top
     * face reads the input, the bottom the output then the fuel, the sides
     * the fuel — FurnaceBlockEntity lines 29-31 + 326-333; the chest and
     * the hopper expose every slot on every face). An empty list means no
     * container (or no slots for that face — never happens with these
     * three).
     */
    private List<ContainerSlot> containerSlotsAt(BlockPosition at, int face) {
        BlockType type = world.getBlock(at);
        if (type.identifier().equals(BuiltinBlocks.CHEST.identifier())) {
            ChestBlockEntity chest = chests.getOrCreate(at);
            List<ContainerSlot> slots = new ArrayList<>(ChestBlockEntity.SLOT_COUNT);
            for (int i = 0; i < ChestBlockEntity.SLOT_COUNT; i++) {
                slots.add(chestSlot(chest, i));
            }
            return slots;
        }
        if (HopperBlocks.isHopper(type)) {
            HopperBlockEntity hopper = getOrCreate(at);
            List<ContainerSlot> slots = new ArrayList<>(HopperBlockEntity.SLOT_COUNT);
            for (int i = 0; i < HopperBlockEntity.SLOT_COUNT; i++) {
                slots.add(hopperSlot(hopper, i));
            }
            return slots;
        }
        // The dispenser family (Slice 11a): nine unfiltered slots on every
        // face (the reference's DispenserBlockEntity.isValid always true).
        if (net.zaminmc.torch.server.dispenser.DispenserBlocks.isFamily(type)) {
            net.zaminmc.torch.server.dispenser.DispenserBlockEntity container =
                    dispenserLookup.apply(at);
            if (container != null) {
                List<ContainerSlot> slots =
                        new ArrayList<>(net.zaminmc.torch.server.dispenser.DispenserBlockEntity.SLOT_COUNT);
                for (int i = 0;
                     i < net.zaminmc.torch.server.dispenser.DispenserBlockEntity.SLOT_COUNT;
                     i++) {
                    slots.add(dispenserSlot(container, i));
                }
                return slots;
            }
            return List.of();
        }
        // The brewing stand (Slice 11e — getSlots lines 280-282): the UP
        // face feeds the ingredient slot alone; every other face walks
        // the three potion slots.
        if (net.zaminmc.torch.server.brewing.BrewingStandBlocks.isBrewingStand(type)) {
            net.zaminmc.torch.server.brewing.BrewingStandBlockEntity stand =
                    brewingLookup.apply(at);
            if (stand == null) {
                return List.of();
            }
            List<ContainerSlot> slots = new ArrayList<>(3);
            for (int slot : net.zaminmc.torch.server.brewing.BrewingStandSystem.slotsForFace(face)) {
                slots.add(brewingSlot(stand, slot, at));
            }
            return slots;
        }
        if (type.identifier().equals(BuiltinBlocks.FURNACE.identifier())
                || type.identifier().equals(BuiltinBlocks.FURNACE_LIT.identifier())) {
            FurnaceBlockEntity furnace = furnaces.getOrCreate(at);
            List<ContainerSlot> slots = new ArrayList<>(2);
            // The reference's sided arrays: top {input}, bottom {output,
            // fuel}, sides {fuel}.
            if (face == 1) {
                slots.add(furnaceSlot(furnace, FurnaceBlockEntity.SLOT_INPUT, at));
            } else if (face == 0) {
                slots.add(furnaceSlot(furnace, FurnaceBlockEntity.SLOT_OUTPUT, at));
                slots.add(furnaceSlot(furnace, FurnaceBlockEntity.SLOT_FUEL, at));
            } else {
                slots.add(furnaceSlot(furnace, FurnaceBlockEntity.SLOT_FUEL, at));
            }
            return slots;
        }
        // The entity fallback (Slice 25e — getInventoryAt lines 466-472):
        // no block container at the cell, so the 1x1x1 box around the
        // cell's center scans the INVENTORY entities — the chest minecart
        // parked above a hopper (or at its facing side) IS the container,
        // a random one picked when several share the cell.
        java.util.List<net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity> carts =
                cartLookup.apply(at);
        if (!carts.isEmpty()) {
            net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity cart =
                    carts.get(random.nextInt(carts.size()));
            ChestBlockEntity chest = cart.chest();
            List<ContainerSlot> slots = new ArrayList<>(ChestBlockEntity.SLOT_COUNT);
            for (int i = 0; i < ChestBlockEntity.SLOT_COUNT; i++) {
                slots.add(chestSlot(chest, i));
            }
            return slots;
        }
        return List.of();
    }

    private static ContainerSlot chestSlot(ChestBlockEntity chest, int slot) {
        return new ContainerSlot() {
            @Override
            public ItemStack get() {
                return chest.snapshotSlots()[slot];
            }

            @Override
            public void set(ItemStack stack) {
                chest.setSlot(slot, stack);
            }

            @Override
            public boolean allows(ItemStack moving) {
                return true; // a chest has no slot filters
            }
        };
    }

    private static ContainerSlot hopperSlot(HopperBlockEntity hopper, int slot) {
        return new ContainerSlot() {
            @Override
            public ItemStack get() {
                return hopper.getItem(slot);
            }

            @Override
            public void set(ItemStack stack) {
                hopper.setItem(slot, stack);
            }

            @Override
            public boolean allows(ItemStack moving) {
                return true; // a hopper has no slot filters
            }
        };
    }

    /** The dispenser family's slot adapter (no filters, every face). */
    private static ContainerSlot dispenserSlot(
            net.zaminmc.torch.server.dispenser.DispenserBlockEntity container, int slot) {
        return new ContainerSlot() {
            @Override
            public ItemStack get() {
                return container.getItem(slot);
            }

            @Override
            public void set(ItemStack stack) {
                container.setItem(slot, stack);
            }

            @Override
            public boolean allows(ItemStack moving) {
                return true;
            }
        };
    }

    /**
     * The brewing stand's slot (canPushItem lines 284-287): the ingredient
     * slot takes ingredients, the potion slots potions and bottles.
     */
    private static ContainerSlot brewingSlot(
            net.zaminmc.torch.server.brewing.BrewingStandBlockEntity stand, int slot,
            BlockPosition standAt) {
        return new ContainerSlot() {
            @Override
            public ItemStack get() {
                return stand.getItem(slot);
            }

            @Override
            public void set(ItemStack stack) {
                stand.setItem(slot, stack);
            }

            @Override
            public boolean allows(ItemStack moving) {
                return net.zaminmc.torch.server.brewing.BrewingStandSystem
                        .canPushItem(slot, moving);
            }
        };
    }

    private static ContainerSlot furnaceSlot(FurnaceBlockEntity furnace, int slot,
                                             BlockPosition furnaceAt) {
        return new ContainerSlot() {
            @Override
            public ItemStack get() {
                return furnace.snapshotSlots()[slot];
            }

            @Override
            public void set(ItemStack stack) {
                furnace.setSlot(slot, stack);
            }

            @Override
            public boolean allows(ItemStack moving) {
                // FurnaceBlockEntity.isItemAllowed lines 320-324: the
                // output slot never takes; the fuel slot takes fuel.
                if (slot == FurnaceBlockEntity.SLOT_OUTPUT) {
                    return false;
                }
                if (slot == FurnaceBlockEntity.SLOT_FUEL) {
                    return FurnaceRecipes.burnTicksOf(moving.type()) > 0
                            || moving.type().identifier().value().equals("bucket");
                }
                return true;
            }

            @Override
            public boolean canPullFromBelow() {
                // canPullItem lines 340-348: pulling the FUEL slot from
                // below only answers for buckets (the filled-bucket swap
                // after a burn); everything else pulls freely.
                if (slot == FurnaceBlockEntity.SLOT_FUEL) {
                    ItemStack current = furnace.snapshotSlots()[slot];
                    String value = current.isEmpty() ? "" : current.type().identifier().value();
                    return value.equals("bucket") || value.equals("water_bucket");
                }
                return true;
            }
        };
    }

    // ------------------------------------------------------------------
    // The block arms (HopperBlock's own walks)
    // ------------------------------------------------------------------

    /**
     * The placement wake (the reference's createBlockEntity at placement):
     * every committed hopper state creates its block-entity state — the
     * transfer cadence ticks from the moment the block lands. The world's
     * commit listener path (the engine's placement/replace surface, every
     * writer).
     */
    @Override
    public void onBlockChanged(EngineWorld changed, BlockPosition position, BlockType now) {
        if (HopperBlocks.isHopper(now)) {
            getOrCreate(position);
        }
    }

    /** The cross-system neighbor bus arm: the hopper near a notification re-arms. */
    public void neighborHeard(BlockPosition position) {
        // The notification's six neighbors: any hopper among them re-arms.
        for (int[] offset : OFFSETS) {
            updateEnabled(position.offset(offset[0], offset[1], offset[2]));
        }
        updateEnabled(position);
    }

    /**
     * The ENABLED pair flip (HopperBlock.updateEnabled lines 115-120,
     * through neighborChanged + onAdded): a hopper with any neighbor signal
     * powers off; unpowered it re-enables. The flag-4 write keeps the
     * commit listeners quiet (no re-entrant neighbor storm).
     */
    public void updateEnabled(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        if (!HopperBlocks.isHopper(type)) {
            return;
        }
        boolean shouldEnable = !hasNeighborSignal(pos);
        if (shouldEnable != HopperBlocks.hopperEnabled(type)) {
            world.setBlock(pos, HopperBlocks.hopperOfState(type, shouldEnable));
        }
    }

    /** The reference's hasNeighborSignal: any of the six neighbors emits. */
    private boolean hasNeighborSignal(BlockPosition pos) {
        for (int direction = 0; direction < 6; direction++) {
            int[] o = OFFSETS[direction];
            if (redstone.signal(pos.offset(o[0], o[1], o[2]), direction) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * A survival break committed on the hopper block (HopperBlock.onRemoved
     * lines 123-131): spill every slot into the world, then wake the
     * comparators. The creative path just discards (the per-tick sweep).
     */
    public void onBlockBroken(BlockPosition position) {
        HopperBlockEntity hopper = hoppers.remove(position);
        if (hopper == null) {
            return;
        }
        Position origin = new Position(position.x(), position.y(), position.z());
        for (ItemStack stack : hopper.snapshotSlots()) {
            if (!stack.isEmpty()) {
                itemEntities.spawnDropAtBlock(origin, stack, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
        }
        redstone.wakeComparators(position);
    }

    // ------------------------------------------------------------------
    // Placement helpers
    // ------------------------------------------------------------------

    /**
     * The placement facing (HopperBlock.getPlacementState lines 65-72): the
     * clicked face's OPPOSITE — an UP outcome clamps to DOWN (the FACING
     * predicate excludes UP).
     */
    public static int placementFacing(int clickedFace) {
        int facing = opposite(clickedFace & 7);
        return facing == 1 ? 0 : facing; // UP -> DOWN
    }

    /** The opposite direction id (public: the dropper's receiving face). */
    public static int opposite(int direction) {
        return switch (direction) {
            case 0 -> 1; // down <-> up
            case 1 -> 0;
            case 2 -> 3; // north <-> south
            case 3 -> 2;
            case 4 -> 5; // west <-> east
            default -> 4;
        };
    }

    /** @return the block type at the position (test surface). */
    BlockType blockAt(BlockPosition pos) {
        return world.getBlock(pos);
    }
}
