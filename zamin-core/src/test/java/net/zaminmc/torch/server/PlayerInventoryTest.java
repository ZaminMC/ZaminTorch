package net.zaminmc.torch.server.player;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slot semantics, validated operations and atomicity of the player inventory (§428-§432). */
class PlayerInventoryTest {

    @Test
    void newInventoryIsEmptyWithHotbarSelected() {
        PlayerInventory inventory = new PlayerInventory();
        assertTrue(inventory.held().isEmpty());
        assertEquals(0, inventory.heldSlot());
        assertEquals(PlayerInventory.TOTAL_SLOTS, inventory.snapshot().size());
        inventory.snapshot().forEach(stack -> assertTrue(stack.isEmpty()));
    }

    @Test
    void pickupFillsHotbarFirstThenMain() {
        PlayerInventory inventory = new PlayerInventory();
        ItemStack remainder = inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 5));
        assertTrue(remainder.isEmpty());
        assertEquals(5, inventory.held().count()); // slot 0 = first hotbar slot

        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 10));
        assertEquals(15, inventory.held().count()); // stacked into slot 0

        // Fill hotbar 0-8, then spill into main inventory slot 9.
        for (int i = 0; i < 8; i++) {
            inventory.pickUp(ItemStack.of(BuiltinItems.OAK_LOG, 64));
        }
        inventory.pickUp(ItemStack.of(BuiltinItems.OAK_LOG, 64)); // slot 8
        remainder = inventory.pickUp(ItemStack.of(BuiltinItems.OAK_LOG, 64));
        assertTrue(remainder.isEmpty());
        assertEquals(64, inventory.snapshot().get(9).count()); // first main slot
    }

    @Test
    void pickupTopsUpStacksThenFillsEmptySlots() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 60));
        // Historical order: top up matching stacks first, then the rest goes to
        // the next empty slot — the pickup only fails when NO slot fits anymore.
        ItemStack remainder = inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 10));
        assertTrue(remainder.isEmpty());
        assertEquals(64, inventory.held().count());
        assertEquals(6, inventory.snapshot().get(1).count());
    }

    @Test
    void pickupIsPartialOnlyWhenNoSlotFits() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 60));
        // Every other slot filled with a different item: no empty slot remains.
        for (int i = 0; i < PlayerInventory.TOTAL_SLOTS - 1; i++) {
            inventory.pickUp(ItemStack.of(BuiltinItems.OAK_LOG, 64));
        }
        ItemStack remainder = inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 10));
        assertEquals(6, remainder.count());
        assertEquals(64, inventory.held().count());
    }

    @Test
    void fullInventoryReturnsWholeStack() {
        PlayerInventory inventory = new PlayerInventory();
        for (int i = 0; i < PlayerInventory.TOTAL_SLOTS; i++) {
            inventory.pickUp(ItemStack.of(BuiltinItems.OAK_LOG, 64));
        }
        ItemStack remainder = inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 3));
        assertEquals(3, remainder.count());
    }

    @Test
    void consumeHeldRemovesFromSelectedSlot() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 10));

        ItemStack consumed = inventory.consumeHeld(4);
        assertEquals(4, consumed.count());
        assertEquals(6, inventory.held().count());

        inventory.selectHotbarSlot(1);
        assertTrue(inventory.consumeHeld(1).isEmpty()); // empty slot consumes nothing

        inventory.selectHotbarSlot(0);
        assertEquals(6, inventory.consumeHeld(100).count()); // clamps to what exists
        assertTrue(inventory.held().isEmpty());
    }

    @Test
    void dropHeldRemovesOneOrEverything() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.COBBLESTONE, 12));

        assertEquals(1, inventory.dropHeld(false).count());
        assertEquals(11, inventory.held().count());

        assertEquals(11, inventory.dropHeld(true).count());
        assertTrue(inventory.held().isEmpty());
        assertTrue(inventory.dropHeld(true).isEmpty()); // empty hand drops nothing
    }

    @Test
    void invalidOperationsLeaveStateUnchanged() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 7));

        assertThrows(IllegalArgumentException.class, () -> inventory.selectHotbarSlot(9));
        assertThrows(IllegalArgumentException.class, () -> inventory.selectHotbarSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> inventory.consumeHeld(0));

        assertEquals(0, inventory.heldSlot());
        assertEquals(7, inventory.held().count());
    }

    @Test
    void durabilityWearAccumulatesOnTheHeldTool() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.WOODEN_PICKAXE)); // 59 durability

        assertTrue(inventory.damageHeld(1, new java.util.Random()));
        assertEquals(1, inventory.held().damage());
        assertTrue(inventory.damageHeld(2, new java.util.Random()));
        assertEquals(3, inventory.held().damage());
        assertEquals(1, inventory.held().count(), "a worn tool is not consumed, only worn");
    }

    @Test
    void toolBreaksAtItsDurabilityLimit() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.GOLDEN_PICKAXE)); // 32 durability

        for (int i = 0; i < 31; i++) {
            assertTrue(inventory.damageHeld(1, new java.util.Random()));
        }
        assertEquals(31, inventory.held().damage());
        // The 32nd wear point breaks it: the slot empties, exactly as historically.
        assertTrue(inventory.damageHeld(1, new java.util.Random()));
        assertTrue(inventory.held().isEmpty());
        // Breaking an empty hand is a no-op.
        assertEquals(false, inventory.damageHeld(1, new java.util.Random()));
    }

    @Test
    void nonDurableItemsAreNeverWorn() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 5));
        assertEquals(false, inventory.damageHeld(1, new java.util.Random()));
        assertEquals(0, inventory.held().damage());
        assertEquals(5, inventory.held().count());

        assertThrows(IllegalArgumentException.class, () -> inventory.damageHeld(0, new java.util.Random()));
    }

    @Test
    void wornToolKeepsItsDamageThroughSplitAndCountChanges() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.IRON_PICKAXE));
        inventory.damageHeld(10, new java.util.Random());

        ItemStack thrown = inventory.dropHeld(false); // Q-drop of the stack-of-one
        assertEquals(10, thrown.damage(), "the thrown tool stays worn");
        assertTrue(inventory.held().isEmpty());

        ItemStack rePicked = inventory.pickUp(thrown);
        assertTrue(rePicked.isEmpty());
        assertEquals(10, inventory.held().damage(), "pickup restores the same worn tool");
    }

    // ------------------------------------------------------------------ window clicks

    @Test
    void leftClickPicksUpAndPlacesAWholeStack() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 10));

        inventory.clickSlot(0, 0); // left click: pick up
        assertTrue(inventory.held().isEmpty());
        assertEquals(10, inventory.cursor().count());

        inventory.clickSlot(5, 0); // place into main slot 5
        assertTrue(inventory.cursor().isEmpty());
        assertEquals(10, inventory.snapshot().get(5).count());
    }

    @Test
    void leftClickMergesMatchingStacksAndSwapsMismatchedOnes() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 60));
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 8)); // slot 1: 8 dirt
        inventory.clickSlot(0, 0); // cursor: 60 dirt

        inventory.clickSlot(2, 0); // place 60 into empty slot 2
        inventory.clickSlot(2, 0); // pick the 60 back up
        inventory.clickSlot(1, 0); // merge into the 8-stack: 4 spill over on the cursor
        assertEquals(64, inventory.snapshot().get(1).count());
        assertEquals(4, inventory.cursor().count());

        inventory.clickSlot(0, 0); // cursor: 4 dirt; slot 0 empty -> place
        assertTrue(inventory.cursor().isEmpty());
        assertEquals(4, inventory.snapshot().get(0).count());

        // pick the 4 back up; a full matching stack is a no-op merge (historical)
        inventory.clickSlot(0, 0);
        inventory.clickSlot(1, 0);
        assertEquals(4, inventory.cursor().count());
        assertEquals(64, inventory.snapshot().get(1).count());

        // mismatched stacks swap between cursor and slot
        inventory.pickUp(ItemStack.of(BuiltinItems.OAK_LOG, 2)); // lands in the next empty slot (0)
        inventory.clickSlot(0, 0); // cursor 4 dirt vs logs: swap -> cursor carries the logs
        assertTrue(inventory.cursor().type().equals(BuiltinItems.OAK_LOG));
        inventory.clickSlot(1, 0); // swap the logs with the full dirt stack
        assertEquals(BuiltinItems.OAK_LOG, inventory.snapshot().get(1).type());
        assertEquals(64, inventory.cursor().count());
    }

    @Test
    void rightClickSplitsHalfAndPlacesOne() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 11));
        inventory.clickSlot(0, 1); // right click: take half, rounding up
        assertEquals(6, inventory.cursor().count());
        assertEquals(5, inventory.held().count());

        inventory.clickSlot(3, 1); // place ONE into empty slot 3
        assertEquals(1, inventory.snapshot().get(3).count());
        assertEquals(5, inventory.cursor().count());

        inventory.clickSlot(3, 1); // place another one on top
        assertEquals(2, inventory.snapshot().get(3).count());
        assertEquals(4, inventory.cursor().count());
    }

    @Test
    void quickMoveSwitchesRangesAndPreservesOrder() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 30));  // hotbar 0
        inventory.pickUp(ItemStack.of(BuiltinItems.COBBLESTONE, 5)); // hotbar 1

        inventory.quickMove(0); // shift-click hotbar -> main
        assertTrue(inventory.snapshot().get(0).isEmpty());
        assertEquals(30, inventory.snapshot().get(9).count()); // first main slot

        inventory.quickMove(9); // shift-click main -> hotbar: tops up matching stacks first
        assertTrue(inventory.snapshot().get(9).isEmpty());
        assertEquals(30, inventory.snapshot().get(0).count()); // first empty hotbar slot
    }

    @Test
    void numberKeySwapsMainWithHotbar() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 3));  // hotbar 0
        inventory.pickUp(ItemStack.of(BuiltinItems.COBBLESTONE, 4)); // hotbar 1

        inventory.swapWithHotbar(9, 1); // main slot 9 (empty) <-> hotbar 1
        assertTrue(inventory.snapshot().get(1).isEmpty());
        assertEquals(4, inventory.snapshot().get(9).count());

        inventory.swapWithHotbar(9, 1); // swap back
        assertEquals(4, inventory.snapshot().get(1).count());
        assertTrue(inventory.snapshot().get(9).isEmpty());
    }

    @Test
    void dropClickThrowsFromAnySlot() {
        PlayerInventory inventory = new PlayerInventory();
        for (int i = 0; i < PlayerInventory.HOTBAR_SLOTS; i++) {
            inventory.pickUp(ItemStack.of(BuiltinItems.OAK_LOG, 64)); // fills hotbar 0-8
        }
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 10)); // spills into main slot 9

        assertEquals(1, inventory.dropFromSlot(9, false).count()); // button 0: one
        assertEquals(9, inventory.snapshot().get(9).count());
        assertEquals(9, inventory.dropFromSlot(9, true).count()); // button 1: the rest
        assertTrue(inventory.snapshot().get(9).isEmpty());
        assertTrue(inventory.dropFromSlot(9, true).isEmpty()); // nothing left to drop
    }

    @Test
    void cursorReturnsToTheInventoryAndOverflowIsReported() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 10));
        inventory.clickSlot(0, 0); // cursor: 10 dirt
        assertTrue(inventory.returnCursor().isEmpty());
        assertEquals(10, inventory.held().count());
        assertTrue(inventory.cursor().isEmpty());

        // a cursor stack that cannot fully fit reports the remainder
        inventory.clickSlot(0, 0); // cursor: 10 dirt (hotbar 0 emptied)
        for (int i = 0; i < PlayerInventory.TOTAL_SLOTS; i++) {
            inventory.pickUp(ItemStack.of(BuiltinItems.OAK_LOG, 64)); // fills every slot incl. 0
        }
        assertEquals(10, inventory.returnCursor().count(), "no slot fits: remainder to the world");
    }

    @Test
    void invalidClicksAreRejectedWithoutStateChanges() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 5));
        assertThrows(IllegalArgumentException.class, () -> inventory.clickSlot(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> inventory.clickSlot(36, 0));
        assertThrows(IllegalArgumentException.class, () -> inventory.clickSlot(0, 2));
        assertThrows(IllegalArgumentException.class, () -> inventory.quickMove(40));
        assertThrows(IllegalArgumentException.class, () -> inventory.swapWithHotbar(0, 1)); // hotbar target
        assertThrows(IllegalArgumentException.class, () -> inventory.swapWithHotbar(9, 9));
        assertEquals(5, inventory.held().count());
        assertTrue(inventory.cursor().isEmpty());
    }
}
