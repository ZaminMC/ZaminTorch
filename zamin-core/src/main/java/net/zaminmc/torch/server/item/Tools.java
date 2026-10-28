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
            case HOE -> 1.0f; // the historical ItemHoe: no dig acceleration
        };
    }

    /**
     * The per-block durability wear of one successful dig (the tool-wear
     * parity audit — the reference's own {@code mineBlock} rows): the
     * SWORD wears TWO (SwordItem line 55: {@code takeDamageAndBreak(2)}),
     * the ToolItem family and the SHEARS wear ONE (ToolItem line 42,
     * ShearsItem line 28 — the shears band-gated at the call site), the
     * HOE wears NOTHING (HoeItem extends Item directly — the default
     * {@code mineBlock} returns false and never wears), and a non-tool
     * wears nothing. The hardness gate lives at the call site
     * ({@code block.getMiningTime != 0.0}).
     */
    public static int blockWearOf(ItemType itemType) {
        ToolSpec spec = specOf(itemType).orElse(null);
        if (spec == null) {
            return 0;
        }
        return switch (spec.toolClass()) {
            case SWORD -> 2;  // SwordItem line 55
            case HOE -> 0;    // HoeItem extends Item: no mineBlock arm
            default -> 1;     // ToolItem line 42 + ShearsItem line 28
        };
    }

    /**
     * The melee durability wear of one landed hit on a living entity (the
     * tool-wear parity audit — the reference's own {@code attack} rows):
     * the ToolItem family wears TWO (ToolItem line 35 — pickaxe, axe,
     * shovel), the SWORD wears ONE (SwordItem line 48), and the shears
     * and hoes wear NOTHING (ShearsItem/HoeItem extend Item directly —
     * the default {@code attack} returns false and never wears).
     */
    public static int meleeWearOf(ItemType itemType) {
        ToolSpec spec = specOf(itemType).orElse(null);
        if (spec == null) {
            return 0;
        }
        return switch (spec.toolClass()) {
            case SWORD -> 1;
            case PICKAXE, AXE, SHOVEL -> 2;
            default -> 0; // shears + hoe: Item's own default — no attack arm
        };
    }

    /**
     * Whether one successful dig pays the used-item row (the stats family
     * audit — {@code ItemStack.mineBlock} lines 252-257's flag over
     * {@code item.mineBlock}): the ToolItem family and the SWORD answer TRUE
     * for EVERY broken block (ToolItem line 45, SwordItem line 58 — the
     * return sits OUTSIDE the hardness gate, so even an instant dig pays
     * while only the WEAR is hardness-gated), while the HOE and every
     * non-tool answer FALSE (HoeItem extends Item directly — the default
     * {@code mineBlock} of Item line 146). The SHEARS answer their own band
     * at the call site (ShearsItem lines 18-29: leaves, web, tallgrass,
     * vine, tripwire, wool — the engine's {@code inShearsWearBand}), exactly
     * the reference's own shape where the band override calls super outside
     * it.
     */
    public static boolean mineUsePaysOf(ItemType itemType) {
        ToolSpec spec = specOf(itemType).orElse(null);
        if (spec == null) {
            return false;
        }
        return switch (spec.toolClass()) {
            case PICKAXE, AXE, SHOVEL, SWORD -> true;
            default -> false; // shears: the band at the call site; hoe: never
        };
    }

    /**
     * Whether one landed melee hit pays the used-item row (the stats family
     * audit — {@code ItemStack.attack} lines 245-250's flag over
     * {@code item.attack}): the ToolItem family (ToolItem line 36) and the
     * SWORD (SwordItem line 49) answer TRUE, while the SHEARS, the HOE, and
     * every non-tool answer FALSE (Item's own default {@code attack}) — the
     * asymmetric twin of {@link #mineUsePaysOf}, whose shears arm pays the
     * band on digs but never on hits.
     */
    public static boolean meleeUsePaysOf(ItemType itemType) {
        ToolSpec spec = specOf(itemType).orElse(null);
        if (spec == null) {
            return false;
        }
        return switch (spec.toolClass()) {
            case PICKAXE, AXE, SHOVEL, SWORD -> true;
            default -> false;
        };
    }

    /**
     * The held stack's melee damage (Wave J — the reference's whole walk):
     * the attackDamage-named modifiers of {@code tag.AttributeModifiers}
     * folded over the bare-hand base 1.0 (EntityPlayer's own registered
     * base — {@code register(ATTACK_DAMAGE).setBase(1.0)}), exactly
     * ModifiableEntityAttributeInstance.computeValue's order:
     * operation 0 adds to the base, then d1 = d0 snapshots, operation 1
     * adds {@code amount * d0} (the POST-op-0 base — the reference's own
     * subtle order), operation 2 multiplies {@code d1 *= 1 + amount}, the
     * RangedEntityAttribute's own clamp 0..2048 closes the walk. A stack
     * WITHOUT the list falls to the item's own defaults — the type table
     * above (the engine's ledgered approximation of the default weapon
     * modifiers). A stack WITH a present-but-empty list folds to the bare
     * fist (the attribute-wipe: the reference never falls back to the
     * defaults when the tag carries the row).
     *
     * <p>The three player melee walks and the mob equipment-better compare
     * consult this fold; the crit multiply and the enchant family ride the
     * walk's own steps after it, exactly the reference's attack order.</p>
     */
    public static float heldDamageOf(net.zaminmc.torch.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 1.0f; // the bare fist
        }
        var modifiers = stack.attributeModifiers();
        if (modifiers == null) {
            return attackDamageOf(stack.type()); // the tagless fall-back: the item's own defaults
        }
        // computeValue's fold, verbatim (ModifiableEntityAttributeInstance
        // lines 137-155): d0 over the op-0 adds, d1 snapshots, the op-1
        // adds scale d0, the op-2 multiplies close, the range clamps.
        double d0 = 1.0;
        for (var modifier : modifiers) {
            if (modifier.operation() == 0 && modifier.isAttackDamage()) {
                d0 += modifier.amount();
            }
        }
        double d1 = d0;
        for (var modifier : modifiers) {
            if (modifier.operation() == 1 && modifier.isAttackDamage()) {
                d1 += d0 * modifier.amount();
            }
        }
        for (var modifier : modifiers) {
            if (modifier.operation() == 2 && modifier.isAttackDamage()) {
                d1 *= 1.0 + modifier.amount();
            }
        }
        // The attackDamage attribute's own range (EntityAttributes line 16:
        // RangedEntityAttribute "generic.attackDamage", 2.0 default, 0.0
        // minimum, 2048.0 maximum — the clamp is the attribute's own law).
        return (float) Math.max(0.0, Math.min(2048.0, d1));
    }
}
