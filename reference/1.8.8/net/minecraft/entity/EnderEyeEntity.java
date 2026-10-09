package net.minecraft.entity;

import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class EnderEyeEntity extends Entity {
    private double targetX;
    private double targetY;
    private double targetZ;
    private int lifespan;
    private boolean dropsItem;

    public EnderEyeEntity(World world) {
        super(world);
        this.setSize(0.25F, 0.25F);
    }

    @Override
    protected void registerSyncedData() {
    }

    @Override
    public boolean shouldRender(double squaredDistanceToCamera) {
        double d0 = this.getShape().getAverageSideLength() * 4.0;
        if (Double.isNaN(d0)) {
            d0 = 4.0;
        }

        d0 *= 64.0;
        return squaredDistanceToCamera < d0 * d0;
    }

    public EnderEyeEntity(World world, double x, double y, double z) {
        super(world);
        this.lifespan = 0;
        this.setSize(0.25F, 0.25F);
        this.setPosition(x, y, z);
    }

    public void setTarget(BlockPos x) {
        double d0 = x.getX();
        int i = x.getY();
        double d1 = x.getZ();
        double d2 = d0 - this.x;
        double d3 = d1 - this.z;
        float f = MathHelper.sqrt(d2 * d2 + d3 * d3);
        if (f > 12.0F) {
            this.targetX = this.x + d2 / f * 12.0;
            this.targetZ = this.z + d3 / f * 12.0;
            this.targetY = this.y + 8.0;
        } else {
            this.targetX = d0;
            this.targetY = i;
            this.targetZ = d1;
        }

        this.lifespan = 0;
        this.dropsItem = this.random.nextInt(5) > 0;
    }

    @Override
    public void lerpVelocity(double velocityX, double velocityY, double velocityZ) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        if (this.lastPitch == 0.0F && this.lastYaw == 0.0F) {
            float f = MathHelper.sqrt(velocityX * velocityX + velocityZ * velocityZ);
            this.lastYaw = this.yaw = (float)(MathHelper.fastAtan2(velocityX, velocityZ) * 180.0 / (float) Math.PI);
            this.lastPitch = this.pitch = (float)(MathHelper.fastAtan2(velocityY, f) * 180.0 / (float) Math.PI);
        }
    }

    @Override
    public void tick() {
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;
        super.tick();
        this.x = this.x + this.velocityX;
        this.y = this.y + this.velocityY;
        this.z = this.z + this.velocityZ;
        float f = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
        this.yaw = (float)(MathHelper.fastAtan2(this.velocityX, this.velocityZ) * 180.0 / (float) Math.PI);
        this.pitch = (float)(MathHelper.fastAtan2(this.velocityY, f) * 180.0 / (float) Math.PI);

        while (this.pitch - this.lastPitch < -180.0F) {
            this.lastPitch -= 360.0F;
        }

        while (this.pitch - this.lastPitch >= 180.0F) {
            this.lastPitch += 360.0F;
        }

        while (this.yaw - this.lastYaw < -180.0F) {
            this.lastYaw -= 360.0F;
        }

        while (this.yaw - this.lastYaw >= 180.0F) {
            this.lastYaw += 360.0F;
        }

        this.pitch = this.lastPitch + (this.pitch - this.lastPitch) * 0.2F;
        this.yaw = this.lastYaw + (this.yaw - this.lastYaw) * 0.2F;
        if (!this.world.isClient) {
            double d0 = this.targetX - this.x;
            double d1 = this.targetZ - this.z;
            float f1 = (float)Math.sqrt(d0 * d0 + d1 * d1);
            float f2 = (float)MathHelper.fastAtan2(d1, d0);
            double d2 = f + (f1 - f) * 0.0025;
            if (f1 < 1.0F) {
                d2 *= 0.8;
                this.velocityY *= 0.8;
            }

            this.velocityX = Math.cos(f2) * d2;
            this.velocityZ = Math.sin(f2) * d2;
            if (this.y < this.targetY) {
                this.velocityY = this.velocityY + (1.0 - this.velocityY) * 0.015F;
            } else {
                this.velocityY = this.velocityY + (-1.0 - this.velocityY) * 0.015F;
            }
        }

        float f3 = 0.25F;
        if (this.isInWater()) {
            for (int i = 0; i < 4; i++) {
                this.world
                    .addParticle(
                        ParticleType.WATER_BUBBLE,
                        this.x - this.velocityX * f3,
                        this.y - this.velocityY * f3,
                        this.z - this.velocityZ * f3,
                        this.velocityX,
                        this.velocityY,
                        this.velocityZ
                    );
            }
        } else {
            this.world
                .addParticle(
                    ParticleType.PORTAL,
                    this.x - this.velocityX * f3 + this.random.nextDouble() * 0.6 - 0.3,
                    this.y - this.velocityY * f3 - 0.5,
                    this.z - this.velocityZ * f3 + this.random.nextDouble() * 0.6 - 0.3,
                    this.velocityX,
                    this.velocityY,
                    this.velocityZ
                );
        }

        if (!this.world.isClient) {
            this.setPosition(this.x, this.y, this.z);
            this.lifespan++;
            if (this.lifespan > 80 && !this.world.isClient) {
                this.remove();
                if (this.dropsItem) {
                    this.world.addEntity(new ItemEntity(this.world, this.x, this.y, this.z, new ItemStack(Items.ENDER_EYE)));
                } else {
                    this.world.doEvent(2003, new BlockPos(this), 0);
                }
            }
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
    }

    @Override
    public float getBrightness(float tickDelta) {
        return 1.0F;
    }

    @Override
    public int getLightLevel(float tickDelta) {
        return 15728880;
    }

    @Override
    public boolean canBePunched() {
        return false;
    }
}
