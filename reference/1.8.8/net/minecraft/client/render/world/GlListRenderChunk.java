package net.minecraft.client.render.world;

import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class GlListRenderChunk extends RenderChunk {
    private final int list = MemoryTracker.getLists(BlockLayer.values().length);

    public GlListRenderChunk(World world, WorldRenderer worldRenderer, BlockPos blockPos, int i) {
        super(world, worldRenderer, blockPos, i);
    }

    public int getGlList(BlockLayer layer, CompiledChunk compiled) {
        return !compiled.hasBlock(layer) ? this.list + layer.ordinal() : -1;
    }

    @Override
    public void delete() {
        super.delete();
        MemoryTracker.releaseLists(this.list, BlockLayer.values().length);
    }
}
