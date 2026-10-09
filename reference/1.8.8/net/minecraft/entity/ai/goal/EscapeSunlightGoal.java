package net.minecraft.entity.ai.goal;

import java.util.Random;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EscapeSunlightGoal extends Goal {
    private PathFinderMobEntity entity;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double speed;
    private World world;

    public EscapeSunlightGoal(PathFinderMobEntity entity, double speed) {
        this.entity = entity;
        this.speed = speed;
        this.world = entity.world;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        if (!this.world.isSunny()) {
            return false;
        }

        if (!this.entity.isOnFire()) {
            return false;
        }

        if (!this.world.hasSkyAccess(new BlockPos(this.entity.x, this.entity.getShape().minY, this.entity.z))) {
            return false;
        }

        Vec3d vec3d = this.getShadedLocationPos();
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

    private Vec3d getShadedLocationPos() {
        Random random = this.entity.getRandom();
        BlockPos blockpos = new BlockPos(this.entity.x, this.entity.getShape().minY, this.entity.z);

        for (int i = 0; i < 10; i++) {
            BlockPos blockpos1 = blockpos.add(random.nextInt(20) - 10, random.nextInt(6) - 3, random.nextInt(20) - 10);
            if (!this.world.hasSkyAccess(blockpos1) && this.entity.getPathfindingFavor(blockpos1) < 0.0F) {
                return new Vec3d(blockpos1.getX(), blockpos1.getY(), blockpos1.getZ());
            }
        }

        return null;
    }
}
