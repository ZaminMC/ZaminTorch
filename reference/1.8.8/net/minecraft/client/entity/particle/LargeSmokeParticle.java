package net.minecraft.client.entity.particle;

import net.minecraft.world.World;

public class LargeSmokeParticle extends SmokeParticle {
    protected LargeSmokeParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, g, h, i, 2.5F);
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new LargeSmokeParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
