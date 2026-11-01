package net.zaminmc.torch.server.sign;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.command.EngineCommandResults;
import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.world.WorldSavePipeline;
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
 * THE SIGN STORE'S PIPELINE ADOPTION'S OWN EVIDENCE (slice S4, part
 * three): the pure serializer over the manager's two snapshot maps, the
 * file half, the named pipeline path. THE EQUIVALENCE: the split path's
 * bytes and the historical interleaved walk's bytes (replicated here)
 * are BYTE-IDENTICAL — the four-line rows and the CommandStats tail's
 * flattened pair walk included; the sync file and the pipeline bytes
 * agree; the load round-trip restores the signs and the bindings. Wave
 * FL4-GAP2 Slice C: the ZSD v3 row carries the dimension stamp (the
 * chest store's own v+1 law) — the legacy walk below writes the v3
 * form, each row's own world at its tail.
 */
class SignDataStoreSplitTest {

    @TempDir
    Path dir;

    private static byte[] legacyBytes(int dimension,
                                      Map<BlockPosition, String[]> signs,
                                      Map<BlockPosition, EngineCommandResults> stats)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('S');
        out.writeByte('D');
        out.writeByte(3);
        writeVarInt(out, signs.size());
        for (Map.Entry<BlockPosition, String[]> entry : signs.entrySet()) {
            BlockPosition position = entry.getKey();
            out.writeInt(position.x());
            out.writeInt(position.y());
            out.writeInt(position.z());
            String[] lines = entry.getValue();
            for (int i = 0; i < SignManager.LINES; i++) {
                String line = i < lines.length ? lines[i] : "";
                writeString(out, line);
            }
            // the CommandStats tail: the pair count + the pairs (the
            // no-bindings arm writes the 0 count) — the envelope walk the
            // historical writeCommandStats ran
            EngineCommandResults results = stats == null ? null : stats.get(position);
            if (results == null || !results.hasBindings()) {
                writeVarInt(out, 0);
            } else {
                java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> envelope =
                        new LinkedHashMap<>();
                results.writeNbt(envelope);
                java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> tail =
                        net.zaminmc.torch.server.nbt.NbtOps.getCompound(envelope, "CommandStats");
                writeVarInt(out, tail.size());
                for (Map.Entry<String, net.zaminmc.torch.server.nbt.Snbt.Tag> pair
                        : tail.entrySet()) {
                    writeString(out, pair.getKey());
                    writeString(out, net.zaminmc.torch.server.nbt.NbtOps
                            .getString(tail, pair.getKey()));
                }
            }
            out.writeByte(dimension);
        }
        out.flush();
        return sink.toByteArray();
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
    void signSplitIsByteIdenticalAndRoundTrips() throws Exception {
        Map<BlockPosition, String[]> signs = new LinkedHashMap<>();
        signs.put(new BlockPosition(4, 33, -8),
                new String[]{"Shop", "Open", "", ""});
        signs.put(new BlockPosition(0, 64, 0),
                new String[]{"", "Second row", "Line", "Tail"});

        SignDataStore store = new SignDataStore(dir.resolve("signs.bin"));
        SignDataStore.UnionInput union = new SignDataStore.UnionInput(Map.of(0, signs), Map.of());
        byte[] split = store.serialize(union);
        assertArrayEquals(legacyBytes(0, signs, null), split,
                "the off-tick serialization and the historical walk are byte-identical (the no-stats arm)");

        store.save(union);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("signs.bin")),
                "the sync save's file carries the split path's bytes");

        java.util.List<SignDataStore.Loaded> rows = store.load();
        assertEquals(2, rows.size());
        for (SignDataStore.Loaded row : rows) {
            assertEquals(0, row.dimension(), "every row stamps the overworld");
            assertArrayEquals(signs.get(row.signs().keySet().iterator().next()),
                    row.signs().values().iterator().next());
        }
    }

    @Test
    void signSplitCarriesTheCommandStatsTailAndRoundTrips() throws Exception {
        Map<BlockPosition, String[]> signs = new LinkedHashMap<>();
        signs.put(new BlockPosition(1, 2, 3), new String[]{"Stat", "", "", ""});

        EngineCommandResults results = new EngineCommandResults();
        results.set(EngineCommandResults.Type.SUCCESS_COUNT, "Alice", "points");
        Map<BlockPosition, EngineCommandResults> stats = new LinkedHashMap<>();
        stats.put(new BlockPosition(1, 2, 3), results);

        SignDataStore store = new SignDataStore(dir.resolve("stat-signs.bin"));
        SignDataStore.UnionInput union = new SignDataStore.UnionInput(
                Map.of(0, signs), Map.of(0, stats));
        byte[] split = store.serialize(union);
        assertArrayEquals(legacyBytes(0, signs, stats), split,
                "the off-tick serialization and the historical walk are byte-identical (the tail arm)");

        store.save(union);
        SignDataStore.Loaded loaded = store.load().get(0);
        EngineCommandResults restored = loaded.stats().get(new BlockPosition(1, 2, 3));
        assertEquals("Alice", restored.sourceOf(EngineCommandResults.Type.SUCCESS_COUNT),
                "the binding's source restored");
        assertEquals("points", restored.objectiveOf(EngineCommandResults.Type.SUCCESS_COUNT),
                "the binding's objective restored");
    }

    @Test
    void theSignRidesThePipelineThroughTheLanes() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s4sign:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            Map<BlockPosition, String[]> signs =
                    Map.of(new BlockPosition(9, 9, 9), new String[]{"Pipe", "", "", ""});
            SignDataStore store = new SignDataStore(dir.resolve("live-signs.bin"));
            SignDataStore.UnionInput input = new SignDataStore.UnionInput(
                    Map.of(0, signs), Map.of());
            byte[] pure = store.serialize(input);

            CountDownLatch done = pipeline.submitSave("sign", input,
                    store::serialize,
                    store::writeSerialized);
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
            assertArrayEquals(pure, Files.readAllBytes(dir.resolve("live-signs.bin")),
                    "the landed file carries exactly the pure serializer's bytes");
            assertEquals("Pipe", store.load().get(0).signs()
                    .get(new BlockPosition(9, 9, 9))[0], "the sign row round-trips");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }
}
