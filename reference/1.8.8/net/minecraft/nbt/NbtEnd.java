package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class NbtEnd extends NbtElement {
    NbtEnd() {
    }

    @Override
    void read(DataInput input, int depth, NbtReadLimiter limiter) throws IOException {
        limiter.read(64L);
    }

    @Override
    void write(DataOutput output) throws IOException {
    }

    @Override
    public byte getType() {
        return 0;
    }

    @Override
    public String toString() {
        return "END";
    }

    @Override
    public NbtElement copy() {
        return new NbtEnd();
    }
}
