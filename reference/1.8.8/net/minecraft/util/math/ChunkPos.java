package net.minecraft.util.math;

public class ChunkPos {
    public final int x;
    public final int z;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public static long toLong(int x, int y) {
        return x & 4294967295L | (y & 4294967295L) << 32;
    }

    @Override
    public int hashCode() {
        int i = 1664525 * this.x + 1013904223;
        int j = 1664525 * (this.z ^ -559038737) + 1013904223;
        return i ^ j;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ChunkPos)) {
            return false;
        }

        ChunkPos chunkpos = (ChunkPos)object;
        return this.x == chunkpos.x && this.z == chunkpos.z;
    }

    public int getCenterX() {
        return (this.x << 4) + 8;
    }

    public int getCenterZ() {
        return (this.z << 4) + 8;
    }

    public int getMinBlockPosX() {
        return this.x << 4;
    }

    public int getMinBlockPosZ() {
        return this.z << 4;
    }

    public int getMaxBlockPosX() {
        return (this.x << 4) + 15;
    }

    public int getMaxBlockPosZ() {
        return (this.z << 4) + 15;
    }

    public BlockPos getBlockPos(int localX, int y, int localZ) {
        return new BlockPos((this.x << 4) + localX, y, (this.z << 4) + localZ);
    }

    public BlockPos getCenterPos(int y) {
        return new BlockPos(this.getCenterX(), y, this.getCenterZ());
    }

    @Override
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }
}
