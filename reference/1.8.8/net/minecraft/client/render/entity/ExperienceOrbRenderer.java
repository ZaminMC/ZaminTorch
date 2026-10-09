package net.minecraft.client.render.entity;

import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class ExperienceOrbRenderer extends EntityRenderer<ExperienceOrbEntity> {
    private static final Identifier EXPERIENCE_ORB_LOCATION = new Identifier("textures/entity/experience_orb.png");

    public ExperienceOrbRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
        this.shadowSize = 0.15F;
        this.shadowDarkness = 0.75F;
    }

    public void render(ExperienceOrbEntity experienceOrbEntity, double d, double e, double f, float g, float h) {
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float)d, (float)e, (float)f);
        this.bindTexture(experienceOrbEntity);
        int i = experienceOrbEntity.getSize();
        float fx = (i % 4 * 16 + 0) / 64.0F;
        float f1 = (i % 4 * 16 + 16) / 64.0F;
        float f2 = (i / 4 * 16 + 0) / 64.0F;
        float f3 = (i / 4 * 16 + 16) / 64.0F;
        float f4 = 1.0F;
        float f5 = 0.5F;
        float f6 = 0.25F;
        int j = experienceOrbEntity.getLightLevel(h);
        int k = j % 65536;
        int l = j / 65536;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, k / 1.0F, l / 1.0F);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        float f8 = 255.0F;
        float f9 = (experienceOrbEntity.renderTicks + h) / 2.0F;
        l = (int)((MathHelper.sin(f9 + 0.0F) + 1.0F) * 0.5F * 255.0F);
        int i1 = 255;
        int j1 = (int)((MathHelper.sin(f9 + (float) (Math.PI * 4.0 / 3.0)) + 1.0F) * 0.1F * 255.0F);
        GlStateManager.rotatef(180.0F - this.dispatcher.cameraYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(-this.dispatcher.cameraPitch, 1.0F, 0.0F, 0.0F);
        float f7 = 0.3F;
        GlStateManager.scalef(0.3F, 0.3F, 0.3F);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        bufferbuilder.vertex(0.0F - f5, 0.0F - f6, 0.0).texture(fx, f3).color(l, 255, j1, 128).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(f4 - f5, 0.0F - f6, 0.0).texture(f1, f3).color(l, 255, j1, 128).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(f4 - f5, 1.0F - f6, 0.0).texture(f1, f2).color(l, 255, j1, 128).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(0.0F - f5, 1.0F - f6, 0.0).texture(fx, f2).color(l, 255, j1, 128).normal(0.0F, 1.0F, 0.0F).nextVertex();
        tesselator.end();
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        super.render(experienceOrbEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(ExperienceOrbEntity experienceOrbEntity) {
        return EXPERIENCE_ORB_LOCATION;
    }
}
