package net.zaminmc.torch.server.crafting;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One crafting recipe with vanilla 1.8 matching semantics, generated into
 * {@link BuiltinRecipes} from community data (PrismarineJS/minecraft-data,
 * pc/1.8 recipes.json).
 *
 * <p>Two historical forms exist. A <em>shaped</em> recipe stores its trimmed
 * pattern (empty border rows/columns removed) and matches the non-empty
 * bounding box of the 2x2 grid exactly, in normal or horizontally mirrored
 * orientation. A <em>shapeless</em> recipe stores a multiset of ingredients
 * and matches when every non-empty grid cell pairs with exactly one
 * ingredient.</p>
 *
 * <p>Ingredients and results identify items by canonical identifier. An
 * ingredient with {@code exactMetadata == false} accepts any damage value
 * (the historical "any metadata" bare id); an exact ingredient demands the
 * exact damage-field value, which for block items carries the block
 * metadata (the historical double duty of the slot damage field).</p>
 */
public final class CraftingRecipe {

    /** The crafting output: an item identity, a count and the damage to carry. */
    public record Result(Identifier item, int count, int damage) {

        public Result {
            Objects.requireNonNull(item, "item");
            if (count < 1) {
                throw new IllegalArgumentException("Recipe result count must be positive: " + count);
            }
            if (damage < 0) {
                throw new IllegalArgumentException("Recipe result damage must not be negative: " + damage);
            }
        }
    }

    private final Ingredient[][] shape;      // non-null for shaped: trimmed rows, <=3x3
    private final List<Ingredient> loose;    // non-null for shapeless
    private final Result result;

    private CraftingRecipe(Ingredient[][] shape, List<Ingredient> loose, Result result) {
        this.shape = shape;
        this.loose = loose;
        this.result = Objects.requireNonNull(result, "result");
    }

    /** A shaped recipe: {@code shape} is the trimmed pattern, null cell = must be empty. */
    public static CraftingRecipe shaped(Ingredient[][] shape, Result result) {
        Objects.requireNonNull(shape, "shape");
        if (shape.length < 1 || shape.length > 3) {
            throw new IllegalArgumentException("Shaped recipe rows must be 1..3: " + shape.length);
        }
        int width = shape[0].length;
        if (width < 1 || width > 3) {
            throw new IllegalArgumentException("Shaped recipe columns must be 1..3: " + width);
        }
        for (Ingredient[] row : shape) {
            if (row.length != width) {
                throw new IllegalArgumentException("Shaped recipe must be rectangular");
            }
        }
        return new CraftingRecipe(deepCopy(shape), null, result);
    }

    /** A shapeless recipe: the ingredients form a multiset rule. */
    public static CraftingRecipe shapeless(List<Ingredient> ingredients, Result result) {
        Objects.requireNonNull(ingredients, "ingredients");
        if (ingredients.isEmpty() || ingredients.size() > 9) {
            throw new IllegalArgumentException(
                    "Shapeless recipes take 1..9 ingredients: " + ingredients.size());
        }
        return new CraftingRecipe(null, List.copyOf(ingredients), result);
    }

    public Result result() {
        return result;
    }

    public boolean isShaped() {
        return shape != null;
    }

    /**
     * @return whether the 2x2 grid (4 cells, row-major) matches this recipe.
     *         Deterministic and side-effect free; runs on the simulation thread.
     */
    public boolean matches(ItemStack[] grid) {
        return matches(grid, 2);
    }

    /**
     * @return whether the grid ({@code cols} cells per row, row-major) matches
     *         this recipe. The recipe's trimmed pattern must equal the grid
     *         content's bounding box, so a 3x3 pattern never matches a 2x2
     *         grid and small patterns match anywhere in a larger grid — the
     *         historical bounding-box semantics. Deterministic and
     *         side-effect free; runs on the simulation thread.
     */
    public boolean matches(ItemStack[] grid, int cols) {
        if (grid == null || grid.length != 4 && grid.length != 9) {
            throw new IllegalArgumentException("Grid must be a crafting grid (4 or 9 cells)");
        }
        if (grid.length % cols != 0) {
            throw new IllegalArgumentException("Grid length " + grid.length + " is not " + cols + "-aligned");
        }
        return shape != null ? matchesShaped(grid, cols) : matchesShapeless(grid);
    }

    private boolean matchesShaped(ItemStack[] grid, int cols) {
        int rows = grid.length / cols;
        int minRow = -1, maxRow = -1, minCol = -1, maxCol = -1;
        for (int index = 0; index < grid.length; index++) {
            if (!grid[index].isEmpty()) {
                int row = index / cols;
                int col = index % cols;
                if (minRow < 0) {
                    minRow = row;
                    maxRow = row;
                    minCol = col;
                    maxCol = col;
                } else {
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                    minCol = Math.min(minCol, col);
                    maxCol = Math.max(maxCol, col);
                }
            }
        }
        if (minRow < 0) {
            return false; // empty grid matches nothing
        }
        int height = maxRow - minRow + 1;
        int width = maxCol - minCol + 1;
        if (height != shape.length || width != shape[0].length) {
            return false; // the pattern must occupy the full bounding box
        }
        return cellsAgree(grid, cols, minRow, minCol, false)
                || cellsAgree(grid, cols, minRow, minCol, true);
    }

    private boolean cellsAgree(ItemStack[] grid, int cols, int minRow, int minCol, boolean mirrored) {
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[0].length; col++) {
                int gridRow = minRow + row;
                int gridCol = minCol + (mirrored ? shape[0].length - 1 - col : col);
                ItemStack cell = grid[gridRow * cols + gridCol];
                Ingredient ingredient = shape[row][col];
                if (ingredient == null) {
                    if (!cell.isEmpty()) {
                        return false;
                    }
                } else if (!ingredient.matches(cell)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesShapeless(ItemStack[] grid) {
        List<ItemStack> present = Arrays.stream(grid).filter(s -> !s.isEmpty()).toList();
        if (present.size() != loose.size()) {
            return false;
        }
        boolean[] used = new boolean[loose.size()];
        for (ItemStack stack : present) {
            boolean matched = false;
            for (int i = 0; i < loose.size() && !matched; i++) {
                if (!used[i] && loose.get(i).matches(stack)) {
                    used[i] = true;
                    matched = true;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static Ingredient[][] deepCopy(Ingredient[][] shape) {
        Ingredient[][] copy = new Ingredient[shape.length][];
        for (int i = 0; i < shape.length; i++) {
            copy[i] = shape[i].clone();
        }
        return copy;
    }
}
