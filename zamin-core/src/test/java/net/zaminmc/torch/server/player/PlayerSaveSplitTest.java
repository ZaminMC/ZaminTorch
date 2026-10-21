package net.zaminmc.torch.server.player;

import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.world.WorldSavePipeline;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE PLAYER STORE'S PIPELINE ADOPTION'S OWN EVIDENCE (slice S6 — the save
 * walk's last synchronous store): the shutdown walk's per-player persist
 * arm splits into the capture (the tick lane's own {@code snapshotOf} —
 * the fresh immutable snapshot) and the compute-then-publish pair (the
 * pure ZPD serialization + the temp-write/atomic-move file half) that
 * rides the pipeline's named path under the PER-PLAYER sequence gate.
 * THE EQUIVALENCE: the split path's bytes and the historical interleaved
 * walk's bytes are BYTE-IDENTICAL (the ZPD v11 body replicated verbatim
 * here; the deep tails' layout is proven by the round-trip through the
 * UNCHANGED pre-split loader); the sync file and the pipeline bytes agree;
 * the load round-trips. THE PIPELINE: the named path through the real
 * compute subsystem + I/O lane, each player gating independently under
 * its own name — an older snapshot of one player never overwrites that
 * player's newer file, and one player's save never drops another's (the
 * independent sequence spaces). THE QUIT ARM: the synchronous save keeps
 * its own law (the quit path's tick-queue ordering — unchanged, its
 * byte-identity proven by the same composition).
 */
class PlayerSaveSplitTest {

    @TempDir
    Path dir;

    private static final UUID ALICE =
            UUID.nameUUIDFromBytes("OfflinePlayer:Alice".getBytes(StandardCharsets.UTF_8));
    private static final UUID BOB =
            UUID.nameUUIDFromBytes("OfflinePlayer:Bob".getBytes(StandardCharsets.UTF_8));

    // ---- the shared wire helpers (the store's own conventions) ----------

    private static void writeString(DataOutputStream out, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    /** Varint-length UTF-8; a 0 length is the "absent" marker (never null). */
    private static void writeOptionalString(DataOutputStream out, String value) throws Exception {
        if (value == null) {
            writeVarInt(out, 0);
            return;
        }
        writeString(out, value);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws Exception {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static void writeVarLong(DataOutputStream out, long value) throws Exception {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                out.write((int) value);
                return;
            }
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
    }

    // ---- the historical player walk (the pre-S6 save body, verbatim) ----

    private static byte[] legacyPlayerBytes(PlayerSnapshot snapshot) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('P');
        out.writeByte('D');
        out.writeByte(11);
        writeString(out, snapshot.name());
        out.writeDouble(snapshot.position().x());
        out.writeDouble(snapshot.position().y());
        out.writeDouble(snapshot.position().z());
        out.writeFloat(snapshot.rotation().yaw());
        out.writeFloat(snapshot.rotation().pitch());
        writeVarInt(out, snapshot.heldSlot());
        writeVarInt(out, snapshot.slots().size());
        for (PlayerSnapshot.SlotStack stack : snapshot.slots()) {
            writeVarInt(out, stack.slot());
            writeString(out, stack.item().toString());
            writeVarInt(out, stack.count());
            writeVarInt(out, stack.damage());
            writeOptionalString(out, stack.displayName());
        }
        out.writeFloat(snapshot.health());
        writeVarInt(out, snapshot.food());
        out.writeFloat(snapshot.saturation());
        out.writeByte(snapshot.gamemodeId());
        writeVarLong(out, snapshot.totalXp());
        if (snapshot.bedSpawn() == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeDouble(snapshot.bedSpawn().x());
            out.writeDouble(snapshot.bedSpawn().y());
            out.writeDouble(snapshot.bedSpawn().z());
        }
        writeVarInt(out, snapshot.enderSlots().size());
        for (PlayerSnapshot.SlotStack stack : snapshot.enderSlots()) {
            writeVarInt(out, stack.slot());
            writeString(out, stack.item().toString());
            writeVarInt(out, stack.count());
            writeVarInt(out, stack.damage());
            writeOptionalString(out, stack.displayName());
        }
        writeVarInt(out, snapshot.stats().size());
        for (Map.Entry<String, Integer> stat : snapshot.stats().entrySet()) {
            writeString(out, stat.getKey());
            writeVarInt(out, stat.getValue());
        }
        writeVarInt(out, snapshot.commandStats().size());
        for (Map.Entry<String, String> pair : snapshot.commandStats().entrySet()) {
            writeString(out, pair.getKey());
            writeString(out, pair.getValue());
        }
        writeVarInt(out, snapshot.progress().size());
        for (Map.Entry<String, Set<String>> row : snapshot.progress().entrySet()) {
            writeString(out, row.getKey());
            writeVarInt(out, row.getValue().size());
            for (String value : row.getValue()) {
                writeString(out, value);
            }
        }
        out.flush();
        return sink.toByteArray();
    }

    /** The rich shape: every tail the ZPD v11 format carries. */
    private static PlayerSnapshot richSnapshot(UUID uuid, String name, double x) {
        List<PlayerSnapshot.SlotStack> slots = List.of(
                new PlayerSnapshot.SlotStack(0, Identifier.parse("minecraft:dirt"), 5, 0, null),
                new PlayerSnapshot.SlotStack(3, Identifier.parse("minecraft:diamond_pickaxe"),
                        1, 42, "Digger"),
                // the ZPD v5 armor namespace: slot 36 is the head row.
                new PlayerSnapshot.SlotStack(36, Identifier.parse("minecraft:diamond_helmet"),
                        1, 7, null));
        List<PlayerSnapshot.SlotStack> ender = List.of(
                new PlayerSnapshot.SlotStack(2, Identifier.parse("minecraft:ender_pearl"),
                        16, 0, null));
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("stat.leaveGame", 3);
        stats.put("stat.playOneMinute", 123456);
        Map<String, String> commandStats = new LinkedHashMap<>();
        commandStats.put("SuccessCountName", "killed");
        Map<String, Set<String>> progress = new LinkedHashMap<>();
        Set<String> biomes = new LinkedHashSet<>();
        biomes.add("plains");
        biomes.add("desert");
        progress.put("stat.exploreAllBiomes", biomes);
        return new PlayerSnapshot(uuid, name, new Position(x, 5.0, -7.25),
                new Rotation(97.5f, -18.0f), 3, slots, 17.5f, 18, 4.25f, 0, 777,
                new Position(2.5, 6.0, 2.5), ender, stats, commandStats, progress);
    }

    @Test
    void playerSplitIsByteIdenticalToTheHistoricalWalkAndRoundTrips() throws Exception {
        PlayerSnapshot snapshot = richSnapshot(ALICE, "Alice", 12.5);
        PlayerDataStore store = new PlayerDataStore(dir.resolve("playerdata"));

        byte[] split = store.serialize(snapshot);
        assertArrayEquals(legacyPlayerBytes(snapshot), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(snapshot);
        Path file = dir.resolve("playerdata").resolve(ALICE + ".zpd");
        assertArrayEquals(split, Files.readAllBytes(file),
                "the sync save's file carries the split path's bytes (the quit arm's own form)");

        PlayerSnapshot restored = store.load(ALICE).orElseThrow();
        assertEquals("Alice", restored.name());
        assertEquals(new Position(12.5, 5.0, -7.25), restored.position());
        assertEquals(new Rotation(97.5f, -18.0f), restored.rotation());
        assertEquals(3, restored.heldSlot());
        assertEquals(3, restored.slots().size(), "the slot rows round-trip");
        assertEquals("minecraft:dirt", restored.slots().get(0).item().toString());
        assertEquals(42, restored.slots().get(1).damage(), "the tool wear survives");
        assertEquals("Digger", restored.slots().get(1).displayName(), "the name survives");
        assertEquals(36, restored.slots().get(2).slot(), "the armor namespace row survives");
        assertEquals(17.5f, restored.health());
        assertEquals(18, restored.food());
        assertEquals(4.25f, restored.saturation());
        assertEquals(777, restored.totalXp());
        assertEquals(new Position(2.5, 6.0, 2.5), restored.bedSpawn());
        assertEquals(1, restored.enderSlots().size());
        assertEquals(16, restored.enderSlots().get(0).count(), "the ender tail survives");
        assertEquals(2, restored.stats().size(), "the stats tail survives");
        assertEquals(123456, restored.stats().get("stat.playOneMinute"));
        assertEquals("killed", restored.commandStats().get("SuccessCountName"),
                "the command-stat tail survives");
        assertEquals(Set.of("plains", "desert"),
                restored.progress().get("stat.exploreAllBiomes"),
                "the progress tail survives");
    }

    @Test
    void theAbsentBedSpawnArmStaysTheHistoricalMarker() throws Exception {
        // The no-bed arm (ZPD v7's absent marker): the plain shape with no
        // tails — the historical walk's own byte-0 marker arms.
        PlayerSnapshot plain = new PlayerSnapshot(BOB, "Bob",
                new Position(0.5, 64.0, 0.5), new Rotation(0f, 0f), 0,
                List.of(), 20.0f, 20, 5.0f, 1, 0, null,
                List.of(), Map.of(), Map.of(), Map.of());
        PlayerDataStore store = new PlayerDataStore(dir.resolve("playerdata"));

        byte[] split = store.serialize(plain);
        assertArrayEquals(legacyPlayerBytes(plain), split,
                "the plain shape's bytes are byte-identical");
        store.save(plain);
        assertArrayEquals(split,
                Files.readAllBytes(dir.resolve("playerdata").resolve(BOB + ".zpd")));
        PlayerSnapshot restored = store.load(BOB).orElseThrow();
        assertTrue(restored.bedSpawn() == null, "the absent bed marker round-trips");
        assertEquals(0, restored.slots().size());
        assertEquals(0, restored.stats().size());
    }

    @Test
    void thePerPlayerNamedPathLandsThroughTheLanes() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s6player:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 2);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            PlayerDataStore store = new PlayerDataStore(dir.resolve("playerdata"));
            PlayerSnapshot alice = richSnapshot(ALICE, "Alice", 12.5);
            PlayerSnapshot bob = richSnapshot(BOB, "Bob", -3.5);
            byte[] aliceBytes = store.serialize(alice);
            byte[] bobBytes = store.serialize(bob);

            CountDownLatch aliceDone = pipeline.submitSave("player-" + ALICE,
                    alice, store::serialize, bytes -> store.writeSerialized(ALICE, bytes));
            CountDownLatch bobDone = pipeline.submitSave("player-" + BOB,
                    bob, store::serialize, bytes -> store.writeSerialized(BOB, bytes));
            drainUntil(domain, compute, io, aliceDone, bobDone);

            Path aliceFile = dir.resolve("playerdata").resolve(ALICE + ".zpd");
            Path bobFile = dir.resolve("playerdata").resolve(BOB + ".zpd");
            assertArrayEquals(aliceBytes, Files.readAllBytes(aliceFile),
                    "the landed file carries exactly the pure serializer's bytes");
            assertArrayEquals(bobBytes, Files.readAllBytes(bobFile),
                    "the second player's landed file carries its own bytes");
            assertEquals(2, pipeline.submitted(), "both saves submitted");
            assertEquals(2, pipeline.serialized(), "both serializations left the tick lane");
            assertEquals(2, pipeline.landed(), "both publications landed");
            assertEquals(0, pipeline.staleDropped(), "nothing dropped");
            assertTrue(pipeline.serializeNanos() > 0,
                    "the off-tick serialization cost was measured");
            assertEquals("Alice", store.load(ALICE).orElseThrow().name(),
                    "the round-trip restores the first player");
            assertEquals("Bob", store.load(BOB).orElseThrow().name(),
                    "the round-trip restores the second player");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }

    @Test
    void theOlderSnapshotDropsAndTheOtherPlayersGateStaysIndependent() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s6gate:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 2);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            PlayerDataStore store = new PlayerDataStore(dir.resolve("playerdata"));
            PlayerSnapshot aliceOld = richSnapshot(ALICE, "Alice", 12.5);
            PlayerSnapshot aliceNew = richSnapshot(ALICE, "Alice", 99.5);
            PlayerSnapshot bob = richSnapshot(BOB, "Bob", -3.5);
            byte[] aliceNewBytes = store.serialize(aliceNew);
            byte[] bobBytes = store.serialize(bob);

            // The older Alice save's serialization BLOCKS on the compute
            // pool (the hold below) — its publication arrives at the gate
            // only after the newer Alice save already landed: the §15
            // refusal. Bob's save carries the LOWER global sequence but a
            // DIFFERENT name: the independent per-player sequence spaces
            // must let it land anyway.
            CountDownLatch holdOlder = new CountDownLatch(1);
            CountDownLatch aliceOldDone = pipeline.submitSave("player-" + ALICE,
                    aliceOld,
                    snapshot -> {
                        try {
                            holdOlder.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return store.serialize(snapshot);
                    },
                    bytes -> store.writeSerialized(ALICE, bytes));
            CountDownLatch bobDone = pipeline.submitSave("player-" + BOB,
                    bob, store::serialize, bytes -> store.writeSerialized(BOB, bytes));
            CountDownLatch aliceNewDone = pipeline.submitSave("player-" + ALICE,
                    aliceNew, store::serialize, bytes -> store.writeSerialized(ALICE, bytes));

            drainUntil(domain, compute, io, bobDone, aliceNewDone);
            Path aliceFile = dir.resolve("playerdata").resolve(ALICE + ".zpd");
            Path bobFile = dir.resolve("playerdata").resolve(BOB + ".zpd");
            assertArrayEquals(aliceNewBytes, Files.readAllBytes(aliceFile),
                    "the newer Alice snapshot owns the file");
            assertArrayEquals(bobBytes, Files.readAllBytes(bobFile),
                    "the other player's lower-sequence save landed (independent gates)");

            // Release the older save: its publication arrives last — the
            // gate drops it, the file keeps the newer bytes, and the
            // dropped save's durability latch still releases (the newer
            // content on disk satisfies the demand — the supersession).
            holdOlder.countDown();
            drainUntil(domain, compute, io, aliceOldDone);
            assertTrue(aliceOldDone.await(0, TimeUnit.MILLISECONDS),
                    "the superseded save's latch released");
            assertArrayEquals(aliceNewBytes, Files.readAllBytes(aliceFile),
                    "the older snapshot never overwrote the newer file");
            assertEquals(1, pipeline.staleDropped(), "the older save dropped at the gate");
            assertEquals(2, pipeline.landed(),
                    "exactly the two fresh saves landed (the older dropped)");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }

    @Test
    void theFailedPublicationStillReleasesItsDurabilityDemand() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s6fail:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            PlayerDataStore store = new PlayerDataStore(dir.resolve("playerdata"));
            PlayerSnapshot alice = richSnapshot(ALICE, "Alice", 12.5);
            long failedBefore = io.failedCount();

            CountDownLatch done = pipeline.submitSave("player-" + ALICE,
                    alice, store::serialize,
                    bytes -> {
                        throw new IllegalStateException("the disk is gone (the loud failure)");
                    });
            drainUntil(domain, compute, io, done);
            assertTrue(done.await(0, TimeUnit.MILLISECONDS),
                    "the failed save's latch released — the caller's walk never hangs");
            assertEquals(0, pipeline.landed(), "the failed save never landed");
            assertFalse(Files.exists(dir.resolve("playerdata").resolve(ALICE + ".zpd")),
                    "the failed save wrote nothing");
            // the failure stays loud at the lane: markFailed runs on the
            // worker after the latch released — poll briefly
            long deadline = System.currentTimeMillis() + 2_000;
            while (io.failedCount() <= failedBefore
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(io.failedCount() > failedBefore,
                    "the failure stayed loud at the lane");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }

    /** The owner's drain loop (the ticker's own rhythm), bounded. */
    private static void drainUntil(OwnershipDomain domain, ComputeSubsystem compute,
                                   BlockingIoLane io, CountDownLatch... done)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!allDone(done) && System.currentTimeMillis() < deadline) {
            domain.enter();
            try {
                compute.drainResults(domain);
                io.drainCompletions(domain);
            } finally {
                domain.exit();
            }
            // The bounded pause (the established helper's own 10ms await).
            done[0].await(10, TimeUnit.MILLISECONDS);
        }
    }

    private static boolean allDone(CountDownLatch... done) {
        for (CountDownLatch latch : done) {
            if (latch.getCount() > 0) {
                return false;
            }
        }
        return true;
    }
}
