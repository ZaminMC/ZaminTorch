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

    public PlayerInventory() {
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
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
