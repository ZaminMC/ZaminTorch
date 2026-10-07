package net.zamin.engine.furnace;

import net.zamin.api.ItemStack;
import net.zamin.engine.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The historical 1.8 furnace state machine: 200-tick cook cycles, fuel
 * consumption at ignition only, interruption resets the progress, and
 * the cursor rules shared across every container.
 */
class FurnaceBlockEntityTest {

    @Test
    void smeltsIronOreIntoIngotsAfterTwoHundredTicks() {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 2));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.COAL, 2));

        // One tick ignites the fuel (1600 = coal) but produces nothing yet.
        furnace.tick();
        assertEquals(1600, furnace.burnTimeRemaining());
        assertEquals(1600, furnace.burnTimeTotal());
        assertEquals(1, furnace.fuel().count(), "ignition consumed one coal");
        assertEquals(0, furnace.output().count());

        // The ignition tick was cook point 1 of 200; 198 more keep the cycle
        // one point short of completion.
        for (int tick = 0; tick < FurnaceRecipes.COOK_TICKS - 2; tick++) {
            furnace.tick();
        }
        assertEquals(FurnaceRecipes.COOK_TICKS - 1, furnace.cookTime(),
                "one point before the cycle completes");
        assertEquals(0, furnace.output().count(), "nothing smelted before tick 200");

        furnace.tick(); // the 200th cook point
        assertEquals(1, furnace.output().count());
        assertEquals(BuiltinItems.IRON_INGOT, furnace.output().type());
        assertEquals(1, furnace.input().count(), "one ore was consumed");
    }

    @Test
    void smeltsCobblestoneIntoStone() {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.COBBLESTONE, 1));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.OAK_PLANKS, 2)); // 600 ticks

        for (int tick = 0; tick <= FurnaceRecipes.COOK_TICKS; tick++) {
            furnace.tick();
        }
        assertEquals(BuiltinItems.STONE, furnace.output().type(),
                "cobblestone smelts into stone");
        assertEquals(1, furnace.output().count());
        assertEquals(0, furnace.output().damage());
    }

    @Test
    void interruptionResetsCookProgress() {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 1));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.COAL, 1));
        furnace.tick();
        for (int tick = 1; tick < 100; tick++) {
            furnace.tick();
        }
        assertEquals(100, furnace.cookTime());

        // The input vanishes (player takes it out): progress resets to zero.
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT, ItemStack.EMPTY);
        furnace.tick();
        assertEquals(0, furnace.cookTime(), "the historical reset");
        assertTrue(furnace.burning(), "the fuel keeps burning out anyway");

        int burnLeft = furnace.burnTimeRemaining();
        for (int tick = 0; tick < burnLeft + 10; tick++) {
            furnace.tick(); // burn out; no smelting happens without input
        }
        assertFalse(furnace.burning());
        assertEquals(0, furnace.output().count());
    }

    @Test
    void consecutiveSmeltsShareOneFuelCycle() {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 8));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.COAL, 1)); // 1600 ticks = exactly 8 smelts

        for (int tick = 0; tick < 1600 + 8; tick++) {
            furnace.tick();
        }
        assertEquals(8, furnace.output().count(), "one coal smelts eight items");
        assertEquals(0, furnace.input().count());
        assertFalse(furnace.burning(), "the coal is spent");
        assertEquals(0, furnace.fuel().count());
    }

    @Test
    void outputRefusesToOverflowItsStackLimit() {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 64));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.COAL, 8));
        // Fill the output almost to the limit: only 2 more ingots fit.
        furnace.setSlot(FurnaceBlockEntity.SLOT_OUTPUT,
                new ItemStack(BuiltinItems.IRON_INGOT, 62, 0));

        for (int tick = 0; tick < FurnaceRecipes.COOK_TICKS * 3 + 10; tick++) {
            furnace.tick();
        }
        assertEquals(64, furnace.output().count(), "smelting stops at the stack limit");
        assertTrue(furnace.input().count() > 0, "the excess ore waits");
    }

    @Test
    void nonFuelDoesNotIgnite() {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 1));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.IRON_INGOT, 1)); // not fuel
        furnace.tick();
        assertFalse(furnace.burning());
        assertEquals(1, furnace.fuel().count(), "the non-fuel stays");
    }

    @Test
    void shiftClickRoutesSmeltablesToInputAndFuelToFuel() {
        var inventory = new net.zamin.engine.player.PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.IRON_ORE, 3));   // hotbar 0
        inventory.pickUp(ItemStack.of(BuiltinItems.COAL, 2));       // hotbar 1
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 1));       // hotbar 2

        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        // Shift-click the ore (hotbar 0) into the furnace.
        assertTrue(furnace.quickMoveIn(inventory.dropFromSlot(0, true)).isEmpty());
        assertEquals(3, furnace.input().count());
        // Shift-click the coal (hotbar 1): fuel routing.
        assertTrue(furnace.quickMoveIn(inventory.dropFromSlot(1, true)).isEmpty());
        assertEquals(2, furnace.fuel().count());
        // Dirt is neither: it comes straight back, the furnace stays untouched.
        ItemStack dirt = inventory.dropFromSlot(2, true);
        assertEquals(1, furnace.quickMoveIn(dirt).count());
        assertEquals(3, furnace.input().count(), "the input kept the ore");
        assertEquals(2, furnace.fuel().count(), "the fuel kept the coal");
    }

    @Test
    void clicksPlayByTheSharedCursorRules() {
        var inventory = new net.zamin.engine.player.PlayerInventory();
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 10));

        // Left click with an empty cursor picks the whole stack up.
        furnace.clickSlot(FurnaceBlockEntity.SLOT_INPUT, 0, inventory);
        assertTrue(furnace.input().isEmpty());
        assertEquals(10, inventory.cursor().count());
        // Left click again puts it back.
        furnace.clickSlot(FurnaceBlockEntity.SLOT_INPUT, 0, inventory);
        assertTrue(inventory.cursor().isEmpty());
        assertEquals(10, furnace.input().count());
        // Right click takes half, rounding down on even counts (10 -> 5).
        furnace.clickSlot(FurnaceBlockEntity.SLOT_INPUT, 1, inventory);
        assertEquals(5, inventory.cursor().count());
        assertEquals(5, furnace.input().count());
    }
}
