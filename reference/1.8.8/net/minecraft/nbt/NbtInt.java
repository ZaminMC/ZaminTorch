package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class NbtInt extends NbtElement.Number {
    private int value;

    NbtInt() {
    }

    public NbtInt(int value) {
        this.value = value;
    }

    @Override
    void write(DataOutput output) throws IOException {
        output.writeInt(this.value);
    }

    @Override
    void read(DataInput input, int depth, NbtReadLimiter limiter) throws IOException {
        limiter.read(96L);
        this.value = input.readInt();
    }

    @Override
    public byte getType() {
        return 3;
    }

    @Override
    public String toString() {
        return "" + this.value;
    }

    @Override
    public NbtElement copy() {
        return new NbtInt(this.value);
    }

    @Override
    public boolean equals(Object object) {
        if (super.equals(object)) {
            NbtInt nbtint = (NbtInt)object;
            return this.value == nbtint.value;
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
        return (short)(this.value & 65535);
    }

    @Override
    public byte getByte() {
        return (byte)(this.value & 0xFF);
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
