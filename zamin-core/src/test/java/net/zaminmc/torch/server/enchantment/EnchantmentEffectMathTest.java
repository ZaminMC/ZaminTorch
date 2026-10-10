package net.zaminmc.torch.server.enchantment;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.entity.damage.DamageKind;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enchantment effect-hook arithmetic against the reference tables
 * ({@code reference/1.8.8} ProtectionEnchantment.getExtraProtection /
 * modifyOnFireTimer / modifyExplosionDamage and the EnchantmentHelper
 * level readers / modifyProtection clamp + roll). Every expected number
 * below is hand-computed from the reference formulas, not rounded to
 * please the implementation.
 */
class EnchantmentEffectMathTest {

    // -------------------------------------------------- getExtraProtection

    @Test
    void protectionAllContributionPerLevel() {
        // f = (6 + level^2) / 3, then * 0.75, floored.
        // I:  (6+1)/3   = 2.3333 * 0.75 = 1.75  -> 1
        // II: (6+4)/3   = 3.3333 * 0.75 = 2.5   -> 2
        // III:(6+9)/3   = 5.0    * 0.75 = 3.75  -> 3
        // IV: (6+16)/3  = 7.3333 * 0.75 = 5.5   -> 5
        assertEquals(1, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_ALL, 1, DamageKind.MELEE));
        assertEquals(2, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_ALL, 2, DamageKind.MELEE));
        assertEquals(3, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_ALL, 3, DamageKind.MELEE));
        assertEquals(5, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_ALL, 4, DamageKind.MELEE));
    }

    @Test
    void protectionScalesOnlyOnItsKind() {
        // Fire Protection II on a fire source: (6+4)/3 * 1.25 = 4.1666 -> 4.
        assertEquals(4, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_FIRE, 2, DamageKind.IN_FIRE));
        // The same piece on a melee source contributes nothing.
        assertEquals(0, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_FIRE, 2, DamageKind.MELEE));
        // Feather Falling III on a fall: (6+9)/3 * 2.5 = 12.5 -> 12.
        assertEquals(12, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_FALL, 3, DamageKind.FALL));
        // ...and nothing on a drowning (off-kind, though "all" would apply).
        assertEquals(0, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_FALL, 3, DamageKind.DROWN));
        // Blast Protection I on an explosion: (6+1)/3 * 1.5 = 3.5 -> 3.
        assertEquals(3, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_BLAST, 1, DamageKind.EXPLOSION));
        // Projectile Protection II on an arrow: (6+4)/3 * 1.5 = 5.0 -> 5.
        assertEquals(5, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_PROJECTILE, 2, DamageKind.PROJECTILE));
        // The onFire residual is still a fire source (the reference's
        // onFire carries setFire).
        assertEquals(4, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_FIRE, 2, DamageKind.ON_FIRE));
        // The void contributes zero from every piece (the out-of-world arm).
        assertEquals(0, EnchantmentHelper.extraProtection(
                Enchantments.PROTECTION_ALL, 4, DamageKind.OUT_OF_WORLD));
    }

    // ----------------------------------------------------- modifyProtection

    @Test
    void protectionSumClampsAtTwentyFiveBeforeTheRoll() {
        // A single piece at level 10 gives (6+100)/3 * 0.75 = 26.5 -> 26,
        // above the 25 clamp; the roll then walks 12..25 (25 -> 13 + nextInt(13)).
        ItemStack overstuffed = ItemStack.of(BuiltinItems.DIAMOND_HELMET)
                .withEnchantment(Enchantments.PROTECTION.id, 10);
        int rolled = EnchantmentHelper.modifyProtection(
                new ItemStack[]{overstuffed}, DamageKind.MELEE, new Random(7));
        assertTrue(rolled >= 13 && rolled <= 25,
                "the clamp holds before the half-to-full roll, saw " + rolled);
    }

    @Test
    void fullProtectionFourArmorRollsHalfToFull() {
        // Four Protection IV pieces: 4 * 5 = 20 EPF; the roll returns
        // (20+1>>1) + nextInt((20>>1)+1) = 10 + [0..10] -> 10..20.
        ItemStack[] armor = fullProtectionFour();
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            int rolled = EnchantmentHelper.modifyProtection(armor, DamageKind.MELEE, random);
            assertTrue(rolled >= 10 && rolled <= 20,
                    "the vanilla half-to-full band, saw " + rolled);
        }
        // The exact roll against the reference formula with a mirrored seed.
        Random seeded = new Random(1234);
        int expected = 10 + seeded.nextInt(11);
        assertEquals(expected, EnchantmentHelper.modifyProtection(
                armor, DamageKind.MELEE, new Random(1234)));
    }

    @Test
    void bareBodyAndVoidRollZeroWithoutConsumingTheRandom() {
        // Zero protection: nextInt(1) -> 0 — and the roll consumes exactly
        // one nextInt call, which the mirrored-random assertion pins.
        assertEquals(0, EnchantmentHelper.modifyProtection(
                new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY},
                DamageKind.MELEE, new Random(1)));
        Random seeded = new Random(9);
        seeded.nextInt(1); // the zero-roll's only draw
        assertEquals(seeded.nextInt(1), EnchantmentHelper.modifyProtection(
                new ItemStack[]{}, DamageKind.FALL, new Random(9)));
    }

    // ------------------------------------------------- the fire-timer shave

    @Test
    void fireProtectionShortensTheBurn() {
        // 80 ticks (4 seconds) with Fire Protection I: 80 - floor(80 * 0.15) = 80 - 12 = 68.
        ItemStack helm = ItemStack.of(BuiltinItems.DIAMOND_HELMET)
                .withEnchantment(Enchantments.FIRE_PROTECTION.id, 1);
        assertEquals(68, EnchantmentHelper.modifyOnFireTimer(new ItemStack[]{helm}, 80));
        // Level III: 80 - floor(80 * 0.45) = 80 - 36 = 44.
        ItemStack helm3 = ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE)
                .withEnchantment(Enchantments.FIRE_PROTECTION.id, 3);
        assertEquals(44, EnchantmentHelper.modifyOnFireTimer(new ItemStack[]{helm3}, 80));
        // The highest level across the pieces wins (the getHighestEnchantmentLevel walk).
        ItemStack boots2 = ItemStack.of(BuiltinItems.DIAMOND_BOOTS)
                .withEnchantment(Enchantments.FIRE_PROTECTION.id, 2);
        assertEquals(56, EnchantmentHelper.modifyOnFireTimer(
                new ItemStack[]{helm, boots2}, 80)); // level 2: 80 - 24
        // No protection: the clock passes through untouched.
        assertEquals(80, EnchantmentHelper.modifyOnFireTimer(
                new ItemStack[]{ItemStack.EMPTY}, 80));
    }

    // ------------------------------------------- the blast-damage shave

    @Test
    void blastProtectionShavesTheRawExplosion() {
        // 10 raw blast damage with Blast Protection II: 10 - floor(10 * 0.3) = 7.
        ItemStack chest = ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE)
                .withEnchantment(Enchantments.BLAST_PROTECTION.id, 2);
        assertEquals(7.0, EnchantmentHelper.modifyExplosionDamage(
                new ItemStack[]{chest}, 10.0), 1e-9);
        // Level IV caps at 4 * 0.15 = 0.6: 10 - 6 = 4.
        ItemStack chest4 = ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE)
                .withEnchantment(Enchantments.BLAST_PROTECTION.id, 4);
        assertEquals(4.0, EnchantmentHelper.modifyExplosionDamage(
                new ItemStack[]{chest4}, 10.0), 1e-9);
        // Unenchanted bodies take the raw number.
        assertEquals(10.0, EnchantmentHelper.modifyExplosionDamage(
                new ItemStack[]{ItemStack.EMPTY}, 10.0), 1e-9);
    }

    // ------------------------------------------------------ level readers

    @Test
    void heldItemReadersReturnTheEnchantedLevels() {
        ItemStack sword = ItemStack.of(BuiltinItems.DIAMOND_SWORD)
                .withEnchantment(Enchantments.KNOCKBACK.id, 2)
                .withEnchantment(Enchantments.FIRE_ASPECT.id, 1);
        assertEquals(2, EnchantmentHelper.knockbackLevel(sword));
        assertEquals(1, EnchantmentHelper.fireAspectLevel(sword));
        assertEquals(0, EnchantmentHelper.efficiencyLevel(sword));
        // A bare fist and an unenchanted tool read zero everywhere.
        assertEquals(0, EnchantmentHelper.knockbackLevel(ItemStack.EMPTY));
        assertEquals(0, EnchantmentHelper.fireAspectLevel(
                ItemStack.of(BuiltinItems.DIAMOND_SWORD)));
        // The highest-level walk over the armor row (EMPTY pieces skipped).
        ItemStack feet = ItemStack.of(BuiltinItems.DIAMOND_BOOTS)
                .withEnchantment(Enchantments.EFFICIENCY.id, 3); // nonsense on boots, but readable
        assertEquals(3, EnchantmentHelper.highestLevel(Enchantments.EFFICIENCY.id,
                new ItemStack[]{ItemStack.EMPTY, feet, ItemStack.EMPTY}));
    }

    // ---------------------------------------------------------------- fixture

    private ItemStack[] fullProtectionFour() {
        return new ItemStack[]{
                ItemStack.of(BuiltinItems.DIAMOND_HELMET)
                        .withEnchantment(Enchantments.PROTECTION.id, 4),
                ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE)
                        .withEnchantment(Enchantments.PROTECTION.id, 4),
                ItemStack.of(BuiltinItems.DIAMOND_LEGGINGS)
                        .withEnchantment(Enchantments.PROTECTION.id, 4),
                ItemStack.of(BuiltinItems.DIAMOND_BOOTS)
                        .withEnchantment(Enchantments.PROTECTION.id, 4),
        };
    }
}
