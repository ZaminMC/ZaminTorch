package net.zaminmc.torch.server.noteblock;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.world.WorldSavePipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
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
 * THE NOTE TILE'S PIPELINE ADOPTION'S OWN EVIDENCE (slice S4): the deep
 * capture + the pure serializer + the file publication (the store's own
 * failure contract — the severe log — preserved). THE EQUIVALENCE: the
 * split path's bytes and the historical interleaved walk's bytes are
 * BYTE-IDENTICAL; the sync file and the pipeline bytes agree; the load
 * round-trip restores the same rows. THE PIPELINE: the named path
 * through the real compute subsystem + I/O lane. Wave FL4-GAP2 Slice C:
 * the ZND v2 row carries the dimension stamp (the chest store's own
 * v+1 law) — the legacy walk below writes the v2 form, each row's own
 * world at its tail.
 */
class NoteDataStoreSplitTest {

    @TempDir
    Path dir;

    /** The historical walk (the pre-S4 save body, verbatim — the v2 form
     *  with the dimension stamp at each row's tail). */
    private static byte[] legacyBytes(int dimension,
            Map<BlockPosition, NoteBlockSystem.NoteTile> notes) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('N');
        out.writeByte('D');
        out.writeByte(2);
        writeVarInt(out, notes.size());
        for (Map.Entry<BlockPosition, NoteBlockSystem.NoteTile> entry : notes.entrySet()) {
            BlockPosition position = entry.getKey();
            out.writeInt(position.x());
            out.writeInt(position.y());
            out.writeInt(position.z());
            out.writeByte(entry.getValue().note());
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
    void noteSplitIsByteIdenticalToTheHistoricalWalkAndRoundTrips() throws Exception {
        Map<BlockPosition, NoteBlockSystem.NoteTile> live = new LinkedHashMap<>();
        live.put(new BlockPosition(3, 40, -2), new NoteBlockSystem.NoteTile((byte) 0, true));
        live.put(new BlockPosition(-9, 65, 11), new NoteBlockSystem.NoteTile((byte) 25, false));
        // the powered flag never persists — the format's own rule
        live.put(new BlockPosition(0, 1, 0), new NoteBlockSystem.NoteTile((byte) 12, true));

        NoteDataStore store = new NoteDataStore(dir.resolve("notes.bin"));

        Map<Integer, Map<BlockPosition, NoteBlockSystem.NoteTile>> union = Map.of(0, live);
        byte[] split = store.serialize(store.capture(union));
        assertArrayEquals(legacyBytes(0, live), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(union);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("notes.bin")),
                "the sync save's file carries the split path's bytes");
        java.util.List<NoteDataStore.Loaded> restored = store.load();
        assertEquals(3, restored.size());
        assertEquals(0, restored.stream().filter(
                row -> row.position().equals(new BlockPosition(3, 40, -2))).findFirst().orElseThrow()
                .note());
        assertEquals((byte) 25, restored.stream().filter(
                row -> row.position().equals(new BlockPosition(-9, 65, 11))).findFirst().orElseThrow()
                .note());
        assertEquals((byte) 12, restored.stream().filter(
                row -> row.position().equals(new BlockPosition(0, 1, 0))).findFirst().orElseThrow()
                .note());
        restored.forEach(row -> assertEquals(0, row.dimension(),
                "every row stamps the overworld"));
    }

    @Test
    void theNoteRidesThePipelineThroughTheLanes() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s4note:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            Map<BlockPosition, NoteBlockSystem.NoteTile> live = new LinkedHashMap<>();
            live.put(new BlockPosition(5, 5, 5), new NoteBlockSystem.NoteTile((byte) 7, false));
            NoteDataStore store = new NoteDataStore(dir.resolve("live-notes.bin"));
            NoteDataStore.Snapshot snapshot = store.capture(Map.of(0, live));
            byte[] pure = store.serialize(snapshot);

            CountDownLatch done = pipeline.submitSave("note", snapshot,
                    store::serialize, store::writeSerialized);
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
            assertTrue(done.await(0, TimeUnit.MILLISECONDS), "the save landed");
            assertArrayEquals(pure, Files.readAllBytes(dir.resolve("live-notes.bin")),
                    "the landed file carries exactly the pure serializer's bytes");
            assertEquals((byte) 7, store.load().stream().filter(
                    row -> row.position().equals(new BlockPosition(5, 5, 5)))
                    .findFirst().orElseThrow().note(),
                    "the note row round-trips");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }
}
