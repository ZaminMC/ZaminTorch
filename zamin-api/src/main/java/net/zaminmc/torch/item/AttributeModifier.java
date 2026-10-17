package net.zaminmc.torch.item;

import java.util.Objects;
import java.util.UUID;

/**
 * One attribute modifier row on an item stack's tag (Wave J — vanilla
 * {@code tag.AttributeModifiers}, a TAG_LIST of TAG_COMPOUND).
 *
 * <p>The element's shape is the reference's own (ItemStack.getAttributeModifiers
 * lines 646-670 over EntityAttributes.fromNbt lines 95-106): each element
 * carries {@code AttributeName} (the multimap key — which attribute the row
 * modifies, e.g. {@code generic.attackDamage}), {@code Name} (the modifier's
 * own display name, e.g. {@code Weapon modifier}), {@code Amount} (double),
 * {@code Operation} (0 add / 1 add-amount-times-base / 2 multiply-total) and
 * the two uuid longs. The fold itself — {@code ModifiableEntityAttributeInstance
 * .computeValue} — is the melee walk's own consult
 * ({@code net.zaminmc.torch.server.item.Tools#heldDamageOf}).</p>
 *
 * <p>Validation mirrors the reference's constructor (AttributeModifier lines
 * 19-26): the name may not be empty and the operation must be 0..2 — the
 * reference's own {@code Validate} pair, the same exceptions the reference's
 * {@code fromNbt} catches into a skipped element. The element-skip (an
 * invalid row, a uuid with a zero arm) lives in the READ walk
 * ({@code ItemNbt}), never here: a record that exists is a valid row.</p>
 *
 * <p>Identity is the whole row (the tag-compare semantics of the historical
 * {@code areItemStacksEqual}), not the reference's uuid-only equals — that
 * one is the live-attribute-instance set's own de-duplication
 * ({@code ModifiableEntityAttributeInstance.addModifier}'s duplicate gate),
 * a different subsystem with a different rule.</p>
 *
 * @param attributeName the attribute the row modifies (the multimap key,
 *                     {@code generic.attackDamage} for the melee consult);
 *                     may be any non-null string — the reference never
 *                     validates it, an unknown key simply never consults
 * @param name         the modifier's own name (non-empty, the reference's
 *                     Validate.notEmpty)
 * @param amount       the modifier's amount in the operation's own units
 * @param operation    0 (add), 1 (add amount*base) or 2 (multiply total) —
 *                     the reference's Validate.inclusiveBetween(0, 2)
 * @param uuid         the row's identity on the wire and in NBT (the
 *                     UUIDMost/UUIDLeast long pair)
 */
public record AttributeModifier(String attributeName, String name,
                                double amount, int operation, UUID uuid) {

    public AttributeModifier {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(attributeName, "attributeName");
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Modifier name cannot be empty");
        }
        if (operation < 0 || operation > 2) {
            throw new IllegalArgumentException("Invalid operation: " + operation);
        }
    }

    /** The melee consult's own attribute key (the reference's EntityAttributes.ATTACK_DAMAGE name). */
    public static final String ATTACK_DAMAGE = "generic.attackDamage";

    /**
     * @return true when this row names {@code generic.attackDamage} — the
     *         attribute the held-damage fold consults (every other key rides
     *         the tag unconsulted: the engine models the data, the tooltip
     *         and the attribute families it does not yet run stay faithful).
     */
    public boolean isAttackDamage() {
        return ATTACK_DAMAGE.equals(attributeName);
    }
}
