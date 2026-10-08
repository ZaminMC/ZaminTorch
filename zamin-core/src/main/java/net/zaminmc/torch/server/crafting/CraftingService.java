package net.zaminmc.torch.server.crafting;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The crafting matcher: given the player's 2x2 grid, decides the recipe and
 * hands out results. Pure query logic over grid snapshots; the grid itself is
 * player window state owned by the tick thread (§429-§432 discipline).
 */
public final class CraftingService {

    private final List<CraftingRecipe> recipes;

    public CraftingService(List<CraftingRecipe> recipes) {
        this.recipes = List.copyOf(Objects.requireNonNull(recipes, "recipes"));
    }

    /** The engine's community-data recipe set (see {@link BuiltinRecipes}). */
    public static CraftingService builtin() {
        return new CraftingService(BuiltinRecipes.ALL);
    }

    /**
     * @return the result stack the 2x2 grid currently crafts, or empty. The
     *         result is a preview: taking it costs one unit per non-empty grid
     *         cell (vanilla consumption), which the caller performs through
     *         {@link net.zaminmc.torch.server.player.CraftingGrid#consumeOne()} only
     *         after the take has actually succeeded.
     */
    public Optional<ItemStack> resultOf(ItemStack[] gridSnapshot) {
        return resultOf(gridSnapshot, 2);
    }

    /** The table form: matching over a 3x3 grid (9 cells, row-major). */
    public Optional<ItemStack> resultOf3x3(ItemStack[] gridSnapshot) {
        return resultOf(gridSnapshot, 3);
    }

    private Optional<ItemStack> resultOf(ItemStack[] gridSnapshot, int cols) {
        return match(gridSnapshot, cols).map(this::resultStackOf);
    }

    /** @return the first matching recipe on a 2x2 grid, or empty (dataset order). */
    public Optional<CraftingRecipe> match(ItemStack[] gridSnapshot) {
        return match(gridSnapshot, 2);
    }

    /** @return the first matching recipe on the given grid shape, or empty. */
    public Optional<CraftingRecipe> match(ItemStack[] gridSnapshot, int cols) {
        for (CraftingRecipe recipe : recipes) {
            if (recipe.matches(gridSnapshot, cols)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves a recipe result into a stack. Unresolvable identifiers yield
     * empty (a generated recipe can never hit this while its items are
     * registered; the guard keeps a registry regression loud rather than fatal).
     */
    private ItemStack resultStackOf(CraftingRecipe recipe) {
        CraftingRecipe.Result result = recipe.result();
        return BuiltinItems.lookup(result.item())
                .map(type -> {
                    if (result.damage() == 0) {
                        return ItemStack.of(type, result.count());
                    }
                    return ItemStack.of(type, result.count()).withDamage(result.damage());
                })
                .orElse(ItemStack.EMPTY);
    }
}
