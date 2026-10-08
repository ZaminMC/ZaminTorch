package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.nio.charset.StandardCharsets;

/**
 * Wire primitive encoding for protocol 47. All multi-byte primitives are
 * big-endian; strings are VarInt-length-prefixed UTF-8; positions use the
 * packed-block-position layout of this protocol version.
 */
final class ByteBufOps {

    private ByteBufOps() {
    }

    static void writeVarInt(ByteBuf out, int value) {
        // Proof: 7 bits per byte, high bit = continuation, as protocol 47 requires.
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    static int readVarInt(ByteBuf in) {
        int value = 0;
        int shift = 0;
        while (true) {
            if (!in.isReadable()) {
                return Integer.MIN_VALUE; // incomplete; caller decides how to react
            }
            byte b = in.readByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 28) {
                throw new IllegalArgumentException("VarInt too large (malformed packet)");
            }
        }
    }

    static int varIntSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    static void writeString(ByteBuf out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("String too long for protocol: " + bytes.length);
        }
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    static String readString(ByteBuf in, int maxLength) {
        int length = readVarInt(in);
        if (length < 0 || length > maxLength * 4) {
            throw new IllegalArgumentException("String length out of bounds: " + length);
        }
        if (in.readableBytes() < length) {
            throw new IllegalArgumentException("Truncated string (declared " + length + " bytes)");
        }
        String value = new String(ByteBufUtil.getBytes(in.readBytes(length)), StandardCharsets.UTF_8);
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("String exceeds maximum length: " + value.length());
        }
        return value;
    }

    /**
     * Packs a block position in the protocol 47 layout (community-verified,
     * PrismarineJS/minecraft-data): x 26 bits (high, signed), y 12 bits
     * (middle, signed), z 26 bits (low, signed).
     */
    static void writePackedBlockPosition(ByteBuf out, int x, int y, int z) {
        long packed = ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
        out.writeLong(packed);
    }

    /** @return {x, y, z} decoded from the packed long block position of this protocol. */
    static int[] readPackedBlockPosition(ByteBuf in) {
        long packed = in.readLong();
        return decodePackedBlockPosition(packed);
    }

    /** @return {x, y, z} decoded from an already-read packed position long. */
    static int[] decodePackedBlockPosition(long packed) {
        int x = (int) (packed >> 38);                    // high 26 bits, sign-extended
        int y = (int) (((packed >>> 26) & 0xFFF) << 20 >> 20); // middle 12 bits, sign-extended
        int z = (int) (packed << 38 >> 38);              // low 26 bits, sign-extended
        return new int[]{x, y, z};
    }
}
