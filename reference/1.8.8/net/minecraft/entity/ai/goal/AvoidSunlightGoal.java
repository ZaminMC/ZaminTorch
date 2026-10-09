package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.living.mob.PathFinderMobEntity;

public class AvoidSunlightGoal extends Goal {
    private PathFinderMobEntity entity;

    public AvoidSunlightGoal(PathFinderMobEntity entity) {
        this.entity = entity;
    }

    @Override
    public boolean canStart() {
        return this.entity.world.isSunny();
    }

    @Override
    public void start() {
        ((GroundPathNavigation)this.entity.getNavigation()).setAvoidSunLight(true);
    }

    @Override
    public void stop() {
        ((GroundPathNavigation)this.entity.getNavigation()).setAvoidSunLight(false);
    }
}
