package net.minecraft.client.render.entity;

import net.minecraft.client.render.Culler;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DecorationEntity;
import net.minecraft.entity.living.mob.MobEntity;

public abstract class MobRenderer<T extends MobEntity> extends LivingEntityRenderer<T> {
    public MobRenderer(EntityRenderDispatcher entityRenderDispatcher, Model model, float f) {
        super(entityRenderDispatcher, model, f);
    }

    protected boolean shouldRenderNameTag(T mobEntity) {
        return super.shouldRenderNameTag(mobEntity)
            && (mobEntity.shouldShowNameTag() || mobEntity.hasCustomName() && mobEntity == this.dispatcher.targetEntity);
    }

    public boolean shouldRender(T mobEntity, Culler culler, double d, double e, double f) {
        if (super.shouldRender(mobEntity, culler, d, e, f)) {
            return true;
        } else if (mobEntity.isLeashed() && mobEntity.getLeashHolder() != null) {
            Entity entity = mobEntity.getLeashHolder();
            return culler.isVisible(entity.getShape());
        } else {
            return false;
        }
    }

    public void render(T mobEntity, double d, double e, double f, float g, float h) {
        super.render(mobEntity, d, e, f, g, h);
        this.renderRiders(mobEntity, d, e, f, g, h);
    }

    public void setLightColor(T mob, float tickDelta) {
        int i = mob.getLightLevel(tickDelta);
        int j = i % 65536;
        int k = i / 65536;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, j / 1.0F, k / 1.0F);
    }

    private double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    protected void renderRiders(T entity, double dx, double dy, double dz, float yaw, float tickDelta) {
        Entity entityx = entity.getLeashHolder();
        if (entityx != null) {
            dy -= (1.6 - entity.height) * 0.5;
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            double d0 = this.lerp(entityx.lastYaw, entityx.yaw, tickDelta * 0.5F) * (float) (Math.PI / 180.0);
            double d1 = this.lerp(entityx.lastPitch, entityx.pitch, tickDelta * 0.5F) * (float) (Math.PI / 180.0);
            double d2 = Math.cos(d0);
            double d3 = Math.sin(d0);
            double d4 = Math.sin(d1);
            if (entityx instanceof DecorationEntity) {
                d2 = 0.0;
                d3 = 0.0;
                d4 = -1.0;
            }

            double d5 = Math.cos(d1);
            double d6 = this.lerp(entityx.lastX, entityx.x, tickDelta) - d2 * 0.7 - d3 * 0.5 * d5;
            double d7 = this.lerp(entityx.lastY + entityx.getEyeHeight() * 0.7, entityx.y + entityx.getEyeHeight() * 0.7, tickDelta) - d4 * 0.5 - 0.25;
            double d8 = this.lerp(entityx.lastZ, entityx.z, tickDelta) - d3 * 0.7 + d2 * 0.5 * d5;
            double d9 = this.lerp(entity.lastBodyYaw, entity.bodyYaw, tickDelta) * (float) (Math.PI / 180.0) + (Math.PI / 2);
            d2 = Math.cos(d9) * entity.width * 0.4;
            d3 = Math.sin(d9) * entity.width * 0.4;
            double d10 = this.lerp(entity.lastX, entity.x, tickDelta) + d2;
            double d11 = this.lerp(entity.lastY, entity.y, tickDelta);
            double d12 = this.lerp(entity.lastZ, entity.z, tickDelta) + d3;
            dx += d2;
            dz += d3;
            double d13 = (float)(d6 - d10);
            double d14 = (float)(d7 - d11);
            double d15 = (float)(d8 - d12);
            GlStateManager.disableTexture();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            int i = 24;
            double d16 = 0.025;
            bufferbuilder.begin(5, DefaultVertexFormat.POSITION_COLOR);

            for (int j = 0; j <= 24; j++) {
                float f = 0.5F;
                float f1 = 0.4F;
                float f2 = 0.3F;
                if (j % 2 == 0) {
                    f *= 0.7F;
                    f1 *= 0.7F;
                    f2 *= 0.7F;
                }

                float f3 = j / 24.0F;
                bufferbuilder.vertex(dx + d13 * f3 + 0.0, dy + d14 * (f3 * f3 + f3) * 0.5 + ((24.0F - j) / 18.0F + 0.125F), dz + d15 * f3)
                    .color(f, f1, f2, 1.0F)
                    .nextVertex();
                bufferbuilder.vertex(dx + d13 * f3 + 0.025, dy + d14 * (f3 * f3 + f3) * 0.5 + ((24.0F - j) / 18.0F + 0.125F) + 0.025, dz + d15 * f3)
                    .color(f, f1, f2, 1.0F)
                    .nextVertex();
            }

            tesselator.end();
            bufferbuilder.begin(5, DefaultVertexFormat.POSITION_COLOR);

            for (int k = 0; k <= 24; k++) {
                float f4 = 0.5F;
                float f5 = 0.4F;
                float f6 = 0.3F;
                if (k % 2 == 0) {
                    f4 *= 0.7F;
                    f5 *= 0.7F;
                    f6 *= 0.7F;
                }

                float f7 = k / 24.0F;
                bufferbuilder.vertex(dx + d13 * f7 + 0.0, dy + d14 * (f7 * f7 + f7) * 0.5 + ((24.0F - k) / 18.0F + 0.125F) + 0.025, dz + d15 * f7)
                    .color(f4, f5, f6, 1.0F)
                    .nextVertex();
                bufferbuilder.vertex(dx + d13 * f7 + 0.025, dy + d14 * (f7 * f7 + f7) * 0.5 + ((24.0F - k) / 18.0F + 0.125F), dz + d15 * f7 + 0.025)
                    .color(f4, f5, f6, 1.0F)
                    .nextVertex();
            }

            tesselator.end();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture();
            GlStateManager.enableCull();
        }
    }
}
