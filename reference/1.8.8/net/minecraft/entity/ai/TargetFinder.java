package net.minecraft.entity.ai;

import java.util.Random;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class TargetFinder {
    private static Vec3d startOffset = new Vec3d(0.0, 0.0, 0.0);

    public static Vec3d getTarget(PathFinderMobEntity mob, int horizontalRange, int verticalRange) {
        return getTarget(mob, horizontalRange, verticalRange, null);
    }

    public static Vec3d getTargetAwayFromPosition(PathFinderMobEntity mob, int horizontalRange, int verticalRange, Vec3d start) {
        startOffset = start.subtract(mob.x, mob.y, mob.z);
        return getTarget(mob, horizontalRange, verticalRange, startOffset);
    }

    public static Vec3d getTargetAwayFromEntity(PathFinderMobEntity mob, int horizontalRange, int verticalRange, Vec3d start) {
        startOffset = new Vec3d(mob.x, mob.y, mob.z).subtract(start);
        return getTarget(mob, horizontalRange, verticalRange, startOffset);
    }

    private static Vec3d getTarget(PathFinderMobEntity mob, int horizontalRange, int verticalRange, Vec3d startOffset) {
        Random random = mob.getRandom();
        boolean flag = false;
        int i = 0;
        int j = 0;
        int k = 0;
        float f = -99999.0F;
        boolean flag1;
        if (mob.inVillage()) {
            double d0 = mob.getPos().squaredDistanceTo(MathHelper.floor(mob.x), MathHelper.floor(mob.y), MathHelper.floor(mob.z)) + 4.0;
            double d1 = mob.getVillageRadius() + horizontalRange;
            flag1 = d0 < d1 * d1;
        } else {
            flag1 = false;
        }

        for (int j1 = 0; j1 < 10; j1++) {
            int l = random.nextInt(2 * horizontalRange + 1) - horizontalRange;
            int k1 = random.nextInt(2 * verticalRange + 1) - verticalRange;
            int i1 = random.nextInt(2 * horizontalRange + 1) - horizontalRange;
            if (startOffset == null || !(l * startOffset.x + i1 * startOffset.z < 0.0)) {
                if (mob.inVillage() && horizontalRange > 1) {
                    BlockPos blockpos = mob.getPos();
                    if (mob.x > blockpos.getX()) {
                        l -= random.nextInt(horizontalRange / 2);
                    } else {
                        l += random.nextInt(horizontalRange / 2);
                    }

                    if (mob.z > blockpos.getZ()) {
                        i1 -= random.nextInt(horizontalRange / 2);
                    } else {
                        i1 += random.nextInt(horizontalRange / 2);
                    }
                }

                l += MathHelper.floor(mob.x);
                k1 += MathHelper.floor(mob.y);
                i1 += MathHelper.floor(mob.z);
                BlockPos blockpos1 = new BlockPos(l, k1, i1);
                if (!flag1 || mob.isValidGoalTarget(blockpos1)) {
                    float f1 = mob.getPathfindingFavor(blockpos1);
                    if (f1 > f) {
                        f = f1;
                        i = l;
                        j = k1;
                        k = i1;
                        flag = true;
                    }
                }
            }
        }

        return flag ? new Vec3d(i, j, k) : null;
    }
}
