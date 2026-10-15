package net.zaminmc.torch.server.block;

import net.zaminmc.torch.server.crafting.CraftingService;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The building-shape families (Slice 36): the eight stair families' facing
 * bands, the five new wood fences, the wooden-slab pairs over 126 with the
 * variant nibble, and the crafting rows (the stair ladder per family, the
 * three-plank slab bands, the fence gates of sticks and planks).
 */
class WoodShapeBlocksTest {

    @Test
    void theShapeFamiliesCarryTheReferenceWireIds() {
        // The stair families' facing states all resolve with their band.
        for (String family : WoodShapeBlocks.STAIRS.keySet()) {
            for (String facing : new String[] {"east", "west", "south", "north"}) {
                Optional<? extends net.zaminmc.torch.block.BlockType> state = BuiltinBlocks
                        .registerAll(new BlockRegistryBuilder()).freeze()
                        .lookup(Identifier.parse("minecraft:" + family + "_stairs_" + facing));
                assertTrue(state.isPresent(), family + " stairs " + facing);
            }
        }
        assertEquals(8, WoodShapeBlocks.STAIRS.size());
        assertEquals(134, WoodShapeBlocks.STAIRS.get("spruce"));
        assertEquals(108, WoodShapeBlocks.STAIRS.get("brick"));
        assertEquals(109, WoodShapeBlocks.STAIRS.get("stone_brick"));
        assertEquals(156, WoodShapeBlocks.STAIRS.get("quartz"));
        assertEquals(164, WoodShapeBlocks.STAIRS.get("dark_oak"));
        assertEquals(5, WoodShapeBlocks.FENCES.size());
        assertEquals(188, WoodShapeBlocks.FENCES.get("spruce"));
        assertEquals(191, WoodShapeBlocks.FENCES.get("dark_oak"));
        assertEquals(192, WoodShapeBlocks.FENCES.get("acacia"));
        // The slab pairs: the variant nibble with the top's bit 8.
        for (int variant = 1; variant < 6; variant++) {
            assertEquals(variant, WoodShapeBlocks.slabStates(variant).get(0).legacyMetadata());
            assertEquals(variant | 8, WoodShapeBlocks.slabStates(variant).get(1).legacyMetadata());
        }
    }

    @Test
    void theShapeCraftsCoverTheNewFamilies() {
        CraftingService service = CraftingService.builtin();
        ItemStack planks = stack("minecraft:planks");
        // The stair ladder: six exact-variant planks of the stair shape
        // (row of 3, then 2, then 1 — the reference's "#  ", "## ", "###").
        for (int variant = 1; variant < 6; variant++) {
            ItemStack p = new ItemStack(planks.type(), 1, variant);
            var result = service.resultOf3x3(new ItemStack[]{
                    p, ItemStack.EMPTY, ItemStack.EMPTY,
                    p, p, ItemStack.EMPTY,
                    p, p, p});
            assertTrue(result.isPresent(), "the stair craft over variant " + variant);
            assertEquals("minecraft:" + WoodVariantBlocks.WOODS.get(variant) + "_stairs",
                    result.orElseThrow().type().identifier().toString());
            assertEquals(4, result.orElseThrow().count());
        }
        // The masonry stairs over the plain blocks.
        for (String material : new String[] {"brick_block", "stone_bricks", "quartz_block"}) {
            ItemStack m = stack("minecraft:" + material);
            var result = service.resultOf3x3(new ItemStack[]{
                    m, ItemStack.EMPTY, ItemStack.EMPTY,
                    m, m, ItemStack.EMPTY,
                    m, m, m});
            assertTrue(result.isPresent(), "the " + material + " stair craft");
        }
        // The slabs: three planks -> six slabs of the same variant.
        for (int variant = 1; variant < 6; variant++) {
            ItemStack p = new ItemStack(planks.type(), 1, variant);
            var result = service.resultOf3x3(new ItemStack[]{
                    p, p, p,
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY});
            assertTrue(result.isPresent(), "the slab craft over variant " + variant);
            assertEquals("minecraft:wooden_slab",
                    result.orElseThrow().type().identifier().toString());
            assertEquals(6, result.orElseThrow().count());
            assertEquals(variant, result.orElseThrow().damage());
        }
        // The fences: two planks + four sticks -> three fences.
        ItemStack stick = stack("minecraft:stick");
        for (int variant = 1; variant < 6; variant++) {
            ItemStack p = new ItemStack(planks.type(), 1, variant);
            var result = service.resultOf3x3(new ItemStack[]{
                    stick, p, stick,
                    stick, p, stick,
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY});
            assertTrue(result.isPresent(), "the fence craft over variant " + variant);
            assertEquals("minecraft:" + WoodVariantBlocks.WOODS.get(variant) + "_fence",
                    result.orElseThrow().type().identifier().toString());
            assertEquals(3, result.orElseThrow().count());
        }
    }

    private static ItemStack stack(String value) {
        return ItemStack.of(BuiltinItems.lookup(Identifier.parse(value)).orElseThrow(), 1);
    }
}
