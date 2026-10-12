package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.living.mob.MobEntity;

public class SwimGoal extends Goal {
    private MobEntity entity;

    public SwimGoal(MobEntity mob) {
        this.entity = mob;
        this.setControls(4);
        ((GroundPathNavigation)mob.getNavigation()).setCanFloat(true);
    }

    @Override
    public boolean canStart() {
        return this.entity.isInWater() || this.entity.isInLava();
    }

    @Override
    public void tick() {
        if (this.entity.getRandom().nextFloat() < 0.8F) {
            this.entity.getJumpControl().setActive();
        }
    }
}
