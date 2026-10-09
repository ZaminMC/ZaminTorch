package net.minecraft.client.entity.particle;

import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class WaterBubbleParticle extends Particle {
    protected WaterBubbleParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, g, h, i);
        this.red = 1.0F;
        this.green = 1.0F;
        this.blue = 1.0F;
        this.setTextureCoordinates(32);
        this.setSize(0.02F, 0.02F);
        this.size = this.size * (this.random.nextFloat() * 0.6F + 0.2F);
        this.velocityX = g * 0.2F + (Math.random() * 2.0 - 1.0) * 0.02F;
        this.velocityY = h * 0.2F + (Math.random() * 2.0 - 1.0) * 0.02F;
        this.velocityZ = i * 0.2F + (Math.random() * 2.0 - 1.0) * 0.02F;
        this.lifetime = (int)(8.0 / (Math.random() * 0.8 + 0.2));
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.velocityY += 0.002;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= 0.85F;
        this.velocityY *= 0.85F;
        this.velocityZ *= 0.85F;
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
            return new WaterBubbleParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
