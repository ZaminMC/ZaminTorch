package net.minecraft.entity.ai.pathing;

import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class WaterPathNavigation extends PathNavigation {
    public WaterPathNavigation(MobEntity mobEntity, World world) {
        super(mobEntity, world);
    }

    @Override
    protected PathFinder createPathFinder() {
        return new PathFinder(new SwimNodeEvaluator());
    }

    @Override
    protected boolean canUpdatePath() {
        return this.isInLiquid();
    }

    @Override
    protected Vec3d getTempPos() {
        return new Vec3d(this.mob.x, this.mob.y + this.mob.height * 0.5, this.mob.z);
    }

    @Override
    protected void updatePath() {
        Vec3d vec3d = this.getTempPos();
        float f = this.mob.width * this.mob.width;
        int i = 6;
        if (vec3d.squaredDistanceTo(this.path.getPos(this.mob, this.path.getCurrentIndex())) < f) {
            this.path.advance();
        }

        for (int j = Math.min(this.path.getCurrentIndex() + i, this.path.length() - 1); j > this.path.getCurrentIndex(); j--) {
            Vec3d vec3d1 = this.path.getPos(this.mob, j);
            if (!(vec3d1.squaredDistanceTo(vec3d) > 36.0) && this.canMoveDirectly(vec3d, vec3d1, 0, 0, 0)) {
                this.path.setCurrentIndex(j);
                break;
            }
        }

        this.doStuckCheck(vec3d);
    }

    @Override
    protected void trimPath() {
        super.trimPath();
    }

    @Override
    protected boolean canMoveDirectly(Vec3d from, Vec3d to, int width, int height, int depth) {
        HitResult hitresult = this.world.rayTrace(from, new Vec3d(to.x, to.y + this.mob.height * 0.5, to.z), false, true, false);
        return hitresult == null || hitresult.type == HitResult.Type.MISS;
    }
}
