package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * The comparator's stored analog outputs ("ZCD" format, version 2 —
 * Waves H1 + FL4-F10), one file per world: the reference's
 * {@code ComparatorBlockEntity.writeNbt/readNbt} row (the whole file is
 * that one {@code OutputSignal} int per position) over the engine's
 * {@code RedstoneSystem.comparatorOutputs} maps — the note store's own
 * durability rules.
 *
 * <p>Layout: magic 'Z','C','D',2 — outputs(varint count; each: x(i32)
 * y(i32) z(i32) output(i32, the reference's own int row — no clamp on
 * the read, the walk's own writers keep it 0..15) dimension(i8 — Wave
 * FL4-F10's stamp, the TF-9 ItemDataStore v8 row's own law: the signed
 * byte carries -1 as u8 255; a v1 file's rows are overworld rows, the
 * migration law — every historical comparator lived in the only system
 * that existed)).</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load
 * as absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud. Save points are the engine's
 * {@code saveAllNow} (shutdown, console save); the restore runs at boot
 * BEFORE any comparator update — the reference's loaded BE answers its
 * stored strength until the first re-arms it.</p>
 */
public final class ComparatorDataStore {

    /** One stored output row: the position, its world, and the strength. */
    public record StoredOutput(BlockPosition position, int dimension, int output) {
    }

    private static final Logger LOGGER = Logger.getLogger(ComparatorDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'C';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 2;
    private static final int FORMAT_VERSION_1 = 1;

    private final Path file;

    public ComparatorDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full stored-output rows (every dimension's, stamped —
     * Wave FL4-F10). Callers snapshot state on the tick thread before
     * calling (the engine routes this through the ticker). The
     * synchronous form composes the S4 pipeline's own two halves — the
     * pure serialization over the already-immutable list, the file
     * publication — so the sync save's bytes and the pipeline's bytes are
     * identical by construction (one serializer, one file half).
     */
    public void save(java.util.List<StoredOutput> outputs) {
        writeSerialized(serialize(outputs));
    }

    /**
     * The pure serialization (Slice S4 — the compute pool's own half): the
     * immutable list IN (the engine's stamped union over every dimension's
     * {@code comparatorSnapshot()} copy), the ZCD bytes OUT — no world
     * read, no I/O.
     */
    public byte[] serialize(java.util.List<StoredOutput> outputs) {
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new java.io.BufferedOutputStream(sink))) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            writeVarInt(out, outputs.size());
            for (StoredOutput row : outputs) {
                BlockPosition position = row.position();
                out.writeInt(position.x());
                out.writeInt(position.y());
                out.writeInt(position.z());
                out.writeInt(row.output());
                // The TF-9 law: the signed byte carries the dimension
                // (-1 lands as u8 255).
                out.writeByte(row.dimension());
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Comparator serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZCD bytes: the temp-file write + the
     * atomic move (the non-atomic fallback preserved), the historical save
     * walk's own file half with the store's own failure contract (the
     * severe log, never a throw). Safe from any thread that holds the
     * publication right (the save pipeline's per-storage lock orders the
     * async publications; the synchronous save is uncontended).
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
            LOGGER.severe("Comparator output save failed for " + file + ": " + e);
        }
    }

    /**
     * Loads the stored-output rows, each stamped with its dimension; an
     * absent or corrupt file loads empty (corrupt files are preserved,
     * not destroyed). A v1 file's rows load as overworld rows (the
     * migration law — the only system that existed was the overworld's).
     */
    public java.util.List<StoredOutput> load() {
        if (!Files.exists(file)) {
            return new java.util.ArrayList<>();
        }
        java.util.List<StoredOutput> loaded = new java.util.ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                LOGGER.warning("Comparator output state " + file + " is not ZCD; loading empty");
                return new java.util.ArrayList<>();
            }
            int version = in.readByte() & 0xFF;
            if (version != FORMAT_VERSION && version != FORMAT_VERSION_1) {
                LOGGER.warning("Comparator output state " + file + " version " + version
                        + " unsupported; loading empty");
                return new java.util.ArrayList<>();
            }
            int count = readVarInt(in);
            for (int i = 0; i < count; i++) {
                BlockPosition position = new BlockPosition(in.readInt(), in.readInt(), in.readInt());
                int output = in.readInt();
                // The FL4-F10 stamp: a v1 row is an overworld row.
                int dimension = version >= FORMAT_VERSION ? in.readByte() : 0;
                loaded.add(new StoredOutput(position, dimension, output));
            }
            return loaded;
        } catch (IOException e) {
            LOGGER.warning("Comparator output state " + file + " corrupt; loading empty ("
                    + e + ")");
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
        int position = 0;
        int current;
        do {
            current = in.readByte();
            value |= (current & 0x7F) << position;
            position += 7;
            if (position > 35) {
                throw new IOException("VarInt too long");
            }
        } while ((current & 0x80) != 0);
        return value;
    }
}
