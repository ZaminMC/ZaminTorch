package net.zaminmc.torch.server.decoration;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.armorstand.ArmorStand;
import net.zaminmc.torch.server.armorstand.ArmorStandDataStore;
import net.zaminmc.torch.server.armorstand.ArmorStandSystem;
import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.endcrystal.EnderCrystalDataStore;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.world.WorldSavePipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE TILE-EXTRA TRIO'S PIPELINE ADOPTION'S OWN EVIDENCE (slice S5):
 * the ender-crystal marks', the armor stands', and the decorations'
 * compute-then-publish splits. THE EQUIVALENCE: the split path's bytes
 * and the historical interleaved walks' bytes are BYTE-IDENTICAL; the
 * sync files and the pipeline bytes agree; the load round-trips restore
 * the same rows. THE PIPELINE: the named path through the real compute
 * subsystem + I/O lane, each store gating independently under its own
 * name.
 */
class TileExtraSaveSplitTest {

    @TempDir
    Path dir;

    /** The historical ender-crystal walk (the pre-S5 save body, verbatim). */
    private static byte[] legacyCrystalBytes(Set<Long> cells) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('E');
        out.writeByte('C');
        out.writeByte(1);
        writeVarInt(out, cells.size());
        for (long cell : cells) {
            out.writeLong(cell);
        }
        out.flush();
        return sink.toByteArray();
    }

    /** The historical armor-stand walk (the pre-S5 save body, verbatim). */
    private static byte[] legacyStandBytes(List<ArmorStandSystem.StandRecord> stands)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('A');
        out.writeByte('D');
        out.writeByte(1);
        writeVarInt(out, stands.size());
        for (ArmorStandSystem.StandRecord stand : stands) {
            out.writeDouble(stand.x());
            out.writeDouble(stand.y());
            out.writeDouble(stand.z());
            out.writeFloat(stand.yaw());
            out.writeByte(stand.status());
            for (ItemStack piece : stand.equipment()) {
                legacyStack(out, piece);
            }
            legacyPose(out, stand.head());
            legacyPose(out, stand.body());
            legacyPose(out, stand.leftArm());
            legacyPose(out, stand.rightArm());
            legacyPose(out, stand.leftLeg());
            legacyPose(out, stand.rightLeg());
        }
        out.flush();
        return sink.toByteArray();
    }

    /** The historical decoration walk (the pre-S5 save body, verbatim). */
    private static byte[] legacyDecorationBytes(
            List<DecorationSystem.FrameRecord> frames,
            List<DecorationSystem.PaintingRecord> paintings) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('E');
        out.writeByte('D');
        out.writeByte(1);
        writeVarInt(out, frames.size());
        for (DecorationSystem.FrameRecord record : frames) {
            out.writeInt(record.pos().x());
            out.writeInt(record.pos().y());
            out.writeInt(record.pos().z());
            out.writeByte(record.facing());
            legacyStack(out, record.item());
            out.writeByte(record.rotation());
        }
        writeVarInt(out, paintings.size());
        for (DecorationSystem.PaintingRecord record : paintings) {
            out.writeInt(record.pos().x());
            out.writeInt(record.pos().y());
            out.writeInt(record.pos().z());
            out.writeByte(record.facing());
            byte[] bytes = record.motive().getBytes(StandardCharsets.UTF_8);
            writeVarInt(out, bytes.length);
            out.write(bytes);
        }
        out.flush();
        return sink.toByteArray();
    }

    /** The ZCD item form (present byte + identifier + count + damage + name). */
    private static void legacyStack(DataOutputStream out, ItemStack stack) throws Exception {
        if (stack == null || stack.isEmpty()) {
            out.writeByte(0);
            return;
        }
        out.writeByte(1);
        byte[] id = stack.type().identifier().toString().getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, id.length);
        out.write(id);
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

    private static void legacyPose(DataOutputStream out, ArmorStand.Pose pose) throws Exception {
        if (pose == null) {
            pose = ArmorStand.Pose.ZERO;
        }
        out.writeFloat(pose.pitch());
        out.writeFloat(pose.yaw());
        out.writeFloat(pose.roll());
    }

    private static void writeString(DataOutputStream out, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws Exception {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    @Test
    void crystalSplitIsByteIdenticalToTheHistoricalWalkAndRoundTrips() throws Exception {
        Set<Long> cells = new HashSet<>(List.of(123456789L, -987654321L, 42L));

        EnderCrystalDataStore store = new EnderCrystalDataStore(dir.resolve("crystals.bin"));

        byte[] split = store.serialize(cells);
        assertArrayEquals(legacyCrystalBytes(cells), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(cells);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("crystals.bin")),
                "the sync save's file carries the split path's bytes");
        Set<Long> restored = store.load();
        assertEquals(cells, restored, "the destroyed-cell marks round-trip");
    }

    @Test
    void armorStandSplitIsByteIdenticalToTheHistoricalWalkAndRoundTrips() throws Exception {
        List<ArmorStandSystem.StandRecord> stands = List.of(
                new ArmorStandSystem.StandRecord(1.5, 40.0, -2.25, 90.0f,
                        List.of(ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1),
                                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                                ItemStack.of(BuiltinItems.IRON_SWORD, 1).withName("Blade")),
                        (byte) 0x10,
                        new ArmorStand.Pose(0.1f, 0.2f, 0.3f), null,
                        new ArmorStand.Pose(1.0f, 0.0f, -1.0f), null, null, null),
                new ArmorStandSystem.StandRecord(-7.0, 65.5, 11.0, -45.0f,
                        List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                                ItemStack.EMPTY, ItemStack.EMPTY),
                        (byte) 0, null, null, null, null, null, null));

        ArmorStandDataStore store = new ArmorStandDataStore(dir.resolve("stands.bin"));

        byte[] split = store.serialize(stands);
        assertArrayEquals(legacyStandBytes(stands), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(stands);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("stands.bin")),
                "the sync save's file carries the split path's bytes");
        List<ArmorStandSystem.StandRecord> restored = store.load();
        assertEquals(2, restored.size(), "the stand rows round-trip");
        assertEquals(BuiltinItems.DIAMOND_HELMET, restored.get(0).equipment().get(0).type());
        assertEquals(0x10, restored.get(0).status() & 0xFF);
        assertEquals(new ArmorStand.Pose(0.1f, 0.2f, 0.3f), restored.get(0).head());
        assertEquals(-45.0f, restored.get(1).yaw());
    }

    @Test
    void decorationSplitIsByteIdenticalToTheHistoricalWalkAndRoundTrips() throws Exception {
        List<DecorationSystem.FrameRecord> frames = List.of(
                new DecorationSystem.FrameRecord(new BlockPosition(3, 40, -2), 2,
                        ItemStack.of(BuiltinItems.DIAMOND, 1).withName("Shiny"), 5),
                new DecorationSystem.FrameRecord(new BlockPosition(0, 1, 0), 0,
                        ItemStack.EMPTY, 0));
        List<DecorationSystem.PaintingRecord> paintings = List.of(
                new DecorationSystem.PaintingRecord(new BlockPosition(-9, 65, 11), 1, "Kebab"),
                new DecorationSystem.PaintingRecord(new BlockPosition(4, 4, 4), 3, "Wanderer"));

        DecorationDataStore store = new DecorationDataStore(dir.resolve("decorations.bin"));

        byte[] split = store.serialize(frames, paintings);
        assertArrayEquals(legacyDecorationBytes(frames, paintings), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(frames, paintings);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("decorations.bin")),
                "the sync save's file carries the split path's bytes");
        DecorationDataStore.DecorationSnapshot restored = store.load();
        assertEquals(2, restored.frames().size(), "the frame rows round-trip");
        assertEquals(BuiltinItems.DIAMOND, restored.frames().get(0).item().type());
        assertEquals("Shiny", restored.frames().get(0).item().displayName());
        assertEquals(5, restored.frames().get(0).rotation());
        assertEquals(2, restored.paintings().size(), "the painting rows round-trip");
        assertEquals("Kebab", restored.paintings().get(0).motive());
    }

    @Test
    void theTrioRidesThePipelineThroughTheLanes() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s5trio:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            EnderCrystalDataStore crystalStore =
                    new EnderCrystalDataStore(dir.resolve("live-crystals.bin"));
            Set<Long> cells = new HashSet<>(List.of(77L, 88L, 99L));
            byte[] crystalBytes = crystalStore.serialize(cells);

            ArmorStandDataStore standStore =
                    new ArmorStandDataStore(dir.resolve("live-stands.bin"));
            List<ArmorStandSystem.StandRecord> stands = List.of(
                    new ArmorStandSystem.StandRecord(2.0, 30.0, 3.0, 180.0f,
                            List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                                    ItemStack.EMPTY, ItemStack.EMPTY),
                            (byte) 0, null, null, null, null, null, null));
            byte[] standBytes = standStore.serialize(stands);

            DecorationDataStore decorationStore =
                    new DecorationDataStore(dir.resolve("live-decorations.bin"));
            List<DecorationSystem.FrameRecord> frames = List.of(
                    new DecorationSystem.FrameRecord(new BlockPosition(1, 2, 3), 1,
                            ItemStack.of(BuiltinItems.DIAMOND, 1), 2));
            List<DecorationSystem.PaintingRecord> paintings = List.of(
                    new DecorationSystem.PaintingRecord(new BlockPosition(5, 6, 7), 2, "Kebab"));
            byte[] decorationBytes = decorationStore.serialize(frames, paintings);

            CountDownLatch crystalsDone = pipeline.submitSave("ender-crystal", cells,
                    crystalStore::serialize, crystalStore::writeSerialized);
            CountDownLatch standsDone = pipeline.submitSave("armor-stand", stands,
                    standStore::serialize, standStore::writeSerialized);
            CountDownLatch decorationsDone = pipeline.submitSave("decoration",
                    new DecorationDataStore.SaveInput(frames, paintings),
                    input -> decorationStore.serialize(input.frames(), input.paintings()),
                    decorationStore::writeSerialized);
            long deadline = System.currentTimeMillis() + 10_000;
            while ((!crystalsDone.await(10, TimeUnit.MILLISECONDS)
                    || !standsDone.await(10, TimeUnit.MILLISECONDS)
                    || !decorationsDone.await(10, TimeUnit.MILLISECONDS))
                    && System.currentTimeMillis() < deadline) {
                domain.enter();
                try {
                    compute.drainResults(domain);
                    io.drainCompletions(domain);
                } finally {
                    domain.exit();
                }
            }
            assertTrue(crystalsDone.await(0, TimeUnit.MILLISECONDS), "the crystal save landed");
            assertTrue(standsDone.await(0, TimeUnit.MILLISECONDS), "the stand save landed");
            assertTrue(decorationsDone.await(0, TimeUnit.MILLISECONDS),
                    "the decoration save landed");
            assertArrayEquals(crystalBytes, Files.readAllBytes(dir.resolve("live-crystals.bin")),
                    "the landed crystal file carries exactly the pure serializer's bytes");
            assertArrayEquals(standBytes, Files.readAllBytes(dir.resolve("live-stands.bin")),
                    "the landed stand file carries exactly the pure serializer's bytes");
            assertArrayEquals(decorationBytes,
                    Files.readAllBytes(dir.resolve("live-decorations.bin")),
                    "the landed decoration file carries exactly the pure serializer's bytes");
            assertEquals(3, crystalStore.load().size(), "the crystal rows round-trip");
            assertEquals(1, standStore.load().size(), "the stand rows round-trip");
            assertEquals(1, decorationStore.load().paintings().size(),
                    "the decoration rows round-trip");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }
}
