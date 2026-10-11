package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;

/**
 * The piece's world view (Slice 12a — the engine's replacement for the
 * reference's {@code World} parameter on the structure paths): the
 * deterministic block surface one chunk's populate pass sees.
 *
 * <p>Reads inside the generating chunk answer the chunk's own array
 * (terrain + this pass's earlier writes); reads outside answer the
 * generator's pure pre-structure terrain function — the deterministic
 * equivalent of the reference's neighbor-chunk reads at populate time.
 * Writes land only inside the chunk (the clip box already guarantees
 * it; the view double-guards).</p>
 */
public interface StructureWorld {

    /** The block at the world cell (the chunk's own, or the pure terrain). */
    BlockType blockAt(int x, int y, int z);

    /** Whether the block is a liquid (the reference's material test). */
    boolean isLiquid(BlockType type);

    /** Writes one cell (ignored outside the generating chunk). */
    void setBlock(int x, int y, int z, BlockType type);

    /** The world's sea level (moveBelowSeaLevel's anchor). */
    int seaLevel();

    /** The structure block palette (resolved once per world). */
    StructurePalette palette();

    /** Whether the block is air (the reference's Material.AIR test). */
    default boolean isAir(BlockType type) {
        return type.identifier().value().equals("air");
    }

    /** Whether the block is opaque (the corridor's rail-floor gate). */
    boolean isOpaque(BlockType type);
}
