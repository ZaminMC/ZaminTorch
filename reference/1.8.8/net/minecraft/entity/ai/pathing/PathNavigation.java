package net.minecraft.entity.ai.pathing;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldRegion;

public abstract class PathNavigation {
    protected MobEntity mob;
    protected World world;
    protected Path path;
    protected double speed;
    private final EntityAttributeInstance followRange;
    private int ticks;
    private int lastStuckCheckTime;
    private Vec3d lastStuckCheckPos = new Vec3d(0.0, 0.0, 0.0);
    private float offset = 1.0F;
    private final PathFinder pathFinder;

    public PathNavigation(MobEntity mob, World world) {
        this.mob = mob;
        this.world = world;
        this.followRange = mob.getAttribute(EntityAttributes.FOLLOW_RANGE);
        this.pathFinder = this.createPathFinder();
    }

    protected abstract PathFinder createPathFinder();

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public float getFollowRange() {
        return (float)this.followRange.get();
    }

    public final Path findPath(double x, double y, double z) {
        return this.findPath(new BlockPos(MathHelper.floor(x), (int)y, MathHelper.floor(z)));
    }

    public Path findPath(BlockPos target) {
        if (!this.canUpdatePath()) {
            return null;
        }

        float f = this.getFollowRange();
        this.world.profiler.push("pathfind");
        BlockPos blockpos = new BlockPos(this.mob);
        int i = (int)(f + 8.0F);
        WorldRegion worldregion = new WorldRegion(this.world, blockpos.add(-i, -i, -i), blockpos.add(i, i, i), 0);
        Path path = this.pathFinder.findPath(worldregion, this.mob, target, f);
        this.world.profiler.pop();
        return path;
    }

    public boolean moveTo(double x, double y, double z, double speed) {
        Path path = this.findPath(MathHelper.floor(x), (int)y, MathHelper.floor(z));
        return this.moveAlong(path, speed);
    }

    public void setOffset(float offset) {
        this.offset = offset;
    }

    public Path findPath(Entity target) {
        if (!this.canUpdatePath()) {
            return null;
        }

        float f = this.getFollowRange();
        this.world.profiler.push("pathfind");
        BlockPos blockpos = new BlockPos(this.mob).up();
        int i = (int)(f + 16.0F);
        WorldRegion worldregion = new WorldRegion(this.world, blockpos.add(-i, -i, -i), blockpos.add(i, i, i), 0);
        Path path = this.pathFinder.findPath(worldregion, this.mob, target, f);
        this.world.profiler.pop();
        return path;
    }

    public boolean moveTo(Entity entity, double speed) {
        Path path = this.findPath(entity);
        return path != null && this.moveAlong(path, speed);
    }

    public boolean moveAlong(Path path, double speed) {
        if (path == null) {
            this.path = null;
            return false;
        }

        if (!path.equals(this.path)) {
            this.path = path;
        }

        this.trimPath();
        if (this.path.length() == 0) {
            return false;
        }

        this.speed = speed;
        Vec3d vec3d = this.getTempPos();
        this.lastStuckCheckTime = this.ticks;
        this.lastStuckCheckPos = vec3d;
        return true;
    }

    public Path getPath() {
        return this.path;
    }

    public void tick() {
        this.ticks++;
        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.updatePath();
            } else if (this.path != null && this.path.getCurrentIndex() < this.path.length()) {
                Vec3d vec3d = this.getTempPos();
                Vec3d vec3d1 = this.path.getPos(this.mob, this.path.getCurrentIndex());
                if (vec3d.y > vec3d1.y
                    && !this.mob.onGround
                    && MathHelper.floor(vec3d.x) == MathHelper.floor(vec3d1.x)
                    && MathHelper.floor(vec3d.z) == MathHelper.floor(vec3d1.z)) {
                    this.path.setCurrentIndex(this.path.getCurrentIndex() + 1);
                }
            }

            if (!this.isDone()) {
                Vec3d vec3d2 = this.path.getCurrentPos(this.mob);
                if (vec3d2 != null) {
                    Box box1 = new Box(vec3d2.x, vec3d2.y, vec3d2.z, vec3d2.x, vec3d2.y, vec3d2.z).grown(0.5, 0.5, 0.5);
                    List<Box> list = this.world.getCollisions(this.mob, box1.expanded(0.0, -1.0, 0.0));
                    double d0 = -1.0;
                    box1 = box1.moved(0.0, 1.0, 0.0);

                    for (Box box : list) {
                        d0 = box.intersectY(box1, d0);
                    }

                    this.mob.getMovementControl().update(vec3d2.x, vec3d2.y + d0, vec3d2.z, this.speed);
                }
            }
        }
    }

    protected void updatePath() {
        Vec3d vec3d = this.getTempPos();
        int i = this.path.length();

        for (int j = this.path.getCurrentIndex(); j < this.path.length(); j++) {
            if (this.path.getNode(j).y != (int)vec3d.y) {
                i = j;
                break;
            }
        }

        float f = this.mob.width * this.mob.width * this.offset;

        for (int k = this.path.getCurrentIndex(); k < i; k++) {
            Vec3d vec3d1 = this.path.getPos(this.mob, k);
            if (vec3d.squaredDistanceTo(vec3d1) < f) {
                this.path.setCurrentIndex(k + 1);
            }
        }

        int j1 = MathHelper.ceil(this.mob.width);
        int k1 = (int)this.mob.height + 1;
        int l = j1;

        for (int i1 = i - 1; i1 >= this.path.getCurrentIndex(); i1--) {
            if (this.canMoveDirectly(vec3d, this.path.getPos(this.mob, i1), j1, k1, l)) {
                this.path.setCurrentIndex(i1);
                break;
            }
        }

        this.doStuckCheck(vec3d);
    }

    protected void doStuckCheck(Vec3d pos) {
        if (this.ticks - this.lastStuckCheckTime > 100) {
            if (pos.squaredDistanceTo(this.lastStuckCheckPos) < 2.25) {
                this.stop();
            }

            this.lastStuckCheckTime = this.ticks;
            this.lastStuckCheckPos = pos;
        }
    }

    public boolean isDone() {
        return this.path == null || this.path.isDone();
    }

    public void stop() {
        this.path = null;
    }

    protected abstract Vec3d getTempPos();

    protected abstract boolean canUpdatePath();

    protected boolean isInLiquid() {
        return this.mob.isInWater() || this.mob.isInLava();
    }

    protected void trimPath() {
    }

    protected abstract boolean canMoveDirectly(Vec3d from, Vec3d to, int width, int height, int depth);
}
