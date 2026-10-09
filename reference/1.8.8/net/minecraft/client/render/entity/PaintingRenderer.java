package net.minecraft.client.render.entity;

import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.decoration.PaintingEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public class PaintingRenderer extends EntityRenderer<PaintingEntity> {
    private static final Identifier PAINTINGS_LOCATION = new Identifier("textures/painting/paintings_kristoffer_zetterstrand.png");

    public PaintingRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
    }

    public void render(PaintingEntity paintingEntity, double d, double e, double f, float g, float h) {
        GlStateManager.pushMatrix();
        GlStateManager.translated(d, e, f);
        GlStateManager.rotatef(180.0F - g, 0.0F, 1.0F, 0.0F);
        GlStateManager.enableRescaleNormal();
        this.bindTexture(paintingEntity);
        PaintingEntity.Motive paintingentity$motive = paintingEntity.motive;
        float fx = 0.0625F;
        GlStateManager.scalef(fx, fx, fx);
        this.renderPainting(paintingEntity, paintingentity$motive.width, paintingentity$motive.height, paintingentity$motive.u, paintingentity$motive.v);
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        super.render(paintingEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(PaintingEntity paintingEntity) {
        return PAINTINGS_LOCATION;
    }

    private void renderPainting(PaintingEntity painting, int width, int height, int u, int v) {
        float f = -width / 2.0F;
        float f1 = -height / 2.0F;
        float f2 = 0.5F;
        float f3 = 0.75F;
        float f4 = 0.8125F;
        float f5 = 0.0F;
        float f6 = 0.0625F;
        float f7 = 0.75F;
        float f8 = 0.8125F;
        float f9 = 0.001953125F;
        float f10 = 0.001953125F;
        float f11 = 0.7519531F;
        float f12 = 0.7519531F;
        float f13 = 0.0F;
        float f14 = 0.0625F;

        for (int i = 0; i < width / 16; i++) {
            for (int j = 0; j < height / 16; j++) {
                float f15 = f + (i + 1) * 16;
                float f16 = f + i * 16;
                float f17 = f1 + (j + 1) * 16;
                float f18 = f1 + j * 16;
                this.applyBrightness(painting, (f15 + f16) / 2.0F, (f17 + f18) / 2.0F);
                float f19 = (u + width - i * 16) / 256.0F;
                float f20 = (u + width - (i + 1) * 16) / 256.0F;
                float f21 = (v + height - j * 16) / 256.0F;
                float f22 = (v + height - (j + 1) * 16) / 256.0F;
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder bufferbuilder = tesselator.getBuffer();
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_NORMAL);
                bufferbuilder.vertex(f15, f18, -f2).texture(f20, f21).normal(0.0F, 0.0F, -1.0F).nextVertex();
                bufferbuilder.vertex(f16, f18, -f2).texture(f19, f21).normal(0.0F, 0.0F, -1.0F).nextVertex();
                bufferbuilder.vertex(f16, f17, -f2).texture(f19, f22).normal(0.0F, 0.0F, -1.0F).nextVertex();
                bufferbuilder.vertex(f15, f17, -f2).texture(f20, f22).normal(0.0F, 0.0F, -1.0F).nextVertex();
                bufferbuilder.vertex(f15, f17, f2).texture(f3, f5).normal(0.0F, 0.0F, 1.0F).nextVertex();
                bufferbuilder.vertex(f16, f17, f2).texture(f4, f5).normal(0.0F, 0.0F, 1.0F).nextVertex();
                bufferbuilder.vertex(f16, f18, f2).texture(f4, f6).normal(0.0F, 0.0F, 1.0F).nextVertex();
                bufferbuilder.vertex(f15, f18, f2).texture(f3, f6).normal(0.0F, 0.0F, 1.0F).nextVertex();
                bufferbuilder.vertex(f15, f17, -f2).texture(f7, f9).normal(0.0F, 1.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f16, f17, -f2).texture(f8, f9).normal(0.0F, 1.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f16, f17, f2).texture(f8, f10).normal(0.0F, 1.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f15, f17, f2).texture(f7, f10).normal(0.0F, 1.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f15, f18, f2).texture(f7, f9).normal(0.0F, -1.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f16, f18, f2).texture(f8, f9).normal(0.0F, -1.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f16, f18, -f2).texture(f8, f10).normal(0.0F, -1.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f15, f18, -f2).texture(f7, f10).normal(0.0F, -1.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f15, f17, f2).texture(f12, f13).normal(-1.0F, 0.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f15, f18, f2).texture(f12, f14).normal(-1.0F, 0.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f15, f18, -f2).texture(f11, f14).normal(-1.0F, 0.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f15, f17, -f2).texture(f11, f13).normal(-1.0F, 0.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f16, f17, -f2).texture(f12, f13).normal(1.0F, 0.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f16, f18, -f2).texture(f12, f14).normal(1.0F, 0.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f16, f18, f2).texture(f11, f14).normal(1.0F, 0.0F, 0.0F).nextVertex();
                bufferbuilder.vertex(f16, f17, f2).texture(f11, f13).normal(1.0F, 0.0F, 0.0F).nextVertex();
                tesselator.end();
            }
        }
    }

    private void applyBrightness(PaintingEntity painting, float u, float v) {
        int i = MathHelper.floor(painting.x);
        int j = MathHelper.floor(painting.y + v / 16.0F);
        int k = MathHelper.floor(painting.z);
        Direction direction = painting.dir;
        if (direction == Direction.NORTH) {
            i = MathHelper.floor(painting.x + u / 16.0F);
        }

        if (direction == Direction.WEST) {
            k = MathHelper.floor(painting.z - u / 16.0F);
        }

        if (direction == Direction.SOUTH) {
            i = MathHelper.floor(painting.x - u / 16.0F);
        }

        if (direction == Direction.EAST) {
            k = MathHelper.floor(painting.z + u / 16.0F);
        }

        int l = this.dispatcher.world.getLightColor(new BlockPos(i, j, k), 0);
        int i1 = l % 65536;
        int j1 = l / 65536;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, i1, j1);
        GlStateManager.color3f(1.0F, 1.0F, 1.0F);
    }
}
