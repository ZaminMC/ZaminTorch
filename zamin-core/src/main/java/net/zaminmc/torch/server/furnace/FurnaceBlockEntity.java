package net.zaminmc.torch.server.furnace;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;

import java.util.Objects;
import java.util.Optional;

/**
 * One placed furnace's state (the historical tile entity): three slots
 * (community-verified window layout — 0 input, 1 fuel, 2 output) plus the
 * burn/cook cycle. Slots live in the world, not in any player session: every
 * viewer of the furnace sees the same state, contents survive the window
 * closing and the server restarting (ZFD persistence), exactly like the
 * historical model.
 *
 * <p>The tick reproduces the historical 1.8 {@code TileEntityFurnace} cycle
 * (FurnaceBlockEntity.tick lines 168-216): burning decrements the remaining
 * burn time; when the furnace can smelt and is not burning, one fuel item is
 * consumed to ignite a cycle of its burn value — the last unit leaving the
 * item's recipe remainder when it carries one (the lava bucket's empty
 * bucket); while burning and smeltable, {@code cookTime} advances one per
 * tick and reaching {@link FurnaceRecipes#COOK_TICKS} smelts one result; the
 * interruption pays the reference's own two laws — the GUARDED block (the
 * furnace burning, or both a fuel item and an input present) hard-resets
 * {@code cookTime} to zero, while outside that guard the progress DECAYS by
 * two per tick, never a whole reset (lines 203-204).</p>
 *
 * <p>Ownership: mutated only by the simulation context (the furnace manager's
 * tick and the engine's click routing on the tick thread). Change serials let
 * the wire layer sync only what moved.</p>
 */
public final class FurnaceBlockEntity {

    /** Slot indexes of the community-verified furnace window layout. */
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];
    private int burnTimeRemaining; // ticks left of the current burn cycle
    private int burnTimeTotal;    // burn value of the fuel that ignited it
    private int cookTime;         // progress into the 200-tick cook cycle

    private int slotsSerial;      // bumped on any slot mutation
    private int propsSerial;      // bumped on any burn/cook mutation

    /**
     * The contents-changed wake (Slice 9d — the reference's
     * {@code TileEntity.markDirty} -> {@code updateNeighborComparators}):
     * invoked on every slot mutation so the comparators reading this
     * furnace re-evaluate. Wired by the manager; runs on the tick thread.
     */
    private Runnable contentsListener;

    /**
     * The public §27.7 gates (the burn + smelt observe hooks — Slice 82):
     * consulted at the ignite decision and the smelt completion, before
     * any slot mutation. A null gate (the unwired window) admits
     * everything. Wired by the manager; runs on the tick thread.
     */
    public interface Gates {
        /** @return whether the ignition stands (the fuel is consumed). */
        boolean allowIgnite(ItemStack fuel, int burnTicks);

        /** @return whether the smelt commits (the input is consumed). */
        boolean allowSmelt(ItemStack input, ItemStack result);
    }

    private Gates gates;

    public FurnaceBlockEntity() {
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
    }

    /** Wires the contents-changed wake (the manager's per-position fan-out). */
    public void setContentsListener(Runnable listener) {
        this.contentsListener = listener;
    }

    /** Wires the public §27.7 gates (the manager's burn + smelt observe hooks). */
    public void setGates(Gates gates) {
        this.gates = gates;
    }

    /** The slot-mutation bump: the serial + the comparator wake. */
    private void bump() {
        slotsSerial++;
        if (contentsListener != null) {
            contentsListener.run();
        }
    }

    public ItemStack input() {
        return slots[SLOT_INPUT];
    }

    public ItemStack fuel() {
        return slots[SLOT_FUEL];
    }

    public ItemStack output() {
        return slots[SLOT_OUTPUT];
    }

    public int burnTimeRemaining() {
        return burnTimeRemaining;
    }

    public int burnTimeTotal() {
        return burnTimeTotal;
    }

    public int cookTime() {
        return cookTime;
    }

    public boolean burning() {
        return burnTimeRemaining > 0;
    }

    /** @return how many times the slot array mutated (wire sync dedup). */
    public int slotsSerial() {
        return slotsSerial;
    }

    /** @return how many times the burn/cook state mutated (property sync dedup). */
    public int propsSerial() {
        return propsSerial;
    }

    /**
     * One simulation tick of the smelting state machine, the reference's own
     * shape (FurnaceBlockEntity.tick lines 168-216): the burn decrement runs
     * first; then the tick's outer GUARD — burning, or both a fuel item and
     * an input present — decides which interruption law applies. INSIDE the
     * guard the furnace ignites when it can smelt, and the cook either
     * advances or hard-resets (the reference's {@code else { cookTime = 0 }
     * — a burning furnace with its input pulled loses the progress, the
     * historical punishment). OUTSIDE the guard — not burning with the fuel
     * or the input gone — the progress DECAYS by two per tick (lines
     * 203-204's {@code clamp(cookTime - 2, 0, totalCookTime)}), never the
     * hard reset the pre-audit fold paid both arms alike.
     *
     * @return whether the furnace is still (or newly) burning after the
     *         tick — used by tests and the manager for lit-state decisions.
     */
    public boolean tick() {
        boolean wasBurning = burnTimeRemaining > 0;
        if (burnTimeRemaining > 0) {
            burnTimeRemaining--;
            propsSerial++;
        }
        boolean fuelPresent = !slots[SLOT_FUEL].isEmpty();
        boolean inputPresent = !slots[SLOT_INPUT].isEmpty();
        if (burning() || (fuelPresent && inputPresent)) {
            // The guarded block (reference lines 177-202).
            if (!burning() && canSmelt()) {
                // Ignite: consume one fuel unit, start its burn cycle
                // (historical) — the last unit leaves the item's recipe
                // remainder in the slot when it carries one.
                int burn = fuelBurnValue();
                if (burn > 0 && (gates == null || gates.allowIgnite(slots[SLOT_FUEL], burn))) {
                    burnTimeRemaining = burn;
                    burnTimeTotal = burn;
                    consumeFuel();
                    propsSerial++;
                }
            }
            if (burning() && canSmelt()) {
                cookTime++;
                propsSerial++;
                if (cookTime >= FurnaceRecipes.COOK_TICKS) {
                    cookTime = 0;
                    if (gates == null || gates.allowSmelt(slots[SLOT_INPUT], smeltResultOf(slots[SLOT_INPUT]))) {
                        smeltOne();
                        bump();
                    }
                }
            } else if (cookTime != 0) {
                // The guarded hard reset: burning without a smeltable input
                // (or blocked by a full output) loses the progress whole.
                cookTime = 0;
                propsSerial++;
            }
        } else if (!burning() && cookTime > 0) {
            // The unguarded decay (reference lines 203-204): two points per
            // tick, clamped at zero — the cook window closes slowly when the
            // fire and the input part between smelts.
            cookTime = Math.max(cookTime - 2, 0);
            propsSerial++;
        }
        return burning() || wasBurning;
    }

    /** @return whether a smelt would produce a valid output right now. */
    private boolean canSmelt() {
        ItemStack in = slots[SLOT_INPUT];
        if (in.isEmpty()) {
            return false;
        }
        Optional<FurnaceRecipes.SmeltResult> recipe = FurnaceRecipes.resultOf(in.type(), in.damage());
        if (recipe.isEmpty()) {
            return false;
        }
        FurnaceRecipes.SmeltResult result = recipe.get();
        ItemStack out = slots[SLOT_OUTPUT];
        if (out.isEmpty()) {
            return true;
        }
        Optional<ItemType> outType = net.zaminmc.torch.server.item.BuiltinItems.lookup(result.output());
        return outType.isPresent()
                && net.zaminmc.torch.item.ItemStack.mergeable(out,
                        new net.zaminmc.torch.item.ItemStack(outType.get(), 1, result.damage()))
                && out.count() + result.count() <= out.type().maxStackSize();
    }

    private int fuelBurnValue() {
        ItemStack fuel = slots[SLOT_FUEL];
        return fuel.isEmpty() ? 0 : FurnaceRecipes.burnTicksOf(fuel.type());
    }

    /** The would-be output of one smelt (the gate's payload — a pure read). */
    private ItemStack smeltResultOf(ItemStack in) {
        FurnaceRecipes.SmeltResult result =
                FurnaceRecipes.resultOf(in.type(), in.damage()).orElseThrow();
        ItemType outType = net.zaminmc.torch.server.item.BuiltinItems.lookup(result.output())
                .orElseThrow();
        return new ItemStack(outType, result.count(), result.damage());
    }

    private void consumeOne(int slot) {
        ItemStack current = slots[slot];
        slots[slot] = current.withCount(current.count() - 1);
        bump();
    }

    /**
     * The fuel stack's own consumption (the reference tick lines 182-188:
     * {@code size--}, then the recipe-remainder consult when the stack
     * empties) — one unit burns per ignition, and the LAST unit leaves
     * {@link FurnaceRecipes#fuelRemainderOf its leftover} in the slot when
     * the item carries one: the lava bucket's empty bucket (Item.java line
     * 611's {@code setRecipeRemainder}). The smelting input consumes clean
     * ({@code finishCooking} never consults remainders) — only the fuel
     * stack rides this arm.
     */
    private void consumeFuel() {
        ItemStack current = slots[SLOT_FUEL];
        int left = current.count() - 1;
        if (left > 0) {
            slots[SLOT_FUEL] = current.withCount(left);
        } else {
            ItemType remainder = FurnaceRecipes.fuelRemainderOf(current.type());
            slots[SLOT_FUEL] = remainder == null
                    ? ItemStack.EMPTY
                    : new ItemStack(remainder, 1, 0);
        }
        bump();
    }

    /** Moves one smelt result into the output slot (validated by {@link #canSmelt}). */
    private void smeltOne() {
        ItemStack in = slots[SLOT_INPUT];
        FurnaceRecipes.SmeltResult result =
                FurnaceRecipes.resultOf(in.type(), in.damage()).orElseThrow();
        ItemType outType = net.zaminmc.torch.server.item.BuiltinItems.lookup(result.output())
                .orElseThrow();
        ItemStack out = slots[SLOT_OUTPUT];
        if (out.isEmpty()) {
            slots[SLOT_OUTPUT] = new ItemStack(outType, result.count(), result.damage());
        } else {
            slots[SLOT_OUTPUT] = out.withCount(out.count() + result.count());
        }
        consumeOne(SLOT_INPUT);
    }

    // ------------------------------------------------------------------ slot access

    /**
     * Semantic left/right click on one furnace slot (window click mode 0),
     * sharing the inventory's cursor through the historical click semantics.
     * The window's three slots carry the menu's own gates (FurnaceMenu
     * lines 22-24): the input is a plain InventorySlot (no filter — dirt may
     * wait in it, it just never smelts); the fuel slot is the
     * FurnaceFuelSlot (isFuel OR the empty bucket — the bucket the
     * remainder leaves behind — stacking to ONE in the slot,
     * FurnaceFuelSlot.getMaxStackSize's own row); the output is the
     * FurnaceResultSlot, whose {@code isItemAllowed} answers FALSE: taking
     * only, never placement (the pre-audit comment claimed no slot filtered
     * — two of the three do).
     */
    public void clickSlot(int slot, int button, net.zaminmc.torch.server.player.PlayerInventory inventory) {
        rangeCheck(slot);
        ItemStack carried = inventory.cursor();
        int slotMax = 64;
        net.zaminmc.torch.server.player.WindowClicks.SlotFilter filter =
                net.zaminmc.torch.server.player.WindowClicks.ANY;
        if (slot == SLOT_FUEL) {
            // FurnaceFuelSlot lines 14-25: fuel or the EMPTY bucket, and the
            // bucket caps at one unit in the slot.
            filter = stack -> FurnaceRecipes.burnTicksOf(stack.type()) > 0
                    || stack.type().identifier().value().equals("bucket");
            if (carried.type() != null
                    && carried.type().identifier().value().equals("bucket")) {
                slotMax = 1;
            }
        } else if (slot == SLOT_OUTPUT) {
            // FurnaceResultSlot lines 21-24: placement refused outright;
            // taking never gates.
            filter = stack -> false;
        }
        net.zaminmc.torch.server.player.WindowClicks.click(
                slots, slot, button, inventory.cursorBox(), slotMax, filter);
        bump();
    }

    /**
     * Semantic drop-click (window click mode 4) on a furnace slot. @return the
     * stack that left the slot (possibly empty); the caller throws it into the
     * world.
     */
    public ItemStack dropFromSlot(int slot, boolean entireStack) {
        rangeCheck(slot);
        ItemStack current = slots[slot];
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack dropped = entireStack ? current : current.split(1);
        slots[slot] = current.withCount(current.count() - dropped.count());
        bump();
        return dropped;
    }

    /**
     * Semantic shift-click out of a furnace slot: the stack moves into the
     * inventory; a remainder stays (historical full-inventory behavior).
     */
    public void quickMoveToInventory(int slot, net.zaminmc.torch.server.player.PlayerInventory inventory) {
        rangeCheck(slot);
        ItemStack moving = slots[slot];
        if (moving.isEmpty()) {
            return;
        }
        slots[slot] = ItemStack.EMPTY;
        ItemStack remaining = inventory.pickUp(moving);
        if (!remaining.isEmpty()) {
            slots[slot] = remaining;
        }
        bump();
    }

    /**
     * Semantic shift-click from the player inventory into the furnace: the
     * historical routing sends smeltable stacks to the input slot and fuel
     * stacks to the fuel slot; anything else is refused. The caller has
     * already removed the stack from the inventory; @return the part that did
     * not fit (possibly empty — the caller puts it back via pickUp).
     */
    public ItemStack quickMoveIn(ItemStack moving) {
        Objects.requireNonNull(moving, "moving");
        if (moving.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int target;
        if (FurnaceRecipes.resultOf(moving.type(), moving.damage()).isPresent()) {
            target = SLOT_INPUT;
        } else if (FurnaceRecipes.burnTicksOf(moving.type()) > 0) {
            target = SLOT_FUEL;
        } else {
            return moving; // neither smeltable nor fuel: nothing accepted
        }
        ItemStack current = slots[target];
        if (current.isEmpty()) {
            slots[target] = moving;
            bump();
            return ItemStack.EMPTY;
        }
        if (net.zaminmc.torch.server.player.WindowClicks.stacksMergeable(current, moving)
                && current.count() < current.type().maxStackSize()) {
            int capacity = current.type().maxStackSize() - current.count();
            int transfer = Math.min(capacity, moving.count());
            slots[target] = current.withCount(current.count() + transfer);
            bump();
            return moving.withCount(moving.count() - transfer);
        }
        return moving; // mismatched or full target: the whole stack comes back
    }

    /** @return a defensive copy of the three slots, index-aligned. */
    public ItemStack[] snapshotSlots() {
        return slots.clone();
    }

    /** Direct slot write for engine commands only (persistence restore, seeding). */
    public void setSlot(int slot, ItemStack stack) {
        rangeCheck(slot);
        slots[slot] = Objects.requireNonNull(stack, "stack");
        bump();
    }

    /** Direct state write for engine commands only (persistence restore). */
    public void restore(int burnRemaining, int burnTotal, int cook) {
        this.burnTimeRemaining = Math.max(0, burnRemaining);
        this.burnTimeTotal = Math.max(0, burnTotal);
        this.cookTime = Math.max(0, cook);
        propsSerial++;
    }

    private static void rangeCheck(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("Furnace slot out of range: " + slot);
        }
    }

    // The container's own rows (Slice 91 Wave C p3 — the reference's
    // InventoryBlockEntity + the per-family Nameable): the lock key
    // (InventoryLock — "" = none; the named-key gate on the open path)
    // and the custom name (CustomName — the window title override).
    private String lockKey = "";
    private String customName;

    /** The lock key ("" = none — InventoryLock.NONE's own shape). */
    public String lockKey() {
        return lockKey;
    }

    public void setLockKey(String lockKey) {
        this.lockKey = lockKey == null ? "" : lockKey;
    }

    /** The custom name (null = none — the window title falls to the family's own). */
    public String customName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = (customName == null || customName.isEmpty()) ? null : customName;
    }
}
