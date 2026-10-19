package net.zaminmc.torch.server.nbt;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The binary NBT reader (the Anvil canvas' first leg): every tag family
 * round trips through the engine's one typed value model, the byte-array
 * adaptation pulls the raw rows back out, the gzip arm decodes the
 * level.dat shape, and the malformed inputs — a non-compound root, a
 * truncated document, a too-deep nest, an invalid type byte — refuse
 * loudly instead of guessing (§31.6's quarantine duty over foreign
 * files). The document bytes are written by a local port of the
 * reference's own write side (NbtCompound.write's bodies), so the reader
 * is exercised against reference-shaped bytes, not its own twin output.
 */
class BinaryNbtReaderTest {

    // ------------------------------------------------------------------ the local writer (the reference's write side)

    /** NbtIo.write's shape: the type byte, the empty root name, the payload. */
    private static byte[] document(Map<String, Snbt.Tag> root) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeByte(10);
            data.writeUTF("");
            writeRows(root, data);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeRows(Map<String, Snbt.Tag> rows, DataOutputStream data)
            throws IOException {
        for (Map.Entry<String, Snbt.Tag> row : rows.entrySet()) {
            data.writeByte(typeId(row.getValue()));
            data.writeUTF(row.getKey());
            writePayload(row.getValue(), data);
        }
        data.writeByte(0);
    }

    private static void writePayload(Snbt.Tag tag, DataOutputStream data) throws IOException {
        switch (tag.type()) {
            case BYTE -> data.writeByte(tag.asByte());
            case SHORT -> data.writeShort((short) tag.asDouble());
            case INT -> data.writeInt((int) tag.asDouble());
            case LONG -> data.writeLong(tag.asLong());
            case FLOAT -> data.writeFloat(tag.asFloat());
            case DOUBLE -> data.writeDouble(tag.asDouble());
            case STRING -> data.writeUTF(tag.asString());
            case INT_ARRAY -> {
                int[] values = tag.asIntArray();
                data.writeInt(values.length);
                for (int value : values) {
                    data.writeInt(value);
                }
            }
            case LIST -> {
                List<Snbt.Tag> elements = tag.asList();
                data.writeByte(elements.isEmpty() ? 0 : typeId(elements.get(0)));
                data.writeInt(elements.size());
                for (Snbt.Tag element : elements) {
                    writePayload(element, data);
                }
            }
            case COMPOUND -> writeRows(tag.asCompound(), data);
        }
    }

    private static int typeId(Snbt.Tag tag) {
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

    // ------------------------------------------------------------------ the tests

    @Test
    void everyTagFamilyRoundTrips() throws IOException {
        Map<String, Snbt.Tag> rows = new TreeMap<>();
        rows.put("byte", Snbt.Tag.ofByte((byte) -5));
        rows.put("short", Snbt.Tag.ofShort((short) 300));
        rows.put("int", Snbt.Tag.ofInt(-70000));
        rows.put("long", Snbt.Tag.ofLong(0x123456789ABCDEFL));
        rows.put("float", Snbt.Tag.ofFloat(2.5f));
        rows.put("double", Snbt.Tag.ofDouble(-1.25));
        rows.put("string", Snbt.Tag.ofString("vanilla 1.8.8"));
        rows.put("intArray", Snbt.Tag.ofIntArray(new int[] {1, -2, 3}));
        List<Snbt.Tag> bytes = new java.util.ArrayList<>();
        for (byte b : new byte[] {1, 2, 3, 4, 5}) {
            bytes.add(Snbt.Tag.ofByte(b));
        }
        rows.put("byteArray", Snbt.Tag.ofList(bytes));
        rows.put("list", Snbt.Tag.ofList(List.of(
                Snbt.Tag.ofInt(10), Snbt.Tag.ofInt(20))));
        rows.put("nested", Snbt.Tag.ofCompound(Map.of(
                "inner", Snbt.Tag.ofString("row"))));
        Map<String, Snbt.Tag> read = BinaryNbtReader.read(
                new DataInputStream(new ByteArrayInputStream(document(Map.of("Data",
                        Snbt.Tag.ofCompound(rows))))));

        assertEquals(1, read.size());
        Map<String, Snbt.Tag> data = read.get("Data").asCompound();
        assertEquals(Snbt.Tag.Type.BYTE, data.get("byte").type());
        assertEquals(-5, data.get("byte").asByte());
        assertEquals(300, (short) data.get("short").asDouble());
        assertEquals(-70000, (int) data.get("int").asDouble());
        assertEquals(0x123456789ABCDEFL, data.get("long").asLong());
        assertEquals(2.5f, data.get("float").asFloat());
        assertEquals(-1.25, data.get("double").asDouble());
        assertEquals("vanilla 1.8.8", data.get("string").asString());
        assertArrayEquals(new int[] {1, -2, 3}, data.get("intArray").asIntArray());
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5},
                BinaryNbtReader.byteArray(data.get("byteArray")));
        List<Snbt.Tag> list = data.get("list").asList();
        assertEquals(2, list.size());
        assertEquals(10, (int) list.get(0).asDouble());
        assertEquals(20, (int) list.get(1).asDouble());
        assertEquals("row", data.get("nested").asCompound().get("inner").asString());
    }

    @Test
    void theGzipArmDecodesTheLevelDatShape() throws IOException {
        byte[] raw = document(Map.of("Data", Snbt.Tag.ofCompound(Map.of(
                "RandomSeed", Snbt.Tag.ofLong(42L)))));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(out)) {
            gzip.write(raw);
        }
        Map<String, Snbt.Tag> read = BinaryNbtReader.readGzip(
                new ByteArrayInputStream(out.toByteArray()));
        assertEquals(42L, NbtOps.getLong(NbtOps.getCompound(read, "Data"), "RandomSeed"));
    }

    @Test
    void aNonCompoundRootIsRefused() {
        // A root whose type byte is an int (3), then the name, then the payload.
        byte[] document = new byte[] {3, 0, 0, 0, 0, 0, 7};
        assertThrows(IOException.class, () -> BinaryNbtReader.read(
                new DataInputStream(new ByteArrayInputStream(document))),
                "the reference's own refusal: the root must be a named compound");
    }

    @Test
    void aTruncatedDocumentIsRefusedNotGuessed() {
        // A compound root that ends mid-row: type + name then EOF.
        byte[] document = new byte[] {10, 0, 0, 1}; // TAG_Compound, name "", then nothing
        assertThrows(IOException.class, () -> BinaryNbtReader.read(
                new DataInputStream(new ByteArrayInputStream(document))));
    }

    @Test
    void anInvalidTypeByteIsRefused() {
        // Type 12 does not exist in the 0..11 lattice.
        byte[] document = new byte[] {12, 0, 0};
        assertThrows(IOException.class, () -> BinaryNbtReader.read(
                new DataInputStream(new ByteArrayInputStream(document))));
    }

    @Test
    void aTooDeepNestIsRefused() {
        // The reference's own complexity guard: depth > 512 refuses.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(out)) {
            data.writeByte(10); // the root compound
            data.writeUTF("");
            for (int i = 0; i < 600; i++) {
                data.writeByte(10); // a nested compound row
                data.writeUTF("d" + i);
            }
            data.writeByte(0); // the innermost terminator
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        byte[] document = out.toByteArray();
        assertThrows(IOException.class, () -> BinaryNbtReader.read(
                new DataInputStream(new ByteArrayInputStream(document))),
                "the depth-512 complexity guard (NbtCompound.read's own rule)");
    }

    @Test
    void aDuplicateKeyOverwritesLikeTheReference() throws IOException {
        // The reference's compound read puts into a map: the later row wins.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(out)) {
            data.writeByte(10);
            data.writeUTF("");
            data.writeByte(3); // int
            data.writeUTF("row");
            data.writeInt(1);
            data.writeByte(3); // int
            data.writeUTF("row");
            data.writeInt(2);
            data.writeByte(0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Map<String, Snbt.Tag> read = BinaryNbtReader.read(
                new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
        assertEquals(1, read.size());
        assertEquals(2, (int) read.get("row").asDouble());
    }

    @Test
    void anEmptyListKeepsItsPlace() throws IOException {
        Map<String, Snbt.Tag> read = BinaryNbtReader.read(new DataInputStream(
                new ByteArrayInputStream(document(Map.of("Data",
                        Snbt.Tag.ofCompound(Map.of("empty", Snbt.Tag.ofList(List.of()))))))));
        assertTrue(read.get("Data").asCompound().get("empty").asList().isEmpty());
    }

    @Test
    void aListWithTypeZeroAndPositiveCountIsRefused() {
        // NbtList.read's own rule: "Missing type on ListTag".
        byte[] document = new byte[] {
                10, 0, 0,          // the root compound, unnamed
                9, 0, 1, 'a',      // a list row "a"
                0, 0, 0, 0, 2,     // element type 0 but count 2 — the refusal
                0};
        assertThrows(IOException.class, () -> BinaryNbtReader.read(
                new DataInputStream(new ByteArrayInputStream(document))));
    }

    @Test
    void aByteArrayReadsAsTheListAdaptationAndPullsBackOut() throws IOException {
        // TAG_Byte_Array is the one lattice disagreement: the 1.8.8 string
        // language cannot spell it, so the reader answers a LIST of BYTE
        // tags and BinaryNbtReader.byteArray pulls the raw row back out.
        List<Snbt.Tag> bytes = new java.util.ArrayList<>();
        for (byte b : new byte[] {-1, 0, 127}) {
            bytes.add(Snbt.Tag.ofByte(b));
        }
        Map<String, Snbt.Tag> read = BinaryNbtReader.read(new DataInputStream(
                new ByteArrayInputStream(document(Map.of("Blocks",
                        Snbt.Tag.ofList(bytes))))));
        Snbt.Tag tag = read.get("Blocks");
        assertEquals(Snbt.Tag.Type.LIST, tag.type(),
                "the adaptation's documented shape");
        assertArrayEquals(new byte[] {-1, 0, 127}, BinaryNbtReader.byteArray(tag));
        assertEquals(null, BinaryNbtReader.byteArray(Snbt.Tag.ofString("not arrays")),
                "a non-stored shape answers null, the caller's own presence check");
    }
}
