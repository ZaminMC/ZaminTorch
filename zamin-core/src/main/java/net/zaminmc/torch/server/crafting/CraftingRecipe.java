package net.zaminmc.torch.server.crafting;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * One crafting recipe with vanilla 1.8 matching semantics, generated into
 * {@link BuiltinRecipes} from community data (PrismarineJS/minecraft-data,
 * pc/1.8 recipes.json).
 *
 * <p>Two forms exist. A <em>shaped</em> recipe stores its pattern (null cell
 * = must be empty) and matches with the actual vanilla 1.8.8
 * {@code ShapedRecipe.matches} algorithm (ported from
 * {@code reference/1.8.8/net/minecraft/crafting/recipe/ShapedRecipe.java}):
 * every offset of the pattern inside the virtual 3x3 grid is tried, mirror
 * first, and every cell outside the pattern footprint must be empty.
 * A <em>shapeless</em> recipe stores a multiset of ingredients and matches
 * with the vanilla {@code ShapelessRecipe.matches} rule: each filled grid
 * cell consumes the first ingredient it matches and the list must end
 * empty.</p>
 *
 * <p>Ingredients and results identify items by canonical identifier. An
 * ingredient with {@code exactMetadata == false} accepts any damage value
 * (the vanilla metadata wildcard 32767); an exact ingredient demands the
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
     * The vanilla footprint size (ShapedRecipe.size = width*height,
     * ShapelessRecipe.size = ingredient count) — the match-order key.
     */
    public int size() {
        return shape != null ? shape.length * shape[0].length : loose.size();
    }

    /**
     * @return whether the 2x2 grid (4 cells, row-major) matches this recipe.
     *         Deterministic and side-effect free; runs on the simulation thread.
     */
    public boolean matches(ItemStack[] grid) {
        return matches(grid, 2);
    }

    /** Vanilla's hardcoded maximum grid dimension (ShapedRecipe.matches). */
    private static final int MAX_GRID = 3;

    /**
     * @return whether the grid ({@code cols} cells per row, row-major) matches
     *         this recipe, with the actual vanilla 1.8.8 semantics (ported
     *         from reference/1.8.8 ShapedRecipe/ShapelessRecipe): shaped
     *         patterns scan every offset of the virtual 3x3 grid, mirror
     *         orientation first, and demand empty cells around the footprint;
     *         a 3x3 pattern never matches a 2x2 grid, small patterns match
     *         anywhere they fit. Deterministic and side-effect free; runs on
     *         the simulation thread.
     */
    public boolean matches(ItemStack[] grid, int cols) {
        if (grid == null || grid.length != 4 && grid.length != 9) {
            throw new IllegalArgumentException("Grid must be a crafting grid (4 or 9 cells)");
        }
        if (grid.length % cols != 0) {
            throw new IllegalArgumentException("Grid length " + grid.length + " is not " + cols + "-aligned");
        }
        return shape != null ? matchesShaped(grid, cols) : matchesShapeless(grid, cols);
    }

    /**
     * The vanilla offset scan (ShapedRecipe.matches, lines 42-56 of the
     * reference): for every placement of the pattern inside the virtual 3x3,
     * the mirror orientation is tried FIRST, then the normal one. Out of
     * bounds grid cells read empty, so a 2x2 inventory behaves as the corner
     * of the same virtual 3x3 — exactly the vanilla CraftingInventory read.
     */
    private boolean matchesShaped(ItemStack[] grid, int cols) {
        int patternWidth = shape[0].length;
        int patternHeight = shape.length;
        for (int offsetX = 0; offsetX <= MAX_GRID - patternWidth; offsetX++) {
            for (int offsetY = 0; offsetY <= MAX_GRID - patternHeight; offsetY++) {
                if (matchesAt(grid, cols, offsetX, offsetY, true)) {
                    return true;
                }
                if (matchesAt(grid, cols, offsetX, offsetY, false)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The vanilla cell loop (ShapedRecipe.matches, lines 58-90): a full 3x3
     * walk where pattern coordinates fall out of the recipe's (x, y) offset;
     * pattern cells demand their item (with the 32767 metadata wildcard) and
     * everything else demands emptiness.
     */
    private boolean matchesAt(ItemStack[] grid, int cols, int offsetX, int offsetY, boolean mirrored) {
        int patternWidth = shape[0].length;
        int patternHeight = shape.length;
        for (int col = 0; col < MAX_GRID; col++) {
            for (int row = 0; row < MAX_GRID; row++) {
                int patternCol = col - offsetX;
                int patternRow = row - offsetY;
                Ingredient ingredient = null;
                if (patternCol >= 0 && patternRow >= 0
                        && patternCol < patternWidth && patternRow < patternHeight) {
                    ingredient = mirrored
                            ? shape[patternRow][patternWidth - patternCol - 1]
                            : shape[patternRow][patternCol];
                }
                ItemStack cell = cellAt(grid, cols, col, row);
                if (cell.isEmpty() && ingredient == null) {
                    continue;
                }
                if (cell.isEmpty() != (ingredient == null)) {
                    return false; // one side filled, the other empty
                }
                if (!ingredient.matches(cell)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Vanilla CraftingInventory.getItem: out-of-bounds reads as empty. */
    private static ItemStack cellAt(ItemStack[] grid, int cols, int col, int row) {
        int rows = grid.length / cols;
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return ItemStack.EMPTY;
        }
        return grid[row * cols + col];
    }

    /**
     * The vanilla shapeless rule (ShapelessRecipe.matches, lines 38-64):
     * every filled grid cell removes the first ingredient it matches; an
     * unmatched filled cell fails and the ingredient list must end empty.
     */
    private boolean matchesShapeless(ItemStack[] grid, int cols) {
        List<Ingredient> remaining = new ArrayList<>(loose);
        int rows = grid.length / cols;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                ItemStack cell = grid[row * cols + col];
                if (cell.isEmpty()) {
                    continue;
                }
                boolean matched = false;
                for (Iterator<Ingredient> it = remaining.iterator(); it.hasNext() && !matched; ) {
                    if (it.next().matches(cell)) {
                        it.remove();
                        matched = true;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
        }
        return remaining.isEmpty();
    }

    private static Ingredient[][] deepCopy(Ingredient[][] shape) {
        Ingredient[][] copy = new Ingredient[shape.length][];
        for (int i = 0; i < shape.length; i++) {
            copy[i] = shape[i].clone();
        }
        return copy;
    }
}
