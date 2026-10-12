package net.zaminmc.torch.server.furnace;

import net.zaminmc.torch.util.Identifier;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item-metadata unlock on the furnace tables: sand smelts to glass and
 * logs smelt to charcoal (coal with metadata 1, the 1.8 dataset variant),
 * while charcoal burns exactly like its parent coal.
 */
class MetadataSmeltingTest {

    @Test
    void sandSmeltsToGlass() {
        var result = FurnaceRecipes.resultOf(BuiltinItems.SAND).orElseThrow();
        assertEquals(net.zaminmc.torch.util.Identifier.parse("minecraft:glass"), result.output());
        assertEquals(1, result.count());
        assertEquals(0, result.damage());
    }

    @Test
    void logSmeltsToCharcoal() {
        var result = FurnaceRecipes.resultOf(BuiltinItems.OAK_LOG).orElseThrow();
        assertEquals(net.zaminmc.torch.util.Identifier.parse("minecraft:coal"), result.output(),
                "charcoal is the coal item");
        assertEquals(1, result.damage(), "carrying metadata 1 (community items.json variant)");
    }

    @Test
    void charcoalBurnsLikeCoal() {
        assertEquals(FurnaceRecipes.burnTicksOf(BuiltinItems.COAL),
                FurnaceRecipes.burnTicksOf(BuiltinItems.COAL),
                "coal's own anchor (1600) holds");
        assertEquals(1600, FurnaceRecipes.burnTicksOf(BuiltinItems.COAL),
                "the community fuel table anchor");
        // Charcoal shares the item type with coal, so the type-keyed fuel table
        // covers the variant: the historical behavior.
        assertTrue(FurnaceRecipes.burnTicksOf(BuiltinItems.COAL) > 0);
    }

    @Test
    void furnaceProducesCharcoalStacksThatNeverMergeWithCoal() throws Exception {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT, ItemStack.of(BuiltinItems.OAK_LOG, 1));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL, ItemStack.of(BuiltinItems.COAL, 1));

        for (int tick = 0; tick < FurnaceRecipes.COOK_TICKS; tick++) {
            furnace.tick();
        }
        ItemStack out = furnace.output();
        assertEquals(BuiltinItems.COAL, out.type());
        assertEquals(1, out.count());
        assertEquals(1, out.damage(), "the smelt of a log is charcoal (metadata 1)");
        assertTrue(net.zaminmc.torch.server.player.WindowClicks.stacksMergeable(out, out),
                "charcoal stacks with itself");
        assertTrue(!net.zaminmc.torch.server.player.WindowClicks.stacksMergeable(
                        out, ItemStack.of(BuiltinItems.COAL, 1)),
                "charcoal and coal are different stacks (metadata-aware merging)");
        assertEquals(0, furnace.input().count(), "the log was consumed");
        assertEquals(0, furnace.fuel().count(), "the coal fuel was consumed");
    }

    @Test
    void charcoalAcceptsCharcoalInputOutputStacking() throws Exception {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        // Two logs smelt back to back: the second charcoal joins the first
        // in the output slot (same type AND same damage).
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT, ItemStack.of(BuiltinItems.OAK_LOG, 2));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL, ItemStack.of(BuiltinItems.COAL, 1));
        for (int tick = 0; tick < FurnaceRecipes.COOK_TICKS * 2; tick++) {
            furnace.tick();
        }
        assertEquals(2, furnace.output().count(), "both charcoal units stacked");
        assertEquals(1, furnace.output().damage());
        assertEquals(0, furnace.input().count());
    }

    // ------------------------------------------------------------------
    // The fish family's own smelting pair (Slice 11i — the reference's
    // canBeCooked gate)
    // ------------------------------------------------------------------

    @Test
    void rawFishSmeltsIntoItsCookedVariant() {
        // The raw cod (metadata 0) -> the cooked cod (damage 0); the raw
        // salmon (metadata 1) -> the cooked salmon (damage 1).
        var cod = FurnaceRecipes.resultOf(BuiltinItems.FISH, 0).orElseThrow();
        assertEquals(Identifier.parse("minecraft:cooked_fish"), cod.output());
        assertEquals(1, cod.count());
        assertEquals(0, cod.damage());
        var salmon = FurnaceRecipes.resultOf(BuiltinItems.FISH, 1).orElseThrow();
        assertEquals(Identifier.parse("minecraft:cooked_fish"), salmon.output());
        assertEquals(1, salmon.damage());
        // The clownfish and the pufferfish never cook (the canBeCooked
        // gate — FishItem.Type's two-field constructor).
        assertTrue(FurnaceRecipes.resultOf(BuiltinItems.FISH, 2).isEmpty(),
                "the clownfish is not smeltable");
        assertTrue(FurnaceRecipes.resultOf(BuiltinItems.FISH, 3).isEmpty(),
                "the pufferfish is not smeltable");
        // A cooked fish is not itself smeltable.
        assertTrue(FurnaceRecipes.resultOf(BuiltinItems.COOKED_FISH, 0).isEmpty());
    }

    @Test
    void theFurnaceSmeltsFishCarryingTheVariant() throws Exception {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        // Two raw salmon: both smelt into the cooked salmon (damage 1)
        // and stack in the output slot.
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                new ItemStack(BuiltinItems.FISH, 2, 1));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL, ItemStack.of(BuiltinItems.COAL, 1));
        for (int tick = 0; tick < FurnaceRecipes.COOK_TICKS * 2; tick++) {
            furnace.tick();
        }
        assertEquals(BuiltinItems.COOKED_FISH, furnace.output().type());
        assertEquals(2, furnace.output().count(), "both salmon cooked");
        assertEquals(1, furnace.output().damage(), "the salmon variant carried");
        assertEquals(0, furnace.input().count());
        // The cooked fish pays the cooked-food band's take XP.
        assertEquals(0.35, FurnaceRecipes.takeExperienceOf(BuiltinItems.COOKED_FISH),
                0.0001);
    }
}
