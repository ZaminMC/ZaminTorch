package net.zaminmc.torch.server.noteblock;

import net.zaminmc.torch.block.BlockPosition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * File-backed note state ("ZND" format, version 1), one file per world —
 * the hopper store's own durability rules.
 *
 * <p>Layout: magic 'Z','N','D',1 - notes(varint count; each: x(i32) y(i32)
 * z(i32) note(u8, the reference's writeNbt "note" byte alone — the
 * powered flag never persists).</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load as
 * absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud. Save points are the engine's
 * {@code saveAllNow} (shutdown, console save).</p>
 */
public final class NoteDataStore {

    private static final Logger LOGGER = Logger.getLogger(NoteDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'N';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public NoteDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full note map. Callers snapshot state on the tick thread
     * before calling (the engine routes this through the ticker). The
     * synchronous form composes the S4 pipeline's own three halves — the
     * deep capture, the pure serialization, the file publication — so the
     * sync save's bytes and the pipeline's bytes are identical by
     * construction (one serializer, one file half).
     */
    public void save(Map<BlockPosition, NoteBlockSystem.NoteTile> notes) {
        writeSerialized(serialize(capture(notes)));
    }

    /**
     * The immutable persistence projection (Slice S4 — the Phase-5
     * serialization lane's own input contract): every note row captured
     * on the owning lane, safe to cross into the compute pool — the
     * serializer never reads the live tiles (§31.6's snapshot-before-I/O;
     * the system's own snapshotTiles() is the SHALLOW map copy whose
     * values are the LIVE tiles, the capture is the deep form).
     */
    public record Snapshot(java.util.List<Row> rows) {

        /** One noteblock's persisted rows. */
        public record Row(int x, int y, int z, int note) {
        }
    }

    /**
     * The deep capture of the live note map — called ON the owning tick
     * lane (the system's snapshotTiles() is the shallow copy; this walks
     * it into the immutable rows). The caller's map is not retained.
     */
    public Snapshot capture(Map<BlockPosition, NoteBlockSystem.NoteTile> notes) {
        java.util.List<Snapshot.Row> rows = new java.util.ArrayList<>(notes.size());
        for (Map.Entry<BlockPosition, NoteBlockSystem.NoteTile> entry : notes.entrySet()) {
            BlockPosition position = entry.getKey();
            rows.add(new Snapshot.Row(position.x(), position.y(), position.z(),
                    entry.getValue().note()));
        }
        return new Snapshot(java.util.List.copyOf(rows));
    }

    /**
     * The pure serialization (the compute pool's own half): the immutable
     * snapshot IN, the ZND bytes OUT — no world read, no I/O. The byte
     * walk is the historical save walk's own (version 1, byte-identical).
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
                out.writeByte(row.note());
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Note serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZND bytes: the temp-file write + the
     * atomic move (the non-atomic fallback preserved), the historical save
     * walk's own file half with the store's own failure contract (the
     * severe log, never a throw — the established note-store behavior).
     * Safe from any thread that holds the publication right (the save
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
            try {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.severe("Note state save failed for " + file + ": " + e);
        }
    }

    /**
     * Loads the note map; an absent or corrupt file loads empty (corrupt
     * files are preserved, not destroyed).
     */
    public Map<BlockPosition, Byte> load() {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        Map<BlockPosition, Byte> loaded = new LinkedHashMap<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                LOGGER.warning("Note state " + file + " is not ZND; loading empty");
                return new LinkedHashMap<>();
            }
            int version = in.readByte() & 0xFF;
            if (version != FORMAT_VERSION) {
                LOGGER.warning("Note state " + file + " version " + version
                        + " unsupported; loading empty");
                return new LinkedHashMap<>();
            }
            int count = readVarInt(in);
            for (int i = 0; i < count; i++) {
                BlockPosition position = new BlockPosition(in.readInt(), in.readInt(), in.readInt());
                loaded.put(position, in.readByte());
            }
            return loaded;
        } catch (IOException e) {
            LOGGER.warning("Note state " + file + " corrupt; loading empty (" + e + ")");
            return new LinkedHashMap<>();
        }
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
                throw new IOException("VarInt too long");
            }
        }
    }
}
