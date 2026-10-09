package net.minecraft.client.render.world;

import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

public class GlListRenderChunkList extends RenderChunkList {
    @Override
    public void render(BlockLayer layer) {
        if (this.ready) {
            for (RenderChunk renderchunk : this.chunks) {
                GlListRenderChunk gllistrenderchunk = (GlListRenderChunk)renderchunk;
                GlStateManager.pushMatrix();
                this.glTranslatefOrigin(renderchunk);
                GL11.glCallList(gllistrenderchunk.getGlList(layer, gllistrenderchunk.getCompiledChunk()));
                GlStateManager.popMatrix();
            }

            GlStateManager.clearColor();
            this.chunks.clear();
        }
    }
}
