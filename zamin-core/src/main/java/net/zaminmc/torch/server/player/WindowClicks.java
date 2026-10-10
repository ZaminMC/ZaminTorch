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
     * PICKUP walk) with a per-slot max stack and the placement gate: the gate
     * runs on the empty-slot placement (a refused cursor leaves everything)
     * and wraps the merge/swap arms; a not-allowed same-item cursor falls
     * through to the reverse merge (slot into cursor). Taking from a slot is
     * never gated. Every clamp carries the cursor's own max (the reference's
     * {@code getMaxStackSize(cursor)}). The three-argument overload above is
     * this walk with the container's max and no filter.
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
            // The reference's empty-slot arm: the placement gate runs FIRST
            // (a refused cursor leaves everything), then k2 = min(click size,
            // getMaxStackSize(cursor)) with the cursor's own max in the clamp.
            if (!filter.allows(carried)) {
                return;
            }
            int take = Math.min(carried.count(), Math.min(slotMax, carried.type().maxStackSize()));
            if (carried.count() >= take) {
                slots[index] = carried.withCount(take);
                int left = carried.count() - take;
                cursor.set(left == 0 ? ItemStack.EMPTY : carried.withCount(left));
            }
        } else if (filter.allows(carried)) {
            // The allowed branch: the same-item merge, else the gated swap.
            if (stacksMergeable(slot, carried)) {
                // The merge clamp is the slot max and the cursor's own cap
                // (the reference's double min over getMaxStackSize(cursor)).
                int capacity = Math.min(slotMax, carried.type().maxStackSize()) - slot.count();
                int moved = Math.min(capacity, carried.count());
                if (moved > 0) {
                    slots[index] = new ItemStack(slot.type(), slot.count() + moved, slot.damage());
                    int left = carried.count() - moved;
                    cursor.set(left == 0 ? ItemStack.EMPTY : carried.withCount(left));
                }
            } else if (carried.count() <= Math.min(slotMax, carried.type().maxStackSize())) {
                slots[index] = carried;
                cursor.set(slot); // mismatch: historical swap, gated by the slot max
            }
        } else if (stacksMergeable(slot, carried) && carried.type().maxStackSize() > 1
                && slot.count() + carried.count() <= carried.type().maxStackSize()) {
            // The not-allowed same-item cursor: the reference's reverse merge
            // (the slot merges INTO the cursor up to the cursor's cap).
            cursor.set(new ItemStack(carried.type(), carried.count() + slot.count(),
                    carried.damage()));
            slots[index] = ItemStack.EMPTY;
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
            if (!filter.allows(carried)) {
                return; // the placement gate refuses the unit deposit
            }
            int take = Math.min(1, Math.min(slotMax, carried.type().maxStackSize()));
            slots[index] = carried.withCount(take);
            int left = carried.count() - take;
            cursor.set(left == 0 ? ItemStack.EMPTY : carried.withCount(left));
        } else if (filter.allows(carried)) {
            if (stacksMergeable(slot, carried)) {
                int capacity = Math.min(slotMax, carried.type().maxStackSize()) - slot.count();
                int moved = Math.min(capacity, 1); // right click merges one unit
                if (moved > 0) {
                    slots[index] = new ItemStack(slot.type(), slot.count() + moved, slot.damage());
                    int left = carried.count() - moved;
                    cursor.set(left == 0 ? ItemStack.EMPTY : carried.withCount(left));
                }
            } else if (carried.count() <= Math.min(slotMax, carried.type().maxStackSize())) {
                slots[index] = carried;
                cursor.set(slot);
            }
        } else if (stacksMergeable(slot, carried) && carried.type().maxStackSize() > 1
                && slot.count() + carried.count() <= carried.type().maxStackSize()) {
            cursor.set(new ItemStack(carried.type(), carried.count() + slot.count(),
                    carried.damage()));
            slots[index] = ItemStack.EMPTY;
        }
    }

    public static boolean stacksMergeable(ItemStack a, ItemStack b) {
        return ItemStack.mergeable(a, b);
    }
}
