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
 * <p>The tick reproduces the historical 1.8 {@code TileEntityFurnace} cycle:
 * burning decrements the remaining burn time; when the furnace can smelt and
 * is not burning, one fuel item is consumed to ignite a cycle of its burn
 * value; while burning and smeltable, {@code cookTime} advances one per tick
 * and reaching {@link FurnaceRecipes#COOK_TICKS} smelts one result; any
 * interruption (no fuel, no smeltable input) resets {@code cookTime} to zero —
 * the historical punishment for pausing a furnace.</p>
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

    public FurnaceBlockEntity() {
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
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
     * One simulation tick of the smelting state machine. @return whether the
     * furnace is still (or newly) burning after the tick — used by tests and
     * the manager for lit-state decisions.
     */
    public boolean tick() {
        boolean wasBurning = burnTimeRemaining > 0;
        if (burnTimeRemaining > 0) {
            burnTimeRemaining--;
            propsSerial++;
        }
        if (!burning() && canSmelt()) {
            // Ignite: consume one fuel unit, start its burn cycle (historical).
            int burn = fuelBurnValue();
            if (burn > 0) {
                burnTimeRemaining = burn;
                burnTimeTotal = burn;
                consumeOne(SLOT_FUEL);
                propsSerial++;
            }
        }
        if (burning() && canSmelt()) {
            cookTime++;
            propsSerial++;
            if (cookTime >= FurnaceRecipes.COOK_TICKS) {
                cookTime = 0;
                smeltOne();
                slotsSerial++;
            }
        } else if (cookTime != 0) {
            // Not cooking: the historical reset (progress never persists).
            cookTime = 0;
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
        Optional<FurnaceRecipes.SmeltResult> recipe = FurnaceRecipes.resultOf(in.type());
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

    private void consumeOne(int slot) {
        ItemStack current = slots[slot];
        slots[slot] = current.withCount(current.count() - 1);
        slotsSerial++;
    }

    /** Moves one smelt result into the output slot (validated by {@link #canSmelt}). */
    private void smeltOne() {
        ItemStack in = slots[SLOT_INPUT];
        FurnaceRecipes.SmeltResult result =
                FurnaceRecipes.resultOf(in.type()).orElseThrow();
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
     * The output slot accepts placement of anything (the historical container
     * slot does not filter), but the tick machine only ever produces valid
     * results there.
     */
    public void clickSlot(int slot, int button, net.zaminmc.torch.server.player.PlayerInventory inventory) {
        rangeCheck(slot);
        net.zaminmc.torch.server.player.WindowClicks.click(slots, slot, button, inventory.cursorBox());
        slotsSerial++;
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
        slotsSerial++;
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
        slotsSerial++;
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
        if (FurnaceRecipes.resultOf(moving.type()).isPresent()) {
            target = SLOT_INPUT;
        } else if (FurnaceRecipes.burnTicksOf(moving.type()) > 0) {
            target = SLOT_FUEL;
        } else {
            return moving; // neither smeltable nor fuel: nothing accepted
        }
        ItemStack current = slots[target];
        if (current.isEmpty()) {
            slots[target] = moving;
            slotsSerial++;
            return ItemStack.EMPTY;
        }
        if (net.zaminmc.torch.server.player.WindowClicks.stacksMergeable(current, moving)
                && current.count() < current.type().maxStackSize()) {
            int capacity = current.type().maxStackSize() - current.count();
            int transfer = Math.min(capacity, moving.count());
            slots[target] = current.withCount(current.count() + transfer);
            slotsSerial++;
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
        slotsSerial++;
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
}
