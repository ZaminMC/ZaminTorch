package net.zamin.engine.crafting;

import java.util.List;

/**
 * Crafting recipes the engine currently serves, generated from community data.
 *
 * <p>Data source (per the reuse rule): PrismarineJS/minecraft-data,
 * {@code data/pc/1.8/recipes.json} (MIT license,
 * https://github.com/PrismarineJS/minecraft-data, snapshot 2026-10), filtered
 * to patterns that fit the 2x2 player crafting grid and to ingredients and
 * results the engine has registered. The set grows with the registry, never
 * speculatively; regenerate with {@code scripts/gen-recipes.py}. Dataset
 * names differing from canonical names are translated ({@code planks} ->
 * {@code minecraft:oak_planks}, {@code log} -> {@code minecraft:oak_log});
 * the 1.8 adapter separately translates legacy numeric ids at the wire.</p>
 *
 * <p>Semantics mirror vanilla 1.8 shaped/shapeless matching: a bare ingredient
 * accepts any metadata, an exact (id, metadata) ingredient demands the exact
 * damage-field value; shaped patterns match at the grid's bounding box in
 * normal or mirrored orientation. The 3x3 crafting-table grid arrives with the
 * container slice; recipes that need it are simply absent today.</p>
 */
public final class BuiltinRecipes {

    private BuiltinRecipes() {
    }

    private static Ingredient ing(String item, boolean exactMetadata, int metadata) {
        return new Ingredient(net.zamin.api.Identifier.parse(item), exactMetadata, metadata);
    }

    private static CraftingRecipe.Result res(String item, int count, int damage) {
        return new CraftingRecipe.Result(net.zamin.api.Identifier.parse(item), count, damage);
    }

    /** All generated recipes, in dataset order (deterministic matching). */
    public static final List<CraftingRecipe> ALL = List.of(
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_log", true, 0) }
            },
                    res("minecraft:oak_planks", 4, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:coal", false, 0) },
                    { ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:torch", 4, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_planks", false, 0), ing("minecraft:oak_planks", false, 0) },
                    { ing("minecraft:oak_planks", false, 0), ing("minecraft:oak_planks", false, 0) }
            },
                    res("minecraft:crafting_table", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_planks", false, 0) },
                    { ing("minecraft:oak_planks", false, 0) }
            },
                    res("minecraft:stick", 4, 0))
    );
}
