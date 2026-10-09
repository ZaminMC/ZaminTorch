package net.minecraft.client.render.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface RenderChunkFactory {
    RenderChunk createChunk(World world, WorldRenderer worldRenderer, BlockPos pos, int index);
}
