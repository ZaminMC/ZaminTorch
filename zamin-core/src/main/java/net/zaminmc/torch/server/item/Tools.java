package net.zaminmc.torch.server.item;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemType;

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

    /**
     * The historical 1.8 melee attack damage of a held item (EntityPlayer
     * attack vs. living entities): the bare hand deals 1, swords and axes
     * carry their material's bonus. Values per the canonical 1.8 ItemSword /
     * ItemAxe / ItemPickaxe / ItemSpade damage fields.
     */
    public static float attackDamageOf(ItemType itemType) {
        ToolSpec spec = specOf(itemType).orElse(null);
        if (spec == null) {
            return 1.0f; // the bare fist (and non-tools)
        }
        ToolMaterial material = spec.material();
        return switch (spec.toolClass()) {
            case SWORD -> switch (material) {
                case WOOD, GOLD -> 4.0f;
                case STONE -> 5.0f;
                case IRON -> 6.0f;
                case DIAMOND -> 7.0f;
            };
            case AXE -> switch (material) {
                case WOOD, GOLD -> 3.0f;
                case STONE -> 4.0f;
                case IRON -> 5.0f;
                case DIAMOND -> 6.0f;
            };
            case PICKAXE -> switch (material) {
                case WOOD, GOLD -> 2.0f;
                case STONE -> 3.0f;
                case IRON -> 4.0f;
                case DIAMOND -> 5.0f;
            };
            case SHOVEL -> 1.5f;
            case SHEARS -> 1.5f;
        };
    }
}
