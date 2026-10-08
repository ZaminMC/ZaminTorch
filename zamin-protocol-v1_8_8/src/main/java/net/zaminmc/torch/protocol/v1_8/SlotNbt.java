package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;

/**
 * The slot encoding's optional NBT compound (protocol 47): the engine models
 * exactly one entry — the historical {@code tag.display.Name} anvil rename —
 * and this class is its only wire writer (and the test client's reader).
 *
 * <p>Layout, community-verified against the real client's parser and vanilla
 * {@code PacketBuffer.writeItemStackToBuffer}: the slot's NBT marker byte is
 * the root tag's TYPE byte — {@code 0} (TAG_End) when the stack carries no
 * compound, otherwise {@code 10} (TAG_Compound) followed by the full payload:
 * a short-length root name (empty), then the compounds/strings in vanilla NBT
 * order, then TAG_End. Strings inside NBT are <b>short-length modified
 * UTF-8</b> (Java's {@code DataOutput.writeUTF}: CESU-8 with {@code U+0000}
 * as {@code C0 80}) — a different encoding from the protocol's varint-length
 * UTF-8 strings, which is exactly the kind of detail the real client notices.</p>
 */
final class SlotNbt {

    private static final byte TAG_END = 0;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_STRING = 8;

    private SlotNbt() {
    }

    /** @return whether a stack's display name produces NBT (blank names never do). */
    static boolean hasPayload(String displayName) {
        return displayName != null && !displayName.isEmpty();
    }

    /**
     * Writes the full slot NBT for a named stack: the root TAG_Compound marker
     * (which doubles as the slot's "NBT present" signal) plus the payload
     * {@code {display:{Name:"…"}}}.
     */
    static void writeNamed(ByteBuf out, String displayName) {
        out.writeByte(TAG_COMPOUND);   // the marker itself: root compound type
        out.writeShort(0);             // root name: empty
        out.writeByte(TAG_COMPOUND);   // tag.display
        writeShortString(out, "display");
        {
            out.writeByte(TAG_STRING); // tag.display.Name
            writeShortString(out, "Name");
            writeShortString(out, displayName);
            out.writeByte(TAG_END);    // end of tag.display
        }
        out.writeByte(TAG_END);        // end of root
    }

    /**
     * Skips one NBT payload in a buffer whose position sits ON the marker
     * byte. Used by test clients: a {@code 0} marker is nothing; any other
     * type byte starts a full compound whose structure is parsed (values
     * skipped) so the reader lands exactly after it.
     */
    static void skip(ByteBuf in) {
        int type = in.readUnsignedByte();
        if (type == TAG_END) {
            return; // no NBT
        }
        if (type != TAG_COMPOUND) {
            throw new IllegalArgumentException("Slot NBT must root at a compound: " + type);
        }
        skipShortString(in); // root name
        skipCompoundPayload(in);
    }

    /**
     * Reads the display name out of a slot NBT payload (buffer positioned ON
     * the marker byte), or null when the payload is absent or carries no
     * {@code display.Name}. Structural NBT parsing, values otherwise ignored.
     */
    static String readDisplayName(ByteBuf in) {
        int type = in.readUnsignedByte();
        if (type == TAG_END) {
            return null; // no NBT
        }
        if (type != TAG_COMPOUND) {
            throw new IllegalArgumentException("Slot NBT must root at a compound: " + type);
        }
        skipShortString(in); // root name
        return readCompoundForName(in);
    }

    private static String readCompoundForName(ByteBuf in) {
        String found = null;
        while (true) {
            int type = in.readUnsignedByte();
            if (type == TAG_END) {
                return found;
            }
            String name = readShortString(in);
            if (type == TAG_COMPOUND) {
                String nested = readCompoundForName(in);
                if ("display".equals(name)) {
                    found = nested; // display.Name lands here
                }
            } else if (type == TAG_STRING) {
                String value = readShortString(in);
                if ("Name".equals(name)) {
                    found = value;
                }
            } else {
                skipPayload(in, type);
            }
        }
    }

    private static void skipCompoundPayload(ByteBuf in) {
        while (true) {
            int type = in.readUnsignedByte();
            if (type == TAG_END) {
                return;
            }
            skipShortString(in); // the entry's name
            skipPayload(in, type);
        }
    }

    private static void skipPayload(ByteBuf in, int type) {
        switch (type) {
            case 1 -> in.skipBytes(1);      // byte
            case 2 -> in.skipBytes(2);      // short
            case 3 -> in.skipBytes(4);      // int
            case 4 -> in.skipBytes(8);      // long
            case 5 -> in.skipBytes(4);      // float
            case 6 -> in.skipBytes(8);      // double
            case 7 -> {                     // byte array
                int length = in.readInt();
                in.skipBytes(length);
            }
            case 8 -> skipShortString(in);  // string
            case 9 -> {                     // list
                int elementType = in.readUnsignedByte();
                int length = in.readInt();
                for (int i = 0; i < length; i++) {
                    skipPayload(in, elementType);
                }
            }
            case 10 -> skipCompoundPayload(in);
            case 11 -> {                    // int array
                int length = in.readInt();
                in.skipBytes(length * 4);
            }
            case 12 -> {                    // long array
                int length = in.readInt();
                in.skipBytes(length * 8);
            }
            default -> throw new IllegalArgumentException("Unknown NBT tag type: " + type);
        }
    }

    private static void writeShortString(ByteBuf out, String value) {
        byte[] encoded = ModifiedUtf8.encode(value);
        if (encoded.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("NBT string too long: " + encoded.length);
        }
        out.writeShort(encoded.length);
        out.writeBytes(encoded);
    }

    private static void skipShortString(ByteBuf in) {
        int length = in.readUnsignedShort();
        in.skipBytes(length);
    }

    private static String readShortString(ByteBuf in) {
        int length = in.readUnsignedShort();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return ModifiedUtf8.decode(bytes);
    }

    /**
     * Modified UTF-8 (Java {@code DataOutput.writeUTF} semantics): the exact
     * string encoding the historical NBT reader expects — ASCII as-is, Latin
     * ranges in 2-3 bytes, supplementary characters as CESU-8 surrogate pairs,
     * {@code U+0000} as {@code C0 80}.
     */
    static final class ModifiedUtf8 {

        private ModifiedUtf8() {
        }

        static byte[] encode(String value) {
            int length = value.length();
            byte[] out = new byte[bound(length)];
            int written = 0;
            for (int i = 0; i < length; i++) {
                char c = value.charAt(i);
                if (c > 0 && c <= 0x7F) {
                    out[written++] = (byte) c;
                } else if (c <= 0x7FF) {
                    out[written++] = (byte) (0xC0 | (c >> 6));
                    out[written++] = (byte) (0x80 | (c & 0x3F));
                } else {
                    out[written++] = (byte) (0xE0 | (c >> 12));
                    out[written++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                    out[written++] = (byte) (0x80 | (c & 0x3F));
                }
            }
            return java.util.Arrays.copyOf(out, written);
        }

        static String decode(byte[] bytes) {
            StringBuilder out = new StringBuilder(bytes.length);
            for (int i = 0; i < bytes.length; ) {
                int b = bytes[i++] & 0xFF;
                if (b < 0x80) {
                    out.append((char) b);
                } else if ((b & 0xE0) == 0xC0) {
                    int b2 = bytes[i++] & 0xFF;
                    out.append((char) (((b & 0x1F) << 6) | (b2 & 0x3F)));
                } else {
                    int b2 = bytes[i++] & 0xFF;
                    int b3 = bytes[i++] & 0xFF;
                    out.append((char) (((b & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F)));
                }
            }
            return out.toString();
        }

        /** Upper bound of the encoding's byte length (every char worst-case 3 bytes). */
        private static int bound(int chars) {
            long bytes = (long) chars * 3 + 2;
            if (bytes > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("String encodes too large: " + chars);
            }
            return (int) bytes;
        }
    }
}
