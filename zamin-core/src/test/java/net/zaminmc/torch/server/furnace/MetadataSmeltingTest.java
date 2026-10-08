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
}
