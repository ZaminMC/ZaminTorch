package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemRegistry;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry-completion API wave (§27.3's registry + tool-data
 * families): the item registry through {@link Server#items()} (the
 * frozen enumeration + lookups) and the block registry's tool-interaction
 * reads ({@code canHarvest} / {@code miningSpeedMultiplier} over the
 * engine's own class/tier matching) — every read frozen data, exercised
 * from a foreign thread too.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ItemAndToolDataApiAcceptanceTest {

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
    void theItemRegistryEnumeratesAndLooksUp() throws Exception {
        EngineServer server = boot();
        try {
            ItemRegistry items = server.items();
            Collection<ItemType> all = items.all();
            assertFalse(all.isEmpty(), "the frozen set carries the built-ins");
            assertTrue(all.size() > 100, "the full item table (221 built-ins)");

            // The lookups: canonical id, short key, and the miss.
            Optional<ItemType> byId = items.byId(Identifier.parse("minecraft:diamond_pickaxe"));
            assertTrue(byId.isPresent(), "the diamond pickaxe answers");
            assertEquals("Diamond Pickaxe", byId.orElseThrow().displayName());

            Optional<ItemType> byKey = items.byKey("stone");
            assertTrue(byKey.isPresent(), "the short form resolves the minecraft namespace");
            assertEquals(Identifier.parse("minecraft:stone"), byKey.orElseThrow().identifier());

            assertTrue(items.byId(Identifier.parse("minecraft:not_an_item")).isEmpty(),
                    "the unknown id misses");

            // The frozen reads answer from a foreign thread (any thread).
            AtomicReference<Optional<ItemType>> foreign =
                    new AtomicReference<>(Optional.empty());
            Thread offLane = new Thread(() ->
                    foreign.set(items.byId(Identifier.parse("minecraft:diamond_pickaxe"))));
            offLane.start();
            offLane.join();
            assertTrue(foreign.get().isPresent(), "the off-lane lookup answers");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theToolVerdictsReadThroughTheBlockRegistry() throws Exception {
        EngineServer server = boot();
        try {
            BlockRegistry blocks = server.blocks();
            BlockType stone = blocks.require(Identifier.parse("minecraft:stone"));
            BlockType dirt = blocks.require(Identifier.parse("minecraft:dirt"));

            // The harvest ladder: stone is tool-gated (rock, level 1) — the
            // empty hand and the sword never harvest, the wooden pickaxe and
            // the diamond pickaxe do; dirt is free.
            ItemType woodenPickaxe = server.items()
                    .byId(Identifier.parse("minecraft:wooden_pickaxe")).orElseThrow();
            ItemType diamondPickaxe = server.items()
                    .byId(Identifier.parse("minecraft:diamond_pickaxe")).orElseThrow();
            ItemType diamondSword = server.items()
                    .byId(Identifier.parse("minecraft:diamond_sword")).orElseThrow();

            assertFalse(blocks.canHarvest(stone, null), "the empty hand never harvests stone");
            assertFalse(blocks.canHarvest(stone, diamondSword),
                    "the sword's class never matches rock");
            assertTrue(blocks.canHarvest(stone, woodenPickaxe),
                    "the wooden pickaxe satisfies level 1");
            assertTrue(blocks.canHarvest(stone, diamondPickaxe),
                    "the diamond pickaxe satisfies level 1");
            assertTrue(blocks.canHarvest(dirt, null), "dirt is free");

            // The speed ladder: the material's own multiplier on a class
            // match (wood 2.0, diamond 8.0), 1.0 for the hand and the
            // mismatched sword.
            assertEquals(1.0, blocks.miningSpeedMultiplier(stone, null), 1e-9,
                    "the hand contributes nothing");
            assertEquals(2.0, blocks.miningSpeedMultiplier(stone, woodenPickaxe), 1e-9,
                    "the wooden pickaxe's material speed");
            assertEquals(8.0, blocks.miningSpeedMultiplier(stone, diamondPickaxe), 1e-9,
                    "the diamond pickaxe's material speed");
            assertEquals(1.0, blocks.miningSpeedMultiplier(stone, diamondSword), 1e-9,
                    "the mismatched class contributes nothing");
            assertEquals(1.0, blocks.miningSpeedMultiplier(dirt, diamondPickaxe), 1e-9,
                    "a pickaxe does not accelerate dirt");

            // The off-lane read (frozen data, any thread).
            AtomicReference<Double> foreign = new AtomicReference<>(0.0);
            Thread offLane = new Thread(() ->
                    foreign.set(blocks.miningSpeedMultiplier(stone, diamondPickaxe)));
            offLane.start();
            offLane.join();
            assertEquals(8.0, foreign.get(), 1e-9, "the off-lane multiplier answers");
        } finally {
            server.shutdown(null);
        }
    }
}
