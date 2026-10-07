package net.zamin.engine.player;

import net.zamin.api.ItemStack;
import net.zamin.engine.item.BuiltinItems;
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

        assertTrue(inventory.damageHeld(1));
        assertEquals(1, inventory.held().damage());
        assertTrue(inventory.damageHeld(2));
        assertEquals(3, inventory.held().damage());
        assertEquals(1, inventory.held().count(), "a worn tool is not consumed, only worn");
    }

    @Test
    void toolBreaksAtItsDurabilityLimit() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.GOLDEN_PICKAXE)); // 32 durability

        for (int i = 0; i < 31; i++) {
            assertTrue(inventory.damageHeld(1));
        }
        assertEquals(31, inventory.held().damage());
        // The 32nd wear point breaks it: the slot empties, exactly as historically.
        assertTrue(inventory.damageHeld(1));
        assertTrue(inventory.held().isEmpty());
        // Breaking an empty hand is a no-op.
        assertEquals(false, inventory.damageHeld(1));
    }

    @Test
    void nonDurableItemsAreNeverWorn() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 5));
        assertEquals(false, inventory.damageHeld(1));
        assertEquals(0, inventory.held().damage());
        assertEquals(5, inventory.held().count());

        assertThrows(IllegalArgumentException.class, () -> inventory.damageHeld(0));
    }

    @Test
    void wornToolKeepsItsDamageThroughSplitAndCountChanges() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.IRON_PICKAXE));
        inventory.damageHeld(10);

        ItemStack thrown = inventory.dropHeld(false); // Q-drop of the stack-of-one
        assertEquals(10, thrown.damage(), "the thrown tool stays worn");
        assertTrue(inventory.held().isEmpty());

        ItemStack rePicked = inventory.pickUp(thrown);
        assertTrue(rePicked.isEmpty());
        assertEquals(10, inventory.held().damage(), "pickup restores the same worn tool");
    }
}
