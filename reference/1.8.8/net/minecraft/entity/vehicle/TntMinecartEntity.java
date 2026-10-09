package net.minecraft.entity.vehicle;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

public class TntMinecartEntity extends MinecartEntity {
    private int fuseTicks = -1;

    public TntMinecartEntity(World world) {
        super(world);
    }

    public TntMinecartEntity(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Override
    public MinecartEntity.Type getMinecartType() {
        return MinecartEntity.Type.TNT;
    }

    @Override
    public BlockState getDefaultDisplayBlock() {
        return Blocks.TNT.defaultState();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fuseTicks > 0) {
            this.fuseTicks--;
            this.world.addParticle(ParticleType.SMOKE_NORMAL, this.x, this.y + 0.5, this.z, 0.0, 0.0, 0.0);
        } else if (this.fuseTicks == 0) {
            this.explode(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
        }

        if (this.collidingHorizontally) {
            double d0 = this.velocityX * this.velocityX + this.velocityZ * this.velocityZ;
            if (d0 >= 0.01F) {
                this.explode(d0);
            }
        }
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        Entity entity = source.getSource();
        if (entity instanceof ArrowEntity) {
            ArrowEntity arrowentity = (ArrowEntity)entity;
            if (arrowentity.isOnFire()) {
                this.explode(
                    arrowentity.velocityX * arrowentity.velocityX
                        + arrowentity.velocityY * arrowentity.velocityY
                        + arrowentity.velocityZ * arrowentity.velocityZ
                );
            }
        }

        return super.takeDamage(source, amount);
    }

    @Override
    public void dropItems(DamageSource damageSource) {
        super.dropItems(damageSource);
        double d0 = this.velocityX * this.velocityX + this.velocityZ * this.velocityZ;
        if (!damageSource.isExplosive() && this.world.getGameRules().getBoolean("doEntityDrops")) {
            this.dropItem(new ItemStack(Blocks.TNT, 1), 0.0F);
        }

        if (damageSource.isFire() || damageSource.isExplosive() || d0 >= 0.01F) {
            this.explode(d0);
        }
    }

    protected void explode(double distance) {
        if (!this.world.isClient) {
            double d0 = Math.sqrt(distance);
            if (d0 > 5.0) {
                d0 = 5.0;
            }

            this.world.explode(this, this.x, this.y, this.z, (float)(4.0 + this.random.nextDouble() * 1.5 * d0), true);
            this.remove();
        }
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
        if (distance >= 3.0F) {
            float f = distance / 10.0F;
            this.explode(f * f);
        }

        super.takeFallDamage(distance, damageMultiplier);
    }

    @Override
    public void onActivatorRail(int x, int y, int z, boolean powered) {
        if (powered && this.fuseTicks < 0) {
            this.prime();
        }
    }

    @Override
    public void doEvent(byte event) {
        if (event == 10) {
            this.prime();
        } else {
            super.doEvent(event);
        }
    }

    public void prime() {
        this.fuseTicks = 80;
        if (!this.world.isClient) {
            this.world.doEntityEvent(this, (byte)10);
            if (!this.isSilent()) {
                this.world.playSound(this, "game.tnt.primed", 1.0F, 1.0F);
            }
        }
    }

    public int getFuseTicks() {
        return this.fuseTicks;
    }

    public boolean isPrimed() {
        return this.fuseTicks > -1;
    }

    @Override
    public float getBlastResistance(Explosion explosion, World world, BlockPos pos, BlockState state) {
        return !this.isPrimed() || !AbstractRailBlock.isRail(state) && !AbstractRailBlock.isRail(world, pos.up())
            ? super.getBlastResistance(explosion, world, pos, state)
            : 0.0F;
    }

    @Override
    public boolean canExplodeBlock(Explosion explosion, World world, BlockPos pos, BlockState state, float power) {
        return (!this.isPrimed() || !AbstractRailBlock.isRail(state) && !AbstractRailBlock.isRail(world, pos.up()))
            && super.canExplodeBlock(explosion, world, pos, state, power);
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        if (nbt.contains("TNTFuse", 99)) {
            this.fuseTicks = nbt.getInt("TNTFuse");
        }
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("TNTFuse", this.fuseTicks);
    }
}
