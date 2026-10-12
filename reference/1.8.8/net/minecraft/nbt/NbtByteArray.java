package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class NbtByteArray extends NbtElement {
    private byte[] value;

    NbtByteArray() {
    }

    public NbtByteArray(byte[] value) {
        this.value = value;
    }

    @Override
    void write(DataOutput output) throws IOException {
        output.writeInt(this.value.length);
        output.write(this.value);
    }

    @Override
    void read(DataInput input, int depth, NbtReadLimiter limiter) throws IOException {
        limiter.read(192L);
        int i = input.readInt();
        limiter.read(8 * i);
        this.value = new byte[i];
        input.readFully(this.value);
    }

    @Override
    public byte getType() {
        return 7;
    }

    @Override
    public String toString() {
        return "[" + this.value.length + " bytes]";
    }

    @Override
    public NbtElement copy() {
        byte[] abyte = new byte[this.value.length];
        System.arraycopy(this.value, 0, abyte, 0, this.value.length);
        return new NbtByteArray(abyte);
    }

    @Override
    public boolean equals(Object object) {
        return super.equals(object) && Arrays.equals(this.value, ((NbtByteArray)object).value);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ Arrays.hashCode(this.value);
    }

    public byte[] getByteArray() {
        return this.value;
    }
}
