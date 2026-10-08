package net.zaminmc.torch.block;

/**
 * Position of a 16x256x16 chunk column. Chunk coordinates are block
 * coordinates shifted right by 4 (floor division).
 */
public record ChunkPosition(int x, int z) {

    public static ChunkPosition ofBlock(int blockX, int blockZ) {
        return new ChunkPosition(blockX >> 4, blockZ >> 4);
    }

    /** Packs into a stable long key for map use: high 32 bits x, low 32 bits z. */
    public long packed() {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static ChunkPosition unpack(long packed) {
        return new ChunkPosition((int) (packed >> 32), (int) packed);
    }

    public int minBlockX() {
        return x << 4;
    }

    public int minBlockZ() {
        return z << 4;
    }

    /** Euclidean squared distance in chunk space (for view distance checks). */
    public long distanceSquared(ChunkPosition other) {
        long dx = (long) x - other.x;
        long dz = (long) z - other.z;
        return dx * dx + dz * dz;
    }
}
