package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ai.TargetFinder;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.Vec3d;

public class EscapeDangerGoal extends Goal {
    private PathFinderMobEntity entity;
    protected double speed;
    private double targetX;
    private double targetY;
    private double targetZ;

    public EscapeDangerGoal(PathFinderMobEntity entity, double speed) {
        this.entity = entity;
        this.speed = speed;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        if (this.entity.getAttacker() == null && !this.entity.isOnFire()) {
            return false;
        }

        Vec3d vec3d = TargetFinder.getTarget(this.entity, 5, 4);
        if (vec3d == null) {
            return false;
        }

        this.targetX = vec3d.x;
        this.targetY = vec3d.y;
        this.targetZ = vec3d.z;
        return true;
    }

    @Override
    public void start() {
        this.entity.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speed);
    }

    @Override
    public boolean shouldContinue() {
        return !this.entity.getNavigation().isDone();
    }
}
