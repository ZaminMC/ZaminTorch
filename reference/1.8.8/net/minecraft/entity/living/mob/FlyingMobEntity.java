package net.minecraft.entity.living.mob;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public abstract class FlyingMobEntity extends MobEntity {
    public FlyingMobEntity(World world) {
        super(world);
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
    }

    @Override
    protected void checkFallDamage(double dy, boolean landed, Block block, BlockPos pos) {
    }

    @Override
    public void moveRelative(float sideways, float forwards) {
        if (this.isInWater()) {
            this.updateVelocity(sideways, forwards, 0.02F);
            this.move(this.velocityX, this.velocityY, this.velocityZ);
            this.velocityX *= 0.8F;
            this.velocityY *= 0.8F;
            this.velocityZ *= 0.8F;
        } else if (this.isInLava()) {
            this.updateVelocity(sideways, forwards, 0.02F);
            this.move(this.velocityX, this.velocityY, this.velocityZ);
            this.velocityX *= 0.5;
            this.velocityY *= 0.5;
            this.velocityZ *= 0.5;
        } else {
            float f = 0.91F;
            if (this.onGround) {
                f = this.world
                        .getBlockState(new BlockPos(MathHelper.floor(this.x), MathHelper.floor(this.getShape().minY) - 1, MathHelper.floor(this.z)))
                        .getBlock()
                        .slipperiness
                    * 0.91F;
            }

            float f1 = 0.16277136F / (f * f * f);
            this.updateVelocity(sideways, forwards, this.onGround ? 0.1F * f1 : 0.02F);
            f = 0.91F;
            if (this.onGround) {
                f = this.world
                        .getBlockState(new BlockPos(MathHelper.floor(this.x), MathHelper.floor(this.getShape().minY) - 1, MathHelper.floor(this.z)))
                        .getBlock()
                        .slipperiness
                    * 0.91F;
            }

            this.move(this.velocityX, this.velocityY, this.velocityZ);
            this.velocityX *= f;
            this.velocityY *= f;
            this.velocityZ *= f;
        }

        this.lastWalkAnimationSpeed = this.walkAnimationSpeed;
        double d1 = this.x - this.lastX;
        double d0 = this.z - this.lastZ;
        float f2 = MathHelper.sqrt(d1 * d1 + d0 * d0) * 4.0F;
        if (f2 > 1.0F) {
            f2 = 1.0F;
        }

        this.walkAnimationSpeed = this.walkAnimationSpeed + (f2 - this.walkAnimationSpeed) * 0.4F;
        this.walkAnimationProgress = this.walkAnimationProgress + this.walkAnimationSpeed;
    }

    @Override
    public boolean isClimbing() {
        return false;
    }
}
