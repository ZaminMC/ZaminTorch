package net.minecraft.entity;

import net.minecraft.block.material.Material;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class ExperienceOrbEntity extends Entity {
    public int renderTicks;
    public int age;
    public int pickupDelay;
    private int health = 5;
    private int xp;
    private PlayerEntity igniter;
    private int lastTargetUpdateTick;

    public ExperienceOrbEntity(World world, double x, double y, double z, int xp) {
        super(world);
        this.setSize(0.5F, 0.5F);
        this.setPosition(x, y, z);
        this.yaw = (float)(Math.random() * 360.0);
        this.velocityX = (float)(Math.random() * 0.2F - 0.1F) * 2.0F;
        this.velocityY = (float)(Math.random() * 0.2) * 2.0F;
        this.velocityZ = (float)(Math.random() * 0.2F - 0.1F) * 2.0F;
        this.xp = xp;
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    public ExperienceOrbEntity(World world) {
        super(world);
        this.setSize(0.25F, 0.25F);
    }

    @Override
    protected void registerSyncedData() {
    }

    @Override
    public int getLightLevel(float tickDelta) {
        float f = 0.5F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        int i = super.getLightLevel(tickDelta);
        int j = i & 0xFF;
        int k = i >> 16 & 0xFF;
        j += (int)(f * 15.0F * 16.0F);
        if (j > 240) {
            j = 240;
        }

        return j | k << 16;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.pickupDelay > 0) {
            this.pickupDelay--;
        }

        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.velocityY -= 0.03F;
        if (this.world.getBlockState(new BlockPos(this)).getBlock().getMaterial() == Material.LAVA) {
            this.velocityY = 0.2F;
            this.velocityX = (this.random.nextFloat() - this.random.nextFloat()) * 0.2F;
            this.velocityZ = (this.random.nextFloat() - this.random.nextFloat()) * 0.2F;
            this.playSound("random.fizz", 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
        }

        this.pushAwayFrom(this.x, (this.getShape().minY + this.getShape().maxY) / 2.0, this.z);
        double d0 = 8.0;
        if (this.lastTargetUpdateTick < this.renderTicks - 20 + this.getNetworkId() % 100) {
            if (this.igniter == null || this.igniter.squaredDistanceTo(this) > d0 * d0) {
                this.igniter = this.world.getNearestPlayer(this, d0);
            }

            this.lastTargetUpdateTick = this.renderTicks;
        }

        if (this.igniter != null && this.igniter.isSpectator()) {
            this.igniter = null;
        }

        if (this.igniter != null) {
            double d1 = (this.igniter.x - this.x) / d0;
            double d2 = (this.igniter.y + this.igniter.getEyeHeight() - this.y) / d0;
            double d3 = (this.igniter.z - this.z) / d0;
            double d4 = Math.sqrt(d1 * d1 + d2 * d2 + d3 * d3);
            double d5 = 1.0 - d4;
            if (d5 > 0.0) {
                d5 *= d5;
                this.velocityX += d1 / d4 * d5 * 0.1;
                this.velocityY += d2 / d4 * d5 * 0.1;
                this.velocityZ += d3 / d4 * d5 * 0.1;
            }
        }

        this.move(this.velocityX, this.velocityY, this.velocityZ);
        float f = 0.98F;
        if (this.onGround) {
            f = this.world
                    .getBlockState(new BlockPos(MathHelper.floor(this.x), MathHelper.floor(this.getShape().minY) - 1, MathHelper.floor(this.z)))
                    .getBlock()
                    .slipperiness
                * 0.98F;
        }

        this.velocityX *= f;
        this.velocityY *= 0.98F;
        this.velocityZ *= f;
        if (this.onGround) {
            this.velocityY *= -0.9F;
        }

        this.renderTicks++;
        this.age++;
        if (this.age >= 6000) {
            this.remove();
        }
    }

    @Override
    public boolean checkWaterCollisions() {
        return this.world.applyLiquidDrag(this.getShape(), Material.WATER, this);
    }

    @Override
    protected void takeFireDamage(int amount) {
        this.takeDamage(DamageSource.FIRE, amount);
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        this.markDamaged();
        this.health = (int)(this.health - amount);
        if (this.health <= 0) {
            this.remove();
        }

        return false;
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        nbt.putShort("Health", (byte)this.health);
        nbt.putShort("Age", (short)this.age);
        nbt.putShort("Value", (short)this.xp);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        this.health = nbt.getShort("Health") & 255;
        this.age = nbt.getShort("Age");
        this.xp = nbt.getShort("Value");
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        if (!this.world.isClient) {
            if (this.pickupDelay == 0 && player.xpCooldown == 0) {
                player.xpCooldown = 2;
                this.world.playSound((Entity)player, "random.orb", 0.1F, 0.5F * ((this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.8F));
                player.sendPickup(this, 1);
                player.increaseXp(this.xp);
                this.remove();
            }
        }
    }

    public int getXp() {
        return this.xp;
    }

    public int getSize() {
        if (this.xp >= 2477) {
            return 10;
        } else if (this.xp >= 1237) {
            return 9;
        } else if (this.xp >= 617) {
            return 8;
        } else if (this.xp >= 307) {
            return 7;
        } else if (this.xp >= 149) {
            return 6;
        } else if (this.xp >= 73) {
            return 5;
        } else if (this.xp >= 37) {
            return 4;
        } else if (this.xp >= 17) {
            return 3;
        } else if (this.xp >= 7) {
            return 2;
        } else {
            return this.xp >= 3 ? 1 : 0;
        }
    }

    public static int roundSize(int size) {
        if (size >= 2477) {
            return 2477;
        } else if (size >= 1237) {
            return 1237;
        } else if (size >= 617) {
            return 617;
        } else if (size >= 307) {
            return 307;
        } else if (size >= 149) {
            return 149;
        } else if (size >= 73) {
            return 73;
        } else if (size >= 37) {
            return 37;
        } else if (size >= 17) {
            return 17;
        } else if (size >= 7) {
            return 7;
        } else {
            return size >= 3 ? 3 : 1;
        }
    }

    @Override
    public boolean canBePunched() {
        return false;
    }
}
