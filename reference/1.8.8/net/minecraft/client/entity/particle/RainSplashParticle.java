package net.minecraft.client.entity.particle;

import net.minecraft.block.Block;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class RainSplashParticle extends Particle {
    protected RainSplashParticle(World world, double x, double y, double z) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.velocityX *= 0.3F;
        this.velocityY = Math.random() * 0.2F + 0.1F;
        this.velocityZ *= 0.3F;
        this.red = 1.0F;
        this.green = 1.0F;
        this.blue = 1.0F;
        this.setTextureCoordinates(19 + this.random.nextInt(4));
        this.setSize(0.01F, 0.01F);
        this.gravity = 0.06F;
        this.lifetime = (int)(8.0 / (Math.random() * 0.8 + 0.2));
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
        if (this.lifetime-- <= 0) {
            this.remove();
        }

        if (this.onGround) {
            if (Math.random() < 0.5) {
                this.remove();
            }

            this.velocityX *= 0.7F;
            this.velocityZ *= 0.7F;
        }

        BlockPos blockpos = new BlockPos(this);
        BlockState blockstate = this.world.getBlockState(blockpos);
        Block block = blockstate.getBlock();
        block.updateShape(this.world, blockpos);
        Material material = blockstate.getBlock().getMaterial();
        if (material.isLiquid() || material.isSolid()) {
            double d0 = 0.0;
            if (blockstate.getBlock() instanceof LiquidBlock) {
                d0 = 1.0F - LiquidBlock.getHeightLoss(blockstate.get(LiquidBlock.LEVEL));
            } else {
                d0 = block.getMaxY();
            }

            double d1 = MathHelper.floor(this.y) + d0;
            if (this.y < d1) {
                this.remove();
            }
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new RainSplashParticle(world, x, y, z);
        }
    }
}
