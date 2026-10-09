package net.minecraft.entity.ai.pathing;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class WallClimberPathNavigation extends GroundPathNavigation {
    private BlockPos target;

    public WallClimberPathNavigation(MobEntity mobEntity, World world) {
        super(mobEntity, world);
    }

    @Override
    public Path findPath(BlockPos target) {
        this.target = target;
        return super.findPath(target);
    }

    @Override
    public Path findPath(Entity target) {
        this.target = new BlockPos(target);
        return super.findPath(target);
    }

    @Override
    public boolean moveTo(Entity entity, double speed) {
        Path path = this.findPath(entity);
        if (path != null) {
            return this.moveAlong(path, speed);
        }

        this.target = new BlockPos(entity);
        this.speed = speed;
        return true;
    }

    @Override
    public void tick() {
        if (!this.isDone()) {
            super.tick();
        } else {
            if (this.target != null) {
                double d0 = this.mob.width * this.mob.width;
                if (!(this.mob.getSquaredDistanceToCenter(this.target) < d0)
                    && (
                        !(this.mob.y > this.target.getY())
                            || !(this.mob.getSquaredDistanceToCenter(new BlockPos(this.target.getX(), MathHelper.floor(this.mob.y), this.target.getZ())) < d0)
                    )) {
                    this.mob.getMovementControl().update(this.target.getX(), this.target.getY(), this.target.getZ(), this.speed);
                } else {
                    this.target = null;
                }
            }
        }
    }
}
