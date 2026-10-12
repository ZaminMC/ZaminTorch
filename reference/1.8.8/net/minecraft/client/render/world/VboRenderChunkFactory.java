package net.minecraft.client.render.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class VboRenderChunkFactory implements RenderChunkFactory {
    @Override
    public RenderChunk createChunk(World world, WorldRenderer worldRenderer, BlockPos pos, int index) {
        return new RenderChunk(world, worldRenderer, pos, index);
    }
}
