package net.minecraft.client.entity.particle;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class CriticalHitParticle extends Particle {
    float baseScale;

    protected CriticalHitParticle(World world, double d, double e, double f, double g, double h, double i) {
        this(world, d, e, f, g, h, i, 1.0F);
    }

    protected CriticalHitParticle(World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, float scaleFactor) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.velocityX *= 0.1F;
        this.velocityY *= 0.1F;
        this.velocityZ *= 0.1F;
        this.velocityX += velocityX * 0.4;
        this.velocityY += velocityY * 0.4;
        this.velocityZ += velocityZ * 0.4;
        this.red = this.green = this.blue = (float)(Math.random() * 0.3F + 0.6F);
        this.size *= 0.75F;
        this.size *= scaleFactor;
        this.baseScale = this.size;
        this.lifetime = (int)(6.0 / (Math.random() * 0.8 + 0.6));
        this.lifetime = (int)(this.lifetime * scaleFactor);
        this.noClip = false;
        this.setTextureCoordinates(65);
        this.tick();
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = (this.age + tickDelta) / this.lifetime * 32.0F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        this.size = this.baseScale * f;
        super.render(bufferBuilder, camera, tickDelta, dx, dy, dz, forwards, sideways);
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }

        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.green = (float)(this.green * 0.96);
        this.blue = (float)(this.blue * 0.9);
        this.velocityX *= 0.7F;
        this.velocityY *= 0.7F;
        this.velocityZ *= 0.7F;
        this.velocityY -= 0.02F;
        if (this.onGround) {
            this.velocityX *= 0.7F;
            this.velocityZ *= 0.7F;
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new CriticalHitParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }

    public static class MagicFactory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            Particle particle = new CriticalHitParticle(world, x, y, z, velocityX, velocityY, velocityZ);
            particle.setColor(particle.getRed() * 0.3F, particle.getGreen() * 0.8F, particle.getBlue());
            particle.incrementSpriteRow();
            return particle;
        }
    }
}
