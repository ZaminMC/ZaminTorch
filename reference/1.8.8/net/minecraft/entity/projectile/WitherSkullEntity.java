package net.minecraft.entity.projectile;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

public class WitherSkullEntity extends ProjectileEntity {
    public WitherSkullEntity(World world) {
        super(world);
        this.setSize(0.3125F, 0.3125F);
    }

    public WitherSkullEntity(World world, LivingEntity owner, double x, double y, double z) {
        super(world, owner, x, y, z);
        this.setSize(0.3125F, 0.3125F);
    }

    @Override
    protected float getDrag() {
        return this.isCharged() ? 0.73F : super.getDrag();
    }

    public WitherSkullEntity(World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.setSize(0.3125F, 0.3125F);
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public float getBlastResistance(Explosion explosion, World world, BlockPos pos, BlockState state) {
        float f = super.getBlastResistance(explosion, world, pos, state);
        Block block = state.getBlock();
        if (this.isCharged() && WitherEntity.canDestroy(block)) {
            f = Math.min(0.8F, f);
        }

        return f;
    }

    @Override
    protected void onHit(HitResult hit) {
        if (!this.world.isClient) {
            if (hit.entity != null) {
                if (this.shooter != null) {
                    if (hit.entity.takeDamage(DamageSource.mob(this.shooter), 8.0F)) {
                        if (!hit.entity.isAlive()) {
                            this.shooter.heal(5.0F);
                        } else {
                            this.damageEntity(this.shooter, hit.entity);
                        }
                    }
                } else {
                    hit.entity.takeDamage(DamageSource.MAGIC, 5.0F);
                }

                if (hit.entity instanceof LivingEntity) {
                    int i = 0;
                    if (this.world.getDifficulty() == Difficulty.NORMAL) {
                        i = 10;
                    } else if (this.world.getDifficulty() == Difficulty.HARD) {
                        i = 40;
                    }

                    if (i > 0) {
                        ((LivingEntity)hit.entity).addStatusEffect(new StatusEffectInstance(StatusEffect.WITHER.id, 20 * i, 1));
                    }
                }
            }

            this.world.explode(this, this.x, this.y, this.z, 1.0F, false, this.world.getGameRules().getBoolean("mobGriefing"));
            this.remove();
        }
    }

    @Override
    public boolean hasCollision() {
        return false;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void registerSyncedData() {
        this.syncedData.register(10, (byte)0);
    }

    public boolean isCharged() {
        return this.syncedData.getByte(10) == 1;
    }

    public void setCharged(boolean charged) {
        this.syncedData.update(10, Byte.valueOf((byte)(charged ? 1 : 0)));
    }
}
