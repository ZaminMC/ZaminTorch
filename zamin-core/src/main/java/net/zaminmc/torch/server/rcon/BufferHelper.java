package net.zaminmc.torch.server.rcon;

import java.nio.charset.StandardCharsets;

/**
 * The little-endian framing helpers, 1:1 the reference
 * (reference/1.8.8/net/minecraft/server/rcon/BufferHelper.java, 34 lines):
 * the NUL-terminated UTF-8 string read and the little/big-endian int reads
 * the RCON and GS4 packet codecs ride.
 *
 * <p>Guava's {@code Charsets.UTF_8} is the JDK's {@link StandardCharsets}
 * here (the engine's no-Guava convention).</p>
 */
public final class BufferHelper {

    private BufferHelper() {
    }

    /** The reference's HEX_CHARS_LOOKUP (line 6), used by the query's packet log. */
    public static final char[] HEX_CHARS_LOOKUP =
            new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * Reads a NUL-terminated UTF-8 string from {@code pos}, stopping at the
     * terminator or {@code max - 1} (the reference lines 8-17, byte for
     * byte): {@code i = max - 1; j = pos > i ? i : pos;} then the walk.
     * A {@code pos} past the last readable byte yields the reference's own
     * negative-length StringIndexOutOfBounds — the identical crash-for-
     * garbage shape (the client loop catches it and closes).
     */
    public static String getString(byte[] buf, int pos, int max) {
        int i = max - 1;
        int j = pos > i ? i : pos;

        while (0 != buf[j] && j < i) {
            j++;
        }

        return new String(buf, pos, j - pos, StandardCharsets.UTF_8);
    }

    /** The reference line 19: the whole-buffer limit flavor. */
    public static int getIntLE(byte[] buf, int start) {
        return getIntLE(buf, start, buf.length);
    }

    /**
     * The little-endian int (the reference lines 23-25): 0 when fewer than
     * four bytes remain before the limit, else
     * {@code buf[s+3]<<24 | (buf[s+2]&0xFF)<<16 | (buf[s+1]&0xFF)<<8 | buf[s]&0xFF}.
     */
    public static int getIntLE(byte[] buf, int start, int limit) {
        return 0 > limit - start - 4
                ? 0
                : buf[start + 3] << 24 | (buf[start + 2] & 0xFF) << 16
                        | (buf[start + 1] & 0xFF) << 8 | buf[start] & 0xFF;
    }

    /**
     * The big-endian int (the reference lines 27-29): the GS4 challenge is
     * validated through this read (QueryResponseHandler.isValidQuery line
     * 191 reads the echoed token at offset 7).
     */
    public static int getIntBE(byte[] buf, int start, int limit) {
        return 0 > limit - start - 4
                ? 0
                : buf[start] << 24 | (buf[start + 1] & 0xFF) << 16
                        | (buf[start + 2] & 0xFF) << 8 | buf[start + 3] & 0xFF;
    }

    /** The reference lines 31-33: one byte as two hex chars. */
    public static String toHex(byte b) {
        return "" + HEX_CHARS_LOOKUP[(b & 240) >>> 4] + HEX_CHARS_LOOKUP[b & 15];
    }
}
