package net.zaminmc.torch.server.chest;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.player.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chest slot semantics through the shared window-click rules: cursor clicks,
 * the filter-free quick move (matching partials first, then empty slots),
 * drop gestures and serial-based change tracking.
 */
class ChestBlockEntityTest {

    @Test
    void clickPlacesAndTakesThroughTheSharedCursor() {
        ChestBlockEntity chest = new ChestBlockEntity();
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 10));

        // A left click on engine slot 0 lifts the stack onto the cursor; the
        // next left click on chest slot 0 puts it down.
        inventory.clickSlot(0, 0);
        assertFalse(inventory.cursor().isEmpty());
        chest.clickSlot(0, 0, inventory);
        assertEquals(10, chest.snapshotSlots()[0].count());
        assertTrue(inventory.cursor().isEmpty());
        assertEquals(0, inventory.snapshot().get(0).count());

        // Lift it back out of the chest.
        chest.clickSlot(0, 0, inventory);
        assertEquals(10, inventory.cursor().count());
        assertTrue(chest.snapshotSlots()[0].isEmpty());
    }

    @Test
    void quickMoveMergesMatchingPartialsBeforeEmptySlots() {
        ChestBlockEntity chest = new ChestBlockEntity();
        chest.setSlot(0, ItemStack.of(BuiltinItems.DIRT, 40)); // partial stack
        chest.setSlot(5, ItemStack.of(BuiltinItems.DIRT, 64)); // full stack

        ItemStack remainder = chest.quickMoveIn(ItemStack.of(BuiltinItems.DIRT, 30));
        assertTrue(remainder.isEmpty(), "a chest with room accepts everything");
        assertEquals(64, chest.snapshotSlots()[0].count(), "the partial filled first");
        assertEquals(6, chest.snapshotSlots()[1].count(), "the rest went to the first empty slot");
        assertEquals(64, chest.snapshotSlots()[5].count(), "the full stack was skipped");
    }

    @Test
    void quickMoveAcceptsAnyStackUnlikeTheFurnace() {
        ChestBlockEntity chest = new ChestBlockEntity();
        assertTrue(chest.quickMoveIn(ItemStack.of(BuiltinItems.STICK, 7)).isEmpty(),
                "no slot filters: anything fits");
        assertEquals(7, chest.snapshotSlots()[0].count());
        assertTrue(chest.quickMoveIn(new ItemStack(BuiltinItems.COAL, 2, 1)).isEmpty(),
                "charcoal (coal metadata 1) fits like anything else");
        assertEquals(1, chest.snapshotSlots()[1].damage(), "the variant rides along");
    }

    @Test
    void fullChestReturnsTheWholeRemainder() {
        ChestBlockEntity chest = new ChestBlockEntity();
        for (int slot = 0; slot < ChestBlockEntity.SLOT_COUNT; slot++) {
            chest.setSlot(slot, ItemStack.of(BuiltinItems.DIRT, 64));
        }
        ItemStack remainder = chest.quickMoveIn(ItemStack.of(BuiltinItems.DIRT, 5));
        assertEquals(5, remainder.count(), "nothing fit: the stack comes back");
        assertEquals(64, chest.snapshotSlots()[0].count());
    }

    @Test
    void dropGestureLeavesTheRightRemainder() {
        ChestBlockEntity chest = new ChestBlockEntity();
        chest.setSlot(3, ItemStack.of(BuiltinItems.DIRT, 10));

        ItemStack one = chest.dropFromSlot(3, false);
        assertEquals(1, one.count());
        assertEquals(9, chest.snapshotSlots()[3].count());

        ItemStack all = chest.dropFromSlot(3, true);
        assertEquals(9, all.count());
        assertTrue(chest.snapshotSlots()[3].isEmpty());
        assertEquals(ItemStack.EMPTY, chest.dropFromSlot(3, true), "dropping empty is a no-op");
    }

    @Test
    void quickMoveToInventoryKeepsTheRemainderInTheChest() {
        ChestBlockEntity chest = new ChestBlockEntity();
        chest.setSlot(1, ItemStack.of(BuiltinItems.DIRT, 64));
        PlayerInventory full = new PlayerInventory();
        for (int slot = 0; slot < PlayerInventory.TOTAL_SLOTS; slot++) {
            full.pickUp(ItemStack.of(BuiltinItems.COBBLESTONE, 64));
        }
        chest.quickMoveToInventory(1, full);
        assertEquals(64, chest.snapshotSlots()[1].count(),
                "a full inventory leaves the stack inside the chest");

        ChestBlockEntity chest2 = new ChestBlockEntity();
        chest2.setSlot(1, ItemStack.of(BuiltinItems.DIRT, 30));
        chest2.quickMoveToInventory(1, new PlayerInventory());
        assertTrue(chest2.snapshotSlots()[1].isEmpty(), "an open inventory takes everything");
    }

    @Test
    void slotsSerialTracksMutationsAndRangeChecksHold() {
        ChestBlockEntity chest = new ChestBlockEntity();
        int before = chest.slotsSerial();
        chest.setSlot(26, ItemStack.of(BuiltinItems.DIRT, 1)); // last valid slot
        assertTrue(chest.slotsSerial() > before);
        assertThrows(IllegalArgumentException.class, () -> chest.setSlot(27, ItemStack.of(BuiltinItems.DIRT, 1)));
        assertThrows(IllegalArgumentException.class, () -> chest.dropFromSlot(-1, false));
        assertThrows(IllegalArgumentException.class, () -> chest.quickMoveToInventory(27, new PlayerInventory()));
    }
}
