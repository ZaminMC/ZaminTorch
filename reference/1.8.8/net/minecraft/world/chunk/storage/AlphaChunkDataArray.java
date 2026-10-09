package net.minecraft.world.chunk.storage;

public class AlphaChunkDataArray {
    public final byte[] data;
    private final int zBitOffset;
    private final int xBitOffset;

    public AlphaChunkDataArray(byte[] data, int zBitOffset) {
        this.data = data;
        this.zBitOffset = zBitOffset;
        this.xBitOffset = zBitOffset + 4;
    }

    public int get(int x, int y, int z) {
        int i = x << this.xBitOffset | z << this.zBitOffset | y;
        int j = i >> 1;
        int k = i & 1;
        return k == 0 ? this.data[j] & 15 : this.data[j] >> 4 & 15;
    }
}
