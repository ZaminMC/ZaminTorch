package net.zaminmc.torch.server.commandblock;

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
 * THE COMMAND-BLOCK TILE'S PIPELINE ADOPTION'S OWN EVIDENCE (slice S4,
 * part four): the deep capture (the CommandStats tail pre-flattened at
 * capture time — the envelope walk on the owning lane), the pure
 * serializer, the file half. THE EQUIVALENCE: the split path's bytes and
 * the historical interleaved walk's bytes (replicated here, the tail's
 * envelope walk included) are BYTE-IDENTICAL; the round-trip load
 * restores the four rows and the bindings; the named path through the
 * real lanes lands exactly the serializer's bytes. Wave FL4-GAP2 Slice
 * C: the ZCB v3 row carries the dimension stamp (the chest store's own
 * v+1 law) — the legacy walk below writes the v3 form, each row's own
 * world at its tail.
 */
class CommandBlockDataStoreSplitTest {

    @TempDir
    Path dir;

    private static byte[] legacyBytes(int dimension,
            Map<BlockPosition, CommandBlockManager.CommandState> blocks)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('C');
        out.writeByte('B');
        out.writeByte(3);
        writeVarInt(out, blocks.size());
        for (Map.Entry<BlockPosition, CommandBlockManager.CommandState> entry
                : blocks.entrySet()) {
            BlockPosition position = entry.getKey();
            out.writeInt(position.x());
            out.writeInt(position.y());
            out.writeInt(position.z());
            CommandBlockManager.CommandState executor = entry.getValue();
            writeString(out, executor.command());
            out.writeInt(executor.successCount());
            out.writeBoolean(executor.trackOutput());
            writeString(out, executor.lastOutput() == null ? "" : executor.lastOutput());
            // the tail: the envelope walk the historical writeCommandStats ran
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> envelope =
                    new LinkedHashMap<>();
            executor.commandResults().writeNbt(envelope);
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stats =
                    net.zaminmc.torch.server.nbt.NbtOps.getCompound(envelope, "CommandStats");
            writeVarInt(out, stats.size());
            for (Map.Entry<String, net.zaminmc.torch.server.nbt.Snbt.Tag> pair
                    : stats.entrySet()) {
                writeString(out, pair.getKey());
                writeString(out, net.zaminmc.torch.server.nbt.NbtOps
                        .getString(stats, pair.getKey()));
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
    void commandBlockSplitIsByteIdenticalAndRoundTrips() throws Exception {
        Map<BlockPosition, CommandBlockManager.CommandState> live = new LinkedHashMap<>();
        CommandBlockManager.CommandState plain = new CommandBlockManager.CommandState();
        plain.setCommand("say hi");
        live.put(new BlockPosition(2, 40, 6), plain);

        CommandBlockManager.CommandState bound = new CommandBlockManager.CommandState();
        bound.setCommand("tp @p 0 64 0");
        bound.recordOutput("Teleported Alice");
        bound.commandResults().set(EngineCommandResults.Type.SUCCESS_COUNT, "Alice", "points");
        live.put(new BlockPosition(-3, 5, 12), bound);

        CommandBlockDataStore store = new CommandBlockDataStore(dir.resolve("command.bin"));
        Map<Integer, Map<BlockPosition, CommandBlockManager.CommandState>> union = Map.of(0, live);
        byte[] split = store.serialize(store.capture(union));
        assertArrayEquals(legacyBytes(0, live), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(union);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("command.bin")),
                "the sync save's file carries the split path's bytes");

        java.util.Map<BlockPosition, CommandBlockManager.CommandState> restored = new LinkedHashMap<>();
        for (CommandBlockDataStore.Loaded row : store.load()) {
            assertEquals(0, row.dimension(), "every row stamps the overworld");
            restored.put(row.position(), row.state());
        }
        assertEquals(2, restored.size());
        assertEquals("say hi", restored.get(new BlockPosition(2, 40, 6)).command());
        CommandBlockManager.CommandState restoredBound =
                restored.get(new BlockPosition(-3, 5, 12));
        assertEquals("Teleported Alice", restoredBound.lastOutput());
        assertEquals("Alice", restoredBound.commandResults()
                .sourceOf(EngineCommandResults.Type.SUCCESS_COUNT));
        assertEquals("points", restoredBound.commandResults()
                .objectiveOf(EngineCommandResults.Type.SUCCESS_COUNT));
    }

    @Test
    void theCommandBlockRidesThePipelineThroughTheLanes() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s4cmd:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            Map<BlockPosition, CommandBlockManager.CommandState> live = new LinkedHashMap<>();
            CommandBlockManager.CommandState executor = new CommandBlockManager.CommandState();
            executor.setCommand("weather clear");
            live.put(new BlockPosition(1, 1, 1), executor);

            CommandBlockDataStore store = new CommandBlockDataStore(dir.resolve("live-command.bin"));
            CommandBlockDataStore.Snapshot snapshot = store.capture(Map.of(0, live));
            byte[] pure = store.serialize(snapshot);

            CountDownLatch done = pipeline.submitSave("command-block", snapshot,
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
            assertArrayEquals(pure, Files.readAllBytes(dir.resolve("live-command.bin")),
                    "the landed file carries exactly the pure serializer's bytes");
            assertEquals("weather clear", store.load().get(0).state().command(),
                    "the row round-trips");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }
}
