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
 * File-backed sign text ("ZSD" format, version 1), one file per world.
 *
 * <p>Layout: magic 'Z','S','D',1 - signs(varint count; each: x(i32) y(i32)
 * z(i32) - 4 lines(varint-length UTF-8)). The same durability rules as the
 * other stores: writes are atomic (temp file then atomic move), corrupt
 * files load as absent and are preserved beside the storage path instead of
 * being destroyed, and save failures are loud (§54/§286). Save points are
 * the engine's {@code saveAllNow} (shutdown, console save).</p>
 */
public final class SignDataStore {

    private static final Logger LOGGER = Logger.getLogger(SignDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'S';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public SignDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full sign map. Callers snapshot state on the tick thread
     * before calling (the engine routes this through the ticker).
     */
    public void save(Map<BlockPosition, String[]> signs) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, signs.size());
                for (Map.Entry<BlockPosition, String[]> entry : signs.entrySet()) {
                    BlockPosition position = entry.getKey();
                    out.writeInt(position.x());
                    out.writeInt(position.y());
                    out.writeInt(position.z());
                    String[] lines = entry.getValue();
                    for (int i = 0; i < SignManager.LINES; i++) {
                        String line = i < lines.length ? lines[i] : "";
                        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
                        writeVarInt(out, bytes.length);
                        out.write(bytes);
                    }
                }
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOGGER.severe("Sign save failed: " + e.getMessage());
        }
    }

    /**
     * Loads the sign map; a corrupt or missing file reads as empty (a corrupt
     * file is preserved beside the storage path for manual recovery, the
     * established store behavior).
     */
    public Map<BlockPosition, String[]> load() {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2 || in.readByte() != FORMAT_VERSION) {
                throw new IOException("Not a ZSD v1 file");
            }
            int count = readVarInt(in);
            Map<BlockPosition, String[]> signs = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                BlockPosition position = new BlockPosition(in.readInt(), in.readInt(), in.readInt());
                String[] lines = new String[SignManager.LINES];
                for (int line = 0; line < SignManager.LINES; line++) {
                    byte[] bytes = new byte[readVarInt(in)];
                    in.readFully(bytes);
                    lines[line] = new String(bytes, StandardCharsets.UTF_8);
                }
                signs.put(position, lines);
            }
            return signs;
        } catch (Exception e) {
            preserveCorrupt(e);
            return new LinkedHashMap<>();
        }
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
