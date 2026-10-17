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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void theWaveGArmsSurviveTheStore(@TempDir Path dir) throws Exception {
        // The ZID v2 tail (Slice 94): the four Wave G arms ride the stack
        // rows — the flagged repaired lore-carrying sword round-trips the
        // whole body.
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)
                        .withUnbreakable(true)
                        .withRepairCost(9)
                        .withHideFlags(6)
                        .withLore(List.of("Forged in the deep", "Never dulls")),
                0);

        ItemDataStore store = new ItemDataStore(dir.resolve("items.bin"));
        store.save(manager.snapshot());
        ItemEntityManager restored = new ItemEntityManager(GROUND, new Random(1), 100);
        restored.restoreAll(store.load());

        assertEquals(1, restored.size());
        ItemStack sword = restored.all().get(0).stack();
        assertTrue(sword.unbreakable(), "the flag survived");
        assertEquals(9, sword.repairCost());
        assertEquals(6, sword.hideFlags());
        assertEquals(List.of("Forged in the deep", "Never dulls"), sword.lore());
    }

    @Test
    void theV1FileLoadsItsStackWithTheWaveGDefaults(@TempDir Path dir) throws Exception {
        // The version-aware read: a hand-written v1 file (the pre-Wave-G
        // shape — the stack rows stop after the enchant pairs) still
        // loads, the new arms carrying their defaults.
        ItemDataStore store = new ItemDataStore(dir.resolve("items.bin"));
        java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(
                        dir.resolve("items.bin"))));
        out.writeByte('Z');
        out.writeByte('I');
        out.writeByte('D');
        out.writeByte(1); // the v1 shape
        out.write(1); // one body (a single-byte varint)
        out.writeDouble(1.0);
        out.writeDouble(6.0);
        out.writeDouble(1.0);
        out.writeDouble(0.0);
        out.writeDouble(0.0);
        out.writeDouble(0.0);
        byte[] id = "minecraft:diamond_sword".getBytes();
        out.write(id.length);
        out.write(id);
        out.write(1);  // count
        out.write(0);  // damage
        out.writeBoolean(false); // no display name
        out.write(0);  // no enchant pairs
        out.write(42); // age
        out.write(0);  // pickup delay
        out.write(0);  // flags
        out.close();

        List<ItemEntityManager.ItemRecord> loaded = store.load();
        assertEquals(1, loaded.size());
        ItemStack sword = loaded.get(0).stack();
        assertFalse(sword.unbreakable(), "a v1 stack carries the default flag");
        assertEquals(0, sword.repairCost());
        assertEquals(0, sword.hideFlags());
        assertNull(sword.lore());
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

    @Test
    void theBlockEntityTagArmRidesTheZidTail(@TempDir Path dir) throws Exception {
        // Wave H2 — the ZID v3 tail: the raw compound as ONE SNBT string row
        // (NbtOps.write over the adapted boxes), round-tripped through the
        // store. A v2-era body loads with the arm absent (the null marker
        // never parses).
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(7), 1);
        java.util.Map<String, Object> inner = new java.util.LinkedHashMap<>();
        inner.put("Slot", (byte) 0);
        inner.put("id", "minecraft:diamond");
        inner.put("Count", (byte) 5);
        java.util.Map<String, Object> tag = new java.util.LinkedHashMap<>();
        tag.put("Items", java.util.List.of(inner));
        tag.put("Lock", "Kept");
        manager.spawnDropAtBlock(new Position(2.5, 6.0, 2.5),
                ItemStack.of(BuiltinItems.CHEST, 1).withBlockEntityTag(tag), 0);

        ItemDataStore store = new ItemDataStore(dir.resolve("items.bin"));
        store.save(manager.snapshot());
        var loaded = store.load();
        org.junit.jupiter.api.Assertions.assertEquals(1, loaded.size(),
                "the load returned the body: " + loaded.size());
        ItemEntityManager restored = new ItemEntityManager(GROUND, new Random(7), 100);
        restored.restoreAll(loaded);
        org.junit.jupiter.api.Assertions.assertEquals(1, restored.size(),
                "the restore joined the body");

        ItemEntity body = restored.all().get(0);
        assertEquals(1, body.stack().count());
        net.zaminmc.torch.item.ItemStack stack = body.stack();
        assertTrue(stack.blockEntityTag() != null, "the arm rode the tail");
        assertEquals("Kept", stack.blockEntityTag().get("Lock"));
        java.util.List<?> items = (java.util.List<?>) stack.blockEntityTag().get("Items");
        assertEquals(1, items.size());
        java.util.Map<?, ?> row = (java.util.Map<?, ?>) items.get(0);
        assertEquals("minecraft:diamond", row.get("id"));
        assertEquals((byte) 5, row.get("Count"));
        // The tree stays deeply immutable after the ride.
        assertTrue(stack.blockEntityTag().get("Items") instanceof java.util.List);
    }

    @Test
    void theAttributeModifiersRideTheZidTail(@TempDir Path dir) throws Exception {
        // Wave J — the ZID v6 tail: the whole modifier list as ONE
        // parseable-SNBT string row (the BlockEntityTag tail's own
        // pattern), round-tripped through the store. A v5-era body loads
        // with the arm absent (the null marker never parses).
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(11), 1);
        java.util.UUID uuid = java.util.UUID.fromString(
                "CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
        manager.spawnDropAtBlock(new Position(2.5, 6.0, 2.5),
                ItemStack.of(BuiltinItems.DIAMOND_SWORD, 1)
                        .withAttributeModifiers(java.util.List.of(
                                new net.zaminmc.torch.item.AttributeModifier(
                                        "generic.attackDamage", "Weapon modifier",
                                        10.0, 0, uuid))),
                0);

        ItemDataStore store = new ItemDataStore(dir.resolve("items.bin"));
        store.save(manager.snapshot());
        var loaded = store.load();
        assertEquals(1, loaded.size());
        net.zaminmc.torch.item.ItemStack held = loaded.get(0).stack();
        assertTrue(held.attributeModifiers() != null, "the arm rode the v6 tail");
        assertEquals(1, held.attributeModifiers().size());
        net.zaminmc.torch.item.AttributeModifier row =
                held.attributeModifiers().get(0);
        assertEquals("generic.attackDamage", row.attributeName());
        assertEquals("Weapon modifier", row.name());
        assertEquals(10.0, row.amount(), 0.0);
        assertEquals(0, row.operation());
        assertEquals(uuid, row.uuid());
        // The fold consult survives the restart with it (the tagged drop
        // still out-damages the bare table).
        assertEquals(11.0f, net.zaminmc.torch.server.item.Tools.heldDamageOf(held),
                0.0001f, "the fold over the restarted stack (base 1 + 10)");
    }
}
