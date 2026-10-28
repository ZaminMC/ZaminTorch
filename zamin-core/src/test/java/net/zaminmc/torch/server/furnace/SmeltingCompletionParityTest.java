package net.zaminmc.torch.server.furnace;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The smelting-completion parity law (the verifier's third pass — the
 * reference's own SmeltingManager rows the engine never carried, each
 * read from reference/1.8.8/crafting/SmeltingManager.java lines 25-64
 * before the fix):
 *
 * <ul>
 * <li>The raw rabbit smelts to the cooked form (line 33) with the food
 *     band's 0.35 take — the pre-fix table carried NO row, so the rabbit
 *     never entered the furnace at all.</li>
 * <li>The cactus smelts to the GREEN dye — damage 2, DyeColor.GREEN's own
 *     metadata (line 50) — with the 0.2 take; no other dye take pays
 *     (nothing else smelts to a dye).</li>
 * <li>The DEFAULT stone brick alone smelts to the CRACKED form (line 41:
 *     {@code new ItemStack(Blocks.STONE_BRICKS, 1, DEFAULT_ID)} — the
 *     mossy and chiseled pair never enters the furnace), take 0.1.</li>
 * <li>The WET sponge — damage 1 — dries to the plain form (line 63), take
 *     0.15; the dry sponge never enters the furnace.</li>
 * <li>The coal pair's take split (lines 60 + 47-48): the ORE take (plain
 *     coal, damage 0) pays 0.1 and the LOG take (charcoal, damage 1)
 *     pays 0.15 — the reference keys the RESULT STACK, item AND
 *     metadata, and the pre-fix identifier-keyed table answered 0.15
 *     for BOTH takes.</li>
 * </ul>
 */
class SmeltingCompletionParityTest {

    @Test
    void theRabbitSmeltsToTheCookedForm() {
        var result = FurnaceRecipes.resultOf(BuiltinItems.RABBIT).orElseThrow();
        assertEquals(Identifier.parse("minecraft:cooked_rabbit"), result.output());
        assertEquals(1, result.count());
        assertEquals(0, result.damage());
        assertEquals(0.35, FurnaceRecipes.takeExperienceOf(
                BuiltinItems.COOKED_RABBIT, 0), 1e-9, "the food band's take");
    }

    @Test
    void theCactusSmeltsToTheGreenDye() {
        var result = FurnaceRecipes.resultOf(BuiltinItems.CACTUS).orElseThrow();
        assertEquals(Identifier.parse("minecraft:dye"), result.output());
        assertEquals(2, result.damage(), "DyeColor.GREEN's own metadata");
        assertEquals(0.2, FurnaceRecipes.takeExperienceOf(
                BuiltinItems.INK_SAC, 2), 1e-9, "the cactus take");
        // No other dye take pays: the green alone is a smelting result.
        assertEquals(0.0, FurnaceRecipes.takeExperienceOf(
                BuiltinItems.INK_SAC, 0), 1e-9, "an ink sac take pays nothing");
        assertEquals(0.0, FurnaceRecipes.takeExperienceOf(
                BuiltinItems.INK_SAC, 15), 1e-9, "the bone meal take pays nothing");
    }

    @Test
    void thePlainStoneBrickAloneSmeltsToTheCrackedForm() {
        var result = FurnaceRecipes.resultOf(
                BuiltinItems.STONE_BRICKS, 0).orElseThrow();
        assertEquals(Identifier.parse("minecraft:stone_bricks"), result.output());
        assertEquals(2, result.damage(), "the CRACKED variant's own id");
        assertEquals(0.1, FurnaceRecipes.takeExperienceOf(
                BuiltinItems.STONE_BRICKS, 2), 1e-9, "the cracked take");
        // The mossy and chiseled pair never enters the furnace.
        assertTrue(FurnaceRecipes.resultOf(BuiltinItems.STONE_BRICKS, 1).isEmpty(),
                "the mossy brick never smelts");
        assertTrue(FurnaceRecipes.resultOf(BuiltinItems.STONE_BRICKS, 3).isEmpty(),
                "the chiseled brick never smelts");
        // The cracked take is the brick's only band.
        assertEquals(0.0, FurnaceRecipes.takeExperienceOf(
                BuiltinItems.STONE_BRICKS, 0), 1e-9);
    }

    @Test
    void theWetSpongeAloneDries() {
        var result = FurnaceRecipes.resultOf(BuiltinItems.SPONGE, 1).orElseThrow();
        assertEquals(Identifier.parse("minecraft:sponge"), result.output());
        assertEquals(0, result.damage(), "the dried form carries the plain metadata");
        assertEquals(0.15, FurnaceRecipes.takeExperienceOf(
                BuiltinItems.SPONGE, 0), 1e-9, "the dried take");
        assertTrue(FurnaceRecipes.resultOf(BuiltinItems.SPONGE, 0).isEmpty(),
                "the dry sponge never enters the furnace");
    }

    @Test
    void theCoalTakeSplitsByDamage() {
        // The ORE smelt answers plain coal (damage 0) — the take pays the
        // reference's 0.1 row, never the charcoal's 0.15.
        var oreSmelt = FurnaceRecipes.resultOf(
                BuiltinItems.COAL_ORE, 0).orElseThrow();
        assertEquals(Identifier.parse("minecraft:coal"), oreSmelt.output());
        assertEquals(0, oreSmelt.damage());
        assertEquals(0.1, FurnaceRecipes.takeExperienceOf(
                BuiltinItems.COAL, 0), 1e-9, "the ore take's own row");
        // The LOG smelt answers charcoal (damage 1) — the take keeps 0.15.
        var logSmelt = FurnaceRecipes.resultOf(
                BuiltinItems.OAK_LOG, 0).orElseThrow();
        assertEquals(Identifier.parse("minecraft:coal"), logSmelt.output());
        assertEquals(1, logSmelt.damage(), "charcoal rides the damage field");
        assertEquals(0.15, FurnaceRecipes.takeExperienceOf(
                BuiltinItems.COAL, 1), 1e-9, "the charcoal take keeps its band");
    }

    @Test
    void theCactusSmeltsEndToEnd() throws Exception {
        // The furnace entity's own tick walk: the cactus's smelt lands the
        // green dye in the output slot (the metadata rides the stack).
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT, ItemStack.of(BuiltinItems.CACTUS, 1));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL, ItemStack.of(BuiltinItems.COAL, 1));

        for (int tick = 0; tick < FurnaceRecipes.COOK_TICKS; tick++) {
            furnace.tick();
        }
        ItemStack out = furnace.output();
        assertEquals(BuiltinItems.INK_SAC, out.type(), "the dye item carries the cactus's yield");
        assertEquals(2, out.damage(), "the green dye's own metadata");
        assertEquals(1, out.count());
    }
}
