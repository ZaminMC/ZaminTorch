package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry-family API wave (§27.3's read surfaces over the frozen
 * sets): {@link Server#recipes()} — the recipe set with the reference's
 * own matching semantics, and {@link Server#enchantments()} — the
 * enchantment table. Every read is pure; the tests exercise both from a
 * FOREIGN thread (the §27.6 any-thread contract).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistryApiAcceptanceTest {

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
    void theRecipeRegistryReadsAndMatchesFromAnyThread() throws Exception {
        EngineServer engine = boot();
        Server server = engine; // the public surface under test
        try {
            var recipes = server.recipes();
            assertTrue(recipes.all().size() >= 5, "the builtin set carries its rows");

            // The vanilla match order: shaped rows first, larger footprints
            // first within the band.
            boolean sawShapeless = false;
            for (var view : recipes.all()) {
                if (!view.shaped()) {
                    sawShapeless = true;
                } else if (sawShapeless) {
                    throw new AssertionError("a shaped row after a shapeless one");
                }
            }

            // The off-lane reads (the §27.6 any-thread contract): a foreign
            // thread both enumerates and matches.
            AtomicReference<Optional<net.zaminmc.torch.crafting.Recipes.RecipeView>>
                    matched = new AtomicReference<>(Optional.empty());
            Thread foreign = new Thread(() -> matched.set(
                    recipes.match(new ItemStack[] {
                            ItemStack.of(BuiltinItems.OAK_LOG, 1),
                            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY })));
            foreign.start();
            foreign.join();

            // The log-to-planks row: the 2x2 grid of one log crafts four
            // oak planks (the reference's own first row).
            var view = matched.get().orElseThrow(
                    () -> new AssertionError("the single log crafts"));
            assertEquals(Identifier.parse("minecraft:oak_planks"), view.result(),
                    "the planks recipe");
            assertEquals(4, view.resultCount(), "one log gives four planks");
            assertTrue(view.shaped(), "the log row is shaped");
            assertEquals(1, view.ingredientCount(), "the single-cell pattern");

            // The table form: the 3x3 chest ring.
            var chest = recipes.match3x3(new ItemStack[] {
                    ItemStack.of(BuiltinItems.OAK_PLANKS, 1), ItemStack.of(BuiltinItems.OAK_PLANKS, 1), ItemStack.of(BuiltinItems.OAK_PLANKS, 1),
                    ItemStack.of(BuiltinItems.OAK_PLANKS, 1), ItemStack.EMPTY, ItemStack.of(BuiltinItems.OAK_PLANKS, 1),
                    ItemStack.of(BuiltinItems.OAK_PLANKS, 1), ItemStack.of(BuiltinItems.OAK_PLANKS, 1), ItemStack.of(BuiltinItems.OAK_PLANKS, 1)
            }).orElseThrow(() -> new AssertionError("the chest ring crafts"));
            assertEquals(Identifier.parse("minecraft:chest"), chest.result());

            // Nothing crafts from an empty grid.
            assertTrue(recipes.match(new ItemStack[] {
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY })
                    .isEmpty(), "the empty grid crafts nothing");
        } finally {
            engine.shutdown(null);
        }
    }

    @Test
    void theEnchantmentRegistryCarriesTheReferenceTable() throws Exception {
        EngineServer engine = boot();
        Server server = engine; // the public surface under test
        try {
            var enchantments = server.enchantments();
            assertTrue(enchantments.all().size() >= 10, "the reference's table");

            // The known rows (the reference's own registrations): protection
            // id 0 (weight 10, max 4), sharpness id 16 (weight 10, max 5).
            var protection = enchantments.byId(0).orElseThrow(
                    () -> new AssertionError("protection by id"));
            assertEquals("protection", protection.key());
            assertEquals(10, protection.weight());
            assertEquals(4, protection.maxLevel());

            var sharpness = enchantments.byKey("sharpness").orElseThrow(
                    () -> new AssertionError("sharpness by key"));
            assertEquals(16, sharpness.id());
            assertEquals(5, sharpness.maxLevel());

            // The id order: the registry walks in id order.
            int lastId = -1;
            for (var view : enchantments.all()) {
                assertTrue(view.id() > lastId, "the id order holds");
                lastId = view.id();
            }

            // The misses answer empty.
            assertTrue(enchantments.byId(999).isEmpty());
            assertTrue(enchantments.byKey("not_an_enchantment").isEmpty());

            // The off-lane read (§27.6).
            AtomicReference<Integer> foreignCount = new AtomicReference<>();
            Thread foreign = new Thread(() ->
                    foreignCount.set(enchantments.all().size()));
            foreign.start();
            foreign.join();
            assertEquals(enchantments.all().size(), foreignCount.get(),
                    "the foreign thread reads the same frozen set");
            assertFalse(enchantments.all().isEmpty());
        } finally {
            engine.shutdown(null);
        }
    }

    @Test
    void theBlockRegistryCarriesTheFrozenEnumeration() throws Exception {
        EngineServer engine = boot();
        Server server = engine; // the public surface under test
        try {
            var blocks = server.blocks();
            // The frozen set's own floor: the engine's registry carries
            // every registered family — at least the 780 the
            // BlockRegistryTest's own unit registry counts (the public
            // engine registry carries more: the redstone/hopper/piston/
            // beacon/brewing/cauldron families ride their own registers).
            assertTrue(blocks.all().size() >= 780, "the frozen block set: "
                    + blocks.all().size());

            // The known rows: the stone by the canonical id, the short
            // form (the minecraft namespace), and the miss.
            var stone = blocks.lookup(
                    Identifier.parse("minecraft:stone")).orElseThrow(
                    () -> new AssertionError("stone by id"));
            assertEquals("Stone", stone.displayName());
            assertTrue(blocks.byKey("stone").isPresent(),
                    "the short form resolves (the item registry's own shape)");
            assertEquals(stone, blocks.byKey("stone").orElseThrow(),
                    "the short form answers the same type");
            assertTrue(blocks.lookup(Identifier.parse("minecraft:not_a_block")).isEmpty(),
                    "the unknown id answers empty");
            assertTrue(blocks.byKey("not_a_block").isEmpty(),
                    "the short form's miss answers empty");

            // The behavior table rides the same surface (Slice 14b's
            // dataOf): the stone's hardness row reads through the public
            // BlockData.
            var stoneData = blocks.dataOf(stone);
            assertTrue(stoneData.hardness() > 0.0,
                    "the stone's own hardness row (the default shape)");

            // The off-lane read (§27.6): the frozen set answers from a
            // foreign thread.
            AtomicReference<Integer> foreignCount = new AtomicReference<>();
            AtomicReference<Boolean> foreignLookup = new AtomicReference<>();
            Thread foreign = new Thread(() -> {
                foreignCount.set(blocks.all().size());
                foreignLookup.set(blocks.byKey("stone").isPresent());
            });
            foreign.start();
            foreign.join();
            assertEquals(blocks.all().size(), foreignCount.get(),
                    "the foreign thread reads the same frozen set");
            assertEquals(Boolean.TRUE, foreignLookup.get(),
                    "the foreign thread's lookup answers");
        } finally {
            engine.shutdown(null);
        }
    }
}
