package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;

public class CreeperIgniteGoal extends Goal {
    CreeperEntity creeper;
    LivingEntity target;

    public CreeperIgniteGoal(CreeperEntity creeper) {
        this.creeper = creeper;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        LivingEntity livingentity = this.creeper.getAttackTarget();
        return this.creeper.getFuseDirection() > 0 || livingentity != null && this.creeper.squaredDistanceTo(livingentity) < 9.0;
    }

    @Override
    public void start() {
        this.creeper.getNavigation().stop();
        this.target = this.creeper.getAttackTarget();
    }

    @Override
    public void stop() {
        this.target = null;
    }

    @Override
    public void tick() {
        if (this.target == null) {
            this.creeper.setFuseDirection(-1);
        } else if (this.creeper.squaredDistanceTo(this.target) > 49.0) {
            this.creeper.setFuseDirection(-1);
        } else if (!this.creeper.getMobVisibilityCache().canSee(this.target)) {
            this.creeper.setFuseDirection(-1);
        } else {
            this.creeper.setFuseDirection(1);
        }
    }
}
