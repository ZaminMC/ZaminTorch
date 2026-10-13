package net.zaminmc.torch.crafting;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Identifier;

import java.util.Collection;
import java.util.Optional;

/**
 * The public recipe registry (§27.3's crafting read surface): the
 * engine's recipe set, readable and matchable from any thread — the
 * registry is frozen at boot and every read is a pure function over the
 * passed grid.
 *
 * <p>The matching semantics are the reference's own: the vanilla match
 * order (shaped before shapeless, larger footprints first), the offset
 * scan with the mirror-first rule for shaped patterns, and the multiset
 * rule for shapeless ones. The grid is a row-major array of public
 * {@link ItemStack}s ({@code null} or empty cells count as empty).</p>
 *
 * <p><b>Threading (§27.6):</b> every method is safe from any thread. The
 * views are immutable value snapshots — consuming one off the simulation
 * lane never observes live state.</p>
 */
public interface Recipes {

    /**
     * @return every registered recipe in the vanilla match order (shaped
     *         first, larger footprints first) — immutable views.
     */
    Collection<RecipeView> all();

    /**
     * @return the first recipe matching the 2x2 grid (the vanilla order),
     *         or empty when nothing crafts.
     */
    Optional<RecipeView> match(ItemStack[] grid2x2);

    /**
     * @return the first recipe matching the 3x3 grid (the table form),
     *         or empty when nothing crafts.
     */
    Optional<RecipeView> match3x3(ItemStack[] grid3x3);

    /** One recipe's immutable view (§27.5: a value, never a live handle). */
    record RecipeView(
            /** The crafted output's item identifier. */
            Identifier result,
            /** The crafted output's count. */
            int resultCount,
            /** The crafted output's damage field (the variant carry). */
            int resultDamage,
            /** Whether the recipe is shaped (a pattern) or shapeless (a multiset). */
            boolean shaped,
            /** The ingredient count (the shaped footprint's cells or the multiset's size). */
            int ingredientCount) {
    }
}
