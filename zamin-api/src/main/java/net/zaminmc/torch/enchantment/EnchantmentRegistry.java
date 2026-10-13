package net.zaminmc.torch.enchantment;

import java.util.Collection;
import java.util.Optional;

/**
 * The public enchantment registry (§27.3's registry read surface): the
 * engine's enchantment set, readable from any thread — the registry is
 * frozen at boot (the reference's {@code Enchantment.ALL} table).
 *
 * <p><b>Threading (§27.6):</b> every method is safe from any thread. The
 * views are immutable value snapshots.</p>
 */
public interface EnchantmentRegistry {

    /** @return every registered enchantment in the registry's own id order. */
    Collection<EnchantmentView> all();

    /** @return the enchantment with the legacy 1.8 id, or empty. */
    Optional<EnchantmentView> byId(int id);

    /** @return the enchantment with its registry key (e.g. "sharpness"), or empty. */
    Optional<EnchantmentView> byKey(String key);

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
