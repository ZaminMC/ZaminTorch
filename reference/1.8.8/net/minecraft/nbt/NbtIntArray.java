package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class NbtIntArray extends NbtElement {
    private int[] value;

    NbtIntArray() {
    }

    public NbtIntArray(int[] value) {
        this.value = value;
    }

    @Override
    void write(DataOutput output) throws IOException {
        output.writeInt(this.value.length);

        for (int i = 0; i < this.value.length; i++) {
            output.writeInt(this.value[i]);
        }
    }

    @Override
    void read(DataInput input, int depth, NbtReadLimiter limiter) throws IOException {
        limiter.read(192L);
        int i = input.readInt();
        limiter.read(32 * i);
        this.value = new int[i];

        for (int j = 0; j < i; j++) {
            this.value[j] = input.readInt();
        }
    }

    @Override
    public byte getType() {
        return 11;
    }

    @Override
    public String toString() {
        String s = "[";

        for (int i : this.value) {
            s = s + i + ",";
        }

        return s + "]";
    }

    @Override
    public NbtElement copy() {
        int[] aint = new int[this.value.length];
        System.arraycopy(this.value, 0, aint, 0, this.value.length);
        return new NbtIntArray(aint);
    }

    @Override
    public boolean equals(Object object) {
        return super.equals(object) && Arrays.equals(this.value, ((NbtIntArray)object).value);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ Arrays.hashCode(this.value);
    }

    public int[] getIntArray() {
        return this.value;
    }
}
