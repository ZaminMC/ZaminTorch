package net.zaminmc.torch.server.block;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * File-backed scheduled block-tick state ("ZST" format, version 1) — the
 * surface's own persistence (the in-memory envelope retired): the pending
 * TileTicks rows survive a restart, the same rows the anvil import's
 * load arm (c7654e1) lands from a vanilla world's NBT.
 *
 * <p>Layout: magic 'Z','S','T',1 — rows(varint count; each: x(int) y(int)
 * z(int) blockId(string) dueRelative(varint) priority(varint)). The
 * due-relative is the reference's own chunk-save shape
 * {@code time - world time} at the capture (a negative row is an
 * overdue entry — it fires on the first pass after the restore, exactly
 * the reference's load).</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load
 * as absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud. Save points are the engine's
 * {@code saveAllNow} (shutdown, console save) — the capture on the tick
 * lane, the serialization on the compute pool, the file half on the
 * I/O lane.</p>
 */
public final class ScheduledTickDataStore {

    private static final Logger LOGGER = Logger.getLogger(ScheduledTickDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'S';
    private static final int MAGIC_2 = 'T';
    private static final int FORMAT_VERSION = 1;

    /** The persistable row (the system's own snapshot form). */
    public record TickRow(int x, int y, int z, String blockId, int dueRelative, int priority) {
    }

    private final Path file;

    public ScheduledTickDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the tick rows. Callers snapshot the rows on the tick thread
     * before calling (the engine routes this through the ticker); the
     * synchronous form composes the two halves — the pure serialization
     * and the file publication.
     */
    public void save(List<TickRow> rows) {
        writeSerialized(serialize(rows));
    }

    /**
     * The pure serialization (the compute pool's own half): the immutable
     * rows IN, the ZST bytes OUT — no world read, no I/O.
     */
    public byte[] serialize(List<TickRow> rows) {
        Objects.requireNonNull(rows, "rows");
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(sink)) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            writeVarInt(out, rows.size());
            for (TickRow row : rows) {
                out.writeInt(row.x());
                out.writeInt(row.y());
                out.writeInt(row.z());
                writeString(out, row.blockId());
                writeVarInt(out, row.dueRelative());
                writeVarInt(out, row.priority());
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Scheduled-tick serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZST bytes: the temp-file write + the
     * atomic move (the non-atomic fallback preserved).
     */
    public void writeSerialized(byte[] data) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.write(data);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.severe("Scheduled-tick state save failed for " + file + ": " + e);
        }
    }

    /**
     * Loads the tick rows; an absent or corrupt file loads empty (corrupt
     * files are preserved, not destroyed). An unresolvable block id at the
     * RESTORE site drops with its own warning (the engine's convention).
     */
    public List<TickRow> load() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        List<TickRow> loaded = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                LOGGER.warning("Scheduled-tick state " + file + " is not ZST; loading empty");
                return new ArrayList<>();
            }
            int version = in.readByte() & 0xFF;
            if (version != FORMAT_VERSION) {
                LOGGER.warning("Scheduled-tick state " + file + " version " + version
                        + " unsupported; loading empty");
                return new ArrayList<>();
            }
            int count = readVarInt(in);
            if (count < 0 || count > 100_000) {
                LOGGER.warning("Scheduled-tick state " + file + " implausible count; loading empty");
                return new ArrayList<>();
            }
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                String blockId = readString(in);
                int dueRelative = readVarInt(in);
                int priority = readVarInt(in);
                loaded.add(new TickRow(x, y, z, blockId, dueRelative, priority));
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            LOGGER.warning("Scheduled-tick state " + file + " corrupt; loading empty (" + corrupt + ")");
            return new ArrayList<>();
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > 256) {
            throw new IOException("Implausible string length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
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
                throw new IOException("VarInt too long");
            }
        }
    }
}
