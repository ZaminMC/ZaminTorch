package net.zaminmc.torch.server.dispenser;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.player.WindowClicks;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

/**
 * One placed dispenser's or dropper's state (the historical tile entity,
 * reference/1.8.8 block/entity/DispenserBlockEntity.java): nine slots (the
 * 3x3 window layout, wire slots 0-8) living in the world at the block's
 * position — every viewer sees the same contents, they survive the window
 * closing, and breaking the block spills them (the historical container
 * behavior, the same ownership model as the chest and the furnace).
 *
 * <p>The slot-picking walk is the reference's own reservoir sample
 * (pickNonEmptySlot lines 61-72): every occupied slot wins with probability
 * 1/j where j counts the occupied slots seen so far, so all occupied slots
 * are equally likely and the walk stays O(n). The dispense cadence reads it
 * once per activation (DispenserBlock.dispense lines 96-112).</p>
 *
 * <p>Ownership: mutated only by the simulation context (the engine's click
 * routing, the hopper transfers and the dispense walk, all on the tick
 * thread).</p>
 */
public final class DispenserBlockEntity {

    /** The 1.8 dispenser GUI's own slot count (3 rows of 9). */
    public static final int SLOT_COUNT = 9;

    /** The slot-picking reservoir sample's own entropy (the reference's static). */
    private static final Random RANDOM = new Random();

    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];
    private int slotsSerial;

    /**
     * The contents-changed wake (the reference's {@code TileEntity.markDirty}
     * -> {@code updateNeighborComparators}): invoked on every slot mutation
     * so the comparators reading this container re-evaluate. Wired by the
     * system; runs on the tick thread.
     */
    private Runnable contentsListener;

    public DispenserBlockEntity() {
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    /** @return how many times the slot array mutated (wire sync dedup). */
    public int slotsSerial() {
        return slotsSerial;
    }

    /** Wires the contents-changed wake (the system's per-position fan-out). */
    public void setContentsListener(Runnable listener) {
        this.contentsListener = listener;
    }

    /** The mutation bump: the serial + the comparator wake. */
    private void bump() {
        slotsSerial++;
        if (contentsListener != null) {
            contentsListener.run();
        }
    }

    /** @return the stack at the slot (possibly empty). */
    public ItemStack getItem(int slot) {
        rangeCheck(slot);
        return slots[slot];
    }

    /** The direct write (the dispense remainder, the hopper transfers). */
    public void setItem(int slot, ItemStack stack) {
        rangeCheck(slot);
        ItemStack clamped = Objects.requireNonNull(stack, "stack");
        if (!clamped.isEmpty() && clamped.count() > clamped.type().maxStackSize()) {
            clamped = clamped.withCount(clamped.type().maxStackSize());
        }
        slots[slot] = clamped;
        bump();
    }

    /**
     * The occupied-slot pick (pickNonEmptySlot lines 61-72): the reservoir
     * sample — every occupied slot replaces the pick with probability 1/j
     * (j the count of occupied slots seen), so each is equally likely.
     *
     * @return the chosen slot, or -1 when every slot is empty.
     */
    public int pickNonEmptySlot() {
        int chosen = -1;
        int seen = 1;
        for (int slot = 0; slot < slots.length; slot++) {
            if (!slots[slot].isEmpty() && RANDOM.nextInt(seen++) == 0) {
                chosen = slot;
            }
        }
        return chosen;
    }

    /**
     * The first-empty insert (insertItem lines 84-93 — the bucket scoop's
     * give-back arm): places the stack at the first empty slot.
     *
     * @return the slot taken, or -1 when full.
     */
    public int insertItem(ItemStack moving) {
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot].isEmpty()) {
                setItem(slot, moving);
                return slot;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // The window-click semantics (the DispenserMenu over the same nine
    // slots — identical to the chest's walk, the count aside)
    // ------------------------------------------------------------------

    /**
     * Semantic left/right click on one slot (window click mode 0), sharing
     * the inventory's cursor through the historical click semantics.
     */
    public void clickSlot(int slot, int button, net.zaminmc.torch.server.player.PlayerInventory inventory) {
        rangeCheck(slot);
        WindowClicks.click(slots, slot, button, inventory.cursorBox());
        bump();
    }

    /**
     * Semantic drop-click (window click mode 4). @return the stack that
     * left the slot (possibly empty); the caller throws it into the world.
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
     * Semantic shift-click out of a slot: the stack moves into the
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
     * Semantic shift-click from the player inventory into the container: a
     * dispenser has no slot filters (the historical TileEntityDispenser's
     * isValid always true). The historical fill order is matching partial
     * stacks first, then the first empty slot; a remainder comes back.
     */
    public ItemStack quickMoveIn(ItemStack moving) {
        if (moving.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // Pass 1: merge into matching partial stacks (wire order 0..8).
        for (int slot = 0; slot < SLOT_COUNT && !moving.isEmpty(); slot++) {
            ItemStack current = slots[slot];
            if (!current.isEmpty()
                    && WindowClicks.stacksMergeable(current, moving)
                    && current.count() < current.type().maxStackSize()) {
                int capacity = current.type().maxStackSize() - current.count();
                int transfer = Math.min(capacity, moving.count());
                slots[slot] = current.withCount(current.count() + transfer);
                moving = moving.withCount(moving.count() - transfer);
            }
        }
        if (moving.isEmpty()) {
            bump();
            return ItemStack.EMPTY;
        }
        // Pass 2: the first empty slot takes the rest.
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (slots[slot].isEmpty()) {
                slots[slot] = moving;
                bump();
                return ItemStack.EMPTY;
            }
        }
        return moving; // full: the whole remainder comes back
    }

    /** @return a defensive copy of the nine slots, index-aligned. */
    public ItemStack[] snapshotSlots() {
        return slots.clone();
    }

    private static void rangeCheck(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("Dispenser slot out of range: " + slot);
        }
    }
}
