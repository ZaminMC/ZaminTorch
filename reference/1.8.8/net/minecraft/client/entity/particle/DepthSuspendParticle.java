package net.minecraft.client.entity.particle;

import net.minecraft.world.World;

public class DepthSuspendParticle extends Particle {
    protected DepthSuspendParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, g, h, i);
        float fx = this.random.nextFloat() * 0.1F + 0.2F;
        this.red = fx;
        this.green = fx;
        this.blue = fx;
        this.setTextureCoordinates(0);
        this.setSize(0.02F, 0.02F);
        this.size = this.size * (this.random.nextFloat() * 0.6F + 0.5F);
        this.velocityX *= 0.02F;
        this.velocityY *= 0.02F;
        this.velocityZ *= 0.02F;
        this.lifetime = (int)(20.0 / (Math.random() * 0.8 + 0.2));
        this.noClip = true;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= 0.99;
        this.velocityY *= 0.99;
        this.velocityZ *= 0.99;
        if (this.lifetime-- <= 0) {
            this.remove();
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new DepthSuspendParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }

    public static class HappyFactory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            Particle particle = new DepthSuspendParticle(world, x, y, z, velocityX, velocityY, velocityZ);
            particle.setTextureCoordinates(82);
            particle.setColor(1.0F, 1.0F, 1.0F);
            return particle;
        }
    }
}
