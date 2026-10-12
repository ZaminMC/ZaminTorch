package net.minecraft.client.render.vertex;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

public class BufferUploader {
    public void end(BufferBuilder builder) {
        if (builder.getVertexCount() > 0) {
            VertexFormat vertexformat = builder.getFormat();
            int i = vertexformat.getVertexSize();
            ByteBuffer bytebuffer = builder.getBuffer();
            List<VertexFormatElement> list = vertexformat.getElements();

            for (int j = 0; j < list.size(); j++) {
                VertexFormatElement vertexformatelement = list.get(j);
                VertexFormatElement.Usage vertexformatelement$usage = vertexformatelement.getUsage();
                int k = vertexformatelement.getType().getGlCode();
                int l = vertexformatelement.getIndex();
                ((Buffer)bytebuffer).position(vertexformat.getOffset(j));
                switch (vertexformatelement$usage) {
                    case POSITION:
                        GL11.glVertexPointer(vertexformatelement.getCount(), k, i, bytebuffer);
                        GL11.glEnableClientState(32884);
                        break;
                    case UV:
                        GLX.clientActiveTexture(GLX.GL_TEXTURE0 + l);
                        GL11.glTexCoordPointer(vertexformatelement.getCount(), k, i, bytebuffer);
                        GL11.glEnableClientState(32888);
                        GLX.clientActiveTexture(GLX.GL_TEXTURE0);
                        break;
                    case COLOR:
                        GL11.glColorPointer(vertexformatelement.getCount(), k, i, bytebuffer);
                        GL11.glEnableClientState(32886);
                        break;
                    case NORMAL:
                        GL11.glNormalPointer(k, i, bytebuffer);
                        GL11.glEnableClientState(32885);
                }
            }

            GL11.glDrawArrays(builder.getDrawMode(), 0, builder.getVertexCount());
            int i1 = 0;

            for (int j1 = list.size(); i1 < j1; i1++) {
                VertexFormatElement vertexformatelement1 = list.get(i1);
                VertexFormatElement.Usage vertexformatelement$usage1 = vertexformatelement1.getUsage();
                int k1 = vertexformatelement1.getIndex();
                switch (vertexformatelement$usage1) {
                    case POSITION:
                        GL11.glDisableClientState(32884);
                        break;
                    case UV:
                        GLX.clientActiveTexture(GLX.GL_TEXTURE0 + k1);
                        GL11.glDisableClientState(32888);
                        GLX.clientActiveTexture(GLX.GL_TEXTURE0);
                        break;
                    case COLOR:
                        GL11.glDisableClientState(32886);
                        GlStateManager.clearColor();
                        break;
                    case NORMAL:
                        GL11.glDisableClientState(32885);
                }
            }
        }

        builder.clear();
    }
}
