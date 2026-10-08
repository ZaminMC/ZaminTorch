package net.zamin.api;

/**
 * The identity and basic properties of an item, independent of any quantity.
 *
 * <p>An {@code ItemType} is "what a diamond sword is"; an {@link ItemStack} is
 * "three of them in a slot". Item types are stable identities referenced by
 * canonical identifier; numeric protocol representations belong to version
 * adapters as translation data.</p>
 */
public interface ItemType {

    /** The canonical identifier, for example {@code minecraft:dirt}. */
    Identifier identifier();

    /** Human-readable display name for diagnostics and future client use. */
    default String displayName() {
        return identifier().value();
    }

    /**
     * Maximum number of units that may occupy one stack. Historical default is
     * 64; tools and other durability-bound items stack to 1.
     */
    default int maxStackSize() {
        return 64;
    }

    /**
     * Maximum damage (used durability) this item can carry before it breaks.
     * {@code 0} means the item is not durability-bound: its stacks use the
     * damage field as variant metadata instead (charcoal, red sand). Tools
     * expose their material's limit here (wood 59, stone 131, iron 250,
     * diamond 1561, gold 32, shears 238).
     */
    default int maxDurability() {
        return 0;
    }
}
