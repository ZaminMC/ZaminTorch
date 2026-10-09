package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class NbtLong extends NbtElement.Number {
    private long value;

    NbtLong() {
    }

    public NbtLong(long value) {
        this.value = value;
    }

    @Override
    void write(DataOutput output) throws IOException {
        output.writeLong(this.value);
    }

    @Override
    void read(DataInput input, int depth, NbtReadLimiter limiter) throws IOException {
        limiter.read(128L);
        this.value = input.readLong();
    }

    @Override
    public byte getType() {
        return 4;
    }

    @Override
    public String toString() {
        return "" + this.value + "L";
    }

    @Override
    public NbtElement copy() {
        return new NbtLong(this.value);
    }

    @Override
    public boolean equals(Object object) {
        if (super.equals(object)) {
            NbtLong nbtlong = (NbtLong)object;
            return this.value == nbtlong.value;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ (int)(this.value ^ this.value >>> 32);
    }

    @Override
    public long getLong() {
        return this.value;
    }

    @Override
    public int getInt() {
        return (int)(this.value & -1L);
    }

    @Override
    public short getShort() {
        return (short)(this.value & 65535L);
    }

    @Override
    public byte getByte() {
        return (byte)(this.value & 255L);
    }

    @Override
    public double getDouble() {
        return this.value;
    }

    @Override
    public float getFloat() {
        return (float)this.value;
    }
}
