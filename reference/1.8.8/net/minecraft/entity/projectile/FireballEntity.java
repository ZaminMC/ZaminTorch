package net.minecraft.entity.projectile;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class FireballEntity extends ProjectileEntity {
    public int explosionPower = 1;

    public FireballEntity(World world) {
        super(world);
    }

    public FireballEntity(World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
    }

    public FireballEntity(World world, LivingEntity owner, double velocityX, double velocityY, double velocityZ) {
        super(world, owner, velocityX, velocityY, velocityZ);
    }

    @Override
    protected void onHit(HitResult hit) {
        if (!this.world.isClient) {
            if (hit.entity != null) {
                hit.entity.takeDamage(DamageSource.fireball(this, this.shooter), 6.0F);
                this.damageEntity(this.shooter, hit.entity);
            }

            boolean flag = this.world.getGameRules().getBoolean("mobGriefing");
            this.world.explode(null, this.x, this.y, this.z, this.explosionPower, flag, flag);
            this.remove();
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("ExplosionPower", this.explosionPower);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        if (nbt.contains("ExplosionPower", 99)) {
            this.explosionPower = nbt.getInt("ExplosionPower");
        }
    }
}
