package net.zaminmc.torch.server.item;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.player.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The armor contracts: kind-gated slots, the 1.8 reduction envelope, the
 * historical wear-per-hit and the armor bar total.
 */
class ArmorTest {

    @Test
    void fullSetsReachTheHistoricalBarTotals() {
        assertEquals(20, fullSet(BuiltinItems.DIAMOND_HELMET, BuiltinItems.DIAMOND_CHESTPLATE,
                BuiltinItems.DIAMOND_LEGGINGS, BuiltinItems.DIAMOND_BOOTS),
                "diamond is the 20-point set");
        assertEquals(15, fullSet(BuiltinItems.IRON_HELMET, BuiltinItems.IRON_CHESTPLATE,
                BuiltinItems.IRON_LEGGINGS, BuiltinItems.IRON_BOOTS));
        assertEquals(7, fullSet(BuiltinItems.LEATHER_HELMET, BuiltinItems.LEATHER_CHESTPLATE,
                BuiltinItems.LEATHER_LEGGINGS, BuiltinItems.LEATHER_BOOTS));
    }

    private static int fullSet(net.zaminmc.torch.item.ItemType head, net.zaminmc.torch.item.ItemType chest,
                               net.zaminmc.torch.item.ItemType legs, net.zaminmc.torch.item.ItemType feet) {
        PlayerInventory inventory = new PlayerInventory();
        assertTrue(inventory.setArmor(0, ItemStack.of(head, 1)));
        assertTrue(inventory.setArmor(1, ItemStack.of(chest, 1)));
        assertTrue(inventory.setArmor(2, ItemStack.of(legs, 1)));
        assertTrue(inventory.setArmor(3, ItemStack.of(feet, 1)));
        return inventory.totalArmorPoints();
    }

    @Test
    void armorSlotsOnlyAcceptTheirOwnKind() {
        PlayerInventory inventory = new PlayerInventory();
        assertFalse(inventory.setArmor(0, ItemStack.of(BuiltinItems.IRON_BOOTS, 1)),
                "boots never enter the head slot");
        assertFalse(inventory.setArmor(0, ItemStack.of(BuiltinItems.DIRT, 1)),
                "blocks never enter armor");
        assertTrue(inventory.setArmor(0, ItemStack.of(BuiltinItems.IRON_HELMET, 1)));
        // The number-key swap refuses a non-armor hotbar stack, accepts an empty one.
        inventory.setSlot(0, ItemStack.of(BuiltinItems.DIRT, 1));
        assertFalse(inventory.swapArmorWithHotbar(0, 0));
        inventory.setSlot(1, ItemStack.of(BuiltinItems.IRON_HELMET, 1));
        assertTrue(inventory.swapArmorWithHotbar(0, 1));
        assertEquals(BuiltinItems.IRON_HELMET, inventory.armorAt(0).type());
    }

    @Test
    void theReductionEnvelopeMatchesTheHistoricalFormula() {
        // 20 points (diamond full set) vs the 7-damage iron sword hit:
        // absorbed = min(20, max(4, 20 - 3.5)) = 16.5 -> damage * (1 - 16.5/25).
        assertEquals(7.0f * (1.0f - 16.5f / 25.0f), Armor.reduce(20, 7.0f), 1e-5);
        // 7 points (leather full set) vs the same hit:
        // absorbed = min(20, max(1.4, 7 - 3.5)) = 3.5.
        assertEquals(7.0f * (1.0f - 3.5f / 25.0f), Armor.reduce(7, 7.0f), 1e-5);
        // No armor never reduces.
        assertEquals(5.0f, Armor.reduce(0, 5.0f), 1e-6);
    }

    @Test
    void everyHitWearsEveryPieceAndBrokenPiecesLeaveTheSlot() {
        PlayerInventory inventory = new PlayerInventory();
        assertTrue(inventory.setArmor(0, ItemStack.of(BuiltinItems.LEATHER_HELMET, 1)
                .withDamage(BuiltinItems.LEATHER_HELMET.maxDurability() - 1)));
        assertTrue(inventory.setArmor(1, ItemStack.of(BuiltinItems.IRON_CHESTPLATE, 1)));
        assertTrue(inventory.wearArmor());
        assertTrue(inventory.armorAt(0).isEmpty(), "the last-wear helmet breaks");
        assertEquals(1, inventory.armorAt(1).damage(), "the chestplate took one wear");
        assertTrue(inventory.wearArmor()); // keep wearing the chestplate down
        assertEquals(2, inventory.armorAt(1).damage());
    }

    @Test
    void quickMoveAndSwapKeepTheRowConsistent() {
        PlayerInventory inventory = new PlayerInventory();
        assertTrue(inventory.setArmor(1, ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1)));
        assertTrue(inventory.quickMoveFromArmor(1), "the piece moves to the inventory");
        assertTrue(inventory.armorAt(1).isEmpty());
        assertTrue(inventory.swapArmorWithHotbar(1, 0), "a matching hotbar piece swaps in");
        assertEquals(BuiltinItems.DIAMOND_CHESTPLATE, inventory.armorAt(1).type());
        assertTrue(inventory.snapshot().get(0).isEmpty(), "the hotbar slot took the old value");
    }
}
