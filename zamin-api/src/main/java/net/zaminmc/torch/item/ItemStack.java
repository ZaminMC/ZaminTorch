package net.zaminmc.torch.item;

import java.util.Map;
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
 * <p>The optional {@code displayName} is a slice of historical item NBT the
 * engine models (vanilla {@code tag.display.Name}, the anvil rename): it rides
 * the slot encoding's NBT compound on the wire, survives every container, and
 * participates in identity — a named sword and an unnamed sword never merge
 * ({@link #mergeable}), exactly like the historical NBT-aware comparison.</p>
 *
 * <p>The optional {@code enchantments} is the next slice of that NBT (vanilla
 * {@code tag.ench}, the {@code id}/{@code lvl} short-pair list): it rides the
 * same wire compound, participates in identity the same way, and is the
 * storage the enchantment math ({@code server/enchantment}) reads and the
 * enchanting table writes. Null means unenchanted; the map is defensively
 * immutable and keeps insertion order (the historical tooltip order).</p>
 *
 * @param type        the item identity; {@code null} only for the canonical empty stack
 * @param count       units held; {@code 0} only for the canonical empty stack
 * @param damage      durability wear (durable items) or variant metadata (all other
 *                    items); always {@code 0} for empty stacks. Non-negative i16
 *                    range, further bounded by durability where one exists
 * @param displayName the optional custom name (null = none); never blank, never
 *                    longer than {@link #MAX_NAME_LENGTH}, control characters stripped
 * @param enchantments the optional enchantment map (null = none): legacy 1.8
 *                    enchantment id to level, both positive shorts, insertion order kept
 */
public record ItemStack(ItemType type, int count, int damage, String displayName,
                        Map<Integer, Integer> enchantments) {

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

    /** The wire's short-bound level/id ceiling (the historical NBT width). */
    public static final int MAX_ENCHANTMENT_LEVEL = 0x7FFF;

    public ItemStack {
        if (type == null) {
            if (count != 0 || damage != 0 || displayName != null || enchantments != null) {
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
            enchantments = sanitizeEnchantments(enchantments);
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

    /** A stack with a custom name (the anvil shape). */
    public ItemStack(ItemType type, int count, int damage, String displayName) {
        this(type, count, damage, displayName, null);
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
        return new ItemStack(type, newCount, damage, displayName, enchantments);
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
        return new ItemStack(type, count, newDamage, displayName, enchantments);
    }

    /** @return the same stack carrying {@code newName} (null clears the name). */
    public ItemStack withName(String newName) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, newName, enchantments);
    }

    /**
     * @return the same stack carrying {@code newEnchantments} (null or empty
     *         clears the enchantments — the vanilla setEnchantments rule).
     */
    public ItemStack withEnchantments(Map<Integer, Integer> newEnchantments) {
        if (isEmpty()) {
            return this;
        }
        return new ItemStack(type, count, damage, displayName, newEnchantments);
    }

    /**
     * @return the stack with one enchantment set (level 0 or below removes —
     *         the vanilla addEnchantment path stores, it never stacks).
     */
    public ItemStack withEnchantment(int id, int level) {
        if (isEmpty()) {
            return this;
        }
        java.util.LinkedHashMap<Integer, Integer> next = new java.util.LinkedHashMap<>();
        if (enchantments != null) {
            next.putAll(enchantments);
        }
        if (level <= 0) {
            next.remove(id);
        } else {
            next.put(id, level);
        }
        return withEnchantments(next.isEmpty() ? null : next);
    }

    /** @return the level of an enchantment on this stack (0 when absent). */
    public int enchantmentLevel(int id) {
        return enchantments == null ? 0 : enchantments.getOrDefault(id, 0);
    }

    /**
     * @return the read-only enchantment map, null when unenchanted (the
     *         {@code tag.ench} presence check the enchanting table's
     *         {@code isEnchantable} gate reads — an already-enchanted stack
     *         refuses re-enchanting, exactly like the reference).
     */
    public Map<Integer, Integer> enchantments() {
        return enchantments;
    }

    /**
     * Validates the enchantment map: shorts for id and level, levels >= 1,
     * insertion order kept (the historical tooltip order), defensively
     * immutable. Null/empty normalizes to null (unenchanted).
     */
    private static Map<Integer, Integer> sanitizeEnchantments(Map<Integer, Integer> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        java.util.LinkedHashMap<Integer, Integer> validated = new java.util.LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : input.entrySet()) {
            int id = entry.getKey() == null ? -1 : entry.getKey();
            int level = entry.getValue() == null ? -1 : entry.getValue();
            if (id < 0 || id > MAX_ENCHANTMENT_LEVEL) {
                // Vanilla 1.8.8 enchantment ids start at 0 (protection is id
                // 0) — the historical tag.ench shorts carry them as-is.
                throw new IllegalArgumentException("Enchantment id out of short range: " + id);
            }
            if (level < 1 || level > MAX_ENCHANTMENT_LEVEL) {
                throw new IllegalArgumentException("Enchantment level out of short range: " + level);
            }
            validated.put(id, level);
        }
        return java.util.Collections.unmodifiableMap(validated);
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
        return new ItemStack(type, taken, damage, displayName, enchantments);
    }

    /**
     * The merge rule every container and the item-entity system play: same
     * type, same damage field (wear or variant), same custom name and same
     * enchantments — the historical {@code areItemStacksEqual} including its
     * NBT comparison. Two differently named swords never combine; neither do
     * a worn and a fresh pickaxe, nor two differently enchanted ones.
     */
    public static boolean mergeable(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.type.equals(b.type)
                && a.damage == b.damage
                && Objects.equals(a.displayName, b.displayName)
                && Objects.equals(a.enchantments, b.enchantments);
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
