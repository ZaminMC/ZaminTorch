package net.minecraft.client.render.world;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.util.math.BlockPos;

public abstract class RenderChunkList {
    private double x;
    private double y;
    private double z;
    protected List<RenderChunk> chunks = Lists.newArrayListWithCapacity(17424);
    protected boolean ready;

    public void setCameraPos(double x, double y, double z) {
        this.ready = true;
        this.chunks.clear();
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void glTranslatefOrigin(RenderChunk chunk) {
        BlockPos blockpos = chunk.getOrigin();
        GlStateManager.translatef((float)(blockpos.getX() - this.x), (float)(blockpos.getY() - this.y), (float)(blockpos.getZ() - this.z));
    }

    public void add(RenderChunk chunk, BlockLayer layer) {
        this.chunks.add(chunk);
    }

    public abstract void render(BlockLayer layer);
}
