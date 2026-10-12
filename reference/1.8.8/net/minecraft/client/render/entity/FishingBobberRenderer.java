package net.minecraft.client.render.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.FishingBobberEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class FishingBobberRenderer extends EntityRenderer<FishingBobberEntity> {
    private static final Identifier PARTICLES_LOCATION = new Identifier("textures/particle/particles.png");

    public FishingBobberRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
    }

    public void render(FishingBobberEntity fishingBobberEntity, double d, double e, double f, float g, float h) {
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float)d, (float)e, (float)f);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scalef(0.5F, 0.5F, 0.5F);
        this.bindTexture(fishingBobberEntity);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        int i = 1;
        int j = 2;
        float fx = 0.0625F;
        float f1 = 0.125F;
        float f2 = 0.125F;
        float f3 = 0.1875F;
        float f4 = 1.0F;
        float f5 = 0.5F;
        float f6 = 0.5F;
        GlStateManager.rotatef(180.0F - this.dispatcher.cameraYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(-this.dispatcher.cameraPitch, 1.0F, 0.0F, 0.0F);
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_NORMAL);
        bufferbuilder.vertex(-0.5, -0.5, 0.0).texture(0.0625, 0.1875).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(0.5, -0.5, 0.0).texture(0.125, 0.1875).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(0.5, 0.5, 0.0).texture(0.125, 0.125).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(-0.5, 0.5, 0.0).texture(0.0625, 0.125).normal(0.0F, 1.0F, 0.0F).nextVertex();
        tesselator.end();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        if (fishingBobberEntity.thrower != null) {
            float f7 = fishingBobberEntity.thrower.getAttackAnimationProgress(h);
            float f8 = MathHelper.sin(MathHelper.sqrt(f7) * (float) Math.PI);
            Vec3d vec3d = new Vec3d(-0.36, 0.03, 0.35);
            vec3d = vec3d.rotateX(
                -(fishingBobberEntity.thrower.lastPitch + (fishingBobberEntity.thrower.pitch - fishingBobberEntity.thrower.lastPitch) * h)
                    * (float) Math.PI
                    / 180.0F
            );
            vec3d = vec3d.rotateY(
                -(fishingBobberEntity.thrower.lastYaw + (fishingBobberEntity.thrower.yaw - fishingBobberEntity.thrower.lastYaw) * h) * (float) Math.PI / 180.0F
            );
            vec3d = vec3d.rotateY(f8 * 0.5F);
            vec3d = vec3d.rotateX(-f8 * 0.7F);
            double d0 = fishingBobberEntity.thrower.lastX + (fishingBobberEntity.thrower.x - fishingBobberEntity.thrower.lastX) * h + vec3d.x;
            double d1 = fishingBobberEntity.thrower.lastY + (fishingBobberEntity.thrower.y - fishingBobberEntity.thrower.lastY) * h + vec3d.y;
            double d2 = fishingBobberEntity.thrower.lastZ + (fishingBobberEntity.thrower.z - fishingBobberEntity.thrower.lastZ) * h + vec3d.z;
            double d3 = fishingBobberEntity.thrower.getEyeHeight();
            if (this.dispatcher.options != null && this.dispatcher.options.perspective > 0 || fishingBobberEntity.thrower != Minecraft.getInstance().player) {
                float f9 = (fishingBobberEntity.thrower.lastBodyYaw + (fishingBobberEntity.thrower.bodyYaw - fishingBobberEntity.thrower.lastBodyYaw) * h)
                    * (float) Math.PI
                    / 180.0F;
                double d4 = MathHelper.sin(f9);
                double d6 = MathHelper.cos(f9);
                double d8 = 0.35;
                double d10 = 0.8;
                d0 = fishingBobberEntity.thrower.lastX + (fishingBobberEntity.thrower.x - fishingBobberEntity.thrower.lastX) * h - d6 * 0.35 - d4 * 0.8;
                d1 = fishingBobberEntity.thrower.lastY + d3 + (fishingBobberEntity.thrower.y - fishingBobberEntity.thrower.lastY) * h - 0.45;
                d2 = fishingBobberEntity.thrower.lastZ + (fishingBobberEntity.thrower.z - fishingBobberEntity.thrower.lastZ) * h - d4 * 0.35 + d6 * 0.8;
                d3 = fishingBobberEntity.thrower.isSneaking() ? -0.1875 : 0.0;
            }

            double d13 = fishingBobberEntity.lastX + (fishingBobberEntity.x - fishingBobberEntity.lastX) * h;
            double d5 = fishingBobberEntity.lastY + (fishingBobberEntity.y - fishingBobberEntity.lastY) * h + 0.25;
            double d7 = fishingBobberEntity.lastZ + (fishingBobberEntity.z - fishingBobberEntity.lastZ) * h;
            double d9 = (float)(d0 - d13);
            double d11 = (float)(d1 - d5) + d3;
            double d12 = (float)(d2 - d7);
            GlStateManager.disableTexture();
            GlStateManager.disableLighting();
            bufferbuilder.begin(3, DefaultVertexFormat.POSITION_COLOR);
            int k = 16;

            for (int l = 0; l <= 16; l++) {
                float f10 = l / 16.0F;
                bufferbuilder.vertex(d + d9 * f10, e + d11 * (f10 * f10 + f10) * 0.5 + 0.25, f + d12 * f10).color(0, 0, 0, 255).nextVertex();
            }

            tesselator.end();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture();
            super.render(fishingBobberEntity, d, e, f, g, h);
        }
    }

    protected Identifier getTextureLocation(FishingBobberEntity fishingBobberEntity) {
        return PARTICLES_LOCATION;
    }
}
