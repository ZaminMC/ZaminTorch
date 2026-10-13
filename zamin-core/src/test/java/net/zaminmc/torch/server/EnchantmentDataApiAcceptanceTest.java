package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.enchantment.EnchantmentRegistry;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enchanting data predicates (§27.3's registry wave — the former
 * engine-side omissions): {@link Server#enchantments()}'s canEnchant /
 * isCompatible / enchantabilityOf / the XP curves — every table the
 * reference's {@code Enchantment} subclasses carry, read through the
 * public registry from a foreign thread (the §27.6 any-thread pure-read
 * contract).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnchantmentDataApiAcceptanceTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    @Test
    void theCanEnchantPredicateCarriesTheReferenceCategoryWalk() throws Exception {
        EngineServer engine = boot();
        Server server = engine;
        try {
            EnchantmentRegistry enchantments = server.enchantments();

            // The damage family: the sword accepts, the axe arm accepts
            // (DamageEnchantment line 71: instanceof AxeItem || super).
            assertTrue(enchantments.canEnchant(16, ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)),
                    "sharpness accepts the sword");
            assertTrue(enchantments.canEnchant(16, ItemStack.of(BuiltinItems.DIAMOND_AXE, 1)),
                    "sharpness accepts the axe (the reference's own axe arm)");
            assertFalse(enchantments.canEnchant(16, ItemStack.of(BuiltinItems.IRON_CHESTPLATE, 1)),
                    "sharpness refuses the chestplate");

            // The armor family: protection accepts the chestplate, refuses
            // the sword.
            assertTrue(enchantments.canEnchant(0, ItemStack.of(BuiltinItems.IRON_CHESTPLATE, 1)),
                    "protection accepts the chestplate");
            assertFalse(enchantments.canEnchant(0, ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)),
                    "protection refuses the sword");

            // The digger family: efficiency accepts the pickaxe, refuses
            // the sword — and the reference's own shears arm accepts the
            // shears (EfficiencyEnchantment line 24: SHEARS || super).
            assertTrue(enchantments.canEnchant(32, ItemStack.of(BuiltinItems.DIAMOND_PICKAXE, 1)),
                    "efficiency accepts the pickaxe");
            assertFalse(enchantments.canEnchant(32, ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)),
                    "efficiency refuses the sword");
            assertTrue(enchantments.canEnchant(32, ItemStack.of(BuiltinItems.SHEARS, 1)),
                    "efficiency accepts the shears (the reference's own arm)");

            // The book's take-everything rule is the table's own flag, not
            // the predicate: the plain book answers false exactly like the
            // reference's category walk (EnchantmentHelper line 329's
            // `flag` arm, not canEnchant).
            assertFalse(enchantments.canEnchant(16, ItemStack.of(BuiltinItems.BOOK, 1)),
                    "the plain book carries no category");

            // The tolerant arms: an unknown id answers false; an empty
            // stack answers false.
            assertFalse(enchantments.canEnchant(999, ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)),
                    "the unknown id answers false");
            assertFalse(enchantments.canEnchant(16, ItemStack.EMPTY),
                    "the empty stack answers false");

            // The off-lane read (the §27.6 any-thread contract).
            AtomicBoolean foreign = new AtomicBoolean(false);
            Thread stranger = new Thread(() -> foreign.set(
                    enchantments.canEnchant(16, ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1))));
            stranger.start();
            stranger.join();
            assertTrue(foreign.get(), "the off-lane predicate answers");
        } finally {
            engine.shutdown(null);
        }
    }

    @Test
    void theCompatibilityTableMirrorsTheFamilyConflicts() throws Exception {
        EngineServer engine = boot();
        Server server = engine;
        try {
            EnchantmentRegistry enchantments = server.enchantments();

            // The damage trio is mutually exclusive.
            assertFalse(enchantments.isCompatible(16, 17),
                    "sharpness excludes smite");
            assertFalse(enchantments.isCompatible(17, 18),
                    "smite excludes bane of arthropods");
            // Different families pair.
            assertTrue(enchantments.isCompatible(16, 19),
                    "sharpness pairs with knockback");
            assertTrue(enchantments.isCompatible(19, 20),
                    "knockback pairs with fire aspect");

            // The protections exclude each other — except feather
            // falling (subtype 2) pairs with any other protection kind.
            assertFalse(enchantments.isCompatible(0, 1),
                    "protection excludes fire protection");
            assertTrue(enchantments.isCompatible(0, 2),
                    "protection pairs with feather falling");
            assertTrue(enchantments.isCompatible(1, 2),
                    "fire protection pairs with feather falling");
            assertTrue(enchantments.isCompatible(2, 3),
                    "feather falling pairs with blast protection");
            assertFalse(enchantments.isCompatible(1, 3),
                    "fire protection excludes blast protection");

            // The tolerance arm: an unknown id answers true (no known
            // conflict).
            assertTrue(enchantments.isCompatible(999, 16),
                    "the unknown id carries no known conflict");

            // The off-lane read.
            AtomicBoolean foreign = new AtomicBoolean(true);
            Thread stranger = new Thread(() -> foreign.set(
                    enchantments.isCompatible(0, 1)));
            stranger.start();
            stranger.join();
            assertFalse(foreign.get(), "the off-lane conflict answers");
        } finally {
            engine.shutdown(null);
        }
    }

    @Test
    void theEnchantabilityTiersAndXpCurvesReadThroughTheRegistry() throws Exception {
        EngineServer engine = boot();
        Server server = engine;
        try {
            EnchantmentRegistry enchantments = server.enchantments();

            // The tool material tiers (the reference's ToolMaterial
            // enchantability rows: wood 15, stone 5, iron 14, diamond
            // 10, gold 22).
            assertEquals(15, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.WOODEN_SWORD, 1)), "the wood tier");
            assertEquals(5, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.STONE_PICKAXE, 1)), "the stone tier");
            assertEquals(14, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.IRON_SWORD, 1)), "the iron tier");
            assertEquals(10, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)), "the diamond tier");
            assertEquals(22, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.GOLDEN_PICKAXE, 1)), "the gold tier");

            // The armor tiers (leather 15, chain 12, iron 9, gold 25,
            // diamond 10) — ArmorItem's own getEnchantability.
            assertEquals(15, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.LEATHER_HELMET, 1)), "the leather tier");
            assertEquals(9, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.IRON_CHESTPLATE, 1)), "the iron armor tier");
            assertEquals(25, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.GOLDEN_CHESTPLATE, 1)), "the gold armor tier");
            assertEquals(10, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1)), "the diamond armor tier");

            // The quirks: the shears' historical 0 (the reference's
            // silence — the table refuses them in 1.8.8); the book's 1;
            // a plain block item's 0.
            assertEquals(0, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.SHEARS, 1)), "the shears' zero");
            assertEquals(1, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.BOOK, 1)), "the book's one");
            assertEquals(0, enchantments.enchantabilityOf(
                    ItemStack.of(BuiltinItems.COBBLESTONE, 1)), "the plain block's zero");

            // The XP curves (the reference's per-family tables):
            // Sharpness (DamageEnchantment MIN[0]=1, MOD[0]=11, MAX=20):
            // level 1 the window 1..21, level 2 the window 12..32.
            assertEquals(OptionalInt.of(1), enchantments.minXp(16, 1),
                    "sharpness level 1's minimum");
            assertEquals(OptionalInt.of(21), enchantments.maxXp(16, 1),
                    "sharpness level 1's maximum");
            assertEquals(OptionalInt.of(12), enchantments.minXp(16, 2),
                    "sharpness level 2's minimum");
            assertEquals(OptionalInt.of(32), enchantments.maxXp(16, 2),
                    "sharpness level 2's maximum");
            // Protection (MIN[0]=1, MAX[0]=20): the window 1..21 at level 1.
            assertEquals(OptionalInt.of(1), enchantments.minXp(0, 1),
                    "protection level 1's minimum");
            assertEquals(OptionalInt.of(21), enchantments.maxXp(0, 1),
                    "protection level 1's maximum");
            // Feather falling (subtype 2: MIN=5, MAX=10): 5..15 at level 1.
            assertEquals(OptionalInt.of(5), enchantments.minXp(2, 1),
                    "feather falling level 1's minimum");
            assertEquals(OptionalInt.of(15), enchantments.maxXp(2, 1),
                    "feather falling level 1's maximum");
            // Efficiency (min 1 + 10*(l-1); max the BASE min + 50 — the
            // reference's own super.getMinXpRequirement quirk): level 1
            // the window 1..61, level 5 the window 41..101.
            assertEquals(OptionalInt.of(1), enchantments.minXp(32, 1),
                    "efficiency level 1's minimum");
            assertEquals(OptionalInt.of(61), enchantments.maxXp(32, 1),
                    "efficiency level 1's maximum (the BASE curve + 50)");
            assertEquals(OptionalInt.of(41), enchantments.minXp(32, 5),
                    "efficiency level 5's minimum");
            assertEquals(OptionalInt.of(101), enchantments.maxXp(32, 5),
                    "efficiency level 5's maximum (the BASE curve's 51 + 50)");

            // The tolerant arm: an unknown id answers empty.
            assertEquals(OptionalInt.empty(), enchantments.minXp(999, 1),
                    "the unknown id's minimum answers empty");
            assertEquals(OptionalInt.empty(), enchantments.maxXp(999, 1),
                    "the unknown id's maximum answers empty");

            // The off-lane reads (the §27.6 any-thread contract).
            AtomicInteger foreignTier = new AtomicInteger(-1);
            Thread stranger = new Thread(() -> foreignTier.set(
                    enchantments.enchantabilityOf(ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1))));
            stranger.start();
            stranger.join();
            assertEquals(10, foreignTier.get(), "the off-lane tier read answers");
        } finally {
            engine.shutdown(null);
        }
    }
}
