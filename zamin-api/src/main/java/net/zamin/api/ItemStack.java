package net.zamin.api;

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
 * @param type  the item identity; {@code null} only for the canonical empty stack
 * @param count units held; {@code 0} only for the canonical empty stack
 */
public record ItemStack(ItemType type, int count) {

    /** The single canonical empty stack (§427). */
    public static final ItemStack EMPTY = new ItemStack(null, 0);

    public ItemStack {
        if (type == null) {
            if (count != 0) {
                throw new IllegalArgumentException("A stack without a type must be empty (count " + count + ")");
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
        }
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
     * @return a stack of the same type with {@code newCount} units, or the
     *         canonical empty stack when it would hold nothing.
     */
    public ItemStack withCount(int newCount) {
        if (newCount <= 0) {
            return isEmpty() ? this : EMPTY;
        }
        return new ItemStack(type, newCount);
    }

    /**
     * @return a stack holding up to {@code amount} units taken from the top of
     *         this stack (used by validated pickup/split operations), and this
     *         stack reduced accordingly is produced via {@link #withCount}.
     */
    public ItemStack split(int amount) {
        if (isEmpty() || amount <= 0) {
            return EMPTY;
        }
        int taken = Math.min(Math.min(amount, count), type.maxStackSize());
        return new ItemStack(type, taken);
    }
}
