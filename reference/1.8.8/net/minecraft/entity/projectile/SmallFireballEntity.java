package net.minecraft.entity.projectile;

import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class SmallFireballEntity extends ProjectileEntity {
    public SmallFireballEntity(World world) {
        super(world);
        this.setSize(0.3125F, 0.3125F);
    }

    public SmallFireballEntity(World world, LivingEntity livingEntity, double d, double e, double f) {
        super(world, livingEntity, d, e, f);
        this.setSize(0.3125F, 0.3125F);
    }

    public SmallFireballEntity(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, g, h, i);
        this.setSize(0.3125F, 0.3125F);
    }

    @Override
    protected void onHit(HitResult hit) {
        if (!this.world.isClient) {
            if (hit.entity != null) {
                boolean flag = hit.entity.takeDamage(DamageSource.fireball(this, this.shooter), 5.0F);
                if (flag) {
                    this.damageEntity(this.shooter, hit.entity);
                    if (!hit.entity.isImmuneToFire()) {
                        hit.entity.setOnFireFor(5);
                    }
                }
            } else {
                boolean flag1 = true;
                if (this.shooter != null && this.shooter instanceof MobEntity) {
                    flag1 = this.world.getGameRules().getBoolean("mobGriefing");
                }

                if (flag1) {
                    BlockPos blockpos = hit.getPos().offset(hit.face);
                    if (this.world.isAir(blockpos)) {
                        this.world.setBlockState(blockpos, Blocks.FIRE.defaultState());
                    }
                }
            }

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
}
