package net.zaminmc.torch.item;

import java.util.Objects;

/**
 * A concrete, possibly-empty quantity of one {@link ItemType} (§426).
 *
 * <p>An item stack never carries slot semantics: it does not know where it is
 * stored. Inventories, item entities and containers hold stacks; the stacks
 * themselves stay pure values.</p>
 *
 * <p>Empty state is canonical (§427): exactly one representation
 * ({@link #EMPTY}) exists; every subsystem must treat emptiness through
 * {@link #isEmpty()}, never by inventing null conventions.</p>
 *
 * <p>The {@code damage} value is the historical 1.8 stack field (vanilla NBT
 * names it {@code Damage}) and is dual-role, exactly like the wire that carries
 * it as one i16 per slot and per item entity: on durability-bound items it is
 * accumulated wear (a worn pickaxe and a fresh one are different stacks), on
 * everything else it is the item's variant metadata (charcoal is coal with
 * damage 1, red sand is sand with damage 1 — the client resolves the variant
 * name itself from the same field). A stack of coal and a stack of charcoal
 * are therefore different stacks and never merge, matching the historical
 * {@code ItemStack.areItemStacksEqual} rules.</p>
 *
 * <p>The optional {@code displayName} is the slice of historical item NBT the
 * engine models (vanilla {@code tag.display.Name}, the anvil rename): it rides
 * the slot encoding's NBT compound on the wire, survives every container, and
 * participates in identity — a named sword and an unnamed sword never merge
 * ({@link #mergeable}), exactly like the historical NBT-aware comparison.</p>
 *
 * @param type        the item identity; {@code null} only for the canonical empty stack
 * @param count       units held; {@code 0} only for the canonical empty stack
 * @param damage      durability wear (durable items) or variant metadata (all other
 *                    items); always {@code 0} for empty stacks. Non-negative i16
 *                    range, further bounded by durability where one exists
 * @param displayName the optional custom name (null = none); never blank, never
 *                    longer than {@link #MAX_NAME_LENGTH}, control characters stripped
 */
public record ItemStack(ItemType type, int count, int damage, String displayName) {

    /** The wire's i16 damage/metadata ceiling (the historical field width). */
    public static final int MAX_DAMAGE = 0x7FFF;

    /**
     * The custom-name ceiling. Generous beyond the historical anvil field's 35
     * characters (the semantic rename entry is a command, not a GUI text box),
     * but bounded so the slot NBT payload stays small.
     */
    public static final int MAX_NAME_LENGTH = 64;

    /** The single canonical empty stack (§427). */
    public static final ItemStack EMPTY = new ItemStack(null, 0);

    public ItemStack {
        if (type == null) {
            if (count != 0 || damage != 0 || displayName != null) {
                throw new IllegalArgumentException("A stack without a type must be empty (count "
                        + count + ", damage " + damage + ")");
            }
        } else {
            if (count < 1) {
                throw new IllegalArgumentException("Stack count must be positive: " + count);
            }
            if (count > type.maxStackSize()) {
                throw new IllegalArgumentException("Stack count " + count
                        + " exceeds max stack size " + type.maxStackSize()
                        + " of " + type.identifier());
            }
            if (damage < 0 || damage > MAX_DAMAGE) {
                throw new IllegalArgumentException("Stack damage/metadata out of i16 range: " + damage);
            }
            int maxDurability = type.maxDurability();
            if (maxDurability > 0 && damage > maxDurability) {
                throw new IllegalArgumentException("Stack damage " + damage
                        + " exceeds max durability " + maxDurability + " of " + type.identifier());
            }
            displayName = sanitizeName(displayName);
        }
    }

    /** A fresh stack with no damage and no custom name. */
    public ItemStack(ItemType type, int count) {
        this(type, count, 0, null);
    }

    /** A stack with explicit damage and no custom name (the common existing shape). */
    public ItemStack(ItemType type, int count, int damage) {
        this(type, count, damage, null);
    }

    public static ItemStack of(ItemType type, int count) {
        Objects.requireNonNull(type, "type");
        return new ItemStack(type, count);
    }

    public static ItemStack of(ItemType type) {
        return of(type, 1);
    }

    public boolean isEmpty() {
        return type == null || count <= 0;
    }

    /**
     * @return a stack of the same type, damage and name with {@code newCount}
     *         units, or the canonical empty stack when it would hold nothing.
     */
    public ItemStack withCount(int newCount) {
        if (newCount <= 0) {
            return isEmpty() ? this : EMPTY;
        }
        return new ItemStack(type, newCount, damage, displayName);
    }

    /**
     * @return a stack of the same type, count and name with the given damage
     *         value. Dual-role like the field itself: validated durability wear
     *         on durable items, variant metadata otherwise (charcoal, red sand).
     *         Breaking (damage reaching the limit) is an inventory decision,
     *         not a value transformation.
     */
    public ItemStack withDamage(int newDamage) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, newDamage, displayName);
    }

    /** @return the same stack carrying {@code newName} (null clears the name). */
    public ItemStack withName(String newName) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, newName);
    }

    /**
     * @return a stack holding up to {@code amount} units taken from the top of
     *         this stack (used by validated pickup/split operations), and this
     *         stack reduced accordingly is produced via {@link #withCount}.
     *         Damage and the custom name carry over: splitting a worn named
     *         sword yields the same worn named sword.
     */
    public ItemStack split(int amount) {
        if (isEmpty() || amount <= 0) {
            return EMPTY;
        }
        int taken = Math.min(Math.min(amount, count), type.maxStackSize());
        return new ItemStack(type, taken, damage, displayName);
    }

    /**
     * The merge rule every container and the item-entity system play: same
     * type, same damage field (wear or variant) and same custom name — the
     * historical {@code areItemStacksEqual} including its NBT comparison. Two
     * differently named swords never combine; neither do a worn and a fresh
     * pickaxe.
     */
    public static boolean mergeable(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.type.equals(b.type)
                && a.damage == b.damage
                && Objects.equals(a.displayName, b.displayName);
    }

    /**
     * Normalizes a custom name: control characters (including the historical
     * newline exploits) are stripped, the result is trimmed and bounded to
     * {@link #MAX_NAME_LENGTH}; blank input means "no name". Formatting codes
     * ({@code §}-sequences) are kept — the historical rename could carry them
     * and the client renders them.
     */
    private static String sanitizeName(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder cleaned = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= 0x20 && c != 0x7F) {
                cleaned.append(c);
            }
        }
        String trimmed = cleaned.toString().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_NAME_LENGTH
                ? trimmed.substring(0, MAX_NAME_LENGTH)
                : trimmed;
    }
}
