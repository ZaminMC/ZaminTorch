package net.minecraft.client.entity.particle;

import net.minecraft.world.World;

public interface ParticleFactory {
    Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters);
}
