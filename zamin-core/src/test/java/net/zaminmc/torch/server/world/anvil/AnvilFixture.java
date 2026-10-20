package net.zaminmc.torch.server.world.anvil;

import net.zaminmc.torch.server.nbt.NbtOps;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * The Anvil test fixture (the gold-standard writer the task's own testing
 * row demands): region files, level.dat, and the binary NBT documents they
 * carry, written by a small port of the REFERENCE's own write side —
 * {@code NbtCompound.write}/{@code NbtIo.writeCompressed} for the
 * documents and {@code RegionFile.writeChunkData} for the sector layout
 * (the 4-byte length + scheme byte + payload, padded to 4 KiB sectors,
 * the 3-byte location entries, the 4-byte timestamps). The reader under
 * test is then exercised against bytes the reference itself could have
 * written — not against bytes the reader's twin produced.
 *
 * <p>The trees are built in the boxed domain ({@code Map<String, Object>}
 * through {@link NbtOps#fromBoxed}) because the {@code Snbt.Tag} factories
 * are package-private to the nbt package — the engine's own construction
 * surface. A byte array is a {@code List<Byte>}: the boxed form of the
 * reader's byte-array adaptation, written to disk as the format's own
 * TAG_Byte_Array (id 7).</p>
 */
public final class AnvilFixture {

    private AnvilFixture() {
    }

    /** The boxed compound (an insertion-ordered builder). */
    public static Map<String, Object> compound() {
        return new LinkedHashMap<>();
    }

    /** The boxed byte-array row (a List of Byte — TAG_Byte_Array on disk). */
    public static List<Byte> byteArray(byte[] values) {
        List<Byte> out = new ArrayList<>(values.length);
        for (byte value : values) {
            out.add(value);
        }
        return out;
    }

    // ------------------------------------------------------------- the binary NBT writer

    /**
     * One named compound document (the reference's NbtIo.write shape: the
     * type byte, the empty root name, the payload).
     */
    public static byte[] nbtDocument(Map<String, Object> boxedRoot) {
        return writeDocument(net.zaminmc.torch.server.nbt.Snbt.Tag.ofCompound(
                NbtOps.fromBoxed(boxedRoot)));
    }

    /** One gzip-compressed document (NbtIo.writeCompressed — level.dat). */
    public static byte[] gzipNbtDocument(Map<String, Object> boxedRoot) {
        byte[] raw = writeDocument(net.zaminmc.torch.server.nbt.Snbt.Tag.ofCompound(
                NbtOps.fromBoxed(boxedRoot)));
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(raw);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("in-memory write cannot fail", e);
        }
    }

    private static byte[] writeDocument(net.zaminmc.torch.server.nbt.Snbt.Tag root) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeByte(10); // TAG_Compound
            data.writeUTF(""); // the unnamed root
            writeCompoundPayload(root.asCompound(), data);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("in-memory write cannot fail", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeCompoundPayload(Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> rows,
                                             DataOutputStream data) throws IOException {
        for (Map.Entry<String, net.zaminmc.torch.server.nbt.Snbt.Tag> row : rows.entrySet()) {
            data.writeByte(typeId(row.getValue()));
            data.writeUTF(row.getKey());
            writePayload(row.getValue(), data);
        }
        data.writeByte(0); // TAG_End
    }

    private static void writePayload(net.zaminmc.torch.server.nbt.Snbt.Tag tag,
                                     DataOutputStream data) throws IOException {
        switch (tag.type()) {
            case BYTE -> data.writeByte(tag.asByte());
            case SHORT -> data.writeShort((short) tag.asDouble());
            case INT -> data.writeInt((int) tag.asDouble());
            case LONG -> data.writeLong(tag.asLong());
            case FLOAT -> data.writeFloat(tag.asFloat());
            case DOUBLE -> data.writeDouble(tag.asDouble());
            case STRING -> data.writeUTF(tag.asString() == null ? "" : tag.asString());
            case INT_ARRAY -> {
                int[] values = tag.asIntArray();
                data.writeInt(values == null ? 0 : values.length);
                if (values != null) {
                    for (int value : values) {
                        data.writeInt(value);
                    }
                }
            }
            case LIST -> {
                List<net.zaminmc.torch.server.nbt.Snbt.Tag> elements = tag.asList();
                int count = elements == null ? 0 : elements.size();
                int elementType = count == 0 ? 0 : typeId(elements.get(0));
                data.writeByte(elementType);
                data.writeInt(count);
                for (net.zaminmc.torch.server.nbt.Snbt.Tag element : elements) {
                    // Lists carry unnamed elements (NbtList.write).
                    writePayload(element, data);
                }
            }
            case COMPOUND -> writeCompoundPayload(tag.asCompound(), data);
        }
    }

    /** The reference's tag ids (NbtElement.create's own table). */
    private static int typeId(net.zaminmc.torch.server.nbt.Snbt.Tag tag) {
        return switch (tag.type()) {
            case BYTE -> 1;
            case SHORT -> 2;
            case INT -> 3;
            case LONG -> 4;
            case FLOAT -> 5;
            case DOUBLE -> 6;
            case STRING -> 8;
            case INT_ARRAY -> 11;
            case LIST -> 9;
            case COMPOUND -> 10;
        };
    }

    // ------------------------------------------------------------- the region file writer

    /** One written chunk entry: the local position, the scheme, the payload. */
    public record Entry(int localX, int localZ, int scheme, byte[] nbt) {
    }

    /**
     * Writes one whole region file (RegionFile.writeChunkData's layout):
     * the 8 KiB header — 1024 location ints then 1024 timestamp ints —
     * then each entry's payload at its own sector run (sectors start at
     * index 2, after the header's two):
     * {@code length = payload + 1}, the scheme byte, the bytes, padded
     * to the sector multiple; the location int packs
     * {@code (sectorOffset << 8) | sectorCount}.
     */
    public static void writeRegionFile(Path file, List<Entry> entries) throws IOException {
        int[] locations = new int[32 * 32];
        int[] timestamps = new int[32 * 32];
        List<byte[]> runs = new ArrayList<>();
        for (Entry entry : entries) {
            int payloadLength = entry.nbt().length + 1; // the scheme byte rides the length
            int sectorCount = (payloadLength + AnvilRegionFile.SECTOR_BYTES - 1)
                    / AnvilRegionFile.SECTOR_BYTES;
            int offset = 2 + runs.size(); // sectors 0 and 1 are the header
            byte[] run = new byte[sectorCount * AnvilRegionFile.SECTOR_BYTES];
            run[0] = (byte) (payloadLength >> 24);
            run[1] = (byte) (payloadLength >> 16);
            run[2] = (byte) (payloadLength >> 8);
            run[3] = (byte) payloadLength;
            run[4] = (byte) entry.scheme();
            System.arraycopy(entry.nbt(), 0, run, 5, entry.nbt().length);
            runs.add(run);
            locations[entry.localX() + entry.localZ() * 32] = (offset << 8) | sectorCount;
            timestamps[entry.localX() + entry.localZ() * 32] = 1_700_000_000;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int location : locations) {
            out.write(location >> 24); // the top byte stays zero (offset < 256 sectors)
            out.write(location >> 16);
            out.write(location >> 8);
            out.write(location);
        }
        for (int timestamp : timestamps) {
            out.write(timestamp >> 24);
            out.write(timestamp >> 16);
            out.write(timestamp >> 8);
            out.write(timestamp);
        }
        for (byte[] run : runs) {
            out.write(run);
        }
        Files.createDirectories(file.getParent());
        Files.write(file, out.toByteArray());
    }

    // ------------------------------------------------------------- the level.dat writer

    /**
     * Writes one level.dat (the reference's shape: one gzip document whose
     * root holds the {@code Data} compound).
     */
    public static void writeLevelDat(Path folder, Map<String, Object> dataRows)
            throws IOException {
        Files.createDirectories(folder);
        Map<String, Object> root = compound();
        root.put("Data", dataRows);
        Files.write(folder.resolve("level.dat"), gzipNbtDocument(root));
    }

    // ------------------------------------------------------------- the chunk NBT builder

    /**
     * One chunk's NBT (AnvilChunkStorage.writeChunkToNbt's shape): the
     * Level compound with xPos/zPos, the Sections list of section
     * compounds (Y byte, Blocks byte[4096], Data nibble[2048], the
     * optional Add, the light arrays), and the Biomes row.
     */
    public static Map<String, Object> chunkNbt(int xPos, int zPos,
                                               List<Map<String, Object>> sections,
                                               byte[] biomes) {
        return chunkNbt(xPos, zPos, sections, biomes, null, null);
    }

    /**
     * The full form (the content follow-up): the chunk's NBT with the
     * Entities and TileEntities lists beside the sections — the
     * reference's own save shape (the entities carry id/Pos/Rotation +
     * the per-type rows; the tiles carry id/x/y/z + the per-family
     * rows).
     */
    public static Map<String, Object> chunkNbt(int xPos, int zPos,
                                               List<Map<String, Object>> sections,
                                               byte[] biomes,
                                               List<Map<String, Object>> entities,
                                               List<Map<String, Object>> tileEntities) {
        Map<String, Object> level = compound();
        level.put("V", (byte) 1);
        level.put("xPos", xPos);
        level.put("zPos", zPos);
        level.put("LastUpdate", 0L);
        level.put("TerrainPopulated", (byte) 1);
        level.put("LightPopulated", (byte) 1);
        level.put("InhabitedTime", 0L);
        level.put("HeightMap", new int[256]);
        level.put("Sections", sections);
        if (biomes != null) {
            level.put("Biomes", byteArray(biomes));
        }
        if (entities != null) {
            level.put("Entities", entities);
        }
        if (tileEntities != null) {
            level.put("TileEntities", tileEntities);
        }
        Map<String, Object> root = compound();
        root.put("Level", level);
        return root;
    }

    /**
     * One entity row (the reference's Entities.create input): the id
     * string, the Pos double triple, the Rotation float pair, and any
     * per-type rows the caller adds to the returned compound.
     */
    public static Map<String, Object> entityRow(String id, double x, double y, double z) {
        Map<String, Object> entity = compound();
        entity.put("id", id);
        entity.put("Pos", List.of(x, y, z));
        entity.put("Rotation", List.of(0.0f, 0.0f));
        return entity;
    }

    /**
     * One item-entity row (EntityItem's save shape): the Item compound
     * (the stack's id/Count/Damage) + the Age/PickupDelay rows.
     */
    public static Map<String, Object> itemEntityRow(String itemId, int count, int damage,
                                                    double x, double y, double z, short age) {
        Map<String, Object> entity = entityRow("Item", x, y, z);
        Map<String, Object> stack = compound();
        stack.put("id", itemId);
        stack.put("Count", (byte) count);
        stack.put("Damage", (short) damage);
        entity.put("Item", stack);
        entity.put("Age", age);
        entity.put("PickupDelay", (short) 0);
        return entity;
    }

    /**
     * One tile-entity row (BlockEntities.create's input): the id string,
     * the x/y/z ints, and the per-family rows the caller adds.
     */
    public static Map<String, Object> tileRow(String id, int x, int y, int z) {
        Map<String, Object> tile = compound();
        tile.put("id", id);
        tile.put("x", x);
        tile.put("y", y);
        tile.put("z", z);
        return tile;
    }

    /**
     * One section compound: the Y row, the Blocks array, the optional Data
     * nibble, the optional Add nibble, and the light arrays (zeroed — the
     * import ignores them, recomputing at publication).
     */
    public static Map<String, Object> section(int y, byte[] blocks, byte[] data, byte[] add) {
        Map<String, Object> section = compound();
        section.put("Y", (byte) y);
        section.put("Blocks", byteArray(blocks));
        if (data != null) {
            section.put("Data", byteArray(data));
        }
        if (add != null) {
            section.put("Add", byteArray(add));
        }
        section.put("BlockLight", byteArray(new byte[2048]));
        section.put("SkyLight", byteArray(new byte[2048]));
        return section;
    }

    /** The nibble write (ChunkNibbleStorage.set): the low half for even ids. */
    public static void nibble(byte[] nibbles, int id, int value) {
        int index = id >> 1;
        if ((id & 1) == 0) {
            nibbles[index] = (byte) (nibbles[index] & 0xF0 | value & 0x0F);
        } else {
            nibbles[index] = (byte) (nibbles[index] & 0x0F | (value & 0x0F) << 4);
        }
    }

    /** The gzip arm's stored bytes (RegionFile's scheme 1: the payload IS the gzip stream). */
    public static byte[] gzip(byte[] raw) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream stream = new GZIPOutputStream(out)) {
                stream.write(raw);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The zlib arm's stored bytes (RegionFile's scheme 2: the payload IS the deflate stream). */
    public static byte[] zlib(byte[] raw) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (java.util.zip.DeflaterOutputStream stream =
                         new java.util.zip.DeflaterOutputStream(out)) {
                stream.write(raw);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The cell index (the reference's YZX order): {@code (y << 8) | (z << 4) | x}. */
    public static int cellIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }
}
