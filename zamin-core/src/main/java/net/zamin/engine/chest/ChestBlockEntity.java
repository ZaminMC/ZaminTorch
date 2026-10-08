package net.zamin.engine.chest;

import net.zamin.api.ItemStack;
import net.zamin.engine.player.WindowClicks;

import java.util.Arrays;
import java.util.Objects;

/**
 * One placed chest's state (the historical tile entity): twenty-seven slots
 * (the community-verified 3x9 window layout, wire slots 0-26). Slots live in
 * the world, not in any player session: every viewer sees the same contents,
 * they survive the window closing and the server restarting (ZCD
 * persistence), and breaking the chest spills them into the world — the
 * historical container behavior, the same ownership model as the furnace.
 *
 * <p>A chest has no tick of its own (nothing advances inside it); it only
 * hosts window clicks, quick moves and drop gestures through the shared
 * {@link WindowClicks} semantics, so its change serial exists purely for wire
 * sync dedup. Unlike the furnace, quick-move accepts any stack: a chest has
 * no slot filters (the historical {@code TileEntityChest}).</p>
 *
 * <p>Ownership: mutated only by the simulation context (the engine's click
 * routing and the manager's spill/discard paths, all on the tick thread).</p>
 */
public final class ChestBlockEntity {

    /** The 1.8 chest GUI's own slot count (3 rows of 9). */
    public static final int SLOT_COUNT = 27;

    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];
    private int slotsSerial;

    public ChestBlockEntity() {
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    /** @return how many times the slot array mutated (wire sync dedup). */
    public int slotsSerial() {
        return slotsSerial;
    }

    /**
     * Semantic left/right click on one chest slot (window click mode 0),
     * sharing the inventory's cursor through the historical click semantics.
     */
    public void clickSlot(int slot, int button, net.zamin.engine.player.PlayerInventory inventory) {
        rangeCheck(slot);
        WindowClicks.click(slots, slot, button, inventory.cursorBox());
        slotsSerial++;
    }

    /**
     * Semantic drop-click (window click mode 4) on a chest slot. @return the
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
     * Semantic shift-click out of a chest slot: the stack moves into the
     * inventory; a remainder stays (historical full-inventory behavior).
     */
    public void quickMoveToInventory(int slot, net.zamin.engine.player.PlayerInventory inventory) {
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
     * Semantic shift-click from the player inventory into the chest: any stack
     * is accepted (a chest has no slot filters). The historical fill order is
     * matching partial stacks first, then the first empty slot; a remainder
     * comes back. The caller has already removed the stack from the inventory;
     * @return the part that did not fit (possibly empty — the caller puts it
     *         back via pickUp).
     */
    public ItemStack quickMoveIn(ItemStack moving) {
        Objects.requireNonNull(moving, "moving");
        if (moving.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // Pass 1: merge into matching partial stacks (wire order 0..26).
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
            slotsSerial++;
            return ItemStack.EMPTY;
        }
        // Pass 2: the first empty slot takes the rest.
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (slots[slot].isEmpty()) {
                slots[slot] = moving;
                slotsSerial++;
                return ItemStack.EMPTY;
            }
        }
        return moving; // chest full: the whole remainder comes back
    }

    /** @return a defensive copy of the 27 slots, index-aligned. */
    public ItemStack[] snapshotSlots() {
        return slots.clone();
    }

    /** Direct slot write for engine commands only (persistence restore). */
    public void setSlot(int slot, ItemStack stack) {
        rangeCheck(slot);
        slots[slot] = Objects.requireNonNull(stack, "stack");
        slotsSerial++;
    }

    private static void rangeCheck(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("Chest slot out of range: " + slot);
        }
    }
}
