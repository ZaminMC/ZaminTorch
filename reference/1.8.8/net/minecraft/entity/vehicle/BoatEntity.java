package net.minecraft.entity.vehicle;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.ProjectileDamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class BoatEntity extends Entity {
    private boolean empty = true;
    private double speedBoost = 0.07;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYaw;
    private double lerpPitch;
    private double lerpVelocityX;
    private double lerpVelocityY;
    private double lerpVelocityZ;

    public BoatEntity(World world) {
        super(world);
        this.blocksBuilding = true;
        this.setSize(1.5F, 0.6F);
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    @Override
    protected void registerSyncedData() {
        this.syncedData.register(17, new Integer(0));
        this.syncedData.register(18, new Integer(1));
        this.syncedData.register(19, new Float(0.0F));
    }

    @Override
    public Box getCollisionAgainstShape(Entity other) {
        return other.getShape();
    }

    @Override
    public Box getCollisionShape() {
        return this.getShape();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    public BoatEntity(World world, double x, double y, double z) {
        this(world);
        this.setPosition(x, y, z);
        this.velocityX = 0.0;
        this.velocityY = 0.0;
        this.velocityZ = 0.0;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }

    @Override
    public double getMountHeight() {
        return -0.3;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        if (!this.world.isClient && !this.removed) {
            if (this.rider != null && this.rider == source.getAttacker() && source instanceof ProjectileDamageSource) {
                return false;
            }

            this.setDamagedSwingDirection(-this.getDamagedSwingDirection());
            this.setDamagedTimer(10);
            this.setDamage(this.getDamage() + amount * 10.0F);
            this.markDamaged();
            boolean flag = source.getAttacker() instanceof PlayerEntity && ((PlayerEntity)source.getAttacker()).abilities.creativeMode;
            if (flag || this.getDamage() > 40.0F) {
                if (this.rider != null) {
                    this.rider.startRiding(this);
                }

                if (!flag && this.world.getGameRules().getBoolean("doEntityDrops")) {
                    this.dropItem(Items.BOAT, 1, 0.0F);
                }

                this.remove();
            }

            return true;
        } else {
            return true;
        }
    }

    @Override
    public void animateDamage() {
        this.setDamagedSwingDirection(-this.getDamagedSwingDirection());
        this.setDamagedTimer(10);
        this.setDamage(this.getDamage() * 11.0F);
    }

    @Override
    public boolean hasCollision() {
        return !this.removed;
    }

    @Override
    public void lerpPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        if (teleport && this.rider != null) {
            this.lastX = this.x = x;
            this.lastY = this.y = y;
            this.lastZ = this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.lerpSteps = 0;
            this.setPosition(x, y, z);
            this.velocityX = this.lerpVelocityX = 0.0;
            this.velocityY = this.lerpVelocityY = 0.0;
            this.velocityZ = this.lerpVelocityZ = 0.0;
        } else {
            if (this.empty) {
                this.lerpSteps = steps + 5;
            } else {
                double d0 = x - this.x;
                double d1 = y - this.y;
                double d2 = z - this.z;
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                if (!(d3 > 1.0)) {
                    return;
                }

                this.lerpSteps = 3;
            }

            this.lerpX = x;
            this.lerpY = y;
            this.lerpZ = z;
            this.lerpYaw = yaw;
            this.lerpPitch = pitch;
            this.velocityX = this.lerpVelocityX;
            this.velocityY = this.lerpVelocityY;
            this.velocityZ = this.lerpVelocityZ;
        }
    }

    @Override
    public void lerpVelocity(double velocityX, double velocityY, double velocityZ) {
        this.lerpVelocityX = this.velocityX = velocityX;
        this.lerpVelocityY = this.velocityY = velocityY;
        this.lerpVelocityZ = this.velocityZ = velocityZ;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getDamagedTimer() > 0) {
            this.setDamagedTimer(this.getDamagedTimer() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        int i = 5;
        double d0 = 0.0;

        for (int j = 0; j < i; j++) {
            double d1 = this.getShape().minY + (this.getShape().maxY - this.getShape().minY) * (j + 0) / i - 0.125;
            double d3 = this.getShape().minY + (this.getShape().maxY - this.getShape().minY) * (j + 1) / i - 0.125;
            Box box = new Box(this.getShape().minX, d1, this.getShape().minZ, this.getShape().maxX, d3, this.getShape().maxZ);
            if (this.world.containsLiquid(box, Material.WATER)) {
                d0 += 1.0 / i;
            }
        }

        double d9 = Math.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
        if (d9 > 0.2975) {
            double d2 = Math.cos(this.yaw * Math.PI / 180.0);
            double d4 = Math.sin(this.yaw * Math.PI / 180.0);

            for (int k = 0; k < 1.0 + d9 * 60.0; k++) {
                double d5 = this.random.nextFloat() * 2.0F - 1.0F;
                double d6 = (this.random.nextInt(2) * 2 - 1) * 0.7;
                if (this.random.nextBoolean()) {
                    double d7 = this.x - d2 * d5 * 0.8 + d4 * d6;
                    double d8 = this.z - d4 * d5 * 0.8 - d2 * d6;
                    this.world.addParticle(ParticleType.WATER_SPLASH, d7, this.y - 0.125, d8, this.velocityX, this.velocityY, this.velocityZ);
                } else {
                    double d24 = this.x + d2 + d4 * d5 * 0.7;
                    double d25 = this.z + d4 - d2 * d5 * 0.7;
                    this.world.addParticle(ParticleType.WATER_SPLASH, d24, this.y - 0.125, d25, this.velocityX, this.velocityY, this.velocityZ);
                }
            }
        }

        if (this.world.isClient && this.empty) {
            if (this.lerpSteps > 0) {
                double d12 = this.x + (this.lerpX - this.x) / this.lerpSteps;
                double d16 = this.y + (this.lerpY - this.y) / this.lerpSteps;
                double d19 = this.z + (this.lerpZ - this.z) / this.lerpSteps;
                double d22 = MathHelper.wrapDegrees(this.lerpYaw - this.yaw);
                this.yaw = (float)(this.yaw + d22 / this.lerpSteps);
                this.pitch = (float)(this.pitch + (this.lerpPitch - this.pitch) / this.lerpSteps);
                this.lerpSteps--;
                this.setPosition(d12, d16, d19);
                this.setRotation(this.yaw, this.pitch);
            } else {
                double d13 = this.x + this.velocityX;
                double d17 = this.y + this.velocityY;
                double d20 = this.z + this.velocityZ;
                this.setPosition(d13, d17, d20);
                if (this.onGround) {
                    this.velocityX *= 0.5;
                    this.velocityY *= 0.5;
                    this.velocityZ *= 0.5;
                }

                this.velocityX *= 0.99F;
                this.velocityY *= 0.95F;
                this.velocityZ *= 0.99F;
            }
        } else {
            if (d0 < 1.0) {
                double d10 = d0 * 2.0 - 1.0;
                this.velocityY += 0.04F * d10;
            } else {
                if (this.velocityY < 0.0) {
                    this.velocityY /= 2.0;
                }

                this.velocityY += 0.007F;
            }

            if (this.rider instanceof LivingEntity) {
                LivingEntity livingentity = (LivingEntity)this.rider;
                float f = this.rider.yaw + -livingentity.sidewaysSpeed * 90.0F;
                this.velocityX = this.velocityX + -Math.sin(f * (float) Math.PI / 180.0F) * this.speedBoost * livingentity.forwardSpeed * 0.05F;
                this.velocityZ = this.velocityZ + Math.cos(f * (float) Math.PI / 180.0F) * this.speedBoost * livingentity.forwardSpeed * 0.05F;
            }

            double d11 = Math.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
            if (d11 > 0.35) {
                double d14 = 0.35 / d11;
                this.velocityX *= d14;
                this.velocityZ *= d14;
                d11 = 0.35;
            }

            if (d11 > d9 && this.speedBoost < 0.35) {
                this.speedBoost = this.speedBoost + (0.35 - this.speedBoost) / 35.0;
                if (this.speedBoost > 0.35) {
                    this.speedBoost = 0.35;
                }
            } else {
                this.speedBoost = this.speedBoost - (this.speedBoost - 0.07) / 35.0;
                if (this.speedBoost < 0.07) {
                    this.speedBoost = 0.07;
                }
            }

            for (int i1 = 0; i1 < 4; i1++) {
                int l1 = MathHelper.floor(this.x + (i1 % 2 - 0.5) * 0.8);
                int i2 = MathHelper.floor(this.z + (i1 / 2 - 0.5) * 0.8);

                for (int j2 = 0; j2 < 2; j2++) {
                    int l = MathHelper.floor(this.y) + j2;
                    BlockPos blockpos = new BlockPos(l1, l, i2);
                    Block block = this.world.getBlockState(blockpos).getBlock();
                    if (block == Blocks.SNOW_LAYER) {
                        this.world.removeBlock(blockpos);
                        this.collidingHorizontally = false;
                    } else if (block == Blocks.LILY_PAD) {
                        this.world.breakBlock(blockpos, true);
                        this.collidingHorizontally = false;
                    }
                }
            }

            if (this.onGround) {
                this.velocityX *= 0.5;
                this.velocityY *= 0.5;
                this.velocityZ *= 0.5;
            }

            this.move(this.velocityX, this.velocityY, this.velocityZ);
            if (!this.collidingHorizontally || !(d9 > 0.2975)) {
                this.velocityX *= 0.99F;
                this.velocityY *= 0.95F;
                this.velocityZ *= 0.99F;
            } else if (!this.world.isClient && !this.removed) {
                this.remove();
                if (this.world.getGameRules().getBoolean("doEntityDrops")) {
                    for (int j1 = 0; j1 < 3; j1++) {
                        this.dropItem(Item.byBlock(Blocks.PLANKS), 1, 0.0F);
                    }

                    for (int k1 = 0; k1 < 2; k1++) {
                        this.dropItem(Items.STICK, 1, 0.0F);
                    }
                }
            }

            this.pitch = 0.0F;
            double d15 = this.yaw;
            double d18 = this.lastX - this.x;
            double d21 = this.lastZ - this.z;
            if (d18 * d18 + d21 * d21 > 0.001) {
                d15 = (float)(MathHelper.fastAtan2(d21, d18) * 180.0 / Math.PI);
            }

            double d23 = MathHelper.wrapDegrees(d15 - this.yaw);
            if (d23 > 20.0) {
                d23 = 20.0;
            }

            if (d23 < -20.0) {
                d23 = -20.0;
            }

            this.yaw = (float)(this.yaw + d23);
            this.setRotation(this.yaw, this.pitch);
            if (!this.world.isClient) {
                List<Entity> list = this.world.getEntities(this, this.getShape().grown(0.2F, 0.0, 0.2F));
                if (list != null && !list.isEmpty()) {
                    for (int k2 = 0; k2 < list.size(); k2++) {
                        Entity entity = list.get(k2);
                        if (entity != this.rider && entity.isPushable() && entity instanceof BoatEntity) {
                            entity.push(this);
                        }
                    }
                }

                if (this.rider != null && this.rider.removed) {
                    this.rider = null;
                }
            }
        }
    }

    @Override
    public void updateRiderPositon() {
        if (this.rider != null) {
            double d0 = Math.cos(this.yaw * Math.PI / 180.0) * 0.4;
            double d1 = Math.sin(this.yaw * Math.PI / 180.0) * 0.4;
            this.rider.setPosition(this.x + d0, this.y + this.getMountHeight() + this.rider.getRideHeight(), this.z + d1);
        }
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
    }

    @Override
    public boolean interact(PlayerEntity player) {
        if (this.rider != null && this.rider instanceof PlayerEntity && this.rider != player) {
            return true;
        }

        if (!this.world.isClient) {
            player.startRiding(this);
        }

        return true;
    }

    @Override
    protected void checkFallDamage(double dy, boolean landed, Block block, BlockPos pos) {
        if (landed) {
            if (this.fallDistance > 3.0F) {
                this.takeFallDamage(this.fallDistance, 1.0F);
                if (!this.world.isClient && !this.removed) {
                    this.remove();
                    if (this.world.getGameRules().getBoolean("doEntityDrops")) {
                        for (int i = 0; i < 3; i++) {
                            this.dropItem(Item.byBlock(Blocks.PLANKS), 1, 0.0F);
                        }

                        for (int j = 0; j < 2; j++) {
                            this.dropItem(Items.STICK, 1, 0.0F);
                        }
                    }
                }

                this.fallDistance = 0.0F;
            }
        } else if (this.world.getBlockState(new BlockPos(this).down()).getBlock().getMaterial() != Material.WATER && dy < 0.0) {
            this.fallDistance = (float)(this.fallDistance - dy);
        }
    }

    public void setDamage(float damage) {
        this.syncedData.update(19, damage);
    }

    public float getDamage() {
        return this.syncedData.getFloat(19);
    }

    /**
     * When a boat is hit it starts wiggling. You need to consequitively hit it in that window to be able to do
     * enough damage until the boat breaks. This method sets that value in the datatracker.
     * The value is initially set to 10 when hit and decreases by one each tick when the boat is not hit.
     */
    public void setDamagedTimer(int ticks) {
        this.syncedData.update(17, ticks);
    }

    /**
     * When a boat is hit it starts wiggling. You need to consequitively hit it in that window to be able to do
     * enough damage until the boat breaks. This method gets that value in the datatracker.
     * The value is initially set to 10 when hit and decreases by one each tick when the boat is not hit.
     */
    public int getDamagedTimer() {
        return this.syncedData.getInt(17);
    }

    /**
     * This value determines on which side the wiggle animation starts.
     * See {@link net.minecraft.client.render.entity.BoatRenderer#render}}
     *     -1:     start to the right
     *      1:     start to the left
     * Default: 1
     */
    public void setDamagedSwingDirection(int dir) {
        this.syncedData.update(18, dir);
    }

    public int getDamagedSwingDirection() {
        return this.syncedData.getInt(18);
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}
