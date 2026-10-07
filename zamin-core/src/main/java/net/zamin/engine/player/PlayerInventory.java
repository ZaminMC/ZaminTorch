package net.zamin.engine.player;

import net.zamin.api.ItemStack;
import net.zamin.api.ItemType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The authoritative inventory of one player (§428/§430/§431).
 *
 * <p>Slot semantics are explicit: slots 0-8 are the hotbar, 9-35 the main
 * inventory. There are no magic indexes elsewhere; every operation names its
 * intent. All mutations run on the simulation context (engine command queue),
 * so no locks are needed and ordering matches the tick.</p>
 *
 * <p>Operations are semantic (§429) and atomic (§431): a failed operation
 * leaves the inventory untouched. The one deliberate partial transition is
 * pickup (§433): Minecraft's historical behavior fills what fits and leaves
 * the remainder in the world, which {@link #pickUp} models exactly.</p>
 */
public final class PlayerInventory {

    public static final int HOTBAR_SLOTS = 9;
    public static final int MAIN_SLOTS = 27;
    public static final int TOTAL_SLOTS = HOTBAR_SLOTS + MAIN_SLOTS; // 36, like the historical model

    private final ItemStack[] slots = new ItemStack[TOTAL_SLOTS];
    private int heldSlot; // 0-8, the hotbar index the client currently holds
    private ItemStack cursor = ItemStack.EMPTY; // the stack held by the mouse (window clicks)

    public PlayerInventory() {
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
    }

    /** @return the stack currently carried on the mouse cursor (never null). */
    public ItemStack cursor() {
        return cursor;
    }

    /** @return the stack currently held in the selected hotbar slot (never null). */
    public ItemStack held() {
        return slots[heldSlot];
    }

    public int heldSlot() {
        return heldSlot;
    }

    /** Selects the hotbar slot. Invalid selections are rejected, state unchanged (§430). */
    public void selectHotbarSlot(int slot) {
        if (slot < 0 || slot >= HOTBAR_SLOTS) {
            throw new IllegalArgumentException("Hotbar slot out of range: " + slot);
        }
        this.heldSlot = slot;
    }

    /**
     * Semantic pickup (§433): fills matching, then empty slots — hotbar first,
     * matching historical insertion order. @return what did not fit (possibly
     * empty); the world keeps that remainder as the item entity.
     */
    public ItemStack pickUp(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = stack;

        // 1. top up existing matching stacks (hotbar order, then main). Damage
        // must match too: a worn tool never merges into a fresh one (moot while
        // tools stack to one, but the rule keeps the merge semantics total).
        for (int i = 0; i < TOTAL_SLOTS && !remaining.isEmpty(); i++) {
            ItemStack current = slots[i];
            if (!current.isEmpty() && current.type().equals(remaining.type())
                    && current.damage() == remaining.damage()
                    && current.count() < current.type().maxStackSize()) {
                int capacity = current.type().maxStackSize() - current.count();
                ItemStack take = remaining.split(capacity);
                slots[i] = new ItemStack(current.type(), current.count() + take.count(),
                        current.damage());
                remaining = remaining.withCount(remaining.count() - take.count());
            }
        }

        // 2. first empty slot wins (hotbar first, then main inventory)
        for (int i = 0; i < TOTAL_SLOTS && !remaining.isEmpty(); i++) {
            if (slots[i].isEmpty()) {
                ItemStack take = remaining.split(remaining.type().maxStackSize());
                slots[i] = take;
                remaining = remaining.withCount(remaining.count() - take.count());
            }
        }
        return remaining;
    }

    /**
     * Semantic consumption (§432): removes up to {@code amount} units from the
     * held slot in one step. @return what was actually consumed (may be less
     * than requested when the held stack is smaller, possibly empty).
     */
    public ItemStack consumeHeld(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("Consume amount must be positive: " + amount);
        }
        ItemStack current = slots[heldSlot];
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack consumed = current.split(amount);
        slots[heldSlot] = current.withCount(current.count() - consumed.count());
        return consumed;
    }

    /**
     * Semantic drop from the held slot: one unit, or the whole stack when
     * {@code entireStack} (historical Q vs Ctrl+Q). @return the stack that
     * left the inventory (possibly empty when the hand is empty).
     */
    public ItemStack dropHeld(boolean entireStack) {
        ItemStack current = slots[heldSlot];
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack dropped = entireStack ? current : current.split(1);
        slots[heldSlot] = current.withCount(current.count() - dropped.count());
        return dropped;
    }

    // ------------------------------------------------------------------ window clicks (§429)

    /**
     * Semantic left/right click on one inventory slot (window click mode 0).
     * Implements the historical cursor model: pick up, place, merge into
     * matching stacks, split half on right click, swap on mismatch. Invalid
     * slots and no-op clicks leave everything untouched.
     */
    public void clickSlot(int engineSlot, int button) {
        if (engineSlot < 0 || engineSlot >= TOTAL_SLOTS) {
            throw new IllegalArgumentException("Slot out of range: " + engineSlot);
        }
        if (button != 0 && button != 1) {
            throw new IllegalArgumentException("Click button must be 0 or 1: " + button);
        }
        ItemStack slot = slots[engineSlot];
        if (button == 0) {
            if (cursor.isEmpty()) {
                if (slot.isEmpty()) {
                    return; // clicking an empty slot with an empty hand: no-op
                }
                cursor = slot;
                slots[engineSlot] = ItemStack.EMPTY;
            } else if (slot.isEmpty()) {
                slots[engineSlot] = cursor;
                cursor = ItemStack.EMPTY;
            } else if (stacksMergeable(slot, cursor)) {
                int capacity = slot.type().maxStackSize() - slot.count();
                int moved = Math.min(capacity, cursor.count());
                slots[engineSlot] = new ItemStack(slot.type(), slot.count() + moved, slot.damage());
                cursor = cursor.withCount(cursor.count() - moved);
            } else {
                slots[engineSlot] = cursor;
                cursor = slot; // mismatch: historical swap
            }
        } else {
            if (cursor.isEmpty()) {
                if (slot.isEmpty()) {
                    return;
                }
                int taken = (slot.count() + 1) / 2; // historical: right click takes half, rounding up
                cursor = new ItemStack(slot.type(), taken, slot.damage());
                slots[engineSlot] = slot.withCount(slot.count() - taken);
            } else if (slot.isEmpty()) {
                slots[engineSlot] = cursor.withCount(1);
                cursor = cursor.withCount(cursor.count() - 1);
            } else if (stacksMergeable(slot, cursor) && slot.count() < slot.type().maxStackSize()) {
                slots[engineSlot] = new ItemStack(slot.type(), slot.count() + 1, slot.damage());
                cursor = cursor.withCount(cursor.count() - 1);
            } else {
                slots[engineSlot] = cursor;
                cursor = slot;
            }
        }
    }

    private static boolean stacksMergeable(ItemStack a, ItemStack b) {
        return a.type().equals(b.type()) && a.damage() == b.damage();
    }

    /**
     * Semantic shift-click (window click mode 1): quick-moves the whole stack
     * between the hotbar and the main inventory, historical fill order
     * (matching stacks first, then empty slots). No-op on invalid slots.
     */
    public void quickMove(int engineSlot) {
        if (engineSlot < 0 || engineSlot >= TOTAL_SLOTS) {
            throw new IllegalArgumentException("Slot out of range: " + engineSlot);
        }
        ItemStack moving = slots[engineSlot];
        if (moving.isEmpty()) {
            return;
        }
        int rangeStart = engineSlot < HOTBAR_SLOTS ? HOTBAR_SLOTS : 0;
        int rangeEnd = engineSlot < HOTBAR_SLOTS ? TOTAL_SLOTS : HOTBAR_SLOTS;

        slots[engineSlot] = ItemStack.EMPTY;
        ItemStack remaining = moving;

        // 1. top up matching stacks inside the target range
        for (int i = rangeStart; i < rangeEnd && !remaining.isEmpty(); i++) {
            ItemStack current = slots[i];
            if (!current.isEmpty() && stacksMergeable(current, remaining)
                    && current.count() < current.type().maxStackSize()) {
                int capacity = current.type().maxStackSize() - current.count();
                int moved = Math.min(capacity, remaining.count());
                slots[i] = new ItemStack(current.type(), current.count() + moved, current.damage());
                remaining = remaining.withCount(remaining.count() - moved);
            }
        }
        // 2. then empty slots
        for (int i = rangeStart; i < rangeEnd && !remaining.isEmpty(); i++) {
            if (slots[i].isEmpty()) {
                int moved = Math.min(remaining.type().maxStackSize(), remaining.count());
                slots[i] = remaining.withCount(moved);
                remaining = remaining.withCount(remaining.count() - moved);
            }
        }
        if (!remaining.isEmpty()) {
            slots[engineSlot] = remaining; // target range full: everything stays
        }
    }

    /**
     * Semantic number-key swap (window click mode 2): exchanges a main
     * inventory slot with a hotbar slot (either side may be empty).
     */
    public void swapWithHotbar(int engineSlot, int hotbarIndex) {
        if (engineSlot < HOTBAR_SLOTS || engineSlot >= TOTAL_SLOTS) {
            throw new IllegalArgumentException("Number-key swap targets main slots only: " + engineSlot);
        }
        if (hotbarIndex < 0 || hotbarIndex >= HOTBAR_SLOTS) {
            throw new IllegalArgumentException("Hotbar index out of range: " + hotbarIndex);
        }
        ItemStack main = slots[engineSlot];
        slots[engineSlot] = slots[hotbarIndex];
        slots[hotbarIndex] = main;
    }

    /**
     * Semantic drop-click (window click mode 4): removes one unit or the whole
     * stack from the clicked slot and hands it out as a throw. @return the
     * stack that left the inventory (possibly empty), like {@link #dropHeld}.
     */
    public ItemStack dropFromSlot(int engineSlot, boolean entireStack) {
        if (engineSlot < 0 || engineSlot >= TOTAL_SLOTS) {
            throw new IllegalArgumentException("Slot out of range: " + engineSlot);
        }
        ItemStack current = slots[engineSlot];
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack dropped = entireStack ? current : current.split(1);
        slots[engineSlot] = current.withCount(current.count() - dropped.count());
        return dropped;
    }

    /**
     * Takes the cursor stack back into the inventory (window close, disconnect):
     * fills matching then empty slots, historical order. @return what did not
     * fit - the caller throws it into the world so nothing is lost.
     */
    public ItemStack returnCursor() {
        ItemStack carried = cursor;
        cursor = ItemStack.EMPTY;
        if (carried.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return pickUp(carried);
    }

    /**
     * Semantic durability wear on the held stack (§432 family): the tool loses
     * {@code amount} durability units; reaching its limit breaks it and the
     * slot becomes empty (historical: the tool leaves the hand). Non-durable
     * items and the empty hand are unaffected.
     *
     * @return whether the held slot changed at all (worn or broken), so the
     *         caller can re-sync the client.
     */
    public boolean damageHeld(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("Damage amount must be positive: " + amount);
        }
        ItemStack current = slots[heldSlot];
        if (current.isEmpty() || current.type().maxDurability() <= 0) {
            return false;
        }
        int newDamage = current.damage() + amount;
        if (newDamage >= current.type().maxDurability()) {
            slots[heldSlot] = ItemStack.EMPTY; // the tool breaks
        } else {
            slots[heldSlot] = current.withDamage(newDamage);
        }
        return true;
    }

    /**
     * Semantic restore from persisted state (join of a returning player):
     * replaces the whole contents. Unresolvable saved items are dropped loudly
     * by the caller before this runs, so the list is exactly TOTAL_SLOTS long.
     * Atomic: a rejected restore leaves the inventory untouched.
     */
    public void restore(List<ItemStack> restored, int heldSlot) {
        if (restored.size() != TOTAL_SLOTS) {
            throw new IllegalArgumentException(
                    "Restore requires exactly " + TOTAL_SLOTS + " slots: " + restored.size());
        }
        if (heldSlot < 0 || heldSlot >= HOTBAR_SLOTS) {
            throw new IllegalArgumentException("Held slot out of range: " + heldSlot);
        }
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            slots[i] = restored.get(i);
        }
        this.heldSlot = heldSlot;
    }

    /** Read-only slot snapshot for synchronization; callers must not mutate it. */
    public List<ItemStack> snapshot() {
        return List.of(slots.clone());
    }

    /** Directly sets a slot — reserved for explicit engine commands, validated. */
    void setSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= TOTAL_SLOTS) {
            throw new IllegalArgumentException("Slot out of range: " + slot);
        }
        Objects.requireNonNull(stack, "stack");
        slots[slot] = stack;
    }

    /** @return an immutable copy of the backing slot array, index-aligned. */
    List<ItemStack> slotsView() {
        return new ArrayList<>(List.of(slots));
    }
}
