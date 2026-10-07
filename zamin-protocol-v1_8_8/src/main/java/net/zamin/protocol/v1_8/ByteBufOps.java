package net.zamin.protocol.v1_8;

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

    /** Packs a block position (x 26 bits, z 26 bits, y 12 bits) as this version expects. */
    static void writePackedBlockPosition(ByteBuf out, int x, int y, int z) {
        long packed = ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        out.writeLong(packed);
    }

    /** @return {x, y, z} decoded from a packed long block position of this protocol. */
    static int[] readPackedBlockPosition(ByteBuf in) {
        long packed = in.readLong();
        int x = (int) (packed >> 38);
        int y = (int) (packed << 52 >> 52);
        int z = (int) (packed << 26 >> 38);
        return new int[]{x, y, z};
    }
}
