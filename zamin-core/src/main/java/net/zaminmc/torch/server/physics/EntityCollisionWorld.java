package net.zaminmc.torch.server.physics;

import net.zaminmc.torch.block.BlockType;

/**
 * The block-read surface the physics solver resolves against (the
 * physics-solver slice): the reference's own {@code World} consults
 * that {@code Entity.move} makes, distilled to the reads the engine's
 * owner-checked worlds answer.
 *
 * <p>The implementor owns the dimension context — the reference's
 * {@code Entity.world} (every body reads its own world): the mob
 * manager flips its active dimension before each body's walk, the item
 * and falling-block families carry their own. The reads are
 * owner-context consistent (§6.2: the tick lane owns the walk).</p>
 */
public interface EntityCollisionWorld {

    /**
     * The block at the cell (the reference's {@code getBlockState}):
     * unloaded cells answer air through the world's own {@code getBlock}
     * convention — the caller's chunk consult gates the walk itself.
     */
    BlockType blockAt(int x, int y, int z);

    /**
     * Whether the column's chunk is loaded at the cell (the reference's
     * {@code isChunkLoaded} gate in {@code getCollisions} lines 912-940:
     * an unloaded column contributes NO boxes — a body never collides
     * with unloaded terrain).
     */
    boolean chunkLoaded(int x, int z);

    /**
     * Whether the cell lies inside the world border (the reference's
     * {@code worldborder.contains(blockpos)} consult): cells outside the
     * border read as STONE for a body INSIDE the border (the border is
     * a wall) and read real for a body already outside. Implementations
     * without a border answer true everywhere.
     */
    default boolean borderContains(int x, int z) {
        return true;
    }

    /**
     * WAVE KO-F — the per-chunk read face (Paper 0060 part 2's engine
     * mapping, the exact-algorithmic class): one chunk fetch serving
     * every cell of a 16x16 chunk column, replacing the per-cell world
     * lookup ({@code new BlockPosition} + {@code chunkPosition()} + the
     * concurrent-map {@code get} — three allocations and a hashed walk
     * per cell the chunk-banded gather pays ONCE per chunk instead).
     *
     * <p>PROVABLY EXACT (the KO-A shape's own proof obligation): the
     * reader must answer IDENTICALLY to {@link #blockAt(int, int, int)}
     * for every cell of the chunk it was fetched for, and its presence
     * must equal {@link #chunkLoaded(int, int)} for that chunk's columns
     * — a present reader IS a loaded chunk, a null reader an unloaded
     * one (or a world without the face, where the gather falls back to
     * the per-cell reads unchanged). The reference walk's own shape
     * (Paper 0060's {@code getChunkIfLoaded} fetched once per chunk
     * before the cell loops, replacing {@code isChunkLoaded} +
     * {@code getChunkAt} per chunk).</p>
     *
     * <p>The default answers null — every existing surface (the test
     * stubs, the movement guard's worlds) keeps the per-cell path
     * byte-identical until it opts in.</p>
     *
     * @param chunkX the chunk's X coordinate (block x >> 4)
     * @param chunkZ the chunk's Z coordinate (block z >> 4)
     * @return the chunk's read face, or null when the chunk is not
     *         loaded or the surface carries no fast face
     */
    default ChunkReader chunkReader(int chunkX, int chunkZ) {
        return null;
    }

    /**
     * One loaded chunk's own cell-read face (WAVE KO-F): the cells of
     * the chunk the reader was fetched for, in WORLD coordinates — the
     * same answers {@code blockAt} would give, read straight off the
     * chunk the per-cell world lookup would find.
     */
    interface ChunkReader {

        /**
         * The block at the cell, world coordinates (the chunk's own
         * cells): identical to {@link EntityCollisionWorld#blockAt(
         * int, int, int)} for every cell inside the reader's chunk.
         */
        BlockType blockAt(int x, int y, int z);
    }
}
