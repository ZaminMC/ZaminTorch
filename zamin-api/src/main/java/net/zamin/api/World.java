package net.zamin.api;

/**
 * A world holds authoritative block, entity and simulation state under one name.
 *
 * <p>Threading contract (Slice #1): reads are safe from any thread; mutation
 * ({@link #setBlock}) must happen on the engine's simulation execution context.
 * The API deliberately avoids returning live internal collections.</p>
 */
public interface World {

    /** The world's stable name (for example {@code "world"}). */
    String name();

    /** Current world time in ticks (0..23999 for time of day). */
    long timeOfDay();

    /** Total ticks that have elapsed in this world since creation. */
    long totalTicks();

    /** The registered spawn position of this world. */
    Position spawnPosition();

    /** @return the block type at the position, never null (air is a block type). */
    BlockType getBlock(BlockPosition position);

    /**
     * Sets the block type at the position.
     *
     * @return true if the state changed, false if the position already had this type
     * @throws IllegalStateException if called outside the world's allowed execution context
     */
    boolean setBlock(BlockPosition position, BlockType type);

    /** @return whether the chunk containing this position is loaded for gameplay. */
    boolean isChunkLoaded(ChunkPosition position);
}
