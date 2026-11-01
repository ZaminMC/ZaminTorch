package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.jukebox.JukeboxDataStore;
import net.zaminmc.torch.server.redstone.ComparatorDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE TILE-STATE PAIR'S PIPELINE ADOPTION'S OWN EVIDENCE (slice S4,
 * part two): the comparator's stored outputs and the jukebox's records —
 * the two stores whose map copies are ALREADY the immutable input
 * contract (the boxed ints; the engine's immutable ItemStack), so the
 * split is the pure serializer + the file half over the existing
 * snapshot. THE EQUIVALENCE: the split path's bytes and the historical
 * interleaved walk's bytes are BYTE-IDENTICAL; the sync file and the
 * pipeline bytes agree; the loads round-trip.
 */
class TileStateSaveSplitTest {

    @TempDir
    Path dir;

    private static void drainUntil(OwnershipDomain domain, ComputeSubsystem compute,
                                   BlockingIoLane io, CountDownLatch done) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!done.await(10, TimeUnit.MILLISECONDS)
                && System.currentTimeMillis() < deadline) {
            domain.enter();
            try {
                compute.drainResults(domain);
                io.drainCompletions(domain);
            } finally {
                domain.exit();
            }
        }
    }

    // ---- the historical walks (the pre-S4 save bodies, verbatim) ----

    private static byte[] legacyComparatorBytes(
            java.util.List<ComparatorDataStore.StoredOutput> outputs)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('C');
        out.writeByte('D');
        // Wave FL4-F10: the ZCD v2 row — the dimension stamp rides the
        // output's tail (the TF-9 law).
        out.writeByte(2);
        writeVarInt(out, outputs.size());
        for (ComparatorDataStore.StoredOutput row : outputs) {
            BlockPosition position = row.position();
            out.writeInt(position.x());
            out.writeInt(position.y());
            out.writeInt(position.z());
            out.writeInt(row.output());
            out.writeByte(row.dimension());
        }
        out.flush();
        return sink.toByteArray();
    }

    private static byte[] legacyJukeboxBytes(int dimension,
            Map<BlockPosition, ItemStack> records)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('J');
        out.writeByte('D');
        out.writeByte(2);
        writeVarInt(out, records.size());
        for (Map.Entry<BlockPosition, ItemStack> entry : records.entrySet()) {
            BlockPosition position = entry.getKey();
            out.writeInt(position.x());
            out.writeInt(position.y());
            out.writeInt(position.z());
            // the historical writeStack walk
            ItemStack stack = entry.getValue();
            if (stack == null || stack.isEmpty()) {
                out.writeByte(0);
            } else {
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
            out.writeByte(dimension);
        }
        out.flush();
        return sink.toByteArray();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws Exception {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    @Test
    void comparatorSplitIsByteIdenticalAndRoundTrips() throws Exception {
        // Wave FL4-F10: the rows carry their dimension stamps — the
        // overworld, the nether and the End in one file (the -1 stamp
        // lands as u8 255 on the wire).
        java.util.List<ComparatorDataStore.StoredOutput> live = java.util.List.of(
                new ComparatorDataStore.StoredOutput(new BlockPosition(1, 2, 3), 0, 15),
                new ComparatorDataStore.StoredOutput(new BlockPosition(-4, 5, 6), -1, 0),
                new ComparatorDataStore.StoredOutput(new BlockPosition(7, 8, 9), 1, 3));

        ComparatorDataStore store = new ComparatorDataStore(dir.resolve("comparators.bin"));
        byte[] split = store.serialize(live);
        assertArrayEquals(legacyComparatorBytes(live), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(live);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("comparators.bin")),
                "the sync save's file carries the split path's bytes");
        java.util.List<ComparatorDataStore.StoredOutput> restored = store.load();
        assertEquals(3, restored.size());
        for (ComparatorDataStore.StoredOutput row : restored) {
            if (row.position().equals(new BlockPosition(1, 2, 3))) {
                assertEquals(0, row.dimension());
                assertEquals(15, row.output());
            } else if (row.position().equals(new BlockPosition(-4, 5, 6))) {
                assertEquals(-1, row.dimension());
                assertEquals(0, row.output());
            } else if (row.position().equals(new BlockPosition(7, 8, 9))) {
                assertEquals(1, row.dimension());
                assertEquals(3, row.output());
            }
        }
    }

    @Test
    void jukeboxSplitIsByteIdenticalAndRoundTrips() throws Exception {
        Map<BlockPosition, ItemStack> live = new LinkedHashMap<>();
        live.put(new BlockPosition(2, 3, 4), ItemStack.of(BuiltinItems.IRON_INGOT, 1));
        live.put(new BlockPosition(5, 6, 7), ItemStack.EMPTY);
        live.put(new BlockPosition(8, 9, 10),
                new ItemStack(BuiltinItems.COAL, 1, 0).withName("Named Disc"));

        JukeboxDataStore store = new JukeboxDataStore(dir.resolve("jukeboxes.bin"));
        // Wave FL4-GAP2 Slice C: the union save, each row stamped with its
        // world (the ZJD v2 law).
        Map<Integer, Map<BlockPosition, ItemStack>> union = Map.of(0, live);
        byte[] split = store.serialize(union);
        assertArrayEquals(legacyJukeboxBytes(0, live), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(union);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("jukeboxes.bin")),
                "the sync save's file carries the split path's bytes");
        Map<BlockPosition, ItemStack> restored = new LinkedHashMap<>();
        for (JukeboxDataStore.Loaded row : store.load()) {
            assertEquals(0, row.dimension(), "every row stamps the overworld");
            restored.put(row.position(), row.record());
        }
        assertEquals(1, restored.get(new BlockPosition(2, 3, 4)).count());
        assertTrue(restored.get(new BlockPosition(5, 6, 7)).isEmpty(),
                "the empty record loads empty");
        assertEquals("Named Disc",
                restored.get(new BlockPosition(8, 9, 10)).displayName());
    }

    @Test
    void thePairRidesThePipelineThroughTheLanes() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s4tile:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            ComparatorDataStore comparatorStore =
                    new ComparatorDataStore(dir.resolve("live-comparators.bin"));
            JukeboxDataStore jukeboxStore =
                    new JukeboxDataStore(dir.resolve("live-jukeboxes.bin"));

            java.util.List<ComparatorDataStore.StoredOutput> outputs = java.util.List.of(
                    new ComparatorDataStore.StoredOutput(new BlockPosition(1, 1, 1), 0, 9),
                    new ComparatorDataStore.StoredOutput(new BlockPosition(2, 2, 2), -1, 4));
            Map<BlockPosition, ItemStack> records =
                    Map.of(new BlockPosition(2, 2, 2), ItemStack.of(BuiltinItems.COAL, 1));
            Map<Integer, Map<BlockPosition, ItemStack>> recordUnion = Map.of(0, records);
            byte[] comparatorBytes = comparatorStore.serialize(outputs);
            byte[] jukeboxBytes = jukeboxStore.serialize(recordUnion);

            CountDownLatch comparatorDone = pipeline.submitSave("comparator", outputs,
                    comparatorStore::serialize, comparatorStore::writeSerialized);
            CountDownLatch jukeboxDone = pipeline.submitSave("jukebox", recordUnion,
                    jukeboxStore::serialize, jukeboxStore::writeSerialized);
            drainUntil(domain, compute, io, comparatorDone);
            drainUntil(domain, compute, io, jukeboxDone);
            assertTrue(comparatorDone.await(0, TimeUnit.MILLISECONDS), "the comparator landed");
            assertTrue(jukeboxDone.await(0, TimeUnit.MILLISECONDS), "the jukebox landed");

            assertArrayEquals(comparatorBytes, Files.readAllBytes(dir.resolve("live-comparators.bin")),
                    "the landed comparator file carries the serializer's bytes");
            assertArrayEquals(jukeboxBytes, Files.readAllBytes(dir.resolve("live-jukeboxes.bin")),
                    "the landed jukebox file carries the serializer's bytes");
            assertEquals(9, comparatorStore.load().get(0).output());
            assertEquals(0, comparatorStore.load().get(0).dimension());
            assertEquals(1, jukeboxStore.load().get(0).record().count(),
                    "the record row round-trips");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }
}
