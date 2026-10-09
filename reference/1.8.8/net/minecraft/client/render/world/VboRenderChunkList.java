package net.minecraft.client.render.world;

import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.VertexBuffer;
import org.lwjgl.opengl.GL11;

public class VboRenderChunkList extends RenderChunkList {
    @Override
    public void render(BlockLayer layer) {
        if (this.ready) {
            for (RenderChunk renderchunk : this.chunks) {
                VertexBuffer vertexbuffer = renderchunk.getBuffer(layer.ordinal());
                GlStateManager.pushMatrix();
                this.glTranslatefOrigin(renderchunk);
                renderchunk.glMultMatrix();
                vertexbuffer.bind();
                this.m_6871525();
                vertexbuffer.draw(7);
                GlStateManager.popMatrix();
            }

            GLX.bindBuffer(GLX.GL_ARRAY_BUFFER, 0);
            GlStateManager.clearColor();
            this.chunks.clear();
        }
    }

    private void m_6871525() {
        GL11.glVertexPointer(3, 5126, 28, 0L);
        GL11.glColorPointer(4, 5121, 28, 12L);
        GL11.glTexCoordPointer(2, 5126, 28, 16L);
        GLX.clientActiveTexture(GLX.GL_TEXTURE1);
        GL11.glTexCoordPointer(2, 5122, 28, 24L);
        GLX.clientActiveTexture(GLX.GL_TEXTURE0);
    }
}
