package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.util.math.MathHelper;

public class PounceAtTargetGoal extends Goal {
    MobEntity mob;
    LivingEntity targetEntity;
    float speed;

    public PounceAtTargetGoal(MobEntity mob, float speed) {
        this.mob = mob;
        this.speed = speed;
        this.setControls(5);
    }

    @Override
    public boolean canStart() {
        this.targetEntity = this.mob.getAttackTarget();
        if (this.targetEntity == null) {
            return false;
        }

        double d0 = this.mob.squaredDistanceTo(this.targetEntity);
        return !(d0 < 4.0) && !(d0 > 16.0) && this.mob.onGround && this.mob.getRandom().nextInt(5) == 0;
    }

    @Override
    public boolean shouldContinue() {
        return !this.mob.onGround;
    }

    @Override
    public void start() {
        double d0 = this.targetEntity.x - this.mob.x;
        double d1 = this.targetEntity.z - this.mob.z;
        float f = MathHelper.sqrt(d0 * d0 + d1 * d1);
        this.mob.velocityX = this.mob.velocityX + (d0 / f * 0.5 * 0.8F + this.mob.velocityX * 0.2F);
        this.mob.velocityZ = this.mob.velocityZ + (d1 / f * 0.5 * 0.8F + this.mob.velocityZ * 0.2F);
        this.mob.velocityY = this.speed;
    }
}
