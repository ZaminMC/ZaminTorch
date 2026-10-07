package net.zamin.engine.player;

import net.zamin.api.ItemStack;

/**
 * The historical 1.8 cursor-click semantics for window slot arrays, shared by
 * every slot container that plays in the player inventory window (the 36-slot
 * inventory, the 2x2 crafting grid). One mouse means one cursor: every
 * container mutates the same cursor through the caller's box.
 *
 * <p>Left click: pick up, place, merge into matching stacks, swap on
 * mismatch. Right click: take half (rounding up), place one, merge one,
 * swap. Clicks that change nothing (empty onto empty) leave all state
 * untouched.</p>
 */
final class WindowClicks {

    private WindowClicks() {
    }

    /** Read/write access to the one shared cursor stack. */
    interface CursorBox {
        ItemStack get();

        void set(ItemStack stack);
    }

    static void click(ItemStack[] slots, int index, int button, CursorBox cursor) {
        if (index < 0 || index >= slots.length) {
            throw new IllegalArgumentException("Slot out of range: " + index);
        }
        if (button != 0 && button != 1) {
            throw new IllegalArgumentException("Click button must be 0 or 1: " + button);
        }
        ItemStack slot = slots[index];
        if (button == 0) {
            leftClick(slots, index, slot, cursor);
        } else {
            rightClick(slots, index, slot, cursor);
        }
    }

    private static void leftClick(ItemStack[] slots, int index, ItemStack slot, CursorBox cursor) {
        ItemStack carried = cursor.get();
        if (carried.isEmpty()) {
            if (slot.isEmpty()) {
                return; // clicking an empty slot with an empty hand: no-op
            }
            cursor.set(slot);
            slots[index] = ItemStack.EMPTY;
        } else if (slot.isEmpty()) {
            slots[index] = carried;
            cursor.set(ItemStack.EMPTY);
        } else if (stacksMergeable(slot, carried)) {
            int capacity = slot.type().maxStackSize() - slot.count();
            int moved = Math.min(capacity, carried.count());
            slots[index] = new ItemStack(slot.type(), slot.count() + moved, slot.damage());
            cursor.set(carried.withCount(carried.count() - moved));
        } else {
            slots[index] = carried;
            cursor.set(slot); // mismatch: historical swap
        }
    }

    private static void rightClick(ItemStack[] slots, int index, ItemStack slot, CursorBox cursor) {
        ItemStack carried = cursor.get();
        if (carried.isEmpty()) {
            if (slot.isEmpty()) {
                return;
            }
            int taken = (slot.count() + 1) / 2; // historical: right click takes half, rounding up
            cursor.set(new ItemStack(slot.type(), taken, slot.damage()));
            slots[index] = slot.withCount(slot.count() - taken);
        } else if (slot.isEmpty()) {
            slots[index] = carried.withCount(1);
            cursor.set(carried.withCount(carried.count() - 1));
        } else if (stacksMergeable(slot, carried) && slot.count() < slot.type().maxStackSize()) {
            slots[index] = new ItemStack(slot.type(), slot.count() + 1, slot.damage());
            cursor.set(carried.withCount(carried.count() - 1));
        } else {
            slots[index] = carried;
            cursor.set(slot);
        }
    }

    static boolean stacksMergeable(ItemStack a, ItemStack b) {
        return a.type().equals(b.type()) && a.damage() == b.damage();
    }
}
