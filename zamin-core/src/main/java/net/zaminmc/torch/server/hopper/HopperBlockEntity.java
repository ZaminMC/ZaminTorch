package net.zaminmc.torch.server.hopper;

import net.zaminmc.torch.item.ItemStack;

import java.util.Arrays;

/**
 * One placed hopper's state (the historical tile entity —
 * reference/1.8.8 block/entity/HopperBlockEntity.java): five slots, the
 * transfer cooldown, and the contents-changed wake. Slots live in the
 * world keyed by position (the engine's container model, the same
 * ownership as the chest and furnace): every viewer sees the same
 * contents, a break spills them, and every slot mutation re-arms the
 * comparators reading the hopper (the reference's {@code markDirty} ->
 * {@code updateNeighborComparators}).
 *
 * <p>The cooldown starts at {@code -1} (the reference's own initial — a
 * freshly placed hopper transfers on its first tick); a successful
 * transfer sets {@code 8} (the two-item-per-second vanilla cadence); and a
 * hopper that RECEIVES an item resets to {@code 8} while idle — the
 * reference's {@code canTransferNextTick} arm inside the slot push.</p>
 *
 * <p>Ownership: mutated only by the simulation context (the manager's
 * transfer walk and the spill path, all on the tick thread).</p>
 */
public final class HopperBlockEntity {

    /** The hopper GUI's own slot count. */
    public static final int SLOT_COUNT = 5;
    /** The transfer cooldown after a successful transfer (4 items/second... 2 per second: 8 ticks). */
    public static final int TRANSFER_COOLDOWN = 8;

    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];
    private int transferCooldown = -1;
    /** The mutation counter (wire sync dedup — the chest's own pattern). */
    private int slotsSerial;

    /** The contents-changed wake (the comparator re-evaluation fan-out). */
    private Runnable contentsListener;

    public HopperBlockEntity() {
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    /** Wires the contents-changed wake. */
    public void setContentsListener(Runnable listener) {
        this.contentsListener = listener;
    }

    /** The mutation bump: the serial + the comparator wake (the markDirty port). */
    private void bump() {
        slotsSerial++;
        if (contentsListener != null) {
            contentsListener.run();
        }
    }

    /** @return how many times the slot array mutated (wire sync dedup). */
    public int slotsSerial() {
        return slotsSerial;
    }

    /** The cooldown bookkeeping (HopperBlockEntity.tick lines 166-174). */
    public void tickCooldown() {
        transferCooldown--;
        if (!isOnCooldown()) {
            setCooldown(0);
        }
    }

    /** @return whether the hopper still waits out a transfer cooldown. */
    public boolean isOnCooldown() {
        return transferCooldown > 0;
    }

    /** @return the raw cooldown ticks (the TransferCooldown row — Slice 89). */
    public int cooldown() {
        return transferCooldown;
    }

    public void setCooldown(int cooldown) {
        this.transferCooldown = cooldown;
    }

    /**
     * The receiving-hopper reset (HopperBlockEntity.canTransferNextTick +
     * the slot push's arm): a hopper that takes an item while idle
     * (cooldown <= 1) restarts its cooldown so the item is not
     * immediately pushed on.
     */
    public void noteReceived() {
        if (transferCooldown <= 1) {
            transferCooldown = TRANSFER_COOLDOWN;
        }
    }

    /** @return the slot's stack (possibly empty — the reference's null). */
    public ItemStack getItem(int slot) {
        rangeCheck(slot);
        return slots[slot];
    }

    /**
     * The reference's removeItem(slot, amount) (lines 89-106): takes up to
     * the amount from the slot; the slot empties when the whole stack
     * leaves. The caller decides what to do with the remainder.
     */
    public ItemStack removeItem(int slot, int amount) {
        rangeCheck(slot);
        ItemStack current = slots[slot];
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (current.count() <= amount) {
            slots[slot] = ItemStack.EMPTY;
            bump();
            return current;
        }
        ItemStack taken = ItemStack.of(current.type(), amount);
        slots[slot] = current.withCount(current.count() - amount);
        bump();
        return taken;
    }

    /** The engine's direct slot write (the item pickup arm + restore). */
    public void setItem(int slot, ItemStack stack) {
        rangeCheck(slot);
        slots[slot] = stack == null ? ItemStack.EMPTY : stack;
        bump();
    }

    // --- the GUI window's click semantics (the reference's HopperMenu,
    // --- the chest's own shapes — the hopper has no slot filters) ---------

    /**
     * Cursor click on one of the five hopper slots (the window's mode 0):
     * the shared {@code WindowClicks} semantics over the slot grid and the
     * player's cursor.
     */
    public void clickSlot(int slot, int button,
                          net.zaminmc.torch.server.player.PlayerInventory inventory) {
        rangeCheck(slot);
        net.zaminmc.torch.server.player.WindowClicks.click(slots, slot, button,
                inventory.cursorBox());
        bump();
    }

    /**
     * Semantic drop-click (window click mode 4) on a hopper slot.
     * @return the stack that left the slot (possibly empty); the caller
     *         throws it into the world.
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
     * Semantic shift-click out of a hopper slot (quickMoveItem's first arm):
     * the stack moves into the inventory; a remainder stays.
     */
    public void quickMoveToInventory(int slot,
                                     net.zaminmc.torch.server.player.PlayerInventory inventory) {
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
     * Semantic shift-click from the player inventory into the hopper
     * (quickMoveItem's second arm): any stack is accepted (the reference's
     * plain InventorySlots — no filters), matching partials first, then the
     * first empty slot; a remainder comes back.
     */
    public ItemStack quickMoveIn(ItemStack moving) {
        if (moving.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < SLOT_COUNT && !moving.isEmpty(); slot++) {
            ItemStack current = slots[slot];
            if (!current.isEmpty()
                    && net.zaminmc.torch.server.player.WindowClicks
                            .stacksMergeable(current, moving)
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
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (slots[slot].isEmpty()) {
                slots[slot] = moving;
                bump();
                return ItemStack.EMPTY;
            }
        }
        return moving; // hopper full: the whole remainder comes back
    }

    /** @return a defensive copy of the five slots, index-aligned. */
    public ItemStack[] snapshotSlots() {
        return slots.clone();
    }

    /** @return whether every slot is empty (the reference's isInventoryEmpty). */
    public boolean isEmpty() {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** @return whether every slot holds a full stack (the reference's isFull). */
    public boolean isFull() {
        for (ItemStack stack : slots) {
            if (stack.isEmpty() || stack.count() < stack.type().maxStackSize()) {
                return false;
            }
        }
        return true;
    }

    private static void rangeCheck(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("Hopper slot out of range: " + slot);
        }
    }
}
