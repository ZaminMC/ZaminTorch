package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ai.TargetFinder;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.Vec3d;

public class GoToEntityTargetGoal extends Goal {
    private PathFinderMobEntity entity;
    private LivingEntity targetEntity;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double speed;
    private float maxDistance;

    public GoToEntityTargetGoal(PathFinderMobEntity entity, double speed, float f) {
        this.entity = entity;
        this.speed = speed;
        this.maxDistance = f;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        this.targetEntity = this.entity.getAttackTarget();
        if (this.targetEntity == null) {
            return false;
        }

        if (this.targetEntity.squaredDistanceTo(this.entity) > this.maxDistance * this.maxDistance) {
            return false;
        }

        Vec3d vec3d = TargetFinder.getTargetAwayFromPosition(this.entity, 16, 7, new Vec3d(this.targetEntity.x, this.targetEntity.y, this.targetEntity.z));
        if (vec3d == null) {
            return false;
        }

        this.targetX = vec3d.x;
        this.targetY = vec3d.y;
        this.targetZ = vec3d.z;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return !this.entity.getNavigation().isDone()
            && this.targetEntity.isAlive()
            && this.targetEntity.squaredDistanceTo(this.entity) < this.maxDistance * this.maxDistance;
    }

    @Override
    public void stop() {
        this.targetEntity = null;
    }

    @Override
    public void start() {
        this.entity.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speed);
    }
}
