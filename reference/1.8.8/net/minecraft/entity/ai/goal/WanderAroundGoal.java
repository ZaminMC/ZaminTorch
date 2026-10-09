package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ai.TargetFinder;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.Vec3d;

public class WanderAroundGoal extends Goal {
    private PathFinderMobEntity entity;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double speed;
    private int interval;
    private boolean shouldUpdateGoal;

    public WanderAroundGoal(PathFinderMobEntity entity, double speed) {
        this(entity, speed, 120);
    }

    public WanderAroundGoal(PathFinderMobEntity entity, double speed, int interval) {
        this.entity = entity;
        this.speed = speed;
        this.interval = interval;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        if (!this.shouldUpdateGoal) {
            if (this.entity.getDespawnTimer() >= 100) {
                return false;
            }

            if (this.entity.getRandom().nextInt(this.interval) != 0) {
                return false;
            }
        }

        Vec3d vec3d = TargetFinder.getTarget(this.entity, 10, 7);
        if (vec3d == null) {
            return false;
        }

        this.targetX = vec3d.x;
        this.targetY = vec3d.y;
        this.targetZ = vec3d.z;
        this.shouldUpdateGoal = false;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return !this.entity.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.entity.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speed);
    }

    public void updateGoal() {
        this.shouldUpdateGoal = true;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }
}
