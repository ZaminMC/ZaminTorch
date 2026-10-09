package net.minecraft.client.entity.particle;

import net.minecraft.world.World;

public class ExplosionParticle extends Particle {
    protected ExplosionParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, g, h, i);
        this.velocityX = g + (Math.random() * 2.0 - 1.0) * 0.05F;
        this.velocityY = h + (Math.random() * 2.0 - 1.0) * 0.05F;
        this.velocityZ = i + (Math.random() * 2.0 - 1.0) * 0.05F;
        this.red = this.green = this.blue = this.random.nextFloat() * 0.3F + 0.7F;
        this.size = this.random.nextFloat() * this.random.nextFloat() * 6.0F + 1.0F;
        this.lifetime = (int)(16.0 / (this.random.nextFloat() * 0.8 + 0.2)) + 2;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }

        this.setTextureCoordinates(7 - this.age * 8 / this.lifetime);
        this.velocityY += 0.004;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= 0.9F;
        this.velocityY *= 0.9F;
        this.velocityZ *= 0.9F;
        if (this.onGround) {
            this.velocityX *= 0.7F;
            this.velocityZ *= 0.7F;
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new ExplosionParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
