package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.world.World;

public class AttackGoal extends Goal {
    World world;
    MobEntity mob;
    LivingEntity target;
    int cooldown;

    public AttackGoal(MobEntity mob) {
        this.mob = mob;
        this.world = mob.world;
        this.setControls(3);
    }

    @Override
    public boolean canStart() {
        LivingEntity livingentity = this.mob.getAttackTarget();
        if (livingentity == null) {
            return false;
        }

        this.target = livingentity;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return this.target.isAlive() && !(this.mob.squaredDistanceTo(this.target) > 225.0) && (!this.mob.getNavigation().isDone() || this.canStart());
    }

    @Override
    public void stop() {
        this.target = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        this.mob.getLookControl().setLookatValues(this.target, 30.0F, 30.0F);
        double d0 = this.mob.width * 2.0F * (this.mob.width * 2.0F);
        double d1 = this.mob.squaredDistanceTo(this.target.x, this.target.getShape().minY, this.target.z);
        double d2 = 0.8;
        if (d1 > d0 && d1 < 16.0) {
            d2 = 1.33;
        } else if (d1 < 225.0) {
            d2 = 0.6;
        }

        this.mob.getNavigation().moveTo(this.target, d2);
        this.cooldown = Math.max(this.cooldown - 1, 0);
        if (!(d1 > d0)) {
            if (this.cooldown <= 0) {
                this.cooldown = 20;
                this.mob.tryDamage(this.target);
            }
        }
    }
}
