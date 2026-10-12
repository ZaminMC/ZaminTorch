package net.minecraft.entity.living.mob.ambient;

import net.minecraft.entity.SpawnableEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.World;

public abstract class AmbientMobEntity extends MobEntity implements SpawnableEntity {
    public AmbientMobEntity(World world) {
        super(world);
    }

    @Override
    public boolean isTameable() {
        return false;
    }

    @Override
    protected boolean interactMob(PlayerEntity player) {
        return false;
    }
}
