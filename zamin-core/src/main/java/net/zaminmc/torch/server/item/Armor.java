package net.zaminmc.torch.server.item;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Which armor piece an item is: slot, historical armor points, keyed by the
 * item's canonical identifier. The {@link Tools} shape again: gameplay data
 * populated by {@link BuiltinItems} when the armor items are declared — one
 * source, no second list to drift.
 *
 * <p>Consumers: the inventory (armor-slot placement validation + the armor
 * bar total), the combat paths (the 1.8 damage-reduction envelope) and the
 * window clicks (armor slots only accept their own kind). The registry is
 * immutable after the built-ins load; lookups are lock-free reads.</p>
 */
public final class Armor {

    /** The four armor slots (engine order, wire 5-8 of the player window). */
    public enum Slot {
        HEAD, CHEST, LEGS, FEET;

        /** @return the slot for a legacy 1.8 armor damage-group name, or null. */
        public static Slot byName(String name) {
            return switch (name) {
                case "helmet" -> HEAD;
                case "chestplate" -> CHEST;
                case "leggings" -> LEGS;
                case "boots" -> FEET;
                default -> null;
            };
        }
    }

    /** One armor piece's gameplay spec: its slot and historical armor points. */
    public record Spec(Slot slot, int armorPoints) {
    }

    private static final Map<Identifier, Spec> SPECS = new HashMap<>();

    private Armor() {
    }

    /** Declares the armor identity of one item. Built-in registration only. */
    static void define(Identifier item, Slot slot, int armorPoints) {
        Spec existing = SPECS.put(item, new Spec(slot, armorPoints));
        if (existing != null) {
            throw new IllegalStateException("Duplicate armor definition: " + item);
        }
    }

    /** @return the armor identity of the item type, or empty when it is not armor. */
    public static Optional<Spec> specOf(ItemType itemType) {
        if (itemType == null) {
            return Optional.empty();
        }
        // SPECS is fully populated by BuiltinItems' class initialization
        // (which necessarily precedes any item lookup) and never mutated after.
        return Optional.ofNullable(SPECS.get(itemType.identifier()));
    }

    /**
     * @return whether the item may enter the given slot (armor only enters its
     *         own kind; anything else is rejected by the click semantics).
     */
    public static boolean fitsSlot(ItemType itemType, Slot slot) {
        return specOf(itemType).map(spec -> spec.slot() == slot).orElse(false);
    }

    /**
     * The 1.8 damage reduction of an armor total against one hit (toughness 0
     * for every piece we ship): {@code min(20, max(armor/5, armor - dmg/2))}
     * points absorb, each absorbing 1/25 of the damage. The historical
     * EntityLivingBase.applyArmor envelope.
     *
     * @param armorPoints the sum of the equipped pieces (0-20 on the bar)
     * @param damage      the raw incoming damage
     * @return the damage that passes through
     */
    public static float reduce(float armorPoints, float damage) {
        if (armorPoints <= 0 || damage <= 0) {
            return damage;
        }
        float absorbed = Math.min(20.0f,
                Math.max(armorPoints / 5.0f, armorPoints - damage / 2.0f));
        return damage * (1.0f - absorbed / 25.0f);
    }
}
