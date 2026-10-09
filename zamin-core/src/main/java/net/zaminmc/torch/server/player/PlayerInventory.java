package net.zaminmc.torch.server.player;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.item.Armor;

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

    /** The four armor slots (engine order): head, chest, legs, feet. */
    public static final int ARMOR_SLOTS = 4;
    /** The ZPD v5 persistence namespace where the armor row lives (36-39). */
    public static final int ARMOR_BASE = TOTAL_SLOTS;

    private final ItemStack[] slots = new ItemStack[TOTAL_SLOTS];
    private final ItemStack[] armor = new ItemStack[ARMOR_SLOTS];
    private int heldSlot; // 0-8, the hotbar index the client currently holds
    private ItemStack cursor = ItemStack.EMPTY; // the stack held by the mouse (window clicks)

    public PlayerInventory() {
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        java.util.Arrays.fill(armor, ItemStack.EMPTY);
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

    /**
     * Total units of one item type across all slots (the bow's ammunition
     * check). Identity matches by type only; damage/variant stacks count too.
     */
    public int countOf(net.zaminmc.torch.item.ItemType type) {
        Objects.requireNonNull(type, "type");
        int total = 0;
        for (ItemStack slot : slots) {
            if (!slot.isEmpty() && slot.type().equals(type)) {
                total += slot.count();
            }
        }
        return total;
    }

    /**
     * Consumes one unit of the item type from the first matching slot (slot
     * order, hotbar first). @return true when a unit was consumed.
     */
    public boolean consumeOne(net.zaminmc.torch.item.ItemType type) {
        Objects.requireNonNull(type, "type");
        for (int i = 0; i < slots.length; i++) {
            ItemStack slot = slots[i];
            if (!slot.isEmpty() && slot.type().equals(type) && slot.count() > 0) {
                slots[i] = slot.withCount(slot.count() - 1);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes {@code count} units of one type across all slots (the trade
     * buy path): fills from the first matching slot, splitting across stacks
     * as needed. @return whether the full count was removed (a short supply
     * removes nothing — validate with {@link #countOf} first).
     */
    public boolean removeItems(net.zaminmc.torch.item.ItemType type, int count) {
        Objects.requireNonNull(type, "type");
        if (count < 0) {
            throw new IllegalArgumentException("Remove count must not be negative: " + count);
        }
        if (countOf(type) < count) {
            return false; // short supply: nothing moves
        }
        int left = count;
        for (int i = 0; i < slots.length && left > 0; i++) {
            ItemStack slot = slots[i];
            if (slot.isEmpty() || !slot.type().equals(type)) {
                continue;
            }
            int take = Math.min(left, slot.count());
            left -= take;
            slots[i] = take == slot.count() ? ItemStack.EMPTY : slot.withCount(slot.count() - take);
        }
        return true;
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
            if (ItemStack.mergeable(current, remaining)
                    && current.count() < current.type().maxStackSize()) {
                int capacity = current.type().maxStackSize() - current.count();
                ItemStack take = remaining.split(capacity);
                slots[i] = current.withCount(current.count() + take.count());
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
    /**
     * Semantic rename of the held stack (§426's item NBT slice): the anvil
     * rename without the anvil — the display name rides every later wire
     * encoding and survives containers and drops. Null/blank clears.
     */
    public void renameHeld(String name) {
        slots[heldSlot] = slots[heldSlot].withName(name);
    }

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
     * slots and no-op clicks leave everything untouched. The semantics live
     * in {@link WindowClicks} so the crafting grid plays by identical rules.
     */
    public void clickSlot(int engineSlot, int button) {
        WindowClicks.click(slots, engineSlot, button, cursorBox());
    }

    /**
     * The one shared cursor, packaged for window-op collaborators. Public
     * because cross-package containers (the furnace block entity) play by the
     * identical click semantics — the single cursor is an engine-wide contract.
     */
    public WindowClicks.CursorBox cursorBox() {
        return new WindowClicks.CursorBox() {
            @Override
            public ItemStack get() {
                return cursor;
            }

            @Override
            public void set(ItemStack stack) {
                cursor = stack;
            }
        };
    }

    /**
     * Historical result-slot pickup: a crafting result moves onto the cursor
     * (empty cursor takes it; a matching cursor absorbs it while it fits).
     * Anything else — mismatched cursor, overflowing stack — is refused and
     * the caller rejects the click so the client reverts its prediction.
     *
     * @return whether the cursor took the result.
     */
    public boolean takeResultToCursor(ItemStack result) {
        Objects.requireNonNull(result, "result");
        if (result.isEmpty()) {
            return true; // nothing to take: an accepted no-op
        }
        if (cursor.isEmpty()) {
            cursor = result;
            return true;
        }
        if (WindowClicks.stacksMergeable(cursor, result)
                && cursor.count() + result.count() <= cursor.type().maxStackSize()) {
            cursor = cursor.withCount(cursor.count() + result.count());
            return true;
        }
        return false;
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
            if (!current.isEmpty() && WindowClicks.stacksMergeable(current, remaining)
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
     * Takes the cursor stack as-is (the death drop): the cursor becomes empty
     * and the stack is handed out, bypassing the inventory fill.
     */
    public ItemStack takeCursor() {
        ItemStack carried = cursor;
        cursor = ItemStack.EMPTY;
        return carried;
    }

    /**
     * Places a stack on the cursor (the horse window's take paths: the saddle
     * and the armor row jump from the mount onto the cursor). The previous
     * cursor must be empty — the one-mouse rule.
     */
    public void placeOnCursor(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (!cursor.isEmpty()) {
            throw new IllegalArgumentException("Cursor already carries a stack");
        }
        cursor = stack;
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

    // ------------------------------------------------------------------ armor (§armor)

    /** @return the equipped stack in the armor slot (0=head, 1=chest, 2=legs, 3=feet). */
    public ItemStack armorAt(int armorSlot) {
        if (armorSlot < 0 || armorSlot >= ARMOR_SLOTS) {
            throw new IllegalArgumentException("Armor slot out of range: " + armorSlot);
        }
        return armor[armorSlot];
    }

    /**
     * Sets one armor slot, enforcing the kind rule: only armor of the
     * matching slot may enter (the historical inventory's type gate). An
     * empty stack clears. @return whether the write was accepted.
     */
    public boolean setArmor(int armorSlot, ItemStack stack) {
        if (armorSlot < 0 || armorSlot >= ARMOR_SLOTS) {
            throw new IllegalArgumentException("Armor slot out of range: " + armorSlot);
        }
        Objects.requireNonNull(stack, "stack");
        if (!stack.isEmpty()) {
            Armor.Spec spec = Armor.specOf(stack.type()).orElse(null);
            if (spec == null || spec.slot() != Armor.Slot.values()[armorSlot]) {
                return false; // not armor, or the wrong slot's kind
            }
        }
        armor[armorSlot] = stack.isEmpty() ? ItemStack.EMPTY : stack;
        return true;
    }

    /** @return the sum of the equipped pieces' armor points (the 0-20 bar). */
    public int totalArmorPoints() {
        int total = 0;
        for (ItemStack piece : armor) {
            if (!piece.isEmpty()) {
                total += Armor.specOf(piece.type()).map(Armor.Spec::armorPoints).orElse(0);
            }
        }
        return total;
    }

    /**
     * The historical armor wear: every equipped piece loses one durability
     * unit per damaging hit it absorbed; a piece that reaches its limit
     * breaks (leaves the slot). @return whether any slot changed.
     */
    public boolean wearArmor() {
        boolean changed = false;
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            ItemStack piece = armor[i];
            if (piece.isEmpty() || piece.type().maxDurability() <= 0) {
                continue;
            }
            int newDamage = piece.damage() + 1;
            armor[i] = newDamage >= piece.type().maxDurability()
                    ? ItemStack.EMPTY // the piece breaks
                    : piece.withDamage(newDamage);
            changed = true;
        }
        return changed;
    }

    /** Read-only armor snapshot for synchronization; callers must not mutate it. */
    public List<ItemStack> armorSnapshot() {
        return List.of(armor.clone());
    }

    /** Restores the armor row (the persistence path; kind-checked per slot). */
    public void restoreArmor(List<ItemStack> restored) {
        if (restored.size() != ARMOR_SLOTS) {
            throw new IllegalArgumentException(
                    "Armor restore requires exactly " + ARMOR_SLOTS + " slots: " + restored.size());
        }
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            if (!setArmor(i, restored.get(i))) {
                armor[i] = ItemStack.EMPTY; // a no-longer-armor item degrades to empty
            }
        }
    }

    /**
     * Quick-move (shift-click) out of an armor slot into the main inventory
     * (the historical fill order via pickup). Atomic: a full inventory puts
     * everything back. @return whether the piece moved.
     */
    public boolean quickMoveFromArmor(int armorSlot) {
        if (armorSlot < 0 || armorSlot >= ARMOR_SLOTS) {
            throw new IllegalArgumentException("Armor slot out of range: " + armorSlot);
        }
        ItemStack moving = armor[armorSlot];
        if (moving.isEmpty()) {
            return false;
        }
        ItemStack remainder = pickUp(moving);
        if (!remainder.isEmpty()) {
            armor[armorSlot] = remainder; // no room: the piece stays
            return false;
        }
        armor[armorSlot] = ItemStack.EMPTY;
        return true;
    }

    /**
     * The number-key swap between an armor slot and a hotbar slot (mode 2).
     * A non-armor hotbar stack is refused by the kind rule (returns false).
     */
    public boolean swapArmorWithHotbar(int armorSlot, int hotbarIndex) {
        if (armorSlot < 0 || armorSlot >= ARMOR_SLOTS) {
            throw new IllegalArgumentException("Armor slot out of range: " + armorSlot);
        }
        if (hotbarIndex < 0 || hotbarIndex >= HOTBAR_SLOTS) {
            throw new IllegalArgumentException("Hotbar index out of range: " + hotbarIndex);
        }
        ItemStack incoming = slots[hotbarIndex];
        ItemStack equipped = armor[armorSlot];
        if (!incoming.isEmpty() && !Armor.fitsSlot(incoming.type(), Armor.Slot.values()[armorSlot])) {
            return false; // the hotbar stack is not armor of this slot's kind
        }
        slots[hotbarIndex] = equipped;
        armor[armorSlot] = incoming.isEmpty() ? ItemStack.EMPTY : incoming;
        return true;
    }

    /** Read-only slot snapshot for synchronization; callers must not mutate it. */
    public List<ItemStack> snapshot() {
        return List.of(slots.clone());
    }

    /**
     * Empties the whole inventory (the /clear path): all 36 slots, the armor
     * row and the cursor. The command path's cleanup; a dropped variant is
     * the caller's choice (the historical /clear destroys, so nothing is
     * thrown).
     */
    public void clear() {
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        java.util.Arrays.fill(armor, ItemStack.EMPTY);
        cursor = ItemStack.EMPTY;
    }

    /**
     * Directly sets a slot — reserved for explicit engine commands (the
     * creative inventory's write path), validated against the slot range.
     */
    public void setSlot(int slot, ItemStack stack) {
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
