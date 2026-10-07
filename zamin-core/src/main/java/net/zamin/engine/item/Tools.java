package net.zamin.engine.item;

import net.zamin.api.Identifier;
import net.zamin.api.ItemType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Which tool an item is: class and material, keyed by the item's canonical
 * identifier. This is translation data for gameplay rules (harvest gating,
 * mining speed, durability wear), populated by {@link BuiltinItems} when the
 * tool items are declared - one source, no second list to drift.
 *
 * <p>Consumers: {@code BlockBehaviorTable} (harvest gating + speed) and the
 * interaction service (durability wear on successful digs). The registry is
 * immutable after the built-ins are loaded; lookups are lock-free reads.</p>
 */
public final class Tools {

    private static final Map<Identifier, ToolSpec> SPECS = new HashMap<>();

    private Tools() {
    }

    /** Declares the tool identity of one item. Called during built-in registration only. */
    static void define(Identifier item, ToolClass toolClass, ToolMaterial material) {
        ToolSpec existing = SPECS.put(item, new ToolSpec(toolClass, material));
        if (existing != null) {
            throw new IllegalStateException("Duplicate tool definition: " + item);
        }
    }

    /** @return the tool identity of the item type, or empty when it is not a tool. */
    public static Optional<ToolSpec> specOf(ItemType itemType) {
        if (itemType == null) {
            return Optional.empty();
        }
        // SPECS is fully populated by BuiltinItems' class initialization (which
        // necessarily precedes any item lookup) and never mutated afterwards.
        return Optional.ofNullable(SPECS.get(itemType.identifier()));
    }
}
