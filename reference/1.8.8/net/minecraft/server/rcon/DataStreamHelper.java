package net.minecraft.server.rcon;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class DataStreamHelper {
    private ByteArrayOutputStream byteArrayOutputStream;
    private DataOutputStream dataOutputStream;

    public DataStreamHelper(int size) {
        this.byteArrayOutputStream = new ByteArrayOutputStream(size);
        this.dataOutputStream = new DataOutputStream(this.byteArrayOutputStream);
    }

    public void write(byte[] writable) throws IOException {
        this.dataOutputStream.write(writable, 0, writable.length);
    }

    public void writeBytes(String name) throws IOException {
        this.dataOutputStream.writeBytes(name);
        this.dataOutputStream.write(0);
    }

    public void write(int writable) throws IOException {
        this.dataOutputStream.write(writable);
    }

    public void writeShort(short writable) throws IOException {
        this.dataOutputStream.writeShort(Short.reverseBytes(writable));
    }

    public byte[] bytes() {
        return this.byteArrayOutputStream.toByteArray();
    }

    public void reset() {
        this.byteArrayOutputStream.reset();
    }
}
