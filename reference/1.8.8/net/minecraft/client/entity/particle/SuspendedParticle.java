package net.minecraft.client.entity.particle;

import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SuspendedParticle extends Particle {
    protected SuspendedParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e - 0.125, f, g, h, i);
        this.red = 0.4F;
        this.green = 0.4F;
        this.blue = 0.7F;
        this.setTextureCoordinates(0);
        this.setSize(0.01F, 0.01F);
        this.size = this.size * (this.random.nextFloat() * 0.6F + 0.2F);
        this.velocityX = g * 0.0;
        this.velocityY = h * 0.0;
        this.velocityZ = i * 0.0;
        this.lifetime = (int)(16.0 / (Math.random() * 0.8 + 0.2));
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        if (this.world.getBlockState(new BlockPos(this)).getBlock().getMaterial() != Material.WATER) {
            this.remove();
        }

        if (this.lifetime-- <= 0) {
            this.remove();
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new SuspendedParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
