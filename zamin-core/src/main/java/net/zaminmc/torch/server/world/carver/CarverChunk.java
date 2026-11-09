package net.zaminmc.torch.server.world.carver;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.Biome;

/**
 * The carver's chunk seam (the reference's BlockStateStorage + World pair,
 * the engine's generate-time shape): the cell read/write over the chunk
 * being generated (the local coordinates the reference's storage carries),
 * the block-level biome consult (the dirt-floor fix's surface material),
 * and the registration surface consult.
 */
public interface CarverChunk {

    /** The cell at the local coordinates (the carve's own reads see the fill and the surface pass). */
    BlockType get(int localX, int y, int localZ);

    /** The write (the carve's air, the lava floor, the sandstone ceiling fix). */
    void set(int localX, int y, int localZ, BlockType type);

    /** The block-level biome at the world column (the dirt-floor fix's consult). */
    Biome biomeAt(int worldX, int worldZ);

    /** The biome's registered surface material (the registration pair's first member). */
    BlockType registeredSurfaceOf(Biome biome);
}
