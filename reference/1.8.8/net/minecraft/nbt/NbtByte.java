package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class NbtByte extends NbtElement.Number {
    private byte value;

    NbtByte() {
    }

    public NbtByte(byte value) {
        this.value = value;
    }

    @Override
    void write(DataOutput output) throws IOException {
        output.writeByte(this.value);
    }

    @Override
    void read(DataInput input, int depth, NbtReadLimiter limiter) throws IOException {
        limiter.read(72L);
        this.value = input.readByte();
    }

    @Override
    public byte getType() {
        return 1;
    }

    @Override
    public String toString() {
        return "" + this.value + "b";
    }

    @Override
    public NbtElement copy() {
        return new NbtByte(this.value);
    }

    @Override
    public boolean equals(Object object) {
        if (super.equals(object)) {
            NbtByte nbtbyte = (NbtByte)object;
            return this.value == nbtbyte.value;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ this.value;
    }

    @Override
    public long getLong() {
        return this.value;
    }

    @Override
    public int getInt() {
        return this.value;
    }

    @Override
    public short getShort() {
        return this.value;
    }

    @Override
    public byte getByte() {
        return this.value;
    }

    @Override
    public double getDouble() {
        return this.value;
    }

    @Override
    public float getFloat() {
        return this.value;
    }
}
