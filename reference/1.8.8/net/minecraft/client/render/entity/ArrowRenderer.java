package net.minecraft.client.render.entity;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

public class ArrowRenderer extends EntityRenderer<ArrowEntity> {
    private static final Identifier ARROW_LOCATION = new Identifier("textures/entity/arrow.png");

    public ArrowRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
    }

    public void render(ArrowEntity arrowEntity, double d, double e, double f, float g, float h) {
        this.bindTexture(arrowEntity);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float)d, (float)e, (float)f);
        GlStateManager.rotatef(arrowEntity.lastYaw + (arrowEntity.yaw - arrowEntity.lastYaw) * h - 90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(arrowEntity.lastPitch + (arrowEntity.pitch - arrowEntity.lastPitch) * h, 0.0F, 0.0F, 1.0F);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        int i = 0;
        float fx = 0.0F;
        float f1 = 0.5F;
        float f2 = (0 + i * 10) / 32.0F;
        float f3 = (5 + i * 10) / 32.0F;
        float f4 = 0.0F;
        float f5 = 0.15625F;
        float f6 = (5 + i * 10) / 32.0F;
        float f7 = (10 + i * 10) / 32.0F;
        float f8 = 0.05625F;
        GlStateManager.enableRescaleNormal();
        float f9 = arrowEntity.shake - h;
        if (f9 > 0.0F) {
            float f10 = -MathHelper.sin(f9 * 3.0F) * f9;
            GlStateManager.rotatef(f10, 0.0F, 0.0F, 1.0F);
        }

        GlStateManager.rotatef(45.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scalef(f8, f8, f8);
        GlStateManager.translatef(-4.0F, 0.0F, 0.0F);
        GL11.glNormal3f(f8, 0.0F, 0.0F);
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(-7.0, -2.0, -2.0).texture(f4, f6).nextVertex();
        bufferbuilder.vertex(-7.0, -2.0, 2.0).texture(f5, f6).nextVertex();
        bufferbuilder.vertex(-7.0, 2.0, 2.0).texture(f5, f7).nextVertex();
        bufferbuilder.vertex(-7.0, 2.0, -2.0).texture(f4, f7).nextVertex();
        tesselator.end();
        GL11.glNormal3f(-f8, 0.0F, 0.0F);
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(-7.0, 2.0, -2.0).texture(f4, f6).nextVertex();
        bufferbuilder.vertex(-7.0, 2.0, 2.0).texture(f5, f6).nextVertex();
        bufferbuilder.vertex(-7.0, -2.0, 2.0).texture(f5, f7).nextVertex();
        bufferbuilder.vertex(-7.0, -2.0, -2.0).texture(f4, f7).nextVertex();
        tesselator.end();

        for (int j = 0; j < 4; j++) {
            GlStateManager.rotatef(90.0F, 1.0F, 0.0F, 0.0F);
            GL11.glNormal3f(0.0F, 0.0F, f8);
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.vertex(-8.0, -2.0, 0.0).texture(fx, f2).nextVertex();
            bufferbuilder.vertex(8.0, -2.0, 0.0).texture(f1, f2).nextVertex();
            bufferbuilder.vertex(8.0, 2.0, 0.0).texture(f1, f3).nextVertex();
            bufferbuilder.vertex(-8.0, 2.0, 0.0).texture(fx, f3).nextVertex();
            tesselator.end();
        }

        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        super.render(arrowEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(ArrowEntity arrowEntity) {
        return ARROW_LOCATION;
    }
}
