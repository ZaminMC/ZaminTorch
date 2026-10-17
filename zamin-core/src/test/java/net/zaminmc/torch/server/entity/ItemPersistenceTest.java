package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item bodies' persistence (Slice 93/E1 — the ZID v1 store, the
 * reference's own writeCustomNbt row set over the engine's Z-family
 * shape): the whole body round-trips the restart — the position and
 * motion, the stack with its tag arms (the display name + the enchant
 * pairs), the age clock, the pickup delay, the never-despawn pin, and
 * the thrower/owner strings.
 */
class ItemPersistenceTest {

    /** Solid everywhere below y=4 (the flat world's own surface). */
    private static final ItemEntity.Ground GROUND = (x, y, z) -> y < 4.0;

    @Test
    void theStoreRoundTripsTheWholeBody(@TempDir Path dir) throws Exception {
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);

        // The rich body: a named enchanted sword thrown by a player.
        ItemEntity thrown = manager.spawnThrown(
                new Position(2.5, 6.0, 2.5),
                ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)
                        .withName("Excalibur")
                        .withEnchantment(16, 3) // Sharpness III
                        .withEnchantment(34, 1)); // Unbreaking I
        thrown.setThrower("Abasing");
        thrown.setVelocity(0.1, 0.2, 0.3);
        thrown.setAge(1234);
        thrown.setPickupDelayTicks(17);

        // The never-despawn star (the wither's own drop).
        ItemEntity star = manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.NETHER_STAR, 1), 0);
        star.setNeverDespawn();
        star.setAge(7000); // past the wall — the pin holds it
        star.setOwner("Owner");

        // The plain drop beside them.
        manager.spawnDropAtBlock(new Position(3, 4, 3),
                ItemStack.of(BuiltinItems.DIRT, 12), 0);

        // Save into a temp store, load back into a fresh manager.
        ItemDataStore store = new ItemDataStore(dir.resolve("items.bin"));
        store.save(manager.snapshot());
        ItemEntityManager restored = new ItemEntityManager(GROUND, new Random(1), 100);
        restored.restoreAll(store.load());

        assertEquals(3, restored.size());
        List<ItemEntity> bodies = restored.all();
        // The named enchanted sword with its thrower and motion.
        ItemEntity sword = bodies.stream()
                .filter(b -> b.stack().type().equals(BuiltinItems.DIAMOND_SWORD))
                .findFirst().orElseThrow();
        assertEquals("Excalibur", sword.stack().displayName());
        assertEquals(2, sword.stack().enchantments().size());
        assertEquals(3, sword.stack().enchantmentLevel(16));
        assertEquals(1, sword.stack().enchantmentLevel(34));
        assertEquals("Abasing", sword.thrower());
        assertEquals(0.1, sword.velocityX(), 1e-9);
        assertEquals(0.2, sword.velocityY(), 1e-9);
        assertEquals(0.3, sword.velocityZ(), 1e-9);
        assertEquals(1234, sword.age());
        assertEquals(17, sword.pickupDelayTicks());
        // The pinned star past the despawn wall.
        ItemEntity pinned = bodies.stream()
                .filter(b -> b.stack().type().equals(BuiltinItems.NETHER_STAR))
                .findFirst().orElseThrow();
        assertTrue(pinned.neverDespawn());
        assertEquals(7000, pinned.age());
        assertTrue(!pinned.expired()); // the pin holds it past the wall
        assertEquals("Owner", pinned.owner());
        // The plain drop's count.
        ItemEntity dirt = bodies.stream()
                .filter(b -> b.stack().type().equals(BuiltinItems.DIRT))
                .findFirst().orElseThrow();
        assertEquals(12, dirt.stack().count());
    }

    @Test
    void theCorruptFileQuarantinesAndLoadsAbsent(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("items.bin");
        java.nio.file.Files.write(file, new byte[] {'Z', 'I', 'X', 1, 5, 0});
        ItemDataStore store = new ItemDataStore(file);
        assertEquals(List.of(), store.load());
        assertTrue(java.nio.file.Files.exists(dir.resolve("items.bin.corrupt")),
                "the corrupt file is preserved");
        // The quarantined store loads absent again (fresh world).
        assertEquals(List.of(), store.load());
    }

    @Test
    void theUnresolvableStackNeverJoinsTheWorld(@TempDir Path dir) throws Exception {
        // A save carrying an id the registry cannot resolve (a newer save
        // on an older build) consumes its rows and the body never joins.
        ItemDataStore store = new ItemDataStore(dir.resolve("items.bin"));
        java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(
                        dir.resolve("items.bin"))));
        out.writeByte('Z');
        out.writeByte('I');
        out.writeByte('D');
        out.writeByte(1);
        // One body: position, velocity, the unknown stack rows, age,
        // delay, no flags.
        out.writeDouble(1.0);
        out.writeDouble(2.0);
        out.writeDouble(3.0);
        out.writeDouble(0.0);
        out.writeDouble(0.0);
        out.writeDouble(0.0);
        byte[] id = "minecraft:not_a_real_item".getBytes();
        out.write(id.length);
        out.write(id);
        out.write(1);   // count
        out.write(0);   // damage
        out.writeBoolean(false); // no display name
        out.write(0);   // no enchant pairs
        out.write(10);  // age
        out.write(0);   // pickup delay
        out.write(0);   // flags
        out.close();
        assertEquals(List.of(), store.load());
    }

    @Test
    void theDropsSurviveTheRestartOverTheLiveEngine(@TempDir Path dir) throws Exception {
        // The live-engine round trip: drop a diamond over engine one, save,
        // shut down, reboot over the same data dir — the diamond is still
        // on the ground with its age and thrower.
        EngineConfig first = new EngineConfig("127.0.0.1", 0,
                "itp" + Math.abs(java.util.UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL,
                true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer serverOne = new EngineServer(first);
        serverOne.start();
        try {
            serverOne.ticker().submit(() -> {
                ItemEntity thrown = serverOne.itemEntities().spawnThrown(
                        new Position(30.5, 6.0, 30.5),
                        ItemStack.of(BuiltinItems.DIAMOND, 1));
                if (thrown != null) {
                    thrown.setThrower("Abasing");
                    thrown.setAge(500);
                }
                serverOne.itemEntities().spawnDropAtBlock(new Position(3, 4, 3),
                        ItemStack.of(BuiltinItems.DIRT, 7), 0);
            });
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline
                    && serverOne.itemEntities().size() < 2) {
                Thread.sleep(50);
            }
            assertEquals(2, serverOne.itemEntities().size());
            serverOne.saveAllNow();
        } finally {
            serverOne.shutdown(null);
        }

        EngineServer serverTwo = new EngineServer(first);
        serverTwo.start();
        try {
            assertEquals(2, serverTwo.itemEntities().size(),
                    "the drops survived the restart");
            ItemEntity diamond = serverTwo.itemEntities().all().stream()
                    .filter(b -> b.stack().type().equals(BuiltinItems.DIAMOND))
                    .findFirst().orElseThrow();
            assertEquals("Abasing", diamond.thrower());
            assertTrue(diamond.age() >= 500, "the age clock carried over");
            ItemEntity dirt = serverTwo.itemEntities().all().stream()
                    .filter(b -> b.stack().type().equals(BuiltinItems.DIRT))
                    .findFirst().orElseThrow();
            assertEquals(7, dirt.stack().count());
        } finally {
            serverTwo.shutdown(null);
        }
    }
}
