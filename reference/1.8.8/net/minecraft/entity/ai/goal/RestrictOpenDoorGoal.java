package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.village.Village;
import net.minecraft.world.village.VillageDoor;

public class RestrictOpenDoorGoal extends Goal {
    private PathFinderMobEntity entity;
    private VillageDoor door;

    public RestrictOpenDoorGoal(PathFinderMobEntity entity) {
        this.entity = entity;
        if (!(entity.getNavigation() instanceof GroundPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob type for RestrictOpenDoorGoal");
        }
    }

    @Override
    public boolean canStart() {
        if (this.entity.world.isSunny()) {
            return false;
        }

        BlockPos blockpos = new BlockPos(this.entity);
        Village village = this.entity.world.getVillages().getNearestVillage(blockpos, 16);
        if (village == null) {
            return false;
        }

        this.door = village.getNearestDoor(blockpos);
        return this.door != null && this.door.squaredDistanceToIndoors(blockpos) < 2.25;
    }

    @Override
    public boolean shouldContinue() {
        return !this.entity.world.isSunny() && !this.door.isOutsideVillage() && this.door.isIndoors(new BlockPos(this.entity));
    }

    @Override
    public void start() {
        ((GroundPathNavigation)this.entity.getNavigation()).setCanOpenDoors(false);
        ((GroundPathNavigation)this.entity.getNavigation()).setCanPassThroughDoors(false);
    }

    @Override
    public void stop() {
        ((GroundPathNavigation)this.entity.getNavigation()).setCanOpenDoors(true);
        ((GroundPathNavigation)this.entity.getNavigation()).setCanPassThroughDoors(true);
        this.door = null;
    }

    @Override
    public void tick() {
        this.door.restrictOpening();
    }
}
