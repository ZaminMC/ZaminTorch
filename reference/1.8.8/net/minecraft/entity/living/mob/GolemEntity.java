package net.minecraft.entity.living.mob;

import net.minecraft.entity.SpawnableEntity;
import net.minecraft.world.World;

public abstract class GolemEntity extends PathFinderMobEntity implements SpawnableEntity {
    public GolemEntity(World world) {
        super(world);
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
    }

    @Override
    protected String getAmbientSound() {
        return "none";
    }

    @Override
    protected String getHurtSound() {
        return "none";
    }

    @Override
    protected String getDeathSound() {
        return "none";
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }
}
