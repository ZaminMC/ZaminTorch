package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ai.TargetFinder;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class WanderThroughVillageGoal extends Goal {
    private PathFinderMobEntity entity;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double speed;

    public WanderThroughVillageGoal(PathFinderMobEntity entity, double speed) {
        this.entity = entity;
        this.speed = speed;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        if (this.entity.isInVillage()) {
            return false;
        }

        BlockPos blockpos = this.entity.getPos();
        Vec3d vec3d = TargetFinder.getTargetAwayFromPosition(this.entity, 16, 7, new Vec3d(blockpos.getX(), blockpos.getY(), blockpos.getZ()));
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
        return !this.entity.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.entity.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speed);
    }
}
