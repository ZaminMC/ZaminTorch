package net.zaminmc.torch.server.crafting;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vanilla 1.8 matching semantics of the recipe model and the community-data
 * builtin set: bounding-box shaped matching (normal + mirrored), metadata
 * rules, shapeless multisets, and the exact recipes the generator emits.
 */
class CraftingRecipeTest {

    private static final ItemStack EMPTY = ItemStack.EMPTY;

    private static ItemStack[] grid(ItemStack r0c0, ItemStack r0c1, ItemStack r1c0, ItemStack r1c1) {
        return new ItemStack[]{r0c0, r0c1, r1c0, r1c1};
    }

    private static ItemStack planks(int count) {
        return ItemStack.of(BuiltinItems.OAK_PLANKS, count);
    }

    private static ItemStack log(int count) {
        return ItemStack.of(BuiltinItems.OAK_LOG, count);
    }

    // ---- builtin set -------------------------------------------------------

    @Test
    void builtinSetIsTheRegisteredVanillaRecipes() {
        // planks, sticks, table, torch, furnace, chest, 16 tools (wood/stone/
        // iron/diamond x pickaxe/axe/shovel/sword) + shears. Gold still needs
        // gold ingot — no gold ore item exists in the registry yet.
        // + the nine dye-mix pairs (Slice 30b — DyeRecipes' registerShapeless
        // set): the player table gets them and the sheep's offspring color
        // consults the same rows.
        // + the dye-coloring families (Slice 31 — DyeRecipes' own set): the 16
        // wool-coloring rows, the 16 stained-clay rings, the 16 stained-glass
        // rings, the 16 pane folds, the 16 carpet rows, and the two flower
        // dyes (dandelion/poppy).
        // + the mineral vault (Slice 32 — MineralRecipes' own set): the nine
        // storage pairs (gold/iron/diamond/emerald/lapis/redstone/coal/hay/
        // slime ×2 each) + the gold ingot/nugget pair + the four 2x2
        // masonries (clay, bricks, quartz, nether bricks) = 24 shaped rows.
        assertEquals(181, BuiltinRecipes.ALL.size()); // Slice 35's wood-variant rows + Slice 36's shapes: 8 stairs + 5 slabs + 5 fences
        long shaped = BuiltinRecipes.ALL.stream().filter(CraftingRecipe::isShaped).count();
        long shapeless = BuiltinRecipes.ALL.size() - shaped;
        assertEquals(28, shapeless,
                "the book + nine dye-mix pairs + 16 wool colorings + two flowers are the shapeless crafts");
    }

    // ---- 3x3 crafting-table matching ---------------------------------------

    private static ItemStack[] grid3x3(ItemStack... cells) {
        if (cells.length != 9) {
            throw new IllegalArgumentException("a 3x3 grid needs 9 cells");
        }
        return cells;
    }

    private static ItemStack stick(int count) {
        return ItemStack.of(BuiltinItems.STICK, count);
    }

    private static ItemStack cobble(int count) {
        return ItemStack.of(BuiltinItems.COBBLESTONE, count);
    }

    @Test
    void furnaceRingCraftsOnThe3x3GridOnly() {
        CraftingService service = CraftingService.builtin();
        var result = service.resultOf3x3(grid3x3(
                cobble(1), cobble(1), cobble(1),
                cobble(1), EMPTY, cobble(1),
                cobble(1), cobble(1), cobble(1)));
        assertTrue(result.isPresent());
        assertEquals(BuiltinItems.FURNACE, result.orElseThrow().type());
        // the same ring in a 2x2 grid is impossible: bounding box 3 > 2
        assertTrue(service.resultOf(grid(planks(1), planks(1), planks(1), planks(1))).isPresent(),
                "2x2 recipes still work");
        // an off-center ring (shifted up-left, row 0 empty) still matches:
        // the bounding box is what counts
        var shifted = service.resultOf3x3(grid3x3(
                cobble(1), cobble(1), cobble(1),
                cobble(1), EMPTY, cobble(1),
                cobble(1), cobble(1), cobble(1)));
        assertTrue(shifted.isPresent());
        // a filled center breaks the ring (no empty pattern cell satisfied)
        var filled = service.resultOf3x3(grid3x3(
                cobble(1), cobble(1), cobble(1),
                cobble(1), cobble(1), cobble(1),
                cobble(1), cobble(1), cobble(1)));
        assertTrue(filled.isEmpty(), "8 of 9 cells match no recipe");
    }

    @Test
    void chestRingCraftsOnThe3x3Grid() {
        CraftingService service = CraftingService.builtin();
        var result = service.resultOf3x3(grid3x3(
                planks(1), planks(1), planks(1),
                planks(1), EMPTY, planks(1),
                planks(1), planks(1), planks(1)));
        assertTrue(result.isPresent());
        assertEquals(BuiltinItems.CHEST, result.orElseThrow().type());
    }

    @Test
    void woodenPickaxeCraftsFromIts3x3Pattern() {
        CraftingService service = CraftingService.builtin();
        var result = service.resultOf3x3(grid3x3(
                planks(1), planks(1), planks(1),
                EMPTY, stick(1), EMPTY,
                EMPTY, stick(1), EMPTY));
        assertTrue(result.isPresent());
        assertEquals(BuiltinItems.WOODEN_PICKAXE, result.orElseThrow().type());
        assertEquals(1, result.orElseThrow().count());
    }

    @Test
    void woodenAxeMatchesMirroredOnThe3x3Grid() {
        CraftingService service = CraftingService.builtin();
        // dataset pattern: PP / PS / .S  (2 wide, 3 tall)
        var normal = service.resultOf3x3(grid3x3(
                planks(1), planks(1), EMPTY,
                planks(1), stick(1), EMPTY,
                EMPTY, stick(1), EMPTY));
        assertTrue(normal.isPresent(), "normal orientation");
        assertEquals(BuiltinItems.WOODEN_AXE, normal.orElseThrow().type());
        // mirrored: PP / SP / S.
        var mirrored = service.resultOf3x3(grid3x3(
                planks(1), planks(1), EMPTY,
                stick(1), planks(1), EMPTY,
                stick(1), EMPTY, EMPTY));
        assertTrue(mirrored.isPresent(), "horizontal mirror");
        assertEquals(BuiltinItems.WOODEN_AXE, mirrored.orElseThrow().type());
        // a vertical flip never matches (vanilla mirrors horizontally only)
        var flipped = service.resultOf3x3(grid3x3(
                EMPTY, stick(1), EMPTY,
                planks(1), stick(1), EMPTY,
                planks(1), planks(1), EMPTY));
        assertTrue(flipped.isEmpty(), "vanilla never flips vertically");
    }

    @Test
    void misalignedGridInputIsRejectedLoudly() {
        CraftingService service = CraftingService.builtin();
        // a 9-cell snapshot against the 2x2 matcher cannot arise from a real
        // 2x2 grid; the contract keeps such call sites loud instead of silent
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.resultOf(grid3x3(
                        planks(1), planks(1), planks(1),
                        EMPTY, stick(1), EMPTY,
                        EMPTY, stick(1), EMPTY)));
    }

    @Test
    void smallPatternsMatchAnywhereInThe3x3Grid() {
        CraftingService service = CraftingService.builtin();
        // log -> planks, parked in the bottom-right corner of the table grid
        var result = service.resultOf3x3(grid3x3(
                EMPTY, EMPTY, EMPTY,
                EMPTY, EMPTY, EMPTY,
                EMPTY, EMPTY, log(1)));
        assertTrue(result.isPresent(), "bounding-box matching is position-free");
        assertEquals(BuiltinItems.OAK_PLANKS, result.orElseThrow().type());
        assertEquals(4, result.orElseThrow().count());
    }

    @Test
    void oneLogAnywhereInTheGridCraftsFourPlanks() {
        CraftingService service = CraftingService.builtin();
        // top-left
        var result = service.resultOf(grid(log(1), EMPTY, EMPTY, EMPTY));
        assertTrue(result.isPresent());
        assertEquals(BuiltinItems.OAK_PLANKS, result.orElseThrow().type());
        assertEquals(4, result.orElseThrow().count());
        // bottom-right: the same recipe found at the grid's bounding box
        assertTrue(service.resultOf(grid(EMPTY, EMPTY, EMPTY, log(1))).isPresent());
        // top-right
        assertTrue(service.resultOf(grid(EMPTY, log(1), EMPTY, EMPTY)).isPresent());
    }

    @Test
    void twoPlanksVerticalCraftFourSticksAtAnyOffset() {
        CraftingService service = CraftingService.builtin();
        assertTrue(service.resultOf(grid(planks(1), EMPTY, planks(1), EMPTY)).isPresent(),
                "left column");
        assertTrue(service.resultOf(grid(EMPTY, planks(1), EMPTY, planks(1))).isPresent(),
                "right column (mirror of a 1-wide pattern)");
    }

    @Test
    void twoPlanksHorizontalCraftNothing() {
        CraftingService service = CraftingService.builtin();
        // sticks are strictly vertical: the horizontal pair has a 1x2 bounding
        // box that no recipe occupies
        assertTrue(service.resultOf(grid(planks(1), planks(1), EMPTY, EMPTY)).isEmpty(),
                "sticks are strictly vertical in vanilla 1.8");
    }

    @Test
    void twoByTwoPlanksCraftACraftingTable() {
        CraftingService service = CraftingService.builtin();
        var result = service.resultOf(grid(planks(1), planks(1), planks(1), planks(1)));
        assertTrue(result.isPresent());
        assertEquals(BuiltinItems.CRAFTING_TABLE, result.orElseThrow().type());
        assertEquals(1, result.orElseThrow().count());
    }

    @Test
    void threePlanksLeaveTheGridUnmatched() {
        CraftingService service = CraftingService.builtin();
        // L-shape: bounding box 2x2 but one cell empty -> no 2x2 table, no sticks
        assertTrue(service.resultOf(grid(planks(1), planks(1), planks(1), EMPTY)).isEmpty());
    }

    @Test
    void coalOverStickCraftsFourTorches() {
        CraftingService service = CraftingService.builtin();
        var result = service.resultOf(grid(ItemStack.of(BuiltinItems.COAL, 1), EMPTY,
                ItemStack.of(BuiltinItems.STICK, 1), EMPTY));
        assertTrue(result.isPresent());
        assertEquals(BuiltinItems.TORCH, result.orElseThrow().type());
        assertEquals(4, result.orElseThrow().count());
        // stick over coal is a VERTICAL flip: vanilla mirrors horizontally only,
        // so this arrangement never crafts a torch
        assertTrue(service.resultOf(grid(ItemStack.of(BuiltinItems.STICK, 1), EMPTY,
                ItemStack.of(BuiltinItems.COAL, 1), EMPTY)).isEmpty());
        // side by side never crafts a torch
        assertTrue(service.resultOf(grid(ItemStack.of(BuiltinItems.COAL, 1),
                ItemStack.of(BuiltinItems.STICK, 1), EMPTY, EMPTY)).isEmpty());
    }

    @Test
    void emptyGridMatchesNothing() {
        CraftingService service = CraftingService.builtin();
        assertTrue(service.resultOf(grid(EMPTY, EMPTY, EMPTY, EMPTY)).isEmpty());
    }

    @Test
    void wornToolsAreDifferentIngredientsThanFreshOnes() {
        // The exact-metadata rule rides the damage field: a pickaxe ingredient
        // pinned to damage 0 refuses a worn pickaxe.
        ItemType pickaxe = BuiltinItems.WOODEN_PICKAXE;
        Ingredient freshOnly = new Ingredient(pickaxe.identifier(), true, 0);
        assertTrue(freshOnly.matches(ItemStack.of(pickaxe, 1)));
        assertFalse(freshOnly.matches(ItemStack.of(pickaxe, 1).withDamage(1)));

        Ingredient anyWear = new Ingredient(pickaxe.identifier(), false, 0);
        assertTrue(anyWear.matches(ItemStack.of(pickaxe, 1)));
        assertTrue(anyWear.matches(ItemStack.of(pickaxe, 1).withDamage(3)));
    }

    // ---- synthetic shaped matching (mirror + padding rules) ------------------

    @Test
    void shapedPatternMatchesNormalAndMirroredOrientation() {
        // shape: [stick, empty] / [planks, planks]  (asymmetric, 2x2)
        CraftingRecipe recipe = CraftingRecipe.shaped(new Ingredient[][]{
                {new Ingredient(BuiltinItems.STICK.identifier(), false, 0), null},
                {new Ingredient(BuiltinItems.OAK_PLANKS.identifier(), false, 0),
                        new Ingredient(BuiltinItems.OAK_PLANKS.identifier(), false, 0)}
        }, new CraftingRecipe.Result(BuiltinItems.TORCH.identifier(), 1, 0));

        ItemStack stick = ItemStack.of(BuiltinItems.STICK, 1);
        // normal orientation: stick top-left
        assertTrue(recipe.matches(grid(stick, EMPTY, planks(1), planks(1))));
        // mirrored: stick top-right
        assertTrue(recipe.matches(grid(EMPTY, stick, planks(1), planks(1))));
        // wrong cell: stick bottom-left matches neither orientation
        assertFalse(recipe.matches(grid(planks(1), planks(1), stick, EMPTY)));
    }

    @Test
    void shapedPatternWithEmptyCellRefusesFilledCell() {
        // trimmed 2x2 pattern with an interior empty cell (border cells stay:
        // planks occupy the second column of the bottom row)
        CraftingRecipe recipe = CraftingRecipe.shaped(new Ingredient[][]{
                {new Ingredient(BuiltinItems.STICK.identifier(), false, 0), null},
                {new Ingredient(BuiltinItems.OAK_PLANKS.identifier(), false, 0),
                        new Ingredient(BuiltinItems.OAK_PLANKS.identifier(), false, 0)}
        }, new CraftingRecipe.Result(BuiltinItems.TORCH.identifier(), 1, 0));
        ItemStack stick = ItemStack.of(BuiltinItems.STICK, 1);
        assertTrue(recipe.matches(grid(stick, EMPTY, planks(1), planks(1))),
                "the empty pattern cell demands an empty grid cell");
        assertFalse(recipe.matches(grid(stick, planks(1), planks(1), planks(1))),
                "a filled grid cell can never satisfy an empty pattern cell");
    }

    // ---- shapeless matching ---------------------------------------------------

    @Test
    void shapelessRecipesMatchAsMultisets() {
        Ingredient coal = new Ingredient(BuiltinItems.COAL.identifier(), false, 0);
        Ingredient stick = new Ingredient(BuiltinItems.STICK.identifier(), false, 0);
        CraftingRecipe recipe = CraftingRecipe.shapeless(List.of(coal, coal, stick),
                new CraftingRecipe.Result(BuiltinItems.TORCH.identifier(), 1, 0));
        ItemStack coalStack = ItemStack.of(BuiltinItems.COAL, 1);
        ItemStack stickStack = ItemStack.of(BuiltinItems.STICK, 1);

        assertTrue(recipe.matches(grid(coalStack, coalStack, stickStack, EMPTY)),
                "order within the grid is irrelevant");
        assertTrue(recipe.matches(grid(stickStack, coalStack, coalStack, EMPTY)),
                "pairing is by content, not position");
        assertFalse(recipe.matches(grid(coalStack, stickStack, EMPTY, EMPTY)),
                "missing an ingredient: count mismatch");
        assertFalse(recipe.matches(grid(coalStack, coalStack, stickStack, planks(1))),
                "an extra stack breaks the multiset equality");
    }
}
