package net.zaminmc.torch.server.noteblock;

import net.zaminmc.torch.block.BlockPosition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

/**
 * File-backed note state ("ZND" format, version 2), one file per world —
 * the hopper store's own durability rules.
 *
 * <p>Layout: magic 'Z','N','D',2 - notes(varint count; each: x(i32) y(i32)
 * z(i32) note(u8, the reference's writeNbt "note" byte alone — the
 * powered flag never persists) then dimension(i8, Wave FL4-GAP2 Slice C:
 * the row's own world, the chest store's own v+1 law; version 1 files
 * load as overworld rows).</p>
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
    private static final int FORMAT_VERSION = 2;

    private final Path file;

    public NoteDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves every dimension's note map (the FL4-GAP2 Slice C union: one
     * file, each row stamped with its own world — a nether note and an
     * overworld note at the same coordinates are TWO tiles). Callers
     * snapshot state on the tick thread before calling (the engine routes
     * this through the ticker). The synchronous form composes the S4
     * pipeline's own three halves — the deep capture, the pure
     * serialization, the file publication — so the sync save's bytes and
     * the pipeline's bytes are identical by construction (one serializer,
     * one file half).
     *
     * @param notes each dimension's live map (dimension id -> the map its
     *              own NoteBlockSystem owns)
     */
    public void save(Map<Integer, Map<BlockPosition, NoteBlockSystem.NoteTile>> notes) {
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

        /** One noteblock's persisted rows, the owning dimension stamped. */
        public record Row(int x, int y, int z, int note, int dimension) {
        }
    }

    /**
     * The deep capture of every dimension's live note maps — called ON the
     * owning tick lane (the system's snapshotTiles() is the shallow copy;
     * this walks it into the immutable rows). The caller's map is not
     * retained.
     *
     * @param notes each dimension's live map (the FL4-GAP2 Slice C union)
     */
    public Snapshot capture(
            Map<Integer, Map<BlockPosition, NoteBlockSystem.NoteTile>> notes) {
        java.util.List<Snapshot.Row> rows = new java.util.ArrayList<>();
        for (Map.Entry<Integer, Map<BlockPosition, NoteBlockSystem.NoteTile>> byDimension
                : notes.entrySet()) {
            int dimension = byDimension.getKey();
            for (Map.Entry<BlockPosition, NoteBlockSystem.NoteTile> entry
                    : byDimension.getValue().entrySet()) {
                BlockPosition position = entry.getKey();
                rows.add(new Snapshot.Row(position.x(), position.y(), position.z(),
                        entry.getValue().note(), dimension));
            }
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
                // The signed byte carries the dimension — -1 lands as u8 255.
                out.writeByte(row.dimension());
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

    /** One loaded note row, stamped with the world it lives in. */
    public record Loaded(BlockPosition position, int dimension, byte note) {
    }

    /**
     * Loads the note rows, each stamped with its dimension. A version 1
     * file's rows carry no dimension byte — they load as overworld rows
     * (the migration law every FL4-GAP2 store carries). An absent or
     * corrupt file loads empty (corrupt files are preserved, not
     * destroyed).
     */
    public java.util.List<Loaded> load() {
        if (!Files.exists(file)) {
            return new java.util.ArrayList<>();
        }
        java.util.List<Loaded> loaded = new java.util.ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                LOGGER.warning("Note state " + file + " is not ZND; loading empty");
                return new java.util.ArrayList<>();
            }
            int version = in.readByte() & 0xFF;
            if (version < 1 || version > FORMAT_VERSION) {
                LOGGER.warning("Note state " + file + " version " + version
                        + " unsupported; loading empty");
                return new java.util.ArrayList<>();
            }
            int count = readVarInt(in);
            for (int i = 0; i < count; i++) {
                BlockPosition position = new BlockPosition(in.readInt(), in.readInt(), in.readInt());
                byte note = in.readByte();
                int dimension = version >= FORMAT_VERSION ? in.readByte() : 0;
                loaded.add(new Loaded(position, dimension, note));
            }
            return loaded;
        } catch (IOException e) {
            LOGGER.warning("Note state " + file + " corrupt; loading empty (" + e + ")");
            return new java.util.ArrayList<>();
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
