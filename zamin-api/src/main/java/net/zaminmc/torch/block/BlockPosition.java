package net.zaminmc.torch.block;

/**
 * An integer position of a single block in a world. Not a world reference —
 * coordinates alone describe the position; the containing world is implicit
 * in the API call that receives it.
 */
public record BlockPosition(int x, int y, int z) {

    public static final int MAX_Y = 255;
    public static final int MIN_Y = 0;

    public BlockPosition {
        // y bounds are validated eagerly: unbounded y would silently corrupt chunk access later.
        if (y < MIN_Y || y > MAX_Y) {
            throw new IllegalArgumentException("y out of world bounds: " + y);
        }
    }

    public ChunkPosition chunkPosition() {
        return ChunkPosition.ofBlock(x, z);
    }

    public BlockPosition offset(int dx, int dy, int dz) {
        return new BlockPosition(x + dx, y + dy, z + dz);
    }

    /** The unit vector stepped {@code times} cells (the frame walks). */
    public BlockPosition offset(int dx, int dy, int dz, int times) {
        return new BlockPosition(x + dx * times, y + dy * times, z + dz * times);
    }

    /** Local coordinate inside a chunk (0..15). */
    public int localX() {
        return x & 0xF;
    }

    /** Local coordinate inside a chunk (0..15). */
    public int localZ() {
        return z & 0xF;
    }
}
