package net.minecraft.client.entity.particle;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class EmotionParticle extends Particle {
    float baseScale;

    protected EmotionParticle(World world, double d, double e, double f, double g, double h, double i) {
        this(world, d, e, f, g, h, i, 2.0F);
    }

    protected EmotionParticle(World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, float scaleFactor) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.velocityX *= 0.01F;
        this.velocityY *= 0.01F;
        this.velocityZ *= 0.01F;
        this.velocityY += 0.1;
        this.size *= 0.75F;
        this.size *= scaleFactor;
        this.baseScale = this.size;
        this.lifetime = 16;
        this.noClip = false;
        this.setTextureCoordinates(80);
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
        if (this.y == this.lastY) {
            this.velocityX *= 1.1;
            this.velocityZ *= 1.1;
        }

        this.velocityX *= 0.86F;
        this.velocityY *= 0.86F;
        this.velocityZ *= 0.86F;
        if (this.onGround) {
            this.velocityX *= 0.7F;
            this.velocityZ *= 0.7F;
        }
    }

    public static class AngryFactory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            Particle particle = new EmotionParticle(world, x, y + 0.5, z, velocityX, velocityY, velocityZ);
            particle.setTextureCoordinates(81);
            particle.setColor(1.0F, 1.0F, 1.0F);
            return particle;
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new EmotionParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
