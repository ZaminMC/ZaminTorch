package net.minecraft.entity.living.mob.water;

import net.minecraft.entity.SpawnableEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.World;

public abstract class WaterMobEntity extends MobEntity implements SpawnableEntity {
    public WaterMobEntity(World world) {
        super(world);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean canSpawn() {
        return true;
    }

    @Override
    public boolean isUnobstructed() {
        return this.world.isUnobstructed(this.getShape(), this);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    protected boolean canDespawn() {
        return true;
    }

    @Override
    protected int getXpDrop(PlayerEntity playerEntity) {
        return 1 + this.world.random.nextInt(3);
    }

    @Override
    public void baseTick() {
        int i = this.getBreath();
        super.baseTick();
        if (this.isAlive() && !this.isInWater()) {
            this.setBreath(--i);
            if (this.getBreath() == -20) {
                this.setBreath(0);
                this.takeDamage(DamageSource.DROWN, 2.0F);
            }
        } else {
            this.setBreath(300);
        }
    }

    @Override
    public boolean hasLiquidCollision() {
        return false;
    }
}
