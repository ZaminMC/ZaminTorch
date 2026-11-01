package net.zaminmc.torch.server.banner;

import net.zaminmc.torch.block.BlockPosition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * File-backed banner tiles ("ZBD" format, version 2), one file per world.
 *
 * <p>Layout: magic 'Z','B','D',2 - tiles(varint count; each: x(i32) y(i32)
 * z(i32) base(u8) patterns(varint count; each: hash(varint-length UTF-8)
 * color(u8)) then dimension(i8, Wave FL4-GAP2 Slice C: the row's own
 * world, the chest store's own v+1 law; version 1 files load as overworld
 * rows). The same durability rules as the other stores: writes are
 * atomic (temp file then atomic move), corrupt files load as absent and
 * are preserved beside the storage path, save failures are loud. Save
 * points are the engine's {@code saveAllNow}.</p>
 */
public final class BannerDataStore {

    private static final Logger LOGGER = Logger.getLogger(BannerDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'B';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 2;

    private final Path file;

    public BannerDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves every dimension's tile map (the FL4-GAP2 Slice C union: one
     * file, each row stamped with its own world — a nether banner and an
     * overworld banner at the same coordinates are TWO tiles). The caller
     * snapshots on the tick thread.
     *
     * @param tiles each dimension's live map (dimension id -> the map its
     *              own BannerManager owns)
     */
    public void save(Map<Integer, Map<BlockPosition, BannerManager.BannerTile>> tiles) {
        writeSerialized(serialize(tiles));
    }

    /**
     * The pure serialization (the compute pool's own half): the immutable
     * union IN, the ZBD bytes OUT — no world read, no I/O.
     */
    public byte[] serialize(Map<Integer, Map<BlockPosition, BannerManager.BannerTile>> tiles) {
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new java.io.BufferedOutputStream(sink))) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            int total = 0;
            for (Map<BlockPosition, BannerManager.BannerTile> byDimension : tiles.values()) {
                total += byDimension.size();
            }
            writeVarInt(out, total);
            for (Map.Entry<Integer, Map<BlockPosition, BannerManager.BannerTile>> byDimension
                    : tiles.entrySet()) {
                int dimension = byDimension.getKey();
                for (Map.Entry<BlockPosition, BannerManager.BannerTile> entry
                        : byDimension.getValue().entrySet()) {
                    BlockPosition position = entry.getKey();
                    out.writeInt(position.x());
                    out.writeInt(position.y());
                    out.writeInt(position.z());
                    out.writeByte(entry.getValue().base() & 0xF);
                    List<BannerManager.Pattern> patterns = entry.getValue().patterns();
                    writeVarInt(out, patterns.size());
                    for (BannerManager.Pattern pattern : patterns) {
                        writeString(out, pattern.hash());
                        out.writeByte(pattern.color() & 0xF);
                    }
                    // The signed byte carries the dimension — -1 lands as u8 255.
                    out.writeByte(dimension);
                }
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Banner serialize failed", e);
        }
        return sink.toByteArray();
    }

    /** The temp-file write + the atomic move (the store's failure contract). */
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
            LOGGER.severe("Banner save failed: " + e.getMessage());
        }
    }

    /** One loaded banner row, stamped with the world it lives in. */
    public record Loaded(BlockPosition position, int dimension, BannerManager.BannerTile tile) {
    }

    /**
     * Loads the tile rows, each stamped with its dimension. A version 1
     * file's rows carry no dimension byte — they load as overworld rows
     * (the migration law every FL4-GAP2 store carries). A corrupt or
     * missing file reads as empty.
     */
    public java.util.List<Loaded> load() {
        java.util.List<Loaded> rows = new java.util.ArrayList<>();
        if (!Files.exists(file)) {
            return rows;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZBD file");
            }
            int version = in.readUnsignedByte();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IOException("Unsupported ZBD version: " + version);
            }
            int count = readVarInt(in);
            for (int i = 0; i < count; i++) {
                BlockPosition position = new BlockPosition(
                        in.readInt(), in.readInt(), in.readInt());
                int base = in.readUnsignedByte() & 0xF;
                int patternRows = readVarInt(in);
                if (patternRows < 0 || patternRows > BannerManager.MAX_PATTERNS) {
                    throw new IOException("pattern count out of range: " + patternRows);
                }
                List<BannerManager.Pattern> patterns = new ArrayList<>(patternRows);
                for (int row = 0; row < patternRows; row++) {
                    patterns.add(new BannerManager.Pattern(readString(in),
                            in.readUnsignedByte() & 0xF));
                }
                int dimension = version >= FORMAT_VERSION ? in.readByte() : 0;
                rows.add(new Loaded(position, dimension,
                        new BannerManager.BannerTile(base, patterns)));
            }
            return rows;
        } catch (Exception e) {
            preserveCorrupt(e);
            return new java.util.ArrayList<>();
        }
    }

    private void preserveCorrupt(Exception cause) {
        LOGGER.severe("Banner store corrupt, loading empty: " + cause.getMessage());
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // best-effort; the load already failed empty
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
