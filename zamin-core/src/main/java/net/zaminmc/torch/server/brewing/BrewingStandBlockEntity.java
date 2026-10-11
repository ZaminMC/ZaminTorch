package net.zaminmc.torch.server.brewing;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.player.WindowClicks;
import net.zaminmc.torch.server.potion.PotionHelper;
import net.zaminmc.torch.server.potion.StatusEffectInstance;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One placed brewing stand's state (Slice 11e — the reference port of
 * reference/1.8.8 block/entity/BrewingStandBlockEntity.java): four slots —
 * 0-2 the potion bottles, 3 the ingredient — living in the world at the
 * block's position, every viewer sees the same contents, and breaking the
 * block spills them (the historical container drop).
 *
 * <h2>The brew cycle (tick lines 58-92, 1:1)</h2>
 * <p>A fresh {@code canBrew} arms the 400-tick timer and remembers the
 * ingredient's item identity; the running timer counts down to the brew,
 * cancelling at once when the ingredients stop brewing or the item changed
 * under it. The {@code findHasPotion} occupancy never mutates the block
 * directly here — the system performs the world write (the reference's
 * flag-2 setBlockState, the engine's commit listeners) through
 * {@link #consumeBottleBitsChange()}.</p>
 *
 * <h2>The gates (canBrew lines 94-125 + brew lines 127-156)</h2>
 * <p>The ingredient must be one; every potion slot applies the flag walk
 * ({@link PotionHelper#applyIngredient}); the change gate keeps the
 * reference's exact shape — the splash conversion always brews, the
 * no-op (the same effects) never does. The write gate mirrors the
 * reference's careful inverted condition: a same-effect brew only
 * rewrites when the splash bit moved; a changed-effect brew rewrites on
 * any metadata change.</p>
 *
 * <p>Ownership: mutated only by the simulation context (the engine's click
 * routing, the hopper transfers and the per-tick brew walk, all on the
 * tick thread).</p>
 */
public final class BrewingStandBlockEntity {

    /** The stand's GUI slot count (the reference's 4-stack inventory). */
    public static final int SLOT_COUNT = 4;
    /** The ingredient slot index (the reference's INGREDIENT_SLOTS = {3}). */
    public static final int SLOT_INGREDIENT = 3;
    /** The brew duration (tick line 72: 400). */
    public static final int BREW_TICKS = 400;

    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];
    private final ItemStack[] syncedView = new ItemStack[SLOT_COUNT];
    private int timer;
    /** The ingredient identity the armed timer remembers (the item, not the count). */
    private net.zaminmc.torch.util.Identifier armedIngredient;
    /** The last bottle-bits state the world write published. */
    private boolean[] syncedBottles = new boolean[3];
    private boolean bottlesDirty = true;
    private int slotsSerial;

    /**
     * The contents-changed wake (the reference's {@code TileEntity.markDirty}
     * -> {@code updateNeighborComparators}): invoked on every slot mutation
     * so the comparators reading this container re-evaluate. Wired by the
     * system; runs on the tick thread.
     */
    private Runnable contentsListener;

    public BrewingStandBlockEntity() {
        Arrays.fill(slots, ItemStack.EMPTY);
        Arrays.fill(syncedView, ItemStack.EMPTY);
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

    // ------------------------------------------------------------------
    // The brew cycle (tick 1:1)
    // ------------------------------------------------------------------

    /**
     * One tick of the stand (lines 58-92): the armed timer counts down to
     * the brew (the cancellation gates in the reference's own order — the
     * not-brewing-anymore check, then the item-changed check); an unarmed
     * stand with a fresh brew arms the 400-tick window. The bottle-bits
     * sync delegates to {@link #consumeBottleBitsChange()} (the system's
     * world write).
     */
    public void tick() {
        if (timer > 0) {
            timer--;
            if (timer == 0) {
                brew();
                bump(); // the reference's markDirty
            } else if (!canBrew()) {
                timer = 0;
                bump();
            } else if (!Objects.equals(armedIngredient, ingredientIdentity())) {
                timer = 0;
                bump();
            }
        } else if (canBrew()) {
            timer = BREW_TICKS;
            armedIngredient = ingredientIdentity();
        }
        // The bottle bits (lines 76-91): the occupancy change flags the
        // world write — the system reads it after this tick.
        boolean[] occupancy = findHasPotion();
        if (!Arrays.equals(occupancy, syncedBottles)) {
            syncedBottles = occupancy;
            bottlesDirty = true;
        }
    }

    /** @return the remaining brew ticks (the wire's property 0). */
    public int timer() {
        return timer;
    }

    /**
     * The occupancy change (findHasPotion lines 267-277 over the cached
     * hasPottle pair): consumed once per change by the system's world
     * write.
     */
    public boolean consumeBottleBitsChange() {
        if (!bottlesDirty) {
            return false;
        }
        bottlesDirty = false;
        return true;
    }

    /** The three slots' occupancy (findHasPotion 1:1). */
    public boolean[] findHasPotion() {
        boolean[] occupancy = new boolean[3];
        for (int i = 0; i < 3; i++) {
            occupancy[i] = !slots[i].isEmpty();
        }
        return occupancy;
    }

    // ------------------------------------------------------------------
    // The gates (canBrew + brew 1:1)
    // ------------------------------------------------------------------

    /**
     * Whether the stand brews (canBrew lines 94-125): an ingredient stack
     * present, and at least one potion slot the ingredient changes — the
     * splash conversion always counts; otherwise the reference's change
     * gate: {@code (j <= 0 || lists differ) && (old empty || lists differ
     * and new non-empty) && j != k}.
     */
    public boolean canBrew() {
        ItemStack ingredient = slots[SLOT_INGREDIENT];
        if (ingredient.isEmpty()) {
            return false;
        }
        if (!BrewIngredients.isIngredient(ingredient)) {
            return false;
        }
        boolean changes = false;
        for (int i = 0; i < 3; i++) {
            if (!slots[i].isEmpty() && slots[i].type()
                    .identifier().toString().equals("minecraft:potion")) {
                int from = slots[i].damage();
                int to = applyIngredient(from, ingredient);
                if (!PotionHelper.isSplashPotion(from) && PotionHelper.isSplashPotion(to)) {
                    changes = true;
                    break;
                }
                List<StatusEffectInstance> before = PotionHelper.getStatusEffects(from);
                List<StatusEffectInstance> after = PotionHelper.getStatusEffects(to);
                boolean differs = !effectsEqual(before, after);
                if ((from <= 0 || differs)
                        && (before.isEmpty() || (differs && !after.isEmpty()))
                        && from != to) {
                    changes = true;
                    break;
                }
            }
        }
        return changes;
    }

    /**
     * The brew itself (brew lines 127-156): every changed potion slot
     * rewrites through the reference's careful write gate, then the
     * ingredient pays one unit.
     */
    private void brew() {
        if (!canBrew()) {
            return;
        }
        ItemStack ingredient = slots[SLOT_INGREDIENT];
        for (int i = 0; i < 3; i++) {
            if (!slots[i].isEmpty() && slots[i].type()
                    .identifier().toString().equals("minecraft:potion")) {
                int from = slots[i].damage();
                int to = applyIngredient(from, ingredient);
                List<StatusEffectInstance> before = PotionHelper.getStatusEffects(from);
                List<StatusEffectInstance> after = PotionHelper.getStatusEffects(to);
                boolean same = effectsEqual(before, after);
                if ((from > 0 && same) || (!before.isEmpty() && (same || after.isEmpty()))) {
                    // The same-effect brew only rewrites when the splash
                    // bit itself moved (lines 137-140).
                    if (!PotionHelper.isSplashPotion(from) && PotionHelper.isSplashPotion(to)) {
                        slots[i] = slots[i].withDamage(to);
                    }
                } else if (from != to) {
                    slots[i] = slots[i].withDamage(to);
                }
            }
        }
        // The ingredient's one-unit payment (lines 147-154): no 1.8.8
        // brewing ingredient carries a recipe remainder — the count-down
        // is the whole arm (the bucket-family remainders ride their own
        // items).
        slots[SLOT_INGREDIENT] = ingredient.withCount(ingredient.count() - 1);
        if (slots[SLOT_INGREDIENT].count() <= 0) {
            slots[SLOT_INGREDIENT] = ItemStack.EMPTY;
        }
        armedIngredient = null; // the spent cycle re-arms on the next walk
    }

    /** The flag walk (applyIngredient lines 158-166 over the ingredient). */
    private static int applyIngredient(int metadata, ItemStack ingredient) {
        String recipe = BrewIngredients.recipeOf(ingredient);
        return recipe == null ? metadata : PotionHelper.applyIngredient(metadata, recipe);
    }

    /** The ingredient slot's item identity (the reference's getItem()). */
    private net.zaminmc.torch.util.Identifier ingredientIdentity() {
        ItemStack ingredient = slots[SLOT_INGREDIENT];
        return ingredient.isEmpty() ? null : ingredient.type().identifier();
    }

    /**
     * The effect-list value equality (the reference's cached-list identity:
     * the same metadata yields the same cached instance — element equality
     * over id/amplifier/duration/splash carries the same verdict).
     */
    private static boolean effectsEqual(List<StatusEffectInstance> a, List<StatusEffectInstance> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            StatusEffectInstance x = a.get(i);
            StatusEffectInstance y = b.get(i);
            if (x.id() != y.id() || x.amplifier() != y.amplifier()
                    || x.duration() != y.duration() || x.splash() != y.splash()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // The slots (the Inventory surface)
    // ------------------------------------------------------------------

    /** @return the stack at the slot (possibly empty). */
    public ItemStack getItem(int slot) {
        rangeCheck(slot);
        return slots[slot];
    }

    /** The direct write (the hopper transfers, the click path). */
    public void setItem(int slot, ItemStack stack) {
        rangeCheck(slot);
        ItemStack clamped = Objects.requireNonNull(stack, "stack");
        if (!clamped.isEmpty() && clamped.count() > maxStackOf(slot)) {
            clamped = clamped.withCount(maxStackOf(slot));
        }
        slots[slot] = clamped;
        bump();
    }

    /**
     * The slot filter (isItemAllowed lines 263-265): the ingredient slot
     * takes ingredients; the potion slots take potions and glass bottles.
     */
    public static boolean isItemAllowed(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String id = stack.type().identifier().toString();
        if (slot == SLOT_INGREDIENT) {
            return BrewIngredients.isIngredient(stack);
        }
        return id.equals("minecraft:potion") || id.equals("minecraft:glass_bottle");
    }

    /** The slot's stack limit (the menu slots' own overrides). */
    public static int maxStackOf(int slot) {
        return slot == SLOT_INGREDIENT ? 64 : 1;
    }

    // ------------------------------------------------------------------
    // The window-click semantics (the BrewingStandMenu over the slots)
    // ------------------------------------------------------------------

    /**
     * Semantic left/right click on one slot (window click mode 0): the
     * historical walk with the per-slot filters and the potion slots'
     * one-stack limit.
     */
    public void clickSlot(int slot, int button, net.zaminmc.torch.server.player.PlayerInventory inventory) {
        rangeCheck(slot);
        WindowClicks.click(slots, slot, button, inventory.cursorBox(),
                maxStackOf(slot), cursor -> isItemAllowed(slot, cursor));
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
     * Semantic shift-click out of a stand slot: the stack moves into the
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
     * Semantic shift-click from the player inventory into the stand
     * (quickMoveItem lines 65-113, the content-based arms): an ingredient
     * into the empty ingredient slot, a potion or glass bottle into the
     * potion slots — ONE per slot (the maxStack-1 walk), so a stack of
     * bottles distributes itself; anything else never leaves the
     * inventory (the reference returns the unmoved null).
     *
     * @return the remainder that did not fit (the caller puts it back).
     */
    public ItemStack quickMoveIn(ItemStack moving) {
        if (moving.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (BrewIngredients.isIngredient(moving) && slots[SLOT_INGREDIENT].isEmpty()) {
            // The ingredient arm (lines 72-75): the empty slot takes the
            // whole stack (the 64 merge cap).
            slots[SLOT_INGREDIENT] = moving;
            bump();
            return ItemStack.EMPTY;
        }
        if (isItemAllowed(0, moving)) {
            // The potion arm (lines 76-79): the merge pass first (the
            // matching partial stacks), then the one-per-empty-slot walk
            // (the maxStack-1 rule — moveItem with the PotionSlot limit).
            for (int slot = 0; slot < 3 && !moving.isEmpty(); slot++) {
                ItemStack current = slots[slot];
                if (!current.isEmpty() && ItemStack.mergeable(current, moving)
                        && current.count() < maxStackOf(slot)) {
                    int capacity = maxStackOf(slot) - current.count();
                    int transfer = Math.min(capacity, moving.count());
                    slots[slot] = current.withCount(current.count() + transfer);
                    moving = moving.withCount(moving.count() - transfer);
                }
            }
            for (int slot = 0; slot < 3 && !moving.isEmpty(); slot++) {
                if (slots[slot].isEmpty()) {
                    int take = Math.min(1, moving.count());
                    slots[slot] = moving.withCount(take);
                    moving = moving.withCount(moving.count() - take);
                }
            }
            if (moving.isEmpty()) {
                bump();
                return ItemStack.EMPTY;
            }
            bump();
            return moving; // the overflow stays in the inventory
        }
        return moving; // not brewable: nothing moves (the reference's null)
    }

    /** @return a defensive copy of the four slots, index-aligned. */
    public ItemStack[] snapshotSlots() {
        return slots.clone();
    }

    /** Restores persisted slots (the save's Items list). */
    public void restoreSlots(ItemStack[] restored) {
        for (int i = 0; i < SLOT_COUNT && i < restored.length; i++) {
            slots[i] = restored[i] == null ? ItemStack.EMPTY : restored[i];
        }
        Arrays.fill(syncedView, ItemStack.EMPTY);
        Arrays.fill(syncedBottles, false);
        syncedBottles = findHasPotion();
        bottlesDirty = true;
    }

    private static void rangeCheck(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("Brewing stand slot out of range: " + slot);
        }
    }
}
