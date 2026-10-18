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
}
