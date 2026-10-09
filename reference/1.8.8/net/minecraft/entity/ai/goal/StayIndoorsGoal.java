package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ai.TargetFinder;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.village.Village;
import net.minecraft.world.village.VillageDoor;

public class StayIndoorsGoal extends Goal {
    private PathFinderMobEntity entity;
    private VillageDoor door;
    private int x = -1;
    private int y = -1;

    public StayIndoorsGoal(PathFinderMobEntity entity) {
        this.entity = entity;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        BlockPos blockpos = new BlockPos(this.entity);
        if ((!this.entity.world.isSunny() || this.entity.world.isRaining() && !this.entity.world.getBiome(blockpos).isRainy())
            && !this.entity.world.dimension.hasNoSky()) {
            if (this.entity.getRandom().nextInt(50) != 0) {
                return false;
            }

            if (this.x != -1 && this.entity.squaredDistanceTo(this.x, this.entity.y, this.y) < 4.0) {
                return false;
            }

            Village village = this.entity.world.getVillages().getNearestVillage(blockpos, 14);
            if (village == null) {
                return false;
            }

            this.door = village.getNearestTickingDoor(blockpos);
            return this.door != null;
        } else {
            return false;
        }
    }

    @Override
    public boolean shouldContinue() {
        return !this.entity.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.x = -1;
        BlockPos blockpos = this.door.getIndoorsPos();
        int i = blockpos.getX();
        int j = blockpos.getY();
        int k = blockpos.getZ();
        if (this.entity.getSquaredDistanceTo(blockpos) > 256.0) {
            Vec3d vec3d = TargetFinder.getTargetAwayFromPosition(this.entity, 14, 3, new Vec3d(i + 0.5, j, k + 0.5));
            if (vec3d != null) {
                this.entity.getNavigation().moveTo(vec3d.x, vec3d.y, vec3d.z, 1.0);
            }
        } else {
            this.entity.getNavigation().moveTo(i + 0.5, j, k + 0.5, 1.0);
        }
    }

    @Override
    public void stop() {
        this.x = this.door.getIndoorsPos().getX();
        this.y = this.door.getIndoorsPos().getZ();
        this.door = null;
    }
}
