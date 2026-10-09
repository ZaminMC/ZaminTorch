package net.minecraft.entity.ai.goal;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.entity.ai.TargetFinder;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.village.Village;
import net.minecraft.world.village.VillageDoor;

public class WanderThroughVillageAtNightGoal extends Goal {
    private PathFinderMobEntity entity;
    private double speed;
    private Path path;
    private VillageDoor villageDoor;
    /**
     * friendly is true for iron golems and false for zombies.
     */
    private boolean friendly;
    private List<VillageDoor> doors = Lists.newArrayList();

    public WanderThroughVillageAtNightGoal(PathFinderMobEntity entity, double speed, boolean friendly) {
        this.entity = entity;
        this.speed = speed;
        this.friendly = friendly;
        this.setControls(1);
        if (!(entity.getNavigation() instanceof GroundPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob for MoveThroughVillageGoal");
        }
    }

    @Override
    public boolean canStart() {
        this.clampDoorsListSize();
        if (this.friendly && this.entity.world.isSunny()) {
            return false;
        }

        Village village = this.entity.world.getVillages().getNearestVillage(new BlockPos(this.entity), 0);
        if (village == null) {
            return false;
        }

        this.villageDoor = this.findDoor(village);
        if (this.villageDoor == null) {
            return false;
        }

        GroundPathNavigation groundpathnavigation = (GroundPathNavigation)this.entity.getNavigation();
        boolean flag = groundpathnavigation.canPassThroughDoors();
        groundpathnavigation.setCanOpenDoors(false);
        this.path = groundpathnavigation.findPath(this.villageDoor.getPos());
        groundpathnavigation.setCanOpenDoors(flag);
        if (this.path != null) {
            return true;
        }

        Vec3d vec3d = TargetFinder.getTargetAwayFromPosition(
            this.entity, 10, 7, new Vec3d(this.villageDoor.getPos().getX(), this.villageDoor.getPos().getY(), this.villageDoor.getPos().getZ())
        );
        if (vec3d == null) {
            return false;
        }

        groundpathnavigation.setCanOpenDoors(false);
        this.path = this.entity.getNavigation().findPath(vec3d.x, vec3d.y, vec3d.z);
        groundpathnavigation.setCanOpenDoors(flag);
        return this.path != null;
    }

    @Override
    public boolean shouldContinue() {
        if (this.entity.getNavigation().isDone()) {
            return false;
        }

        float f = this.entity.width + 4.0F;
        return this.entity.getSquaredDistanceTo(this.villageDoor.getPos()) > f * f;
    }

    @Override
    public void start() {
        this.entity.getNavigation().moveAlong(this.path, this.speed);
    }

    @Override
    public void stop() {
        if (this.entity.getNavigation().isDone() || this.entity.getSquaredDistanceTo(this.villageDoor.getPos()) < 16.0) {
            this.doors.add(this.villageDoor);
        }
    }

    private VillageDoor findDoor(Village village) {
        VillageDoor villagedoor = null;
        int i = Integer.MAX_VALUE;

        for (VillageDoor villagedoor1 : village.getDoors()) {
            int j = villagedoor1.squaredDistanceTo(MathHelper.floor(this.entity.x), MathHelper.floor(this.entity.y), MathHelper.floor(this.entity.z));
            if (j < i && !this.isDoorInRange(villagedoor1)) {
                villagedoor = villagedoor1;
                i = j;
            }
        }

        return villagedoor;
    }

    private boolean isDoorInRange(VillageDoor door) {
        for (VillageDoor villagedoor : this.doors) {
            if (door.getPos().equals(villagedoor.getPos())) {
                return true;
            }
        }

        return false;
    }

    /**
     * If the size of the door list gets too big the first entry will get removed to keep the size equal to 15.
     */
    private void clampDoorsListSize() {
        if (this.doors.size() > 15) {
            this.doors.remove(0);
        }
    }
}
