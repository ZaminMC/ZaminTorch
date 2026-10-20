package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.entity.vehicle.VehicleDataStore;
import net.zaminmc.torch.server.entity.vehicle.VehicleEntity;
import net.zaminmc.torch.server.entity.vehicle.VehicleManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.world.WorldSavePipeline;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE ENTITY FAMILY'S PIPELINE ADOPTION'S OWN EVIDENCE (slice S5, part 3):
 * the mobs', the item entities', and the vehicles' compute-then-publish
 * splits. THE EQUIVALENCE: the split path's bytes and the historical
 * interleaved walks' bytes are BYTE-IDENTICAL (the frame + the simple-tail
 * arms replicated verbatim here; the deep stack tails' layout is proven by
 * the round-trip through the UNCHANGED pre-split loader — the loader the
 * split never touched reads the new serializer's bytes back to the same
 * rows); the sync files and the pipeline bytes agree; the loads round-trip.
 * THE PIPELINE: the named path through the real compute subsystem + I/O
 * lane, each store gating independently under its own name.
 */
class EntityFamilySaveSplitTest {

    @TempDir
    Path dir;

    // ---- the shared wire helpers (the stores' own conventions) ----------

    private static void writeString(DataOutputStream out, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    /** The mob store's nullable string: a 0/1 byte then the string. */
    private static void writeNullableByteString(DataOutputStream out, String value)
            throws Exception {
        if (value == null) {
            out.writeByte(0);
        } else {
            out.writeByte(1);
            writeString(out, value);
        }
    }

    /** The item store's nullable string: a boolean then the string. */
    private static void writeNullableBooleanString(DataOutputStream out, String value)
            throws Exception {
        out.writeBoolean(value != null);
        if (value != null) {
            writeString(out, value);
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws Exception {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    /** The vehicle store's own ZCD item form (present byte + id/count/damage/name). */
    private static void writeVehicleStack(DataOutputStream out, ItemStack stack)
            throws Exception {
        if (stack == null || stack.isEmpty()) {
            out.writeByte(0);
            return;
        }
        out.writeByte(1);
        writeString(out, stack.type().identifier().toString());
        writeVarInt(out, stack.count());
        writeVarInt(out, stack.damage());
        String name = stack.displayName();
        if (name == null) {
            writeVarInt(out, 0);
        } else {
            byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
            writeVarInt(out, bytes.length);
            out.write(bytes);
        }
    }

    // ---- the historical mob walk (the pre-S5 save body, verbatim for
    //      the exercised arms) ---------------------------------------------

    private static byte[] legacyMobBytes(List<MobManager.MobSnapshot> mobs) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('M');
        out.writeByte('D');
        out.writeByte(12);
        writeVarInt(out, mobs.size());
        for (MobManager.MobSnapshot mob : mobs) {
            writeString(out, mob.type());
            out.writeDouble(mob.x());
            out.writeDouble(mob.y());
            out.writeDouble(mob.z());
            out.writeFloat(mob.yaw());
            out.writeFloat(mob.health());
            // writeExtras: the flags byte, then the per-bit rows
            if (mob.extras() == null) {
                out.writeByte(0);
            } else {
                int flags = 0;
                if (mob.extras().breedingAge() != 0) {
                    flags |= 0x08;
                }
                out.writeByte(flags);
                if ((flags & 0x08) != 0) {
                    out.writeInt(mob.extras().breedingAge());
                }
            }
            // writeNbtExtras: the flags byte, then the named rows
            if (mob.nbt() == null) {
                out.writeByte(0);
            } else {
                int flags = 0;
                if (mob.nbt().customName() != null) {
                    flags |= 0x01;
                }
                out.writeByte(flags);
                if ((flags & 0x01) != 0) {
                    writeString(out, mob.nbt().customName());
                }
            }
            // writeCommandStatsPairs: the count then the key/value strings
            int pairs = mob.commandStats() == null ? 0 : mob.commandStats().size();
            writeVarInt(out, pairs);
            if (mob.commandStats() != null) {
                for (Map.Entry<String, String> pair : mob.commandStats().entrySet()) {
                    writeString(out, pair.getKey());
                    writeString(out, pair.getValue());
                }
            }
            // writeTameExtras: the present byte then the owner rows
            if (mob.tame() == null) {
                out.writeByte(0);
            } else {
                out.writeByte(1);
                writeNullableByteString(out, mob.tame().ownerName());
                writeNullableByteString(out, mob.tame().ownerUuid());
                out.writeBoolean(mob.tame().tamed());
                out.writeBoolean(mob.tame().sitting());
                out.writeBoolean(mob.tame().angry());
                out.writeBoolean(mob.tame().horseBred());
                out.writeBoolean(mob.tame().horseHasReproduced());
            }
        }
        out.flush();
        return sink.toByteArray();
    }

    @Test
    void mobSplitIsByteIdenticalToTheHistoricalWalkAndRoundTrips() throws Exception {
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("SuccessCountName", "kills");
        List<MobManager.MobSnapshot> mobs = List.of(
                // the bare v1-equivalent shape: every tail null
                new MobManager.MobSnapshot("zombie", 1.5, 40.0, -2.5, 90.0f, 20.0f),
                // the rich shape: the breeding-age flag row, the custom-name
                // NBT row, the one-pair command-stat tail, the tame tail
                new MobManager.MobSnapshot("sheep", -7.25, 65.5, 11.0, -45.0f, 8.0f,
                        new MobManager.MobStateExtras(false, 0, 0, 0, 0, 0,
                                0, -24000, false, 1, 0, 0),
                        new MobManager.MobNbtExtras("Dolly", false, false, false, false),
                        stats,
                        new MobManager.MobTameExtras("Abasing", null,
                                true, false, false, false, false)));

        MobDataStore store = new MobDataStore(dir.resolve("mobs.bin"));

        byte[] split = store.serialize(mobs);
        assertArrayEquals(legacyMobBytes(mobs), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(mobs);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("mobs.bin")),
                "the sync save's file carries the split path's bytes");
        List<MobManager.MobSnapshot> restored = store.load();
        assertEquals(2, restored.size(), "the mob rows round-trip");
        assertEquals("zombie", restored.get(0).type());
        assertEquals(20.0f, restored.get(0).health());
        MobManager.MobSnapshot rich = restored.get(1);
        assertEquals(-24000, rich.extras().breedingAge(), "the baby's age survives");
        assertEquals("Dolly", rich.nbt().customName(), "the custom name survives");
        assertEquals("kills", rich.commandStats().get("SuccessCountName"),
                "the command-stat pair survives");
        assertTrue(rich.tame().tamed(), "the tame row survives");
        assertEquals("Abasing", rich.tame().ownerName());
    }

    // ---- the historical item walk (the pre-S5 save body, verbatim for
    //      the exercised arms: the plain + named stacks, the flags) -------

    private static byte[] legacyItemBytes(List<ItemEntityManager.ItemRecord> items)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('I');
        out.writeByte('D');
        out.writeByte(7);
        writeVarInt(out, items.size());
        for (ItemEntityManager.ItemRecord item : items) {
            out.writeDouble(item.position().x());
            out.writeDouble(item.position().y());
            out.writeDouble(item.position().z());
            out.writeDouble(item.velocityX());
            out.writeDouble(item.velocityY());
            out.writeDouble(item.velocityZ());
            writeString(out, item.stack().type().identifier().toString());
            writeVarInt(out, item.stack().count());
            writeVarInt(out, item.stack().damage());
            String name = item.stack().displayName();
            writeNullableBooleanString(out, name == null || name.isEmpty() ? null : name);
            writeVarInt(out, 0); // no enchant pairs
            out.writeBoolean(item.stack().unbreakable());
            writeVarInt(out, item.stack().repairCost());
            writeVarInt(out, item.stack().hideFlags());
            writeVarInt(out, 0); // no lore rows
            out.writeBoolean(false); // no BlockEntityTag row
            writeVarInt(out, 0); // no canDestroy rows
            writeVarInt(out, 0); // no canPlaceOn rows
            out.writeBoolean(item.stack().displayColor() != null);
            out.writeBoolean(false); // no attribute-modifiers row
            out.writeBoolean(false); // no SkullOwner row
            writeVarInt(out, item.age());
            writeVarInt(out, item.pickupDelay());
            int flags = 0;
            if (item.neverDespawn()) {
                flags |= 0x01;
            }
            if (item.thrower() != null) {
                flags |= 0x02;
            }
            if (item.owner() != null) {
                flags |= 0x04;
            }
            out.writeByte(flags);
            if (item.thrower() != null) {
                writeNullableBooleanString(out, item.thrower());
            }
            if (item.owner() != null) {
                writeNullableBooleanString(out, item.owner());
            }
        }
        out.flush();
        return sink.toByteArray();
    }

    @Test
    void itemSplitIsByteIdenticalToTheHistoricalWalkAndRoundTrips() throws Exception {
        List<ItemEntityManager.ItemRecord> items = List.of(
                new ItemEntityManager.ItemRecord(
                        new Position(3.5, 40.0, -2.5), 0.0, -0.04, 0.0,
                        ItemStack.of(BuiltinItems.DIAMOND, 3),
                        120, 9, false, null, null),
                new ItemEntityManager.ItemRecord(
                        new Position(-6.0, 65.0, 7.5), 0.1, 0.0, -0.2,
                        ItemStack.of(BuiltinItems.IRON_SWORD, 1).withDamage(87).withName("Kept"),
                        0, 0, true, "Abasing", "Abasing"));

        ItemDataStore store = new ItemDataStore(dir.resolve("items.bin"));

        byte[] split = store.serialize(items);
        assertArrayEquals(legacyItemBytes(items), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(items);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("items.bin")),
                "the sync save's file carries the split path's bytes");
        List<ItemEntityManager.ItemRecord> restored = store.load();
        assertEquals(2, restored.size(), "the item rows round-trip");
        assertEquals(BuiltinItems.DIAMOND, restored.get(0).stack().type());
        assertEquals(3, restored.get(0).stack().count());
        assertEquals(120, restored.get(0).age());
        assertEquals(9, restored.get(0).pickupDelay());
        assertEquals("Kept", restored.get(1).stack().displayName());
        assertEquals(87, restored.get(1).stack().damage());
        assertTrue(restored.get(1).neverDespawn(), "the never-despawn flag survives");
        assertEquals("Abasing", restored.get(1).thrower(), "the thrower row survives");
        assertEquals("Abasing", restored.get(1).owner(), "the owner row survives");
    }

    // ---- the historical vehicle walk (the store's own save body at the
    //      ZVD v5 shape, verbatim for the exercised kinds — the v4 base
    //      rows and the v5 cadence rows included, the pin kept current
    //      with every format bump) --------------------------------------

    private static byte[] legacyVehicleBytes(List<VehicleManager.VehicleSnapshot> vehicles)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('V');
        out.writeByte('D');
        out.writeByte(5);
        writeVarInt(out, vehicles.size());
        for (VehicleManager.VehicleSnapshot vehicle : vehicles) {
            writeString(out, vehicle.kind());
            out.writeDouble(vehicle.x());
            out.writeDouble(vehicle.y());
            out.writeDouble(vehicle.z());
            out.writeFloat(vehicle.yaw());
            out.writeFloat(vehicle.health());
            if (vehicle.cargo() != null) {
                for (ItemStack stack : vehicle.cargo()) {
                    writeVehicleStack(out, stack);
                }
            }
            if (vehicle.kind().equals(VehicleEntity.Kind.HOPPER_MINECART.name())) {
                out.writeByte(vehicle.hopperEnabled() ? 1 : 0);
                out.writeInt(vehicle.hopperCooldown());
            } else if (vehicle.kind().equals(VehicleEntity.Kind.SPAWNER_MINECART.name())) {
                writeString(out, vehicle.spawnerType() == null ? "" : vehicle.spawnerType());
                writeVarInt(out, vehicle.spawnerDelay());
                // The v5 cadence rows (MobSpawnerBaseLogic.writeNbt's own
                // six — a null row set writes the reference's defaults).
                VehicleManager.CadenceRows cadence = vehicle.cadence();
                if (cadence == null) {
                    cadence = new VehicleManager.CadenceRows(
                            net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity.MIN_SPAWN_DELAY,
                            net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity.MAX_SPAWN_DELAY,
                            net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity.SPAWN_COUNT,
                            net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity.MAX_NEARBY_ENTITIES,
                            net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity.REQUIRED_PLAYER_RANGE,
                            net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity.SPAWN_RANGE);
                }
                writeVarInt(out, cadence.minSpawnDelay());
                writeVarInt(out, cadence.maxSpawnDelay());
                writeVarInt(out, cadence.spawnCount());
                writeVarInt(out, cadence.maxNearbyEntities());
                writeVarInt(out, cadence.requiredPlayerRange());
                writeVarInt(out, cadence.spawnRange());
            } else if (vehicle.kind().equals(VehicleEntity.Kind.COMMAND_BLOCK_MINECART.name())) {
                writeString(out, vehicle.command() == null ? "" : vehicle.command());
                writeVarInt(out, vehicle.commandSuccessCount());
                out.writeByte(vehicle.commandTrackOutput() ? 1 : 0);
                writeString(out, vehicle.commandLastOutput() == null
                        ? "" : vehicle.commandLastOutput());
            }
            out.writeShort(vehicle.fire());
            // The v4 base rows (the reference's Entity.readNbt base band).
            VehicleManager.BaseRows base = vehicle.base();
            out.writeLong(base.uuidMost());
            out.writeLong(base.uuidLeast());
            out.writeByte(base.hasUuid() ? 1 : 0);
            writeString(out, base.customName() == null ? "" : base.customName());
            out.writeByte(base.customNameVisible() ? 1 : 0);
            out.writeByte(base.silent() ? 1 : 0);
            out.writeFloat(base.fallDistance());
            out.writeShort(base.breath());
            out.writeByte(base.onGround() ? 1 : 0);
            out.writeByte(base.invulnerable() ? 1 : 0);
            out.writeInt(base.dimension());
            writeVarInt(out, base.portalCooldown());
        }
        out.flush();
        return sink.toByteArray();
    }

    @Test
    void vehicleSplitIsByteIdenticalToTheHistoricalWalkAndRoundTrips() throws Exception {
        ItemStack[] chestCargo = new ItemStack[27];
        chestCargo[3] = ItemStack.of(BuiltinItems.DIAMOND, 5).withName("Shiny");
        ItemStack[] hopperCargo = new ItemStack[5];
        hopperCargo[0] = ItemStack.of(BuiltinItems.COAL, 12);
        List<VehicleManager.VehicleSnapshot> vehicles = List.of(
                new VehicleManager.VehicleSnapshot(
                        VehicleEntity.Kind.MINECART.name(), 1.5, 40.0, 2.5, 90.0f, 6.0f,
                        null, true, -1, null, -1, null, 0, true, null, 0),
                new VehicleManager.VehicleSnapshot(
                        VehicleEntity.Kind.CHEST_MINECART.name(), 2.5, 41.0, 3.5, -90.0f, 6.0f,
                        chestCargo, true, -1, null, -1, null, 0, true, null, 40),
                new VehicleManager.VehicleSnapshot(
                        VehicleEntity.Kind.HOPPER_MINECART.name(), 3.5, 42.0, 4.5, 180.0f, 5.0f,
                        hopperCargo, false, 77, null, -1, null, 0, true, null, -20),
                new VehicleManager.VehicleSnapshot(
                        VehicleEntity.Kind.COMMAND_BLOCK_MINECART.name(), 4.5, 43.0, 5.5, 0.0f,
                        6.0f, null, true, -1, null, -1, "say hi", 3, true, "ran", 7),
                // The spawner cart (the v5 cadence rows' own scene — the
                // explicit band the acceptance scene imports).
                new VehicleManager.VehicleSnapshot(
                        VehicleEntity.Kind.SPAWNER_MINECART.name(), 5.5, 44.0, 6.5, 0.0f, 6.0f,
                        null, true, -1, "Zombie", 12, null, 0, true, null, 0,
                        VehicleManager.BaseRows.DEFAULT,
                        new VehicleManager.CadenceRows(120, 240, 2, 3, 12, 2)));

        VehicleDataStore store = new VehicleDataStore(dir.resolve("vehicles.bin"));

        byte[] split = store.serialize(vehicles);
        assertArrayEquals(legacyVehicleBytes(vehicles), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(vehicles);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("vehicles.bin")),
                "the sync save's file carries the split path's bytes");
        List<VehicleManager.VehicleSnapshot> restored = store.load();
        assertEquals(5, restored.size(), "the vehicle rows round-trip");
        assertEquals(VehicleEntity.Kind.MINECART.name(), restored.get(0).kind());
        assertEquals(0, restored.get(0).fire());
        VehicleManager.VehicleSnapshot chest = restored.get(1);
        assertNotNull(chest.cargo(), "the chest cart's cargo round-trips");
        assertEquals(27, chest.cargo().length);
        assertEquals(5, chest.cargo()[3].count());
        assertEquals("Shiny", chest.cargo()[3].displayName());
        assertEquals(40, chest.fire());
        VehicleManager.VehicleSnapshot hopper = restored.get(2);
        assertEquals(12, hopper.cargo()[0].count());
        assertFalse(hopper.hopperEnabled());
        assertEquals(77, hopper.hopperCooldown());
        VehicleManager.VehicleSnapshot command = restored.get(3);
        assertEquals("say hi", command.command());
        assertEquals(3, command.commandSuccessCount());
        assertTrue(command.commandTrackOutput());
        assertEquals("ran", command.commandLastOutput());
        VehicleManager.VehicleSnapshot spawner = restored.get(4);
        assertEquals("Zombie", spawner.spawnerType(), "the spawner cart's type round-trips");
        assertEquals(12, spawner.spawnerDelay(), "the spawner cart's delay round-trips");
        assertEquals(120, spawner.cadence().minSpawnDelay(), "the cadence band's min");
        assertEquals(240, spawner.cadence().maxSpawnDelay(), "the cadence band's max");
        assertEquals(2, spawner.cadence().spawnCount(), "the cadence attempts");
        assertEquals(3, spawner.cadence().maxNearbyEntities(), "the cadence cap");
        assertEquals(12, spawner.cadence().requiredPlayerRange(), "the cadence gate");
        assertEquals(2, spawner.cadence().spawnRange(), "the cadence roll range");
    }

    @Test
    void theTrioRidesThePipelineThroughTheLanes() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s5entity:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            MobDataStore mobStore = new MobDataStore(dir.resolve("live-mobs.bin"));
            List<MobManager.MobSnapshot> mobs = List.of(
                    new MobManager.MobSnapshot("pig", 8.5, 40.0, 8.5, 0.0f, 10.0f));
            byte[] mobBytes = mobStore.serialize(mobs);

            ItemDataStore itemStore = new ItemDataStore(dir.resolve("live-items.bin"));
            List<ItemEntityManager.ItemRecord> items = List.of(
                    new ItemEntityManager.ItemRecord(new Position(1.5, 40.0, 1.5),
                            0, 0, 0, ItemStack.of(BuiltinItems.DIRT, 64), 0, 0, false,
                            null, null));
            byte[] itemBytes = itemStore.serialize(items);

            VehicleDataStore vehicleStore =
                    new VehicleDataStore(dir.resolve("live-vehicles.bin"));
            List<VehicleManager.VehicleSnapshot> vehicles = List.of(
                    new VehicleManager.VehicleSnapshot(
                            VehicleEntity.Kind.BOAT.name(), 0.5, 40.0, 0.5, 0.0f, 10.0f,
                            null, true, -1, null, -1, null, 0, true, null, 0));
            byte[] vehicleBytes = vehicleStore.serialize(vehicles);

            CountDownLatch mobsDone = pipeline.submitSave("mobs", mobs,
                    mobStore::serialize, mobStore::writeSerialized);
            CountDownLatch itemsDone = pipeline.submitSave("items", items,
                    itemStore::serialize, itemStore::writeSerialized);
            CountDownLatch vehiclesDone = pipeline.submitSave("vehicles", vehicles,
                    vehicleStore::serialize, vehicleStore::writeSerialized);
            long deadline = System.currentTimeMillis() + 10_000;
            while ((!mobsDone.await(10, TimeUnit.MILLISECONDS)
                    || !itemsDone.await(10, TimeUnit.MILLISECONDS)
                    || !vehiclesDone.await(10, TimeUnit.MILLISECONDS))
                    && System.currentTimeMillis() < deadline) {
                domain.enter();
                try {
                    compute.drainResults(domain);
                    io.drainCompletions(domain);
                } finally {
                    domain.exit();
                }
            }
            assertTrue(mobsDone.await(0, TimeUnit.MILLISECONDS), "the mob save landed");
            assertTrue(itemsDone.await(0, TimeUnit.MILLISECONDS), "the item save landed");
            assertTrue(vehiclesDone.await(0, TimeUnit.MILLISECONDS),
                    "the vehicle save landed");
            assertArrayEquals(mobBytes, Files.readAllBytes(dir.resolve("live-mobs.bin")),
                    "the landed mob file carries exactly the pure serializer's bytes");
            assertArrayEquals(itemBytes, Files.readAllBytes(dir.resolve("live-items.bin")),
                    "the landed item file carries exactly the pure serializer's bytes");
            assertArrayEquals(vehicleBytes,
                    Files.readAllBytes(dir.resolve("live-vehicles.bin")),
                    "the landed vehicle file carries exactly the pure serializer's bytes");
            assertEquals(1, mobStore.load().size(), "the mob rows round-trip");
            assertEquals(1, itemStore.load().size(), "the item rows round-trip");
            assertEquals(1, vehicleStore.load().size(), "the vehicle rows round-trip");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }
}
