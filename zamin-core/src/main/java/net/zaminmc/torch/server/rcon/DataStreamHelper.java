package net.zaminmc.torch.server.rcon;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The GS4 reply builder, 1:1 the reference
 * (reference/1.8.8/net/minecraft/server/rcon/DataStreamHelper.java, 40 lines):
 * a growable byte sink with the query protocol's three write flavors — raw
 * bytes, the NUL-terminated low-byte string, and the little-endian short.
 */
public class DataStreamHelper {

    private final ByteArrayOutputStream byteArrayOutputStream;
    private final DataOutputStream dataOutputStream;

    public DataStreamHelper(int size) {
        this.byteArrayOutputStream = new ByteArrayOutputStream(size);
        this.dataOutputStream = new DataOutputStream(this.byteArrayOutputStream);
    }

    /** The reference line 16: a raw copy of the whole array. */
    public void write(byte[] writable) throws IOException {
        this.dataOutputStream.write(writable, 0, writable.length);
    }

    /**
     * The reference lines 20-23: {@code DataOutputStream.writeBytes} — the
     * LOW eight bits of every char, no charset decoding — then a NUL
     * terminator. The query's key/value strings ride this flavor (the
     * reference's own shape: a non-ASCII MOTD is truncated per char, never
     * UTF-8 encoded).
     */
    public void writeBytes(String name) throws IOException {
        this.dataOutputStream.writeBytes(name);
        this.dataOutputStream.write(0);
    }

    /** The reference line 25: one raw byte. */
    public void write(int writable) throws IOException {
        this.dataOutputStream.write(writable);
    }

    /** The reference lines 29-31: the little-endian short (the basic stats' port). */
    public void writeShort(short writable) throws IOException {
        this.dataOutputStream.writeShort(Short.reverseBytes(writable));
    }

    /** The reference line 33: the accumulated reply. */
    public byte[] bytes() {
        return this.byteArrayOutputStream.toByteArray();
    }

    /** The reference line 37: the 5-second cached rules reply's reset. */
    public void reset() {
        this.byteArrayOutputStream.reset();
    }
}
