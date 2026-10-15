package net.zaminmc.torch.server.block;

import net.zaminmc.torch.server.crafting.CraftingService;
import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wood-variant foundation (Slice 35 — PlanksBlock.Variant's own domain
 * over the reference's registrations): the five new woods' planks, logs,
 * and leaves plus all six saplings; the damage-domain family items
 * (planks 5, log 17, log2 162, leaves 18, leaves2 161, sapling 6) with the
 * reference's own metadata encodings; the leaves' exact drop walk (the
 * shears/silk arm, the sapling chance band with the jungle 1/40 override,
 * the oak-only apple); the placement consult's variant resolution; and the
 * crafting rows (the log->planks pair per wood, the six doors, the
 * family-planks stick/bowl/sign).
 */
class WoodVariantBlocksTest {

    @Test
    void theFamilyTablesCarryTheReferenceEncodings() {
        for (int variant = 0; variant < 6; variant++) {
            String wood = WoodVariantBlocks.WOODS.get(variant);
            // The oak keeps its shipped blocks; the other five flatten.
            if (variant != 0) {
                assertEquals("minecraft:" + wood + "_planks",
                        WoodVariantBlocks.planksBlock(variant).toString());
                assertEquals("minecraft:" + wood + "_log",
                        WoodVariantBlocks.logBlock(variant).toString());
                assertEquals("minecraft:" + wood + "_leaves",
                        WoodVariantBlocks.leavesBlock(variant).toString());
            }
            assertEquals("minecraft:" + wood + "_sapling",
                    WoodVariantBlocks.saplingBlock(variant).toString());
        }
        // The wire ids: planks 5, the log/leaves split 17/18 vs 162/161,
        // the saplings 6; the metadata nibbles re-base the second pair.
        assertEquals(5, WoodVariantBlocks.wireIdOf("planks", 3));
        assertEquals(17, WoodVariantBlocks.wireIdOf("log", 3));
        assertEquals(162, WoodVariantBlocks.wireIdOf("log", 4));
        assertEquals(18, WoodVariantBlocks.wireIdOf("leaves", 2));
        assertEquals(161, WoodVariantBlocks.wireIdOf("leaves", 5));
        assertEquals(6, WoodVariantBlocks.wireIdOf("sapling", 1));
        assertEquals(1, WoodVariantBlocks.wireMetaOf("log", 1));
        assertEquals(0, WoodVariantBlocks.wireMetaOf("log", 4));  // acacia re-bases
        assertEquals(1, WoodVariantBlocks.wireMetaOf("leaves", 5)); // dark_oak re-bases
        assertEquals(5, WoodVariantBlocks.wireMetaOf("planks", 5));
    }

    @Test
    void thePlacementConsultResolvesEveryFamilyItem() {
        // The planks/sapling domains read the damage directly.
        assertEquals(Optional.of(Identifier.parse("minecraft:spruce_planks")),
                WoodVariantBlocks.placementBlockOf(
                        Identifier.parse("minecraft:planks"), 1));
        assertEquals(Optional.of(Identifier.parse("minecraft:dark_oak_sapling")),
                WoodVariantBlocks.placementBlockOf(
                        Identifier.parse("minecraft:sapling"), 5));
        // The log/leaves domains split on their two ids.
        assertEquals(Optional.of(Identifier.parse("minecraft:birch_log")),
                WoodVariantBlocks.placementBlockOf(Identifier.parse("minecraft:log"), 2));
        assertEquals(Optional.of(Identifier.parse("minecraft:acacia_log")),
                WoodVariantBlocks.placementBlockOf(Identifier.parse("minecraft:log2"), 0));
        assertEquals(Optional.of(Identifier.parse("minecraft:dark_oak_leaves")),
                WoodVariantBlocks.placementBlockOf(Identifier.parse("minecraft:leaves2"), 1));
        // The variant read of a family block.
        assertEquals(3, WoodVariantBlocks.variantOf(Identifier.parse("minecraft:jungle_planks")));
        assertEquals(4, WoodVariantBlocks.variantOf(Identifier.parse("minecraft:acacia_leaves")));
        assertEquals(-1, WoodVariantBlocks.variantOf(Identifier.parse("minecraft:stone")));
    }

    @Test
    void theLeavesWalkCarriesTheShearsArmAndTheChanceBand() {
        DropService drops = new DropService(new Random());
        // The shears arm: the leaf itself, every time (the family item with
        // the variant damage — the reference's getSilkTouchDrop).
        ItemStack sheared = drops.dropsFor(
                block("minecraft:spruce_leaves"), BuiltinItems.SHEARS, false, 0).get(0);
        assertEquals("minecraft:leaves", sheared.type().identifier().toString());
        assertEquals(1, sheared.damage());
        // Silk takes the leaf too (the Leaves2 pair's own family item).
        ItemStack silken = drops.dropsFor(
                block("minecraft:dark_oak_leaves"), null, true, 0).get(0);
        assertEquals("minecraft:leaves2", silken.type().identifier().toString());
        assertEquals(1, silken.damage());
        // The hand rolls the reference's band over many breaks: the sapling
        // lands near 1/20 and the apple NEVER lands off the oak.
        int spruceSaplings = 0;
        int birchApples = 0;
        for (int i = 0; i < 4000; i++) {
            for (ItemStack stack : drops.dropsFor(
                    block("minecraft:birch_leaves"), null, false, 0)) {
                if (stack.type().identifier().toString().equals("minecraft:sapling")) {
                    spruceSaplings++;
                }
                if (stack.type().identifier().toString().equals("minecraft:apple")) {
                    birchApples++;
                }
            }
        }
        assertTrue(spruceSaplings > 120 && spruceSaplings < 280,
                "the sapling band near 1/20 over 4000 breaks: " + spruceSaplings);
        assertEquals(0, birchApples, "the apple is the oak leaf's own arm");
        // The oak rolls the apple too (1/200 over 4000 breaks).
        int oakApples = 0;
        int oakSaplings = 0;
        for (int i = 0; i < 4000; i++) {
            for (ItemStack stack : drops.dropsFor(
                    block("minecraft:oak_leaves"), null, false, 0)) {
                if (stack.type().identifier().toString().equals("minecraft:apple")) {
                    oakApples++;
                }
                if (stack.type().identifier().toString().equals("minecraft:sapling")) {
                    oakSaplings++;
                }
            }
        }
        assertTrue(oakApples > 5 && oakApples < 40,
                "the apple band near 1/200 over 4000 breaks: " + oakApples);
        assertTrue(oakSaplings > 120 && oakSaplings < 280,
                "the oak sapling band near 1/20: " + oakSaplings);
        // The jungle leaf's own 1/40 override (getSaplingDropChance).
        int jungleSaplings = 0;
        for (int i = 0; i < 4000; i++) {
            for (ItemStack stack : drops.dropsFor(
                    block("minecraft:jungle_leaves"), null, false, 0)) {
                if (stack.type().identifier().toString().equals("minecraft:sapling")) {
                    jungleSaplings++;
                }
            }
        }
        assertTrue(jungleSaplings > 50 && jungleSaplings < 150,
                "the jungle band near 1/40 over 4000 breaks: " + jungleSaplings);
    }

    @Test
    void theSaplingChanceBandsMatchTheReference() {
        assertEquals(20, WoodVariantBlocks.saplingChanceOf(
                Identifier.parse("minecraft:oak_leaves"), 0));
        assertEquals(40, WoodVariantBlocks.saplingChanceOf(
                Identifier.parse("minecraft:jungle_leaves"), 0));
        assertEquals(10, WoodVariantBlocks.saplingChanceOf(
                Identifier.parse("minecraft:oak_leaves"), 3), "the fortune relief clamps at 10");
        assertEquals(200, WoodVariantBlocks.appleChanceOf(
                Identifier.parse("minecraft:oak_leaves"), 0));
        assertEquals(40, WoodVariantBlocks.appleChanceOf(
                Identifier.parse("minecraft:oak_leaves"), 4), "the apple relief clamps at 40");
        assertEquals(0, WoodVariantBlocks.appleChanceOf(
                Identifier.parse("minecraft:spruce_leaves"), 0), "the oak-only arm");
    }

    @Test
    void theCraftingRowsCoverTheWoodsAndTheirDoors() {
        CraftingService service = CraftingService.builtin();
        // The log->planks rows: one log of a variant yields four planks of
        // the same variant (CraftingManager lines 203-226's own rows).
        for (int variant = 1; variant < 6; variant++) {
            String logItem = variant < 4 ? "minecraft:log" : "minecraft:log2";
            int logDamage = variant < 4 ? variant : variant - 4;
            ItemStack log = new ItemStack(BuiltinItems.lookup(
                    Identifier.parse(logItem)).orElseThrow(), 1, logDamage);
            var result = service.resultOf(new ItemStack[]{log, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY});
            assertTrue(result.isPresent(), "the " + logItem + " damage " + logDamage
                    + " crafts planks");
            assertEquals("minecraft:planks", result.orElseThrow().type().identifier().toString());
            assertEquals(4, result.orElseThrow().count());
            assertEquals(variant, result.orElseThrow().damage());
        }
        // The six doors: a 2x3 of exact-variant planks yields 3 doors.
        for (int variant = 0; variant < 6; variant++) {
            ItemStack planks = new ItemStack(BuiltinItems.lookup(
                    Identifier.parse("minecraft:planks")).orElseThrow(), 1, variant);
            String door = WoodVariantBlocks.WOODS.get(variant) + "_door";
            var result = service.resultOf3x3(new ItemStack[]{
                    planks, planks, ItemStack.EMPTY,
                    planks, planks, ItemStack.EMPTY,
                    planks, planks, ItemStack.EMPTY});
            assertTrue(result.isPresent(), "the " + door + " craft over variant " + variant);
            assertEquals("minecraft:" + door,
                    result.orElseThrow().type().identifier().toString());
            assertEquals(3, result.orElseThrow().count());
        }
        // The family-planks stick craft matches every variant (the bare
        // Blocks.PLANKS ingredient's own behavior).
        for (int variant = 0; variant < 6; variant++) {
            ItemStack planks = new ItemStack(BuiltinItems.lookup(
                    Identifier.parse("minecraft:planks")).orElseThrow(), 1, variant);
            var result = service.resultOf(new ItemStack[]{planks, ItemStack.EMPTY, planks, ItemStack.EMPTY});
            assertTrue(result.isPresent(), "the stick craft over variant " + variant);
            assertEquals("minecraft:stick",
                    result.orElseThrow().type().identifier().toString());
        }
    }

    private static net.zaminmc.torch.block.BlockType block(String value) {
        return BuiltinBlocks.registerAll(new BlockRegistryBuilder())
                .freeze().lookup(Identifier.parse(value)).orElseThrow();
    }
}
