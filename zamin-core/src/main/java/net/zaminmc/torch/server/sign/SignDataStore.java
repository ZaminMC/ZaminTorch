package net.zaminmc.torch.server.sign;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * File-backed sign text ("ZSD" format, version 3), one file per world.
 *
 * <p>Layout: magic 'Z','S','D',3 - signs(varint count; each: x(i32) y(i32)
 * z(i32) - 4 lines(varint-length UTF-8) - the CommandStats tail (varint
 * pair count; each: key/value varint-length UTF-8) — the same flattened
 * tail the ZCB v2 store carries, Slice 91 Wave B) then dimension(i8, Wave
 * FL4-GAP2 Slice C: the row's own world, the chest store's own v+1 law;
 * version 1/2 files load as overworld rows). Version 1 files (no tail per
 * entry) load with empty stat bindings — the version-aware walk. The same
 * durability rules as the other stores: writes are atomic (temp file then
 * atomic move), corrupt files load as absent and are preserved beside the
 * storage path instead of being destroyed, and save failures are loud
 * (§54/§286). Save points are the engine's {@code saveAllNow} (shutdown,
 * console save).</p>
 */
public final class SignDataStore {

    private static final Logger LOGGER = Logger.getLogger(SignDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'S';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 3;

    /**
     * One load's answer: the per-dimension rows, each carrying the texts
     * and the command-stat bindings of its own world's signs.
     */
    public record Loaded(int dimension,
                         Map<BlockPosition, String[]> signs,
                         Map<BlockPosition, net.zaminmc.torch.server.command.EngineCommandResults> stats) {
        public static Loaded empty() {
            return new Loaded(0, new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }

    private final Path file;

    public SignDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * The two-map save input (the pipeline's named path's own snapshot
     * object) — one dimension's family (the FL4-GAP2 Slice C union's own
     * member).
     */
    public record SaveInput(Map<BlockPosition, String[]> signs,
                            Map<BlockPosition, net.zaminmc.torch.server.command.EngineCommandResults> stats) {
    }

    /**
     * The union save input (the FL4-GAP2 Slice C law): each dimension's
     * own family, one row per sign stamped with its world — a nether sign
     * and an overworld sign at the same coordinates are TWO tiles.
     */
    public record UnionInput(
            Map<Integer, Map<BlockPosition, String[]>> signsByDimension,
            Map<Integer, Map<BlockPosition, net.zaminmc.torch.server.command.EngineCommandResults>>
                    statsByDimension) {
    }

    /**
     * Saves every dimension's sign map with the command-stat tails (the
     * FL4-GAP2 Slice C union). Callers snapshot state on the tick thread
     * before calling (the engine routes this through the ticker).
     */
    public void save(UnionInput union) {
        writeSerialized(serialize(union));
    }

    /**
     * The pure serialization (Slice S4 — the compute pool's own half): the
     * immutable maps IN (each manager's own snapshot copies — the sign
     * lines are fresh arrays per capture, the command results are the
     * engine's immutable rows), the ZSD bytes OUT — no world read, no
     * I/O. The byte walk is the historical save walk's own (byte-identical,
     * the CommandStats tail + the dimension stamp included).
     */
    public byte[] serialize(UnionInput union) {
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new java.io.BufferedOutputStream(sink))) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            int total = 0;
            for (Map<BlockPosition, String[]> byDimension : union.signsByDimension().values()) {
                total += byDimension.size();
            }
            writeVarInt(out, total);
            for (Map.Entry<Integer, Map<BlockPosition, String[]>> byDimension
                    : union.signsByDimension().entrySet()) {
                int dimension = byDimension.getKey();
                Map<BlockPosition, net.zaminmc.torch.server.command.EngineCommandResults> stats =
                        union.statsByDimension().getOrDefault(dimension, Map.of());
                for (Map.Entry<BlockPosition, String[]> entry
                        : byDimension.getValue().entrySet()) {
                    BlockPosition position = entry.getKey();
                    out.writeInt(position.x());
                    out.writeInt(position.y());
                    out.writeInt(position.z());
                    String[] lines = entry.getValue();
                    for (int i = 0; i < SignManager.LINES; i++) {
                        String line = i < lines.length ? lines[i] : "";
                        writeString(out, line);
                    }
                    // The CommandStats tail (Slice 91 Wave B — the ZCB v2
                    // shape): the flattened pair count + the pairs.
                    writeCommandStats(out, stats.get(position));
                    // The signed byte carries the dimension — -1 lands as u8 255.
                    out.writeByte(dimension);
                }
            }
        } catch (java.io.IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Sign serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZSD bytes: the temp-file write + the
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
            LOGGER.severe("Sign save failed: " + e.getMessage());
        }
    }

    /**
     * Loads the sign rows, each stamped with its dimension; a corrupt or
     * missing file reads as empty (a corrupt file is preserved beside the
     * storage path for manual recovery, the established store behavior).
     * Version 1 files carry no tail per entry — their bindings load empty.
     * Version 1/2 files' rows carry no dimension byte — they load as
     * overworld rows (the migration law every FL4-GAP2 store carries).
     */
    public java.util.List<Loaded> load() {
        if (!Files.exists(file)) {
            return java.util.List.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZSD file");
            }
            int version = in.readUnsignedByte();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IOException("Unsupported ZSD version: " + version);
            }
            int count = readVarInt(in);
            java.util.List<Loaded> rows = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                BlockPosition position = new BlockPosition(in.readInt(), in.readInt(), in.readInt());
                String[] lines = new String[SignManager.LINES];
                for (int line = 0; line < SignManager.LINES; line++) {
                    byte[] bytes = new byte[readVarInt(in)];
                    in.readFully(bytes);
                    lines[line] = new String(bytes, StandardCharsets.UTF_8);
                }
                Map<BlockPosition, net.zaminmc.torch.server.command.EngineCommandResults> stats =
                        new LinkedHashMap<>();
                if (version >= 2) {
                    net.zaminmc.torch.server.command.EngineCommandResults results =
                            readCommandStats(in);
                    if (results != null) {
                        stats.put(position, results);
                    }
                }
                int dimension = version >= FORMAT_VERSION ? in.readByte() : 0;
                rows.add(new Loaded(dimension,
                        Map.of(position, lines), stats));
            }
            return rows;
        } catch (Exception e) {
            preserveCorrupt(e);
            return java.util.List.of();
        }
    }

    /**
     * The CommandStats tail's write (the reference's compound flattened —
     * the ZCB v2 store's own shape): the pair count then the key/value
     * strings, every bound type's {@code <Name>Name/<Name>Objective} pair.
     */
    private static void writeCommandStats(DataOutputStream out,
            net.zaminmc.torch.server.command.EngineCommandResults results)
            throws java.io.IOException {
        if (results == null || !results.hasBindings()) {
            writeVarInt(out, 0);
            return;
        }
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
     * same readNbt walk /stats set rides. Null when the entry carries none. */
    private static net.zaminmc.torch.server.command.EngineCommandResults readCommandStats(
            DataInputStream in) throws java.io.IOException {
        int pairs = readVarInt(in);
        if (pairs < 0 || pairs > 64) {
            throw new IOException("command-stats pair count out of range: " + pairs);
        }
        if (pairs == 0) {
            return null;
        }
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> envelope = new LinkedHashMap<>();
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stats = new LinkedHashMap<>();
        for (int i = 0; i < pairs; i++) {
            String key = readString(in);
            String value = readString(in);
            net.zaminmc.torch.server.nbt.NbtOps.putString(stats, key, value);
        }
        net.zaminmc.torch.server.command.EngineCommandResults results =
                new net.zaminmc.torch.server.command.EngineCommandResults();
        net.zaminmc.torch.server.nbt.NbtOps.putCompound(envelope, "CommandStats", stats);
        results.readNbt(envelope);
        return results;
    }

    private void preserveCorrupt(Exception cause) {
        LOGGER.severe("Sign store corrupt, loading empty: " + cause.getMessage());
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // the copy is best-effort; the load already failed empty
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        byte[] bytes = new byte[readVarInt(in)];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
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
                throw new IOException("VarInt too big");
            }
        }
    }
}
