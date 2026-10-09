package net.minecraft.client.entity.particle;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.world.World;

public class HugeExplosionParticle extends Particle {
    private int age;
    private int maxAge = 8;

    protected HugeExplosionParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, 0.0, 0.0, 0.0);
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
    }

    @Override
    public void tick() {
        for (int i = 0; i < 6; i++) {
            double d0 = this.x + (this.random.nextDouble() - this.random.nextDouble()) * 4.0;
            double d1 = this.y + (this.random.nextDouble() - this.random.nextDouble()) * 4.0;
            double d2 = this.z + (this.random.nextDouble() - this.random.nextDouble()) * 4.0;
            this.world.addParticle(ParticleType.EXPLOSION_LARGE, d0, d1, d2, (float)this.age / this.maxAge, 0.0, 0.0);
        }

        this.age++;
        if (this.age == this.maxAge) {
            this.remove();
        }
    }

    @Override
    public int getAtlasType() {
        return 1;
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new HugeExplosionParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
