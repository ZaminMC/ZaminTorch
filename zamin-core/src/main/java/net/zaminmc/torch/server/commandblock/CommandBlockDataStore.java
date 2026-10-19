package net.zaminmc.torch.server.commandblock;

import net.zaminmc.torch.block.BlockPosition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * The command-block tile persistence (Slice 72 — the sign store's own
 * envelope): the executor's four rows per tile (the reference's
 * writeNbt Command/SuccessCount/TrackOutput/LastOutput) plus the v2
 * CommandStats tail (Slice 90 — the reference's CommandResultStats
 * writeNbt compound: the bound types' <Name>Name/<Name>Objective pairs,
 * absent in v1 files), the atomic temp-file swap, the
 * corrupt-file-preserving load (a corrupt or missing file reads as
 * empty, the established store behavior).
 */
public final class CommandBlockDataStore {

    private static final Logger LOGGER = Logger.getLogger(CommandBlockDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'C';
    private static final int MAGIC_2 = 'B';
    private static final int FORMAT_VERSION = 2;

    private final Path file;

    public CommandBlockDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full tile map. Callers snapshot state on the tick thread
     * before calling (the engine routes this through the ticker). The
     * synchronous form composes the S4 pipeline's own three halves — the
     * deep capture, the pure serialization, the file publication — so the
     * sync save's bytes and the pipeline's bytes are identical by
     * construction (one serializer, one file half).
     */
    public void save(Map<BlockPosition, CommandBlockManager.CommandState> blocks) {
        writeSerialized(serialize(capture(blocks)));
    }

    /**
     * The immutable persistence projection (Slice S4 — the Phase-5
     * serialization lane's own input contract): every command-block row
     * captured on the owning lane, safe to cross into the compute pool —
     * the serializer never reads the live executors (the manager's own
     * snapshot() is the SHALLOW map copy whose values are the LIVE
     * mutable CommandState objects; the capture is the deep form, the
     * CommandStats tail pre-flattened into the immutable pair list at
     * capture time — the envelope walk on the owning lane).
     */
    public record Snapshot(java.util.List<Row> rows) {

        /** One command block's persisted rows. */
        public record Row(int x, int y, int z, String command, int successCount,
                          boolean trackOutput, String lastOutput,
                          java.util.List<StatsPair> statsPairs) {
        }

        /** One CommandStats binding pair (the flattened tail row). */
        public record StatsPair(String key, String value) {
        }
    }

    /**
     * The deep capture of the live tile map — called ON the owning tick
     * lane. The CommandStats tail flattens here (the envelope walk the
     * historical writeCommandStats ran — the compound's own pair order),
     * so the serializer's input is fully immutable.
     */
    public Snapshot capture(Map<BlockPosition, CommandBlockManager.CommandState> blocks) {
        java.util.List<Snapshot.Row> rows = new java.util.ArrayList<>(blocks.size());
        for (Map.Entry<BlockPosition, CommandBlockManager.CommandState> entry
                : blocks.entrySet()) {
            BlockPosition position = entry.getKey();
            CommandBlockManager.CommandState executor = entry.getValue();
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> envelope = new LinkedHashMap<>();
            executor.commandResults().writeNbt(envelope);
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stats =
                    net.zaminmc.torch.server.nbt.NbtOps.getCompound(envelope, "CommandStats");
            java.util.List<Snapshot.StatsPair> pairs = new java.util.ArrayList<>(stats.size());
            for (Map.Entry<String, net.zaminmc.torch.server.nbt.Snbt.Tag> pair : stats.entrySet()) {
                pairs.add(new Snapshot.StatsPair(pair.getKey(),
                        net.zaminmc.torch.server.nbt.NbtOps.getString(stats, pair.getKey())));
            }
            rows.add(new Snapshot.Row(position.x(), position.y(), position.z(),
                    executor.command(), executor.successCount(), executor.trackOutput(),
                    executor.lastOutput(), java.util.List.copyOf(pairs)));
        }
        return new Snapshot(java.util.List.copyOf(rows));
    }

    /**
     * The pure serialization (the compute pool's own half): the immutable
     * snapshot IN, the ZCB bytes OUT — no world read, no I/O. The byte
     * walk is the historical save walk's own (version 2, byte-identical;
     * the tail's pair count + pairs = the historical envelope walk's own
     * output, pre-flattened at capture).
     */
    public byte[] serialize(Snapshot snapshot) {
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new java.io.BufferedOutputStream(sink))) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            writeVarInt(out, snapshot.rows().size());
            for (Snapshot.Row row : snapshot.rows()) {
                out.writeInt(row.x());
                out.writeInt(row.y());
                out.writeInt(row.z());
                writeString(out, row.command());
                out.writeInt(row.successCount());
                out.writeBoolean(row.trackOutput());
                writeString(out, row.lastOutput() == null ? "" : row.lastOutput());
                // The v2 CommandStats tail: the pre-flattened pairs (the
                // capture-time envelope walk's own output).
                writeVarInt(out, row.statsPairs().size());
                for (Snapshot.StatsPair pair : row.statsPairs()) {
                    writeString(out, pair.key());
                    writeString(out, pair.value());
                }
            }
        } catch (java.io.IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Command block serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZCB bytes: the temp-file write + the
     * atomic move, the historical save walk's own file half with the
     * store's own failure contract (the severe log, never a throw). Safe
     * from any thread that holds the publication right (the save
     * pipeline's per-storage lock orders the async publications; the
     * synchronous save is uncontended).
     */
    public void writeSerialized(byte[] data) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.write(data);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOGGER.severe("Command block save failed: " + e.getMessage());
        }
    }

    /**
     * Loads the tile map; a corrupt or missing file reads as empty (a
     * corrupt file is preserved beside the storage path for manual
     * recovery, the established store behavior).
     */
    public Map<BlockPosition, CommandBlockManager.CommandState> load() {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                throw new IllegalArgumentException("bad header");
            }
            int version = in.readUnsignedByte();
            if (version != 1 && version != 2) {
                throw new IllegalArgumentException("bad version: " + version);
            }
            int count = readVarInt(in);
            Map<BlockPosition, CommandBlockManager.CommandState> loaded = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                String command = readString(in);
                int successCount = in.readInt();
                boolean trackOutput = in.readBoolean();
                String lastOutput = readString(in);
                CommandBlockManager.CommandState state = CommandBlockManager.restoredState(
                        command, successCount, trackOutput,
                        lastOutput.isEmpty() ? null : lastOutput);
                if (version >= 2) {
                    readCommandStats(in, state.commandResults());
                }
                loaded.put(new BlockPosition(x, y, z), state);
            }
            return loaded;
        } catch (Exception cause) {
            LOGGER.severe("Command block store corrupt, loading empty: "
                    + cause.getMessage());
            preserveCorrupt();
            return new LinkedHashMap<>();
        }
    }

    /**
     * The CommandStats tail's write (the reference's compound flattened):
     * the pair count then the key/value strings — every bound type's
     * <Name>Name/<Name>Objective pair.
     */
    private static void writeCommandStats(DataOutputStream out,
            net.zaminmc.torch.server.command.EngineCommandResults results)
            throws java.io.IOException {
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> envelope = new LinkedHashMap<>();
        results.writeNbt(envelope);
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stats =
                net.zaminmc.torch.server.nbt.NbtOps.getCompound(envelope, "CommandStats");
        writeVarInt(out, stats.size());
        for (Map.Entry<String, net.zaminmc.torch.server.nbt.Snbt.Tag> pair : stats.entrySet()) {
            writeString(out, pair.getKey());
            writeString(out, net.zaminmc.torch.server.nbt.NbtOps.getString(stats, pair.getKey()));
        }
    }

    /** The CommandStats tail's read: the pairs back into the compound, the
     * same readNbt walk /stats set rides. */
    private static void readCommandStats(DataInputStream in,
            net.zaminmc.torch.server.command.EngineCommandResults results)
            throws java.io.IOException {
        int pairs = readVarInt(in);
        if (pairs < 0 || pairs > 64) {
            throw new IllegalArgumentException("command-stats pair count out of range: " + pairs);
        }
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> envelope = new LinkedHashMap<>();
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stats = new LinkedHashMap<>();
        for (int i = 0; i < pairs; i++) {
            String key = readString(in);
            String value = readString(in);
            net.zaminmc.torch.server.nbt.NbtOps.putString(stats, key, value);
        }
        if (!stats.isEmpty()) {
            net.zaminmc.torch.server.nbt.NbtOps.putCompound(envelope, "CommandStats", stats);
            results.readNbt(envelope);
        }
    }

    /** The corrupt file's beside-the-path copy (manual recovery). */
    private void preserveCorrupt() {
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception bestEffort) {
            // the load already failed empty; the copy is best-effort
        }
    }

    private static void writeString(DataOutputStream out, String value) throws java.io.IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws java.io.IOException {
        int length = readVarInt(in);
        if (length < 0 || length > 1 << 16) {
            throw new IllegalArgumentException("string length out of range: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws java.io.IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws java.io.IOException {
        int value = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 28) {
                throw new IllegalArgumentException("VarInt too large");
            }
        }
    }
}
