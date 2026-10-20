package net.zaminmc.torch.server.nbt;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * The binary NBT reader (the Anvil canvas' first leg — the reference's
 * {@code nbt/NbtIo.java} + the {@code NbtElement} family's own read bodies,
 * ported 1:1 onto the engine's one typed value model): big-endian, the
 * complete TAG 0-11 lattice, the named root compound, and the gzip stream
 * arm ({@code NbtIo.readCompressed} — the level.dat shape). The engine's
 * NBT package is SNBT-shaped ({@link Snbt} parses the string language,
 * {@link NbtOps} walks the rows); the Anvil world format is binary, so
 * this reader is the one direction that was missing — a NEW file, the
 * existing NBT files untouched (the SNBT grammar, the ops surface, and
 * every model that reads them keep their contracts).
 *
 * <p><b>The output adaptation (the task's own constraint):</b> the reader
 * answers {@code Map<String, Snbt.Tag>} — the exact structure the codebase
 * already walks — so every downstream read uses the familiar
 * {@link NbtOps} accessors ({@code getCompound("Level")},
 * {@code getList("Sections", ...)}, the numeric family's widening
 * getters). The binary lattice maps one-for-one onto {@link Snbt.Tag}
 * with ONE deliberate adaptation: TAG_Byte_Array (id 7) has no SNBT
 * counterpart — the 1.8.8 string language cannot even spell it — so a
 * byte array reads as a LIST of BYTE tags ({@code NbtOps.getList(rows,
 * key, Snbt.Tag.Type.BYTE)}), and {@link #byteArray} pulls it back out
 * as the raw {@code byte[]}. The Anvil chunk sections ride that arm
 * (Blocks/Data/Add/light arrays); the adaptation is documented here
 * because it is the only place the binary lattice and the SNBT lattice
 * disagree.</p>
 *
 * <p><b>The read bodies (1:1 from the reference):</b> the root is a type
 * byte, a UTF name, then the payload — a non-compound root is the
 * reference's own {@code "Root tag must be a named compound tag"}
 * refusal; compounds read rows until TAG_End with the duplicate-key
 * overwrite the reference's map performs; lists carry one element type
 * byte and a count (the empty list's type 0); strings are modified-UTF;
 * arrays are length-prefixed. The depth guard is the reference's own
 * (depth &gt; 512 refuses — the "too high complexity" arm).</p>
 *
 * <p><b>One recorded deviation:</b> the reference's {@code NbtReadLimiter}
 * runs UNLIMITED on its own saves, but this reader consumes FOREIGN
 * files (an imported 1.8.8 world folder — §31.6's quarantine rule, not a
 * trusted own-store read), so array lengths carry a sanity bound: a
 * length prefix beyond {@link #MAX_ARRAY_LENGTH} is an IOException the
 * caller isolates with the path, never a multi-gigabyte allocation that
 * takes the server down. The bound is orders of magnitude above every
 * real Anvil array (the largest is the 4096-byte Blocks row).</p>
 */
public final class BinaryNbtReader {

    private BinaryNbtReader() {
    }

    /** The reference's own complexity guard (NbtCompound/NbtList read: depth > 512). */
    static final int MAX_DEPTH = 512;

    /**
     * The foreign-file array bound (§31.6's isolation duty): the largest
     * real Anvil array is the 4096-byte Blocks row; level.dat carries none.
     */
    static final int MAX_ARRAY_LENGTH = 1 << 24;

    // ------------------------------------------------------------------ the entries

    /**
     * Reads one full binary NBT document from the raw (uncompressed)
     * input — the reference's {@code NbtIo.read(DataInput)} shape.
     *
     * @throws IOException the malformed-input refusals (the reference's
     *                     own messages where it defines them)
     */
    public static Map<String, Snbt.Tag> read(DataInput input) throws IOException {
        Snbt.Tag root = readElement(input, 0);
        if (root == null || root.type() != Snbt.Tag.Type.COMPOUND) {
            // NbtIo.read: "Root tag must be a named compound tag" — the
            // root name itself is discarded (the Anvil roots are unnamed).
            throw new IOException("Root tag must be a named compound tag");
        }
        Map<String, Snbt.Tag> rows = root.asCompound();
        return rows;
    }

    /**
     * Reads one gzip-compressed document — the reference's
     * {@code NbtIo.readCompressed} (the level.dat shape: the whole file
     * is one gzip stream whose root holds the {@code Data} compound).
     *
     * @throws IOException the gzip arm's own failures plus the read's
     */
    public static Map<String, Snbt.Tag> readGzip(InputStream compressed) throws IOException {
        DataInputStream stream = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(compressed)));
        try {
            return read(stream);
        } finally {
            stream.close();
        }
    }

    /**
     * Reads one zlib-deflated document — the reference's
     * {@code RegionFile.getChunkInputStream} scheme-2 arm (the raw
     * sector payload INFLATEd before the NBT walk; the caller hands the
     * already-decompressed stream, matching the reference's split where
     * the region file owns the inflate and NbtIo owns the walk).
     */
    public static Map<String, Snbt.Tag> readZlib(InputStream inflated) throws IOException {
        return read(new DataInputStream(new BufferedInputStream(inflated)));
    }

    /**
     * The raw byte-array arm of the byte-array adaptation: pulls the
     * {@code byte[]} back out of a TAG_Byte_Array the reader stored as a
     * LIST of BYTE tags. Answers null for every other shape (the
     * caller's own presence check, exactly like the reference's
     * {@code contains(key, 7)}).
     */
    public static byte[] byteArray(Snbt.Tag tag) {
        if (tag == null || tag.type() != Snbt.Tag.Type.LIST) {
            return null;
        }
        List<Snbt.Tag> rows = tag.asList();
        if (rows == null) {
            return null;
        }
        byte[] out = new byte[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Snbt.Tag row = rows.get(i);
            if (row == null || row.type() != Snbt.Tag.Type.BYTE) {
                return null; // not a stored byte array — the caller isolates
            }
            out[i] = row.asByte();
        }
        return out;
    }

    // ------------------------------------------------------------------ the element walk

    /**
     * One element: the type byte, then the name (skipped — the row name
     * is applied by the compound reader), then the payload. TAG_End
     * answers null (the terminator, not a value).
     */
    private static Snbt.Tag readElement(DataInput input, int depth) throws IOException {
        int type = input.readUnsignedByte();
        if (type == 0) {
            return null; // NbtEnd: the terminator the callers consume
        }
        if (type < 1 || type > 11) {
            throw new IOException("Invalid NBT tag type: " + type);
        }
        input.readUTF(); // the element's own name (the root's is empty; rows name themselves in the compound walk)
        return readPayload(type, input, depth);
    }

    /** One payload — the reference's per-type read bodies. */
    private static Snbt.Tag readPayload(int type, DataInput input, int depth) throws IOException {
        switch (type) {
            case 1: // TAG_Byte
                return Snbt.Tag.ofByte(input.readByte());
            case 2: // TAG_Short
                return Snbt.Tag.ofShort(input.readShort());
            case 3: // TAG_Int
                return Snbt.Tag.ofInt(input.readInt());
            case 4: // TAG_Long
                return Snbt.Tag.ofLong(input.readLong());
            case 5: // TAG_Float
                return Snbt.Tag.ofFloat(input.readFloat());
            case 6: // TAG_Double
                return Snbt.Tag.ofDouble(input.readDouble());
            case 7: { // TAG_Byte_Array — the documented LIST-of-BYTE adaptation
                int length = input.readInt();
                if (length < 0 || length > MAX_ARRAY_LENGTH) {
                    throw new IOException("NBT byte-array length out of bounds: " + length);
                }
                byte[] values = new byte[length];
                input.readFully(values);
                List<Snbt.Tag> rows = new ArrayList<>(length);
                for (byte value : values) {
                    rows.add(Snbt.Tag.ofByte(value));
                }
                return Snbt.Tag.ofList(rows);
            }
            case 8: // TAG_String
                return Snbt.Tag.ofString(input.readUTF());
            case 9: { // TAG_List
                if (depth > MAX_DEPTH) {
                    throw new IOException("Tried to read NBT tag with too high complexity, depth > " + MAX_DEPTH);
                }
                int elementType = input.readUnsignedByte();
                int count = input.readInt();
                if (elementType == 0 && count > 0) {
                    // NbtList.read: "Missing type on ListTag"
                    throw new IOException("Missing type on ListTag");
                }
                if (count < 0 || count > MAX_ARRAY_LENGTH) {
                    throw new IOException("NBT list length out of bounds: " + count);
                }
                List<Snbt.Tag> rows = new ArrayList<>(Math.min(count, 1024));
                for (int i = 0; i < count; i++) {
                    rows.add(readPayload(elementType, input, depth + 1));
                }
                return Snbt.Tag.ofList(rows);
            }
            case 10: { // TAG_Compound
                if (depth > MAX_DEPTH) {
                    throw new IOException("Tried to read NBT tag with too high complexity, depth > " + MAX_DEPTH);
                }
                Map<String, Snbt.Tag> rows = new LinkedHashMap<>();
                while (true) {
                    int rowType = input.readUnsignedByte();
                    if (rowType == 0) { // TAG_End: the compound terminator
                        return Snbt.Tag.ofCompound(rows);
                    }
                    if (rowType < 1 || rowType > 11) {
                        throw new IOException("Invalid NBT tag type: " + rowType);
                    }
                    String name = input.readUTF();
                    // The reference's map put: a duplicate key overwrites.
                    rows.put(name, readPayload(rowType, input, depth + 1));
                }
            }
            case 11: { // TAG_Int_Array
                int length = input.readInt();
                if (length < 0 || length > MAX_ARRAY_LENGTH) {
                    throw new IOException("NBT int-array length out of bounds: " + length);
                }
                int[] values = new int[length];
                for (int i = 0; i < length; i++) {
                    values[i] = input.readInt();
                }
                return Snbt.Tag.ofIntArray(values);
            }
            default:
                throw new IOException("Invalid NBT tag type: " + type);
        }
    }
}
