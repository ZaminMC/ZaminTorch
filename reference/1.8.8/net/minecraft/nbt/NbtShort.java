package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class NbtShort extends NbtElement.Number {
    private short value;

    public NbtShort() {
    }

    public NbtShort(short value) {
        this.value = value;
    }

    @Override
    void write(DataOutput output) throws IOException {
        output.writeShort(this.value);
    }

    @Override
    void read(DataInput input, int depth, NbtReadLimiter limiter) throws IOException {
        limiter.read(80L);
        this.value = input.readShort();
    }

    @Override
    public byte getType() {
        return 2;
    }

    @Override
    public String toString() {
        return "" + this.value + "s";
    }

    @Override
    public NbtElement copy() {
        return new NbtShort(this.value);
    }

    @Override
    public boolean equals(Object object) {
        if (super.equals(object)) {
            NbtShort nbtshort = (NbtShort)object;
            return this.value == nbtshort.value;
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
