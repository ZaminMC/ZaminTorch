package net.minecraft.client.entity.particle;

import net.minecraft.world.World;

public class WakeParticle extends Particle {
    protected WakeParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, 0.0, 0.0, 0.0);
        this.velocityX *= 0.3F;
        this.velocityY = Math.random() * 0.2F + 0.1F;
        this.velocityZ *= 0.3F;
        this.red = 1.0F;
        this.green = 1.0F;
        this.blue = 1.0F;
        this.setTextureCoordinates(19);
        this.setSize(0.01F, 0.01F);
        this.lifetime = (int)(8.0 / (Math.random() * 0.8 + 0.2));
        this.gravity = 0.0F;
        this.velocityX = g;
        this.velocityY = h;
        this.velocityZ = i;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.velocityY = this.velocityY - this.gravity;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= 0.98F;
        this.velocityY *= 0.98F;
        this.velocityZ *= 0.98F;
        int i = 60 - this.lifetime;
        float f = i * 0.001F;
        this.setSize(f, f);
        this.setTextureCoordinates(19 + i % 4);
        if (this.lifetime-- <= 0) {
            this.remove();
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new WakeParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
