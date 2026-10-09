package net.minecraft.client.render.entity;

import net.minecraft.client.render.Culler;
import net.minecraft.client.render.model.entity.GuardianModel;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

public class GuardianRenderer extends MobRenderer<GuardianEntity> {
    private static final Identifier GUARDIAN_LOCATION = new Identifier("textures/entity/guardian.png");
    private static final Identifier ELDER_GUARDIAN_TEXTURE = new Identifier("textures/entity/guardian_elder.png");
    private static final Identifier GUARDIAN_BEAM_LOCATION = new Identifier("textures/entity/guardian_beam.png");
    int version = ((GuardianModel)this.model).getVersion();

    public GuardianRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new GuardianModel(), 0.5F);
    }

    public boolean shouldRender(GuardianEntity guardianEntity, Culler culler, double d, double e, double f) {
        if (super.shouldRender(guardianEntity, culler, d, e, f)) {
            return true;
        }

        if (guardianEntity.hasBeamTarget()) {
            LivingEntity livingentity = guardianEntity.getLaserTarget();
            if (livingentity != null) {
                Vec3d vec3d = this.getLerpedPos(livingentity, livingentity.height * 0.5, 1.0F);
                Vec3d vec3d1 = this.getLerpedPos(guardianEntity, guardianEntity.getEyeHeight(), 1.0F);
                if (culler.isVisible(Box.of(vec3d1.x, vec3d1.y, vec3d1.z, vec3d.x, vec3d.y, vec3d.z))) {
                    return true;
                }
            }
        }

        return false;
    }

    private Vec3d getLerpedPos(LivingEntity entity, double heightOffset, float tickDelta) {
        double d0 = entity.prevX + (entity.x - entity.prevX) * tickDelta;
        double d1 = heightOffset + entity.prevY + (entity.y - entity.prevY) * tickDelta;
        double d2 = entity.prevZ + (entity.z - entity.prevZ) * tickDelta;
        return new Vec3d(d0, d1, d2);
    }

    public void render(GuardianEntity guardianEntity, double d, double e, double f, float g, float h) {
        if (this.version != ((GuardianModel)this.model).getVersion()) {
            this.model = new GuardianModel();
            this.version = ((GuardianModel)this.model).getVersion();
        }

        super.render(guardianEntity, d, e, f, g, h);
        LivingEntity livingentity = guardianEntity.getLaserTarget();
        if (livingentity != null) {
            float fx = guardianEntity.getBeamProgress(h);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            this.bindTexture(GUARDIAN_BEAM_LOCATION);
            GL11.glTexParameterf(3553, 10242, 10497.0F);
            GL11.glTexParameterf(3553, 10243, 10497.0F);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
            float f1 = 240.0F;
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, f1, f1);
            GlStateManager.blendFuncSeparate(770, 1, 1, 0);
            float f2 = (float)guardianEntity.world.getTime() + h;
            float f3 = f2 * 0.5F % 1.0F;
            float f4 = guardianEntity.getEyeHeight();
            GlStateManager.pushMatrix();
            GlStateManager.translatef((float)d, (float)e + f4, (float)f);
            Vec3d vec3d = this.getLerpedPos(livingentity, livingentity.height * 0.5, h);
            Vec3d vec3d1 = this.getLerpedPos(guardianEntity, f4, h);
            Vec3d vec3d2 = vec3d.subtract(vec3d1);
            double d0 = vec3d2.length() + 1.0;
            vec3d2 = vec3d2.normalize();
            float f5 = (float)Math.acos(vec3d2.y);
            float f6 = (float)Math.atan2(vec3d2.z, vec3d2.x);
            GlStateManager.rotatef(((float) (Math.PI / 2) + -f6) * (180.0F / (float)Math.PI), 0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(f5 * (180.0F / (float)Math.PI), 1.0F, 0.0F, 0.0F);
            int i = 1;
            double d1 = f2 * 0.05 * (1.0 - (i & 1) * 2.5);
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
            float f7 = fx * fx;
            int j = 64 + (int)(f7 * 240.0F);
            int k = 32 + (int)(f7 * 192.0F);
            int l = 128 - (int)(f7 * 64.0F);
            double d2 = i * 0.2;
            double d3 = d2 * 1.41;
            double d4 = 0.0 + Math.cos(d1 + (Math.PI * 3.0 / 4.0)) * d3;
            double d5 = 0.0 + Math.sin(d1 + (Math.PI * 3.0 / 4.0)) * d3;
            double d6 = 0.0 + Math.cos(d1 + (Math.PI / 4)) * d3;
            double d7 = 0.0 + Math.sin(d1 + (Math.PI / 4)) * d3;
            double d8 = 0.0 + Math.cos(d1 + (Math.PI * 5.0 / 4.0)) * d3;
            double d9 = 0.0 + Math.sin(d1 + (Math.PI * 5.0 / 4.0)) * d3;
            double d10 = 0.0 + Math.cos(d1 + (Math.PI * 7.0 / 4.0)) * d3;
            double d11 = 0.0 + Math.sin(d1 + (Math.PI * 7.0 / 4.0)) * d3;
            double d12 = 0.0 + Math.cos(d1 + Math.PI) * d2;
            double d13 = 0.0 + Math.sin(d1 + Math.PI) * d2;
            double d14 = 0.0 + Math.cos(d1 + 0.0) * d2;
            double d15 = 0.0 + Math.sin(d1 + 0.0) * d2;
            double d16 = 0.0 + Math.cos(d1 + (Math.PI / 2)) * d2;
            double d17 = 0.0 + Math.sin(d1 + (Math.PI / 2)) * d2;
            double d18 = 0.0 + Math.cos(d1 + (Math.PI * 3.0 / 2.0)) * d2;
            double d19 = 0.0 + Math.sin(d1 + (Math.PI * 3.0 / 2.0)) * d2;
            double d20 = d0;
            double d21 = 0.0;
            double d22 = 0.4999;
            double d23 = -1.0F + f3;
            double d24 = d0 * (0.5 / d2) + d23;
            bufferbuilder.vertex(d12, d20, d13).texture(0.4999, d24).color(j, k, l, 255).nextVertex();
            bufferbuilder.vertex(d12, 0.0, d13).texture(0.4999, d23).color(j, k, l, 255).nextVertex();
            bufferbuilder.vertex(d14, 0.0, d15).texture(0.0, d23).color(j, k, l, 255).nextVertex();
            bufferbuilder.vertex(d14, d20, d15).texture(0.0, d24).color(j, k, l, 255).nextVertex();
            bufferbuilder.vertex(d16, d20, d17).texture(0.4999, d24).color(j, k, l, 255).nextVertex();
            bufferbuilder.vertex(d16, 0.0, d17).texture(0.4999, d23).color(j, k, l, 255).nextVertex();
            bufferbuilder.vertex(d18, 0.0, d19).texture(0.0, d23).color(j, k, l, 255).nextVertex();
            bufferbuilder.vertex(d18, d20, d19).texture(0.0, d24).color(j, k, l, 255).nextVertex();
            double d25 = 0.0;
            if (guardianEntity.ticks % 2 == 0) {
                d25 = 0.5;
            }

            bufferbuilder.vertex(d4, d20, d5).texture(0.5, d25 + 0.5).color(j, k, l, 255).nextVertex();
            bufferbuilder.vertex(d6, d20, d7).texture(1.0, d25 + 0.5).color(j, k, l, 255).nextVertex();
            bufferbuilder.vertex(d10, d20, d11).texture(1.0, d25).color(j, k, l, 255).nextVertex();
            bufferbuilder.vertex(d8, d20, d9).texture(0.5, d25).color(j, k, l, 255).nextVertex();
            tesselator.end();
            GlStateManager.popMatrix();
        }
    }

    protected void applyScale(GuardianEntity guardianEntity, float f) {
        if (guardianEntity.isElder()) {
            GlStateManager.scalef(2.35F, 2.35F, 2.35F);
        }
    }

    protected Identifier getTextureLocation(GuardianEntity guardianEntity) {
        return guardianEntity.isElder() ? ELDER_GUARDIAN_TEXTURE : GUARDIAN_LOCATION;
    }
}
