package net.zaminmc.torch.enchantment;

import net.zaminmc.torch.item.ItemStack;

import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The public enchantment registry (§27.3's registry read surface): the
 * engine's enchantment set, readable from any thread — the registry is
 * frozen at boot (the reference's {@code Enchantment.ALL} table).
 *
 * <p>Besides the identity reads, the registry exposes the enchanting
 * <em>data</em> predicates a plugin needs to reason like the reference:
 * whether an item can carry an enchantment, whether two enchantments
 * may coexist, an item's enchantability tier, and the per-level XP
 * curves — every table transcribed from the reference's
 * {@code Enchantment} subclasses.</p>
 *
 * <p><b>Threading (§27.6):</b> every method is safe from any thread. The
 * views are immutable value snapshots and the predicates are pure reads
 * over the frozen tables.</p>
 */
public interface EnchantmentRegistry {

    /** @return every registered enchantment in the registry's own id order. */
    Collection<EnchantmentView> all();

    /** @return the enchantment with the legacy 1.8 id, or empty. */
    Optional<EnchantmentView> byId(int id);

    /** @return the enchantment with its registry key (e.g. "sharpness"), or empty. */
    Optional<EnchantmentView> byKey(String key);

    /**
     * Whether the stack's item can carry the enchantment — the
     * reference's {@code Enchantment.canEnchant} including the damage
     * family's axe arm (a sharpness that refuses a sword still accepts
     * an axe). The book's take-everything rule is the enchanting
     * table's own flag, NOT this predicate: a plain book answers false
     * here exactly like the reference's category walk.
     *
     * @return false for an unknown id or an empty stack
     */
    default boolean canEnchant(int id, ItemStack stack) {
        return false;
    }

    /**
     * Whether two enchantments may coexist on one stack — the
     * reference's {@code isCompatible} over the family conflicts (the
     * protections exclude each other except feather falling's pairing;
     * the damage trio is mutually exclusive). Unknown ids answer true
     * (no known conflict).
     */
    default boolean isCompatible(int id, int otherId) {
        return true;
    }

    /**
     * The stack's enchantability tier — the reference's
     * {@code getEnchantability} material tables: tools by material
     * (wood 15, stone 5, iron 14, diamond 10, gold 22), armor by tier
     * (leather 15, chain 12, iron 9, gold 25, diamond 10), the book,
     * bow and fishing rod 1 — and the shears' historical 0 (the
     * reference's silence: the table refuses them in 1.8.8).
     *
     * @return 0 when the item is not enchantable
     */
    default int enchantabilityOf(ItemStack stack) {
        return 0;
    }

    /**
     * The minimum XP requirement of one level of the enchantment — the
     * family curves ({@code getMinXpRequirement}: sharpness
     * {@code 1 + (level-1)*11}, protection the subtype tables, …).
     *
     * @return empty for an unknown id
     */
    default OptionalInt minXp(int id, int level) {
        return OptionalInt.empty();
    }

    /**
     * The maximum XP requirement of one level — the family curves
     * ({@code getMaxXpRequirement}: the min plus the family's window).
     *
     * @return empty for an unknown id
     */
    default OptionalInt maxXp(int id, int level) {
        return OptionalInt.empty();
    }

    /** One enchantment's immutable view (§27.5: a value, never a live handle). */
    record EnchantmentView(
            /** The legacy 1.8 enchantment id (the wire and NBT carry it). */
            int id,
            /** The registry key (e.g. "protection", "sharpness"). */
            String key,
            /** The rarity weight (the enchanting table's pick pool). */
            int weight,
            /** The highest level the enchantment reaches. */
            int maxLevel) {
    }
}
