package net.minecraft.client.entity.particle;

import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class LiquidDripParticle extends Particle {
    private Material material;
    private int leakTime;

    protected LiquidDripParticle(World world, double x, double y, double z, Material material) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.velocityX = this.velocityY = this.velocityZ = 0.0;
        if (material == Material.WATER) {
            this.red = 0.0F;
            this.green = 0.0F;
            this.blue = 1.0F;
        } else {
            this.red = 1.0F;
            this.green = 0.0F;
            this.blue = 0.0F;
        }

        this.setTextureCoordinates(113);
        this.setSize(0.01F, 0.01F);
        this.gravity = 0.06F;
        this.material = material;
        this.leakTime = 40;
        this.lifetime = (int)(64.0 / (Math.random() * 0.8 + 0.2));
        this.velocityX = this.velocityY = this.velocityZ = 0.0;
    }

    @Override
    public int getLightLevel(float tickDelta) {
        return this.material == Material.WATER ? super.getLightLevel(tickDelta) : 257;
    }

    @Override
    public float getBrightness(float tickDelta) {
        return this.material == Material.WATER ? super.getBrightness(tickDelta) : 1.0F;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.material == Material.WATER) {
            this.red = 0.2F;
            this.green = 0.3F;
            this.blue = 1.0F;
        } else {
            this.red = 1.0F;
            this.green = 16.0F / (40 - this.leakTime + 16);
            this.blue = 4.0F / (40 - this.leakTime + 8);
        }

        this.velocityY = this.velocityY - this.gravity;
        if (this.leakTime-- > 0) {
            this.velocityX *= 0.02;
            this.velocityY *= 0.02;
            this.velocityZ *= 0.02;
            this.setTextureCoordinates(113);
        } else {
            this.setTextureCoordinates(112);
        }

        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= 0.98F;
        this.velocityY *= 0.98F;
        this.velocityZ *= 0.98F;
        if (this.lifetime-- <= 0) {
            this.remove();
        }

        if (this.onGround) {
            if (this.material == Material.WATER) {
                this.remove();
                this.world.addParticle(ParticleType.WATER_SPLASH, this.x, this.y, this.z, 0.0, 0.0, 0.0);
            } else {
                this.setTextureCoordinates(114);
            }

            this.velocityX *= 0.7F;
            this.velocityZ *= 0.7F;
        }

        BlockPos blockpos = new BlockPos(this);
        BlockState blockstate = this.world.getBlockState(blockpos);
        Material material = blockstate.getBlock().getMaterial();
        if (material.isLiquid() || material.isSolid()) {
            double d0 = 0.0;
            if (blockstate.getBlock() instanceof LiquidBlock) {
                d0 = LiquidBlock.getHeightLoss(blockstate.get(LiquidBlock.LEVEL));
            }

            double d1 = MathHelper.floor(this.y) + 1 - d0;
            if (this.y < d1) {
                this.remove();
            }
        }
    }

    public static class LavaFactory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new LiquidDripParticle(world, x, y, z, Material.LAVA);
        }
    }

    public static class WaterFactory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new LiquidDripParticle(world, x, y, z, Material.WATER);
        }
    }
}
