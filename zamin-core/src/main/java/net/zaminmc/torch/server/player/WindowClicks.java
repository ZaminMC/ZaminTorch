package net.zaminmc.torch.server.player;

import net.zaminmc.torch.item.ItemStack;

/**
 * The historical 1.8 cursor-click semantics for window slot arrays, shared by
 * every slot container that plays in a window (the 36-slot inventory, the
 * crafting grids, the furnace's three slots). One mouse means one cursor:
 * every container mutates the same cursor through the caller's box.
 *
 * <p>Left click: pick up, place, merge into matching stacks, swap on
 * mismatch. Right click: take half (rounding up), place one, merge one,
 * swap. Clicks that change nothing (empty onto empty) leave all state
 * untouched.</p>
 */
public final class WindowClicks {

    private WindowClicks() {
    }

    /** Read/write access to the one shared cursor stack. */
    public interface CursorBox {
        ItemStack get();

        void set(ItemStack stack);
    }

    /**
     * The historical {@code InventorySlot.isItemAllowed} gate: whether the
     * cursor stack may be placed into (merged with, swapped onto) the slot.
     * Taking from the slot is never gated.
     */
    public interface SlotFilter {
        boolean allows(ItemStack cursor);
    }

    /** The permissive gate (no filter): every container without slot rules. */
    public static final SlotFilter ANY = cursor -> true;

    public static void click(ItemStack[] slots, int index, int button, CursorBox cursor) {
        if (index < 0 || index >= slots.length) {
            throw new IllegalArgumentException("Slot out of range: " + index);
        }
        // The reference's Slot.getMaxStackSize: an empty slot caps at the
        // container's default (64); an occupied slot caps at its item's own
        // max (EMPTY.type() is null — never dereference it).
        ItemStack occupant = slots[index];
        int max = occupant.isEmpty() ? 64 : occupant.type().maxStackSize();
        click(slots, index, button, cursor, max, ANY);
    }

    /**
     * The full historical arms (the reference {@code InventoryMenu.onClickSlot}
     * PICKUP walk) with a per-slot max stack and the placement gate: an empty
     * slot takes {@code min(cursor, max)} (the split), a merge tops up to
     * {@code max}, a mismatched swap requires {@code cursor <= max}, and the
     * not-allowed same-item cursor falls through to the reverse merge
     * (slot into cursor). The three-argument overload above is this walk with
     * the type's own max and no filter.
     */
    public static void click(ItemStack[] slots, int index, int button, CursorBox cursor,
                             int slotMax, SlotFilter filter) {
        if (index < 0 || index >= slots.length) {
            throw new IllegalArgumentException("Slot out of range: " + index);
        }
        if (button != 0 && button != 1) {
            throw new IllegalArgumentException("Click button must be 0 or 1: " + button);
        }
        if (slotMax < 1) {
            throw new IllegalArgumentException("Slot max stack must be positive: " + slotMax);
        }
        ItemStack slot = slots[index];
        if (button == 0) {
            leftClick(slots, index, slot, cursor, slotMax, filter);
        } else {
            rightClick(slots, index, slot, cursor, slotMax, filter);
        }
    }

    private static void leftClick(ItemStack[] slots, int index, ItemStack slot, CursorBox cursor,
                                  int slotMax, SlotFilter filter) {
        ItemStack carried = cursor.get();
        if (carried.isEmpty()) {
            if (slot.isEmpty()) {
                return; // clicking an empty slot with an empty hand: no-op
            }
            cursor.set(slot);
            slots[index] = ItemStack.EMPTY;
        } else if (slot.isEmpty()) {
            // The reference's empty-slot arm: k2 = min(cursor, slotMax), and the
            // placement splits — a max-1 slot keeps the cursor's remainder.
            int take = Math.min(carried.count(), slotMax);
            if (carried.count() >= take) {
                slots[index] = carried.withCount(take);
                int left = carried.count() - take;
                cursor.set(left == 0 ? ItemStack.EMPTY : carried.withCount(left));
            }
        } else if (stacksMergeable(slot, carried)) {
            // Same item: the top-up clamp is the slot max first (the max-1
            // enchanting slot merges nothing), then the type's own cap.
            int capacity = Math.min(slotMax, slot.type().maxStackSize()) - slot.count();
            int moved = Math.min(capacity, carried.count());
            slots[index] = new ItemStack(slot.type(), slot.count() + moved, slot.damage());
            int left = carried.count() - moved;
            cursor.set(left == 0 ? ItemStack.EMPTY : carried.withCount(left));
        } else if (!filter.allows(carried)) {
            // The not-allowed cursor of the SAME item falls through to the
            // reference's reverse merge (slot into cursor, cursor cap rules);
            // a different not-allowed item leaves everything untouched.
            // The reverse merge matches on the mergeable identity too.
            if (stacksMergeable(slot, carried)
                    && carried.type().maxStackSize() > 1
                    && slot.count() + carried.count() <= carried.type().maxStackSize()) {
                cursor.set(new ItemStack(carried.type(), carried.count() + slot.count(),
                        carried.damage()));
                slots[index] = ItemStack.EMPTY;
            }
        } else if (carried.count() <= slotMax) {
            slots[index] = carried;
            cursor.set(slot); // mismatch: historical swap, gated by the slot max
        }
    }

    private static void rightClick(ItemStack[] slots, int index, ItemStack slot, CursorBox cursor,
                                   int slotMax, SlotFilter filter) {
        ItemStack carried = cursor.get();
        if (carried.isEmpty()) {
            if (slot.isEmpty()) {
                return;
            }
            int taken = (slot.count() + 1) / 2; // historical: right click takes half, rounding up
            cursor.set(new ItemStack(slot.type(), taken, slot.damage()));
            slots[index] = slot.withCount(slot.count() - taken);
        } else if (slot.isEmpty()) {
            int take = Math.min(1, slotMax);
            slots[index] = carried.withCount(take);
            int left = carried.count() - take;
            cursor.set(left == 0 ? ItemStack.EMPTY : carried.withCount(left));
        } else if (stacksMergeable(slot, carried)) {
            int capacity = Math.min(slotMax, slot.type().maxStackSize()) - slot.count();
            int moved = Math.min(capacity, 1); // right click merges one unit
            if (moved > 0 && slot.count() < Math.min(slotMax, slot.type().maxStackSize())) {
                slots[index] = new ItemStack(slot.type(), slot.count() + moved, slot.damage());
                int left = carried.count() - moved;
                cursor.set(left == 0 ? ItemStack.EMPTY : carried.withCount(left));
            }
        } else if (!filter.allows(carried)) {
            if (stacksMergeable(slot, carried)
                    && carried.type().maxStackSize() > 1
                    && slot.count() + carried.count() <= carried.type().maxStackSize()) {
                cursor.set(new ItemStack(carried.type(), carried.count() + slot.count(),
                        carried.damage()));
                slots[index] = ItemStack.EMPTY;
            }
        } else if (carried.count() <= slotMax) {
            slots[index] = carried;
            cursor.set(slot);
        }
    }

    public static boolean stacksMergeable(ItemStack a, ItemStack b) {
        return ItemStack.mergeable(a, b);
    }
}
