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
        // The reference's getDamageAfterArmor (LivingEntity lines 868-877):
        // damage * (25 - points) / 25 — every point absorbs a flat 4%.
        // 20 points (diamond full set) vs the 7-damage iron sword hit:
        // 7.0 * 5 / 25 = 1.4.
        assertEquals(7.0f * (25.0f - 20.0f) / 25.0f, Armor.reduce(20, 7.0f), 1e-5);
        // 7 points (leather full set) vs the same hit: 7.0 * 18 / 25 = 5.04.
        assertEquals(7.0f * (25.0f - 7.0f) / 25.0f, Armor.reduce(7, 7.0f), 1e-5);
        // No armor never reduces.
        assertEquals(5.0f, Armor.reduce(0, 5.0f), 1e-6);
        // The old 1.9-shaped envelope's own example is now refused: at 20
        // points vs 10 damage the 1.9 form passes 4.0, the 1.8.8 law 2.0.
        assertEquals(2.0f, Armor.reduce(20, 10.0f), 1e-5);
    }

    @Test
    void everyHitWearsEveryPieceAndBrokenPiecesLeaveTheSlot() {
        PlayerInventory inventory = new PlayerInventory();
        assertTrue(inventory.setArmor(0, ItemStack.of(BuiltinItems.LEATHER_HELMET, 1)
                .withDamage(BuiltinItems.LEATHER_HELMET.maxDurability() - 1)));
        assertTrue(inventory.setArmor(1, ItemStack.of(BuiltinItems.IRON_CHESTPLATE, 1)));
        assertTrue(inventory.wearArmor(4.0F, new java.util.Random()));
        // The reference's takeDamage boundary (ItemStack.takeDamage line
        // 204: the break lands at damage > max): the helmet at its maximum
        // SURVIVES this wear — the pre-fix >= boundary broke it one use
        // early.
        assertFalse(inventory.armorAt(0).isEmpty(), "the helmet survives AT its maximum");
        assertEquals(BuiltinItems.LEATHER_HELMET.maxDurability(), inventory.armorAt(0).damage());
        assertEquals(1, inventory.armorAt(1).damage(), "the chestplate took one wear");
        assertTrue(inventory.wearArmor(4.0F, new java.util.Random())); // keep wearing the pieces down
        assertTrue(inventory.armorAt(0).isEmpty(), "the next wear exceeds the maximum and breaks the helmet");
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
