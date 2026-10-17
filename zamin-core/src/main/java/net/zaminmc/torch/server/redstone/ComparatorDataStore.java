package net.zaminmc.torch.server.redstone;

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
 * The comparator's stored analog outputs ("ZCD" format, version 1 —
 * Wave H1), one file per world: the reference's
 * {@code ComparatorBlockEntity.writeNbt/readNbt} row (the whole file is
 * that one {@code OutputSignal} int per position) over the engine's
 * {@code RedstoneSystem.comparatorOutputs} map — the note store's own
 * durability rules.
 *
 * <p>Layout: magic 'Z','C','D',1 — outputs(varint count; each: x(i32)
 * y(i32) z(i32) output(i32, the reference's own int row — no clamp on
 * the read, the walk's own writers keep it 0..15)).</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load
 * as absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud. Save points are the engine's
 * {@code saveAllNow} (shutdown, console save); the restore runs at boot
 * BEFORE any comparator update — the reference's loaded BE answers its
 * stored strength until the first re-arms it.</p>
 */
public final class ComparatorDataStore {

    private static final Logger LOGGER = Logger.getLogger(ComparatorDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'C';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public ComparatorDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full stored-output map. Callers snapshot state on the tick
     * thread before calling (the engine routes this through the ticker).
     */
    public void save(Map<BlockPosition, Integer> outputs) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, outputs.size());
                for (Map.Entry<BlockPosition, Integer> entry : outputs.entrySet()) {
                    BlockPosition position = entry.getKey();
                    out.writeInt(position.x());
                    out.writeInt(position.y());
                    out.writeInt(position.z());
                    out.writeInt(entry.getValue());
                }
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
     * Loads the stored-output map; an absent or corrupt file loads empty
     * (corrupt files are preserved, not destroyed).
     */
    public Map<BlockPosition, Integer> load() {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        Map<BlockPosition, Integer> loaded = new LinkedHashMap<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                LOGGER.warning("Comparator output state " + file + " is not ZCD; loading empty");
                return new LinkedHashMap<>();
            }
            int version = in.readByte() & 0xFF;
            if (version != FORMAT_VERSION) {
                LOGGER.warning("Comparator output state " + file + " version " + version
                        + " unsupported; loading empty");
                return new LinkedHashMap<>();
            }
            int count = readVarInt(in);
            for (int i = 0; i < count; i++) {
                BlockPosition position = new BlockPosition(in.readInt(), in.readInt(), in.readInt());
                loaded.put(position, in.readInt());
            }
            return loaded;
        } catch (IOException e) {
            LOGGER.warning("Comparator output state " + file + " corrupt; loading empty ("
                    + e + ")");
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
