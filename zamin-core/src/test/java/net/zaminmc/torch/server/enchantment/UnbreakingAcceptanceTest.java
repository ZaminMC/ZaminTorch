package net.zaminmc.torch.server.enchantment;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.player.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Unbreaking slice against the reference (reference/1.8.8
 * enchantment/UnbreakingEnchantment line 34 + item/ItemStack lines 196-215
 * + entity/living/player/PlayerInventory lines 543-555 damageArmor): the
 * per-unit reduction roll with the armor's 60% early-false gate, the level
 * read once per walk, the wear walk riding the reduction on the held tool
 * and on every armor piece, the damageArmor /4 min-1 scaling, and the
 * statistical band the 1/(level+1) shape implies.
 */
class UnbreakingAcceptanceTest {

    /** A forced random: the queue feeds nextFloat/nextInt in order. */
    private static final class ForcedRandom extends Random {
        private final float[] floats;
        private final int[] ints;
        private int f;
        private int i;

        ForcedRandom(float[] floats, int[] ints) {
            this.floats = floats;
            this.ints = ints;
        }

        @Override
        public float nextFloat() {
            return floats[f++ % floats.length];
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            return ints[i++ % ints.length] % bound;
        }
    }

    private static ItemStack unbreakingTool(int level) {
        return ItemStack.of(BuiltinItems.DIAMOND_PICKAXE)
                .withEnchantment(Enchantments.UNBREAKING.id, level);
    }

    private static ItemStack unbreakingChest(int level) {
        return ItemStack.of(BuiltinItems.IRON_CHESTPLATE)
                .withEnchantment(Enchantments.UNBREAKING.id, level);
    }

    @Test
    void theReferenceGateShapeHolds() {
        // The armor's 60% early-false gate: a float below 0.6 fails the
        // whole roll WITHOUT consuming the int (the reference's branch
        // order — the stream advances identically).
        ForcedRandom gated = new ForcedRandom(new float[]{0.5F}, new int[]{0});
        assertFalse(EnchantmentHelper.unbreakingShouldReduce(true, 3, gated),
                "the 60% gate eats the roll: the wear lands");

        // Past the gate, the 1/(level+1) chance: nextInt(4) > 0 reduces.
        ForcedRandom past = new ForcedRandom(new float[]{0.9F}, new int[]{2});
        assertTrue(EnchantmentHelper.unbreakingShouldReduce(true, 3, past),
                "the gate passed and the int rolled: the unit rolled away");

        // A tool skips the float entirely.
        ForcedRandom tool = new ForcedRandom(new float[]{0.1F}, new int[]{0});
        assertFalse(EnchantmentHelper.unbreakingShouldReduce(false, 3, tool),
                "nextInt(4) == 0: the unit lands (the tool's 1/(L+1) wear chance)");
        ForcedRandom toolPast = new ForcedRandom(new float[]{0.1F}, new int[]{1});
        assertTrue(EnchantmentHelper.unbreakingShouldReduce(false, 3, toolPast),
                "nextInt(4) > 0: the unit rolled away");
    }

    @Test
    void theReductionWalkReadsTheLevelOnceAndReducesPerUnit() {
        // Three pending units, level 1 (nextInt(2)): the forced ints
        // [1, 0, 1] reduce two of them — exactly one unit lands.
        ForcedRandom forced = new ForcedRandom(new float[]{0.9F}, new int[]{1, 0, 1});
        int landing = EnchantmentHelper.unbreakingReducedWear(
                unbreakingTool(1), 3, false, forced);
        assertEquals(1, landing, "three units, two rolled away: one lands");

        // Level 0 never reduces (the reference's `i > 0` loop gate): the
        // full amount lands and the random is untouched.
        ForcedRandom untouched = new ForcedRandom(new float[]{0.9F}, new int[]{1});
        assertEquals(3, EnchantmentHelper.unbreakingReducedWear(
                ItemStack.of(BuiltinItems.DIAMOND_PICKAXE), 3, false, untouched),
                "no enchantment: the full wear lands");
        assertEquals(0, untouched.i, "level 0 consumed no rolls");
    }

    @Test
    void theArmorPieceRollsTheGatePerUnit() {
        // Two units on an armor piece, level 3: the forced floats [0.7, 0.7]
        // pass the 60% gate; the ints [1, 0] reduce one — one unit lands.
        ForcedRandom forced = new ForcedRandom(new float[]{0.7F, 0.7F}, new int[]{1, 0});
        int landing = EnchantmentHelper.unbreakingReducedWear(
                unbreakingChest(3), 2, true, forced);
        assertEquals(1, landing, "two units past the gate, one rolled away");

        // The gate consuming the roll per unit: floats [0.1, 0.1] fail both
        // units without touching the ints.
        ForcedRandom gated = new ForcedRandom(new float[]{0.1F}, new int[]{5});
        assertEquals(2, EnchantmentHelper.unbreakingReducedWear(
                unbreakingChest(3), 2, true, gated),
                "both units behind the 60% gate: the full wear lands");
        assertEquals(0, gated.i, "the short-circuit never consumed an int");
    }

    @Test
    void theStatisticalBandMatchesTheShape() {
        // Level 3 tools: the reduction rate is nextInt(4) > 0 = 75%. Over
        // 20,000 rolls the observed band stays tight around it.
        Random random = new Random(0x5EED);
        int reduced = 0;
        int rolls = 20_000;
        for (int i = 0; i < rolls; i++) {
            if (EnchantmentHelper.unbreakingShouldReduce(false, 3, random)) {
                reduced++;
            }
        }
        double rate = reduced / (double) rolls;
        assertTrue(rate > 0.72 && rate < 0.78,
                "level 3 reduces ~75% of units; saw " + (rate * 100) + "%");

        // Level 1: half the units roll away.
        reduced = 0;
        for (int i = 0; i < rolls; i++) {
            if (EnchantmentHelper.unbreakingShouldReduce(false, 1, random)) {
                reduced++;
            }
        }
        rate = reduced / (double) rolls;
        assertTrue(rate > 0.47 && rate < 0.53,
                "level 1 reduces ~50% of units; saw " + (rate * 100) + "%");
    }

    @Test
    void theHeldToolWearRidesTheReduction() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(unbreakingTool(3));

        // The int queue [0] forces nextInt(4) == 0: the unit lands.
        assertTrue(inventory.damageHeld(1, new ForcedRandom(new float[0], new int[]{0})),
                "the wear landed");
        assertEquals(1, inventory.held().damage());

        // The int queue [3] rolls the unit away: no change, no resync need.
        assertFalse(inventory.damageHeld(1, new ForcedRandom(new float[0], new int[]{3})),
                "every unit rolled away: the slot is untouched");
        assertEquals(1, inventory.held().damage(), "the durability never moved");

        // A plain tool always wears (level 0: no rolls, the full amount).
        PlayerInventory plain = new PlayerInventory();
        plain.pickUp(ItemStack.of(BuiltinItems.DIAMOND_PICKAXE));
        assertTrue(plain.damageHeld(1, new ForcedRandom(new float[0], new int[]{9})),
                "the unenchanted tool wears");
        assertEquals(1, plain.held().damage());
    }

    @Test
    void theArmorWearWalkRidesTheReferenceScaling() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.setArmor(1, unbreakingChest(3));

        // damage 4 → per piece 4/4 = 1 unit (the reference damageArmor).
        // The forced floats [0.9] pass the gate; ints [2] roll the unit away.
        assertFalse(inventory.wearArmor(4.0F, new ForcedRandom(new float[]{0.9F}, new int[]{2})),
                "the single unit rolled away: no slot changed");
        assertEquals(0, inventory.armorAt(1).damage());

        // damage 3 → the min-1 rule: 3/4 < 1 → 1 unit per piece.
        // floats [0.1] fail the gate: the unit lands.
        assertTrue(inventory.wearArmor(3.0F, new ForcedRandom(new float[]{0.1F}, new int[]{5})),
                "the min-1 unit landed");
        assertEquals(1, inventory.armorAt(1).damage());

        // damage 10 → (int)(10/4) = 2 units per piece: floats [0.9, 0.9]
        // pass, ints [1, 0] reduce one — the piece takes exactly 1.
        assertTrue(inventory.wearArmor(10.0F, new ForcedRandom(new float[]{0.9F}, new int[]{1, 0})),
                "the surviving unit landed");
        assertEquals(2, inventory.armorAt(1).damage(), "1 previous + 1 surviving = 2");
    }

    @Test
    void aBreakingPieceBreaksThroughTheReductionWalk() {
        PlayerInventory inventory = new PlayerInventory();
        ItemStack dying = ItemStack.of(BuiltinItems.IRON_CHESTPLATE);
        inventory.setArmor(1, dying.withDamage(dying.type().maxDurability() - 1));

        // The last unit lands (float 0.1 fails the gate): the piece breaks.
        assertTrue(inventory.wearArmor(4.0F, new ForcedRandom(new float[]{0.1F}, new int[]{5})),
                "the breaking wear changed the slot");
        assertTrue(inventory.armorAt(1).isEmpty(), "the piece left the slot");

        // And the roll can spare it: a fresh dying enchanted piece rolls
        // the unit away.
        PlayerInventory spared = new PlayerInventory();
        spared.setArmor(1, unbreakingChest(3).withDamage(
                unbreakingChest(3).type().maxDurability() - 1));
        assertFalse(spared.wearArmor(4.0F, new ForcedRandom(new float[]{0.9F}, new int[]{3})),
                "the rolled-away unit never breaks the piece");
        assertFalse(spared.armorAt(1).isEmpty(), "the piece survived");
    }

    @Test
    void theThornsTargetedWearRidesTheSameReduction() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.setArmor(1, unbreakingChest(3));

        // floats [0.1] fail the gate: the thorns wear lands.
        assertTrue(inventory.wearArmorStack(1, 1, new ForcedRandom(new float[]{0.1F}, new int[]{5})));
        assertEquals(1, inventory.armorAt(1).damage());

        // floats [0.9] pass; int [1] rolls the unit away: untouched.
        assertFalse(inventory.wearArmorStack(1, 1, new ForcedRandom(new float[]{0.9F}, new int[]{1})));
        assertEquals(1, inventory.armorAt(1).damage(), "the thorns visit wore nothing");
    }
}
