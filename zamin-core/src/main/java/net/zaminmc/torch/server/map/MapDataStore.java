package net.zaminmc.torch.server.map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * File-backed map state ("ZMP" format, version 1) — the map family's own
 * persistence (the reference's per-world {@code map_<id>.dat} saved-data
 * family over the engine's standalone-store conventions, the
 * WeatherDataStore/VillageDataStore pattern): every map of ONE dimension
 * rides one file (the reference keys its saved data per world — the
 * overworld's {@code data/}, the nether's {@code DIM-1/data/}), each map
 * carrying the rows the reference's SavedMapData.writeNbt owns (the
 * dimension, the center pair, the scale, the 128x128 colors), plus the id
 * allocator's own next-free counter (the reference's
 * {@code getSavedDataCount("map")} consult — the count law over a
 * never-shrinking set).
 *
 * <p>Layout: magic 'Z','M','P',1 — nextFreeId(int) — maps(varint count;
 * each: id(int) dimension(byte) xCenter(int) zCenter(int) scale(byte)
 * colors(16384 bytes)). Writes are atomic (temp file then atomic move),
 * corrupt files load as absent and are preserved beside the storage path
 * instead of being destroyed, and save failures are loud. Save points are
 * the engine's {@code saveAllNow} (shutdown, console save) — the capture
 * on the tick lane, the serialization on the compute pool, the file half
 * on the I/O lane (the pipeline's named path), the un-pipelined fallback
 * the synchronous form.</p>
 */
public final class MapDataStore {

    private static final Logger LOGGER = Logger.getLogger(MapDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'M';
    private static final int MAGIC_2 = 'P';
    private static final int FORMAT_VERSION = 1;
    /** The defensive ceiling (a world's map count — the plausible bound). */
    private static final int MAX_MAPS = 100_000;

    /** One map's persisted row (SavedMapData.writeNbt's own fields). */
    public record MapRow(int id, int dimension, int xCenter, int zCenter,
                         int scale, byte[] colors) {

        public MapRow {
            if (colors == null || colors.length != MapData.CANVAS_SIZE
                    * MapData.CANVAS_SIZE) {
                throw new IllegalArgumentException("Map colors must be 128x128");
            }
        }
    }

    /** The immutable save input: the rows + the allocator's next-free id. */
    public record SaveInput(int nextFreeId, List<MapRow> rows) {
    }

    /** The load's answer: the same pair (a fresh world answers 0/empty). */
    public record Loaded(int nextFreeId, List<MapRow> rows) {
    }

    private final Path file;

    public MapDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the map rows. Callers snapshot the state on the tick thread
     * before calling; the synchronous form composes the two halves — the
     * pure serialization and the file publication.
     */
    public void save(SaveInput input) {
        writeSerialized(serialize(input));
    }

    /**
     * The pure serialization (the compute pool's own half): the immutable
     * snapshot IN (the fresh rows + the counter), the ZMP bytes OUT — no
     * world read, no I/O.
     */
    public byte[] serialize(SaveInput input) {
        Objects.requireNonNull(input, "input");
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(sink)) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            out.writeInt(input.nextFreeId());
            writeVarInt(out, input.rows().size());
            for (MapRow row : input.rows()) {
                out.writeInt(row.id());
                out.writeByte(row.dimension());
                out.writeInt(row.xCenter());
                out.writeInt(row.zCenter());
                out.writeByte(row.scale());
                out.write(row.colors());
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Map state serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZMP bytes: the temp-file write + the
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
            LOGGER.severe("Map state save failed for " + file + ": " + e);
        }
    }

    /**
     * The loaded state; an absent or corrupt file loads empty (corrupt
     * files are preserved, not destroyed).
     */
    public Loaded load() {
        if (!Files.exists(file)) {
            return new Loaded(0, new ArrayList<>());
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                LOGGER.warning("Map state " + file + " is not ZMP; loading empty");
                return new Loaded(0, new ArrayList<>());
            }
            int version = in.readByte() & 0xFF;
            if (version != FORMAT_VERSION) {
                LOGGER.warning("Map state " + file + " version " + version
                        + " unsupported; loading empty");
                return new Loaded(0, new ArrayList<>());
            }
            int nextFreeId = in.readInt();
            if (nextFreeId < 0) {
                throw new IOException("Implausible next id: " + nextFreeId);
            }
            int count = readVarInt(in);
            if (count < 0 || count > MAX_MAPS) {
                LOGGER.warning("Map state " + file + " implausible count; loading empty");
                return new Loaded(0, new ArrayList<>());
            }
            List<MapRow> rows = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int id = in.readInt();
                int dimension = in.readByte();
                int xCenter = in.readInt();
                int zCenter = in.readInt();
                int scale = in.readByte();
                byte[] colors = new byte[MapData.CANVAS_SIZE * MapData.CANVAS_SIZE];
                in.readFully(colors);
                rows.add(new MapRow(id, dimension, xCenter, zCenter, scale, colors));
            }
            return new Loaded(nextFreeId, rows);
        } catch (IOException | RuntimeException corrupt) {
            LOGGER.warning("Map state " + file + " corrupt; loading empty (" + corrupt + ")");
            return new Loaded(0, new ArrayList<>());
        }
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
