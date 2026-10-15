package net.zaminmc.torch.server.crafting;

import net.zaminmc.torch.util.Identifier;

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
 * normal or mirrored orientation. One list serves both grids: the bounding-box
 * matcher keeps 3x3 patterns from matching a 2x2 grid, and small patterns
 * match anywhere in the 3x3 crafting-table grid — the historical semantics.</p>
 */
public final class BuiltinRecipes {

    private BuiltinRecipes() {
    }

    private static Ingredient ing(String item, boolean exactMetadata, int metadata) {
        return new Ingredient(net.zaminmc.torch.util.Identifier.parse(item), exactMetadata, metadata);
    }

    private static CraftingRecipe.Result res(String item, int count, int damage) {
        return new CraftingRecipe.Result(net.zaminmc.torch.util.Identifier.parse(item), count, damage);
    }

    /** All generated recipes, in dataset order (deterministic matching). */
    public static final List<CraftingRecipe> ALL = withMineralRecipes(withDyeRecipes(List.of(
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_log", true, 0) }
            },
                    res("minecraft:oak_planks", 4, 0)),
            // The wood-variant foundation's crafts (Slice 35 —
            // CraftingManager lines 203-226's own rows, 1:1): each wood's
            // log turns into four planks of its variant (the exact-damage
            // 1x1 shaped form, the reference's own shape — the item damage
            // domain carries the bare variant, the engine's upright axis
            // stand-in), and the six doors ride their planks' variant (the
            // 2x3 craft, Items 3 doors per 6 planks).
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:log", true, 1) }
            },
                    res("minecraft:planks", 4, 1)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:log", true, 2) }
            },
                    res("minecraft:planks", 4, 2)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:log", true, 3) }
            },
                    res("minecraft:planks", 4, 3)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:log2", true, 0) }
            },
                    res("minecraft:planks", 4, 4)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:log2", true, 1) }
            },
                    res("minecraft:planks", 4, 5)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:planks", true, 0), ing("minecraft:planks", true, 0) },
                    { ing("minecraft:planks", true, 0), ing("minecraft:planks", true, 0) },
                    { ing("minecraft:planks", true, 0), ing("minecraft:planks", true, 0) }
            },
                    res("minecraft:oak_door", 3, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:planks", true, 1), ing("minecraft:planks", true, 1) },
                    { ing("minecraft:planks", true, 1), ing("minecraft:planks", true, 1) },
                    { ing("minecraft:planks", true, 1), ing("minecraft:planks", true, 1) }
            },
                    res("minecraft:spruce_door", 3, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:planks", true, 2), ing("minecraft:planks", true, 2) },
                    { ing("minecraft:planks", true, 2), ing("minecraft:planks", true, 2) },
                    { ing("minecraft:planks", true, 2), ing("minecraft:planks", true, 2) }
            },
                    res("minecraft:birch_door", 3, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:planks", true, 3), ing("minecraft:planks", true, 3) },
                    { ing("minecraft:planks", true, 3), ing("minecraft:planks", true, 3) },
                    { ing("minecraft:planks", true, 3), ing("minecraft:planks", true, 3) }
            },
                    res("minecraft:jungle_door", 3, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:planks", true, 4), ing("minecraft:planks", true, 4) },
                    { ing("minecraft:planks", true, 4), ing("minecraft:planks", true, 4) },
                    { ing("minecraft:planks", true, 4), ing("minecraft:planks", true, 4) }
            },
                    res("minecraft:acacia_door", 3, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:planks", true, 5), ing("minecraft:planks", true, 5) },
                    { ing("minecraft:planks", true, 5), ing("minecraft:planks", true, 5) },
                    { ing("minecraft:planks", true, 5), ing("minecraft:planks", true, 5) }
            },
                    res("minecraft:dark_oak_door", 3, 0)),
            // The core planks crafts over the family item (the reference's
            // bare Blocks.PLANKS ingredient matches any variant — the
            // engine's family-item rows for the shipped oak rows' new twin).
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:planks", false, 0) },
                    { ing("minecraft:planks", false, 0) }
            },
                    res("minecraft:stick", 4, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:planks", false, 0), null, ing("minecraft:planks", false, 0) },
                    { ing("minecraft:planks", false, 0), ing("minecraft:planks", false, 0), ing("minecraft:planks", false, 0) }
            },
                    res("minecraft:bowl", 4, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:planks", false, 0), ing("minecraft:planks", false, 0), ing("minecraft:planks", false, 0) },
                    { ing("minecraft:planks", false, 0), ing("minecraft:planks", false, 0), ing("minecraft:planks", false, 0) },
                    { ing("minecraft:stick", false, 0), null, ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:sign", 3, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:coal", false, 0) },
                    { ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:torch", 4, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_planks", false, 0), ing("minecraft:oak_planks", false, 0), ing("minecraft:oak_planks", false, 0) },
                    { ing("minecraft:oak_planks", false, 0), null, ing("minecraft:oak_planks", false, 0) },
                    { ing("minecraft:oak_planks", false, 0), ing("minecraft:oak_planks", false, 0), ing("minecraft:oak_planks", false, 0) }
            },
                    res("minecraft:chest", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_planks", false, 0), ing("minecraft:oak_planks", false, 0) },
                    { ing("minecraft:oak_planks", false, 0), ing("minecraft:oak_planks", false, 0) }
            },
                    res("minecraft:crafting_table", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:cobblestone", false, 0), ing("minecraft:cobblestone", false, 0), ing("minecraft:cobblestone", false, 0) },
                    { ing("minecraft:cobblestone", false, 0), null, ing("minecraft:cobblestone", false, 0) },
                    { ing("minecraft:cobblestone", false, 0), ing("minecraft:cobblestone", false, 0), ing("minecraft:cobblestone", false, 0) }
            },
                    res("minecraft:furnace", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:iron_ingot", false, 0) },
                    { ing("minecraft:stick", false, 0) },
                    { ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:iron_shovel", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:iron_ingot", false, 0), ing("minecraft:iron_ingot", false, 0), ing("minecraft:iron_ingot", false, 0) },
                    { null, ing("minecraft:stick", false, 0), null },
                    { null, ing("minecraft:stick", false, 0), null }
            },
                    res("minecraft:iron_pickaxe", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:iron_ingot", false, 0), ing("minecraft:iron_ingot", false, 0) },
                    { ing("minecraft:iron_ingot", false, 0), ing("minecraft:stick", false, 0) },
                    { null, ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:iron_axe", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:iron_ingot", false, 0) },
                    { ing("minecraft:iron_ingot", false, 0) },
                    { ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:iron_sword", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_planks", true, 0) },
                    { ing("minecraft:oak_planks", true, 0) },
                    { ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:wooden_sword", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_planks", true, 0) },
                    { ing("minecraft:stick", false, 0) },
                    { ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:wooden_shovel", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_planks", true, 0), ing("minecraft:oak_planks", true, 0), ing("minecraft:oak_planks", true, 0) },
                    { null, ing("minecraft:stick", false, 0), null },
                    { null, ing("minecraft:stick", false, 0), null }
            },
                    res("minecraft:wooden_pickaxe", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_planks", true, 0), ing("minecraft:oak_planks", true, 0) },
                    { ing("minecraft:oak_planks", true, 0), ing("minecraft:stick", false, 0) },
                    { null, ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:wooden_axe", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:cobblestone", false, 0) },
                    { ing("minecraft:cobblestone", false, 0) },
                    { ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:stone_sword", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:cobblestone", false, 0) },
                    { ing("minecraft:stick", false, 0) },
                    { ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:stone_shovel", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:cobblestone", false, 0), ing("minecraft:cobblestone", false, 0), ing("minecraft:cobblestone", false, 0) },
                    { null, ing("minecraft:stick", false, 0), null },
                    { null, ing("minecraft:stick", false, 0), null }
            },
                    res("minecraft:stone_pickaxe", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:cobblestone", false, 0), ing("minecraft:cobblestone", false, 0) },
                    { ing("minecraft:cobblestone", false, 0), ing("minecraft:stick", false, 0) },
                    { null, ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:stone_axe", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:diamond", false, 0) },
                    { ing("minecraft:diamond", false, 0) },
                    { ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:diamond_sword", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:diamond", false, 0) },
                    { ing("minecraft:stick", false, 0) },
                    { ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:diamond_shovel", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:diamond", false, 0), ing("minecraft:diamond", false, 0), ing("minecraft:diamond", false, 0) },
                    { null, ing("minecraft:stick", false, 0), null },
                    { null, ing("minecraft:stick", false, 0), null }
            },
                    res("minecraft:diamond_pickaxe", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:diamond", false, 0), ing("minecraft:diamond", false, 0) },
                    { ing("minecraft:diamond", false, 0), ing("minecraft:stick", false, 0) },
                    { null, ing("minecraft:stick", false, 0) }
            },
                    res("minecraft:diamond_axe", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_planks", false, 0) },
                    { ing("minecraft:oak_planks", false, 0) }
            },
                    res("minecraft:stick", 4, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { null, ing("minecraft:iron_ingot", false, 0) },
                    { ing("minecraft:iron_ingot", false, 0), null }
            },
                    res("minecraft:shears", 1, 0)),
            // The farming slice (community recipes.json): bread is a row of
            // three wheat; bone meal is one bone (the historical shapeless
            // craft folded to the 1x1 shaped form the matcher already runs).
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:wheat", false, 0), ing("minecraft:wheat", false, 0), ing("minecraft:wheat", false, 0) }
            },
                    res("minecraft:bread", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:bone", false, 0) }
            },
                    res("minecraft:bone_meal", 3, 0)),
            // The reed chain (community recipes.json): sugar is one cane
            // (the shapeless 1x1 folded to the shaped form), paper is a row
            // of three canes, a book wraps three papers around a leather.
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:sugar_cane", false, 0) }
            },
                    res("minecraft:sugar", 1, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:sugar_cane", false, 0),
                      ing("minecraft:sugar_cane", false, 0),
                      ing("minecraft:sugar_cane", false, 0) }
            },
                    res("minecraft:paper", 3, 0)),
            // The book: one leather plus three papers, the historical
            // shapeless wrap.
            CraftingRecipe.shapeless(java.util.List.of(
                    ing("minecraft:leather", false, 0),
                    ing("minecraft:paper", false, 0),
                    ing("minecraft:paper", false, 0),
                    ing("minecraft:paper", false, 0)),
                    res("minecraft:book", 1, 0)),
            // The dye-mix pairs (DyeRecipes.java lines 36-99 — the
            // reference's own registerShapeless set, 1:1): two dyes in the
            // crafting grid yield two of the mixed color. The ingredient
            // and result damages live in the dye item's own metadata domain
            // (ink sac 0 = black .. bone meal 15 = white). The player table
            // gets them, and the sheep's offspring color consults the same
            // rows (SheepEntity.getColorForOffspring's CraftingManager walk).
            CraftingRecipe.shapeless(java.util.List.of(
                    ing("minecraft:dye", true, 1),   // red
                    ing("minecraft:dye", true, 15)), // white
                    res("minecraft:dye", 2, 9)),     // pink
            CraftingRecipe.shapeless(java.util.List.of(
                    ing("minecraft:dye", true, 1),   // red
                    ing("minecraft:dye", true, 11)), // yellow
                    res("minecraft:dye", 2, 14)),    // orange
            CraftingRecipe.shapeless(java.util.List.of(
                    ing("minecraft:dye", true, 2),   // green (cactus)
                    ing("minecraft:dye", true, 15)), // white
                    res("minecraft:dye", 2, 10)),    // lime
            CraftingRecipe.shapeless(java.util.List.of(
                    ing("minecraft:dye", true, 0),   // black (ink)
                    ing("minecraft:dye", true, 15)), // white
                    res("minecraft:dye", 2, 8)),     // gray
            CraftingRecipe.shapeless(java.util.List.of(
                    ing("minecraft:dye", true, 8),   // gray
                    ing("minecraft:dye", true, 15)), // white
                    res("minecraft:dye", 2, 7)),     // silver
            CraftingRecipe.shapeless(java.util.List.of(
                    ing("minecraft:dye", true, 4),   // blue (lapis)
                    ing("minecraft:dye", true, 15)), // white
                    res("minecraft:dye", 2, 12)),    // light blue
            CraftingRecipe.shapeless(java.util.List.of(
                    ing("minecraft:dye", true, 4),   // blue
                    ing("minecraft:dye", true, 2)),  // green
                    res("minecraft:dye", 2, 6)),     // cyan
            CraftingRecipe.shapeless(java.util.List.of(
                    ing("minecraft:dye", true, 4),   // blue
                    ing("minecraft:dye", true, 1)),  // red
                    res("minecraft:dye", 2, 5)),     // purple
            CraftingRecipe.shapeless(java.util.List.of(
                    ing("minecraft:dye", true, 5),   // purple
                    ing("minecraft:dye", true, 9)),  // pink
                    res("minecraft:dye", 2, 13)),    // magenta
            // The collision-shape slice (community recipes.json): a row of
            // three yields six slabs; the 2x3 stair pattern yields four.
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_planks", false, 0),
                      ing("minecraft:oak_planks", false, 0),
                      ing("minecraft:oak_planks", false, 0) }
            },
                    res("minecraft:oak_slab", 6, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:stone", false, 0),
                      ing("minecraft:stone", false, 0),
                      ing("minecraft:stone", false, 0) }
            },
                    res("minecraft:stone_slab", 6, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:cobblestone", false, 0),
                      ing("minecraft:cobblestone", false, 0),
                      ing("minecraft:cobblestone", false, 0) }
            },
                    res("minecraft:cobblestone_slab", 6, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:sandstone", false, 0),
                      ing("minecraft:sandstone", false, 0),
                      ing("minecraft:sandstone", false, 0) }
            },
                    res("minecraft:sandstone_slab", 6, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:oak_planks", false, 0), null, ing("minecraft:oak_planks", false, 0) },
                    { ing("minecraft:oak_planks", false, 0), ing("minecraft:oak_planks", false, 0), ing("minecraft:oak_planks", false, 0) }
            },
                    res("minecraft:oak_stairs", 4, 0)),
            CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:cobblestone", false, 0), null, ing("minecraft:cobblestone", false, 0) },
                    { ing("minecraft:cobblestone", false, 0), ing("minecraft:cobblestone", false, 0), ing("minecraft:cobblestone", false, 0) }
            },
                    res("minecraft:cobblestone_stairs", 4, 0))
    )));

    /**
     * The mineral vault's recipe set (Slice 32 — MineralRecipes.java's own
     * registration walk, 1:1): the nine-of-a-kind storage blocks ("###",
     * "###", "###" over the ingot/gem/dust rows — gold, iron, diamond,
     * emerald, lapis, redstone, coal, wheat's hay, the slime ball's
     * bounce pad) each with the one-item unpack reverse, plus the 2x2
     * masonries (CraftingManager lines 140-141 + BlockRecipes line 83:
     * the clay ball's clay, the brick's bricks, the nether brick's
     * fortress block, the quartz's block) and the gold ingot/nugget pair
     * (MineralRecipes' own tail rows).
     */
    private static List<CraftingRecipe> withMineralRecipes(List<CraftingRecipe> base) {
        List<CraftingRecipe> recipes = new java.util.ArrayList<>(base);
        // The nine-of-a-kind storage families: "###","###","###" packs,
        // the single-item unpacks. Each pair is MineralRecipes' own loop.
        storagePair(recipes, "minecraft:gold_block", "minecraft:gold_ingot");
        storagePair(recipes, "minecraft:iron_block", "minecraft:iron_ingot");
        storagePair(recipes, "minecraft:diamond_block", "minecraft:diamond");
        storagePair(recipes, "minecraft:emerald_block", "minecraft:emerald");
        storagePair(recipes, "minecraft:lapis_block", "minecraft:lapis");
        storagePair(recipes, "minecraft:redstone_block", "minecraft:redstone");
        storagePair(recipes, "minecraft:coal_block", "minecraft:coal");
        storagePair(recipes, "minecraft:hay_block", "minecraft:wheat");
        storagePair(recipes, "minecraft:slime_block", "minecraft:slime_ball");
        // The gold ingot/nugget pair (MineralRecipes lines 24-27).
        storagePair(recipes, "minecraft:gold_ingot", "minecraft:gold_nugget");
        // The 2x2 masonries (CraftingManager lines 140-141: clay and
        // bricks; line 142: the quartz block; BlockRecipes line 83: the
        // nether bricks).
        recipes.add(CraftingRecipe.shaped(new Ingredient[][] {
                { ing("minecraft:clay_ball", false, 0), ing("minecraft:clay_ball", false, 0) },
                { ing("minecraft:clay_ball", false, 0), ing("minecraft:clay_ball", false, 0) }
        },
                res("minecraft:clay", 1, 0)));
        recipes.add(CraftingRecipe.shaped(new Ingredient[][] {
                { ing("minecraft:brick", false, 0), ing("minecraft:brick", false, 0) },
                { ing("minecraft:brick", false, 0), ing("minecraft:brick", false, 0) }
        },
                res("minecraft:brick_block", 1, 0)));
        recipes.add(CraftingRecipe.shaped(new Ingredient[][] {
                { ing("minecraft:quartz", false, 0), ing("minecraft:quartz", false, 0) },
                { ing("minecraft:quartz", false, 0), ing("minecraft:quartz", false, 0) }
        },
                res("minecraft:quartz_block", 1, 0)));
        recipes.add(CraftingRecipe.shaped(new Ingredient[][] {
                { ing("minecraft:nether_brick", false, 0), ing("minecraft:nether_brick", false, 0) },
                { ing("minecraft:nether_brick", false, 0), ing("minecraft:nether_brick", false, 0) }
        },
                res("minecraft:nether_bricks", 1, 0)));
        return recipes;
    }

    /** MineralRecipes' own loop body: the nine pack and the one unpack. */
    private static void storagePair(List<CraftingRecipe> recipes, String block, String material) {
        Ingredient mat = ing(material, false, 0);
        recipes.add(CraftingRecipe.shaped(new Ingredient[][] {
                { mat, mat, mat },
                { mat, mat, mat },
                { mat, mat, mat }
        },
                res(block, 1, 0)));
        recipes.add(CraftingRecipe.shaped(new Ingredient[][] {
                { ing(block, false, 0) }
        },
                res(material, 9, 0)));
    }

    /**
     * The dye-coloring recipe families (Slice 31 — DyeRecipes.java's own
     * registration set, 1:1): the sixteen wool-coloring rows (a dye of the
     * DyeColor's own INVERTED item metadata 15-i plus a white wool yields
     * the color's wool, lines 23-26), the three ring recipes (stained
     * hardened clay 8-for-1 dye around eight plain, stained glass the
     * same, lines 27-40), the pane fold (six stained glass into sixteen
     * panes, lines 41-42), the sixteen carpet rows (two same-color wool
     * into three carpets, lines 137-139), and the two flower dyes the
     * engine's flora carries (the dandelion's two yellow, the poppy's one
     * red — lines 108-113; the remaining flower/double-plant rows ride
     * their unregistered flora's own slices).
     */
    private static List<CraftingRecipe> withDyeRecipes(List<CraftingRecipe> base) {
        List<CraftingRecipe> recipes = new java.util.ArrayList<>(base);
        for (int i = 0; i < 16; i++) {
            // wool coloring: dye(15-i) + white wool -> wool(i)
            recipes.add(CraftingRecipe.shapeless(java.util.List.of(
                    ing("minecraft:dye", true, 15 - i),
                    ing("minecraft:wool", true, 0)),
                    res("minecraft:wool", 1, i)));
            // stained hardened clay: eight plain around one dye(i) -> eight (15-i)
            recipes.add(CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:hardened_clay", false, 0),
                      ing("minecraft:hardened_clay", false, 0),
                      ing("minecraft:hardened_clay", false, 0) },
                    { ing("minecraft:hardened_clay", false, 0),
                      ing("minecraft:dye", true, i),
                      ing("minecraft:hardened_clay", false, 0) },
                    { ing("minecraft:hardened_clay", false, 0),
                      ing("minecraft:hardened_clay", false, 0),
                      ing("minecraft:hardened_clay", false, 0) }
            },
                    res("minecraft:stained_hardened_clay", 8, 15 - i)));
            // stained glass: eight glass around one dye(i) -> eight (15-i)
            recipes.add(CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:glass", false, 0),
                      ing("minecraft:glass", false, 0),
                      ing("minecraft:glass", false, 0) },
                    { ing("minecraft:glass", false, 0),
                      ing("minecraft:dye", true, i),
                      ing("minecraft:glass", false, 0) },
                    { ing("minecraft:glass", false, 0),
                      ing("minecraft:glass", false, 0),
                      ing("minecraft:glass", false, 0) }
            },
                    res("minecraft:stained_glass", 8, 15 - i)));
            // stained glass panes: six stained glass(i) -> sixteen panes(i)
            recipes.add(CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:stained_glass", true, i),
                      ing("minecraft:stained_glass", true, i),
                      ing("minecraft:stained_glass", true, i) },
                    { ing("minecraft:stained_glass", true, i),
                      ing("minecraft:stained_glass", true, i),
                      ing("minecraft:stained_glass", true, i) }
            },
                    res("minecraft:stained_glass_pane", 16, i)));
            // carpet: two same-color wool -> three carpets of the color
            recipes.add(CraftingRecipe.shaped(new Ingredient[][] {
                    { ing("minecraft:wool", true, i),
                      ing("minecraft:wool", true, i) }
            },
                    res("minecraft:carpet", 3, i)));
        }
        // The flower dyes (DyeRecipes lines 108-113 — the two flora the
        // engine's world grows): the dandelion's TWO yellow (the reference
        // yields a pair), the poppy's one red.
        recipes.add(CraftingRecipe.shapeless(java.util.List.of(
                ing("minecraft:dandelion", false, 0)),
                res("minecraft:dye", 2, 11)));
        recipes.add(CraftingRecipe.shapeless(java.util.List.of(
                ing("minecraft:poppy", false, 0)),
                res("minecraft:dye", 1, 1)));
        return List.copyOf(recipes);
    }
}
