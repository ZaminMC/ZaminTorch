package net.zaminmc.torch.server.enchantment;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enchantment registry and the offer math against the reference tables
 * (reference/1.8.8/enchantment): the 26-id registry with its rarity weights
 * and level caps, the exact XP curves of the two table-driven families, the
 * table-slot ladders with their seeded rolls, the level-window pool, and the
 * compatibility conflicts (protection pairs, damage exclusivity, fortune vs
 * silk touch). Numbers are transcribed in the test from the reference
 * source, not from the port — a transcription typo in either shows up.
 */
class EnchantmentTest {

    // ------------------------------------------------ the registry

    @Test
    void theRegistryCarriesTheHistorical26WithTheirWeights() {
        assertEquals(25, Enchantments.all().size(), "the 1.8.8 enchantment count");
        for (Enchantments.Entry entry : Enchantments.all()) {
            assertNotNull(Enchantments.byId(entry.id), "byId resolves every entry");
        }
        assertEquals(10, Enchantments.PROTECTION.weight, "common weight");
        assertEquals(5, Enchantments.FIRE_PROTECTION.weight, "uncommon weight");
        assertEquals(2, Enchantments.BLAST_PROTECTION.weight, "rare weight");
        assertEquals(1, Enchantments.THORNS.weight, "very rare weight");
        assertEquals(5, Enchantments.SHARPNESS.maxLevel, "sharpness caps at V");
        assertEquals(4, Enchantments.PROTECTION.maxLevel, "protection caps at IV");
        assertEquals(3, Enchantments.UNBREAKING.maxLevel, "unbreaking caps at III");
        assertEquals(1, Enchantments.SILK_TOUCH.maxLevel, "silk touch caps at I");
        assertEquals(1, Enchantments.INFINITY.maxLevel, "infinity caps at I");
    }

    @Test
    void theXpCurvesMatchTheReferenceTables() {
        // DamageEnchantment: MIN {1,5,5}, MOD {11,8,8}, MAX {20,20,20}.
        assertEquals(1, Enchantments.SHARPNESS.minXP(1), "sharpness I floor");
        assertEquals(21, Enchantments.SHARPNESS.maxXP(1), "sharpness I ceiling");
        assertEquals(34, Enchantments.SHARPNESS.minXP(4), "sharpness IV floor (1 + 3*11)");
        assertEquals(5, Enchantments.SMITE.minXP(1), "smite I floor");
        assertEquals(13, Enchantments.SMITE.minXP(2), "smite II floor (5 + 8)");
        // ProtectionEnchantment: MIN {1,10,5,5,3}, MOD {11,8,6,8,6}, MAX {20,12,10,12,15}.
        assertEquals(34, Enchantments.PROTECTION.minXP(4), "protection IV floor");
        assertEquals(54, Enchantments.PROTECTION.maxXP(4), "protection IV ceiling");
        assertEquals(10, Enchantments.FIRE_PROTECTION.minXP(1), "fire protection I floor");
        assertEquals(22, Enchantments.FIRE_PROTECTION.maxXP(1), "fire protection I ceiling");
        // The singles.
        assertEquals(15, Enchantments.SILK_TOUCH.minXP(1), "silk touch floor");
        assertEquals(20, Enchantments.FLAME.minXP(1), "flame floor");
        assertEquals(50, Enchantments.FLAME.maxXP(1), "flame ceiling");
        assertEquals(12, Enchantments.PUNCH.minXP(1), "punch I floor");
        assertEquals(13, Enchantments.UNBREAKING.minXP(2), "unbreaking II floor (5 + 8)");
    }

    @Test
    void theCompatibilityConflictsMatchTheReference() {
        // Protection kinds exclude each other except feather-falling pairs.
        assertTrue(Enchantments.PROTECTION.isCompatible(Enchantments.FEATHER_FALLING),
                "protection stacks with feather-falling");
        assertFalse(Enchantments.PROTECTION.isCompatible(Enchantments.FIRE_PROTECTION),
                "protection conflicts with fire protection");
        assertTrue(Enchantments.FEATHER_FALLING.isCompatible(Enchantments.PROTECTION),
                "feather-falling stacks with protection (the type-2 arm)");
        assertFalse(Enchantments.PROTECTION.isCompatible(Enchantments.PROTECTION),
                "an enchantment conflicts with itself");
        // The damage family is mutually exclusive.
        assertFalse(Enchantments.SHARPNESS.isCompatible(Enchantments.SMITE));
        assertFalse(Enchantments.SMITE.isCompatible(Enchantments.BANE_OF_ARTHROPODS));
        // Fortune and silk touch conflict; fortune and efficiency do not.
        assertFalse(Enchantments.SILK_TOUCH.isCompatible(Enchantments.FORTUNE));
        assertTrue(Enchantments.EFFICIENCY.isCompatible(Enchantments.FORTUNE));
        // Unrelated families never conflict.
        assertTrue(Enchantments.SHARPNESS.isCompatible(Enchantments.FIRE_ASPECT));
        assertTrue(Enchantments.POWER.isCompatible(Enchantments.FLAME));
    }

    @Test
    void theEnchantabilityTableMatchesTheReferenceTiers() {
        assertEquals(15, Enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.WOODEN_PICKAXE)));
        assertEquals(5, Enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.STONE_PICKAXE)));
        assertEquals(14, Enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.IRON_PICKAXE)));
        assertEquals(10, Enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.DIAMOND_PICKAXE)));
        assertEquals(22, Enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.GOLDEN_PICKAXE)));
        assertEquals(25, Enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.GOLDEN_CHESTPLATE)));
        assertEquals(9, Enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.IRON_CHESTPLATE)));
        assertEquals(15, Enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.LEATHER_BOOTS)));
        assertEquals(1, Enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.BOOK)));
        assertEquals(1, Enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.BOW)));
        assertEquals(0, Enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.SHEARS)),
                "the shears carry no override — the reference's silence is the value");
    }

    // ------------------------------------------------ the table ladders

    @Test
    void theSlotLadderRollsMatchTheReferenceFormula() {
        // The reference formula re-derived here: j = nextInt(8) + 1
        // + (max >> 1) + nextInt(max + 1), then the per-slot curve. Every
        // slot call consumes its own two rolls in the same order.
        Random random = new Random(42);
        ItemStack diamondSword = ItemStack.of(BuiltinItems.DIAMOND_SWORD);
        int j0 = random.nextInt(8) + 1 + (15 >> 1) + random.nextInt(15 + 1);
        int expectedSlot0 = Math.max(j0 / 3, 1);
        int j1 = random.nextInt(8) + 1 + (15 >> 1) + random.nextInt(15 + 1);
        int expectedSlot1 = j1 * 2 / 3 + 1;
        int j2 = random.nextInt(8) + 1 + (15 >> 1) + random.nextInt(15 + 1);
        int expectedSlot2 = Math.max(j2, 30);

        Random port = new Random(42);
        assertEquals(expectedSlot0,
                EnchantmentHelper.requiredXpLevel(port, 0, 15, diamondSword),
                "slot 0 takes the cheap third");
        assertEquals(expectedSlot1,
                EnchantmentHelper.requiredXpLevel(port, 1, 15, diamondSword),
                "slot 1 takes the two-thirds-plus-one");
        assertEquals(expectedSlot2,
                EnchantmentHelper.requiredXpLevel(port, 2, 15, diamondSword),
                "slot 2 takes the full roll or double the cap");
    }

    @Test
    void theLadderIsEnchantabilityGated() {
        ItemStack shears = ItemStack.of(BuiltinItems.SHEARS);
        Random random = new Random(1);
        assertEquals(0, EnchantmentHelper.requiredXpLevel(random, 0, 15, shears),
                "enchantability 0 offers nothing");
        assertNull(EnchantmentHelper.buildOffers(random, shears, 30),
                "the shears' null enchantability blocks the table");
    }

    @Test
    void theLevelWindowPoolsTheRightOffers() {
        ItemStack diamondSword = ItemStack.of(BuiltinItems.DIAMOND_SWORD);
        // At xp 27 the sword's windows (transcribed): sharpness I (1-21),
        // II (12-32), III (23-43); knockback I (5-55) and II (25-75);
        // fire aspect I (10-60) — 27 sits below II's floor of 30; looting
        // I (15-65) and II (24-74).
        Map<Integer, EnchantmentHelper.Offer> pool =
                EnchantmentHelper.availableOffers(27, diamondSword);
        assertNotNull(pool);
        // The reference's put walk leaves the LAST qualifying level per id:
        // sharpness III (23-43) overwrites II (12-32) at xp 27.
        assertEquals(3, pool.get(Enchantments.SHARPNESS.id).level());
        assertEquals(2, pool.get(Enchantments.KNOCKBACK.id).level(),
                "knockback I (5-55) and II (25-75) both cover 27");
        assertEquals(1, pool.get(Enchantments.FIRE_ASPECT.id).level(),
                "fire aspect II's floor of 30 sits above 27");
        assertEquals(2, pool.get(Enchantments.LOOTING.id).level(),
                "looting I (15-65) and II (24-74) both cover 27");
        assertEquals(3, pool.get(Enchantments.UNBREAKING.id).level(),
                "unbreaking rides any durable kind — swords included (21-29 covers 27)");
        // At xp 43 sharpness IV's window (34-54) joins and wins the walk.
        Map<Integer, EnchantmentHelper.Offer> poolAt43 =
                EnchantmentHelper.availableOffers(43, diamondSword);
        assertEquals(4, poolAt43.get(Enchantments.SHARPNESS.id).level(),
                "the walk leaves the last qualifying level");
    }

    @Test
    void theBookTakesEveryCategory() {
        ItemStack book = ItemStack.of(BuiltinItems.BOOK);
        Map<Integer, EnchantmentHelper.Offer> pool =
                EnchantmentHelper.availableOffers(20, book);
        assertNotNull(pool);
        assertTrue(pool.containsKey(Enchantments.PROTECTION.id), "armor offers reach the book");
        assertTrue(pool.containsKey(Enchantments.POWER.id), "bow offers reach the book");
        assertTrue(pool.containsKey(Enchantments.EFFICIENCY.id), "digger offers reach the book");
    }

    @Test
    void theOfferBuildIsSeededAndCompatible() {
        ItemStack diamondSword = ItemStack.of(BuiltinItems.DIAMOND_SWORD);
        Random random = new Random(7);
        List<EnchantmentHelper.Offer> offers =
                EnchantmentHelper.buildOffers(random, diamondSword, 30);
        assertNotNull(offers, "a level-30 diamond sword always offers something");
        assertFalse(offers.isEmpty());
        // The picks are pairwise compatible (the cull's contract).
        for (int i = 0; i < offers.size(); i++) {
            for (int j = i + 1; j < offers.size(); j++) {
                assertTrue(offers.get(i).enchantment()
                                .isCompatible(offers.get(j).enchantment()),
                        "the cull removed every conflict: "
                                + offers.get(i).enchantment().key + " vs "
                                + offers.get(j).enchantment().key);
            }
        }
        // Determinism: the same seed rebuilds the same offers.
        List<EnchantmentHelper.Offer> again =
                EnchantmentHelper.buildOffers(new Random(7),
                        ItemStack.of(BuiltinItems.DIAMOND_SWORD), 30);
        assertEquals(offers.size(), again.size(), "the same seed, the same picks");
        for (int i = 0; i < offers.size(); i++) {
            assertEquals(offers.get(i).enchantment().id, again.get(i).enchantment().id);
            assertEquals(offers.get(i).level(), again.get(i).level());
        }
    }

    @Test
    void theRandomEnchantmentLandsOnTheStack() {
        ItemStack sword = ItemStack.of(BuiltinItems.DIAMOND_SWORD);
        ItemStack enchanted = EnchantmentHelper.addRandomEnchantment(
                new Random(11), sword, 30);
        assertTrue(enchanted != sword, "a new value when the enchant lands");
        assertTrue(enchanted.enchantments() != null && !enchanted.enchantments().isEmpty(),
                "the enchant map rode the stack");
        // The levels read back per id (the enchantmentLevel accessor).
        for (Map.Entry<Integer, Integer> ench : enchanted.enchantments().entrySet()) {
            assertNotNull(Enchantments.byId(ench.getKey()), "registered ids only");
            assertTrue(ench.getValue() >= 1, "levels are positive");
        }
        // Identity: a differently enchanted stack never merges.
        assertFalse(ItemStack.mergeable(enchanted, sword), "enchanted vs plain");
        ItemStack same = enchanted.withCount(1);
        assertTrue(ItemStack.mergeable(enchanted, same), "equal NBT merges");
        ItemStack different = enchanted.withEnchantment(Enchantments.FIRE_ASPECT.id, 2);
        assertFalse(ItemStack.mergeable(enchanted, different), "different NBT never merges");
        assertEquals(0, sword.enchantmentLevel(Enchantments.SHARPNESS.id),
                "the plain stack reads 0");
        // Level 0 removes (the withEnchantment rule).
        ItemStack cleared = enchanted.withEnchantment(
                enchanted.enchantments().keySet().iterator().next(), 0);
        assertEquals(enchanted.enchantments().size() - 1, cleared.enchantments().size(),
                "the removal took one enchantment off");
    }

    @Test
    void theDamageMathMatchesTheReferenceTables() {
        // Sharpness: level * 1.25; smite vs undead: level * 2.5.
        ItemStack sword = ItemStack.of(BuiltinItems.DIAMOND_SWORD)
                .withEnchantment(Enchantments.SHARPNESS.id, 3);
        assertEquals(3.75f, EnchantmentHelper.modifyDamage(sword, 0), 0.0f,
                "sharpness III on a plain target");
        ItemStack smiter = ItemStack.of(BuiltinItems.DIAMOND_SWORD)
                .withEnchantment(Enchantments.SMITE.id, 4);
        assertEquals(10.0f, EnchantmentHelper.modifyDamage(smiter, 1), 0.0f,
                "smite IV on an undead body");
        assertEquals(0.0f, EnchantmentHelper.modifyDamage(smiter, 0), 0.0f,
                "smite on a non-undead body");
        // Multi-enchant stacks sum (the DAMAGE_MODIFIER walk).
        Map<Integer, Integer> both = new TreeMap<>();
        both.put(Enchantments.SHARPNESS.id, 2);
        both.put(Enchantments.SMITE.id, 3);
        ItemStack hybrid = ItemStack.of(BuiltinItems.DIAMOND_SWORD)
                .withEnchantments(both);
        // The DAMAGE family is exclusive in offers, but the math walks
        // whatever the stack carries (the anvil can build such stacks).
        assertEquals(2.5f + 7.5f, EnchantmentHelper.modifyDamage(hybrid, 1), 0.0f,
                "the walk sums every damage entry");
    }
}
