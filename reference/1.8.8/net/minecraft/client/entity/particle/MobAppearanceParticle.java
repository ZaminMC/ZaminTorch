package net.minecraft.client.entity.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class MobAppearanceParticle extends Particle {
    private LivingEntity mob;

    protected MobAppearanceParticle(World world, double d, double e, double f) {
        super(world, d, e, f, 0.0, 0.0, 0.0);
        this.red = this.green = this.blue = 1.0F;
        this.velocityX = this.velocityY = this.velocityZ = 0.0;
        this.gravity = 0.0F;
        this.lifetime = 30;
    }

    @Override
    public int getAtlasType() {
        return 3;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.mob == null) {
            GuardianEntity guardianentity = new GuardianEntity(this.world);
            guardianentity.setGhost();
            this.mob = guardianentity;
        }
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        if (this.mob != null) {
            EntityRenderDispatcher entityrenderdispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            entityrenderdispatcher.setCameraPos(Particle.lerpCameraX, Particle.lerpCameraY, Particle.lerpCameraZ);
            float f = 0.42553192F;
            float f1 = (this.age + tickDelta) / this.lifetime;
            GlStateManager.depthMask(true);
            GlStateManager.enableBlend();
            GlStateManager.enableDepthTest();
            GlStateManager.blendFunc(770, 771);
            float f2 = 240.0F;
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, f2, f2);
            GlStateManager.pushMatrix();
            float f3 = 0.05F + 0.5F * MathHelper.sin(f1 * (float) Math.PI);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, f3);
            GlStateManager.translatef(0.0F, 1.8F, 0.0F);
            GlStateManager.rotatef(180.0F - camera.yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(60.0F - 150.0F * f1 - camera.pitch, 1.0F, 0.0F, 0.0F);
            GlStateManager.translatef(0.0F, -0.4F, -1.5F);
            GlStateManager.scalef(f, f, f);
            this.mob.yaw = this.mob.lastYaw = 0.0F;
            this.mob.headYaw = this.mob.lastHeadYaw = 0.0F;
            entityrenderdispatcher.render(this.mob, 0.0, 0.0, 0.0, 0.0F, tickDelta);
            GlStateManager.popMatrix();
            GlStateManager.enableDepthTest();
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new MobAppearanceParticle(world, x, y, z);
        }
    }
}
