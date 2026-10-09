package net.minecraft.entity.ai.goal;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.ai.TargetFinder;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNavigation;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.Vec3d;

public class FleeEntityGoal<T extends Entity> extends Goal {
    private final Predicate<Entity> entityFilter = new Predicate<Entity>() {
        public boolean apply(Entity entity) {
            return entity.isAlive() && FleeEntityGoal.this.mob.getMobVisibilityCache().canSee(entity);
        }
    };
    protected PathFinderMobEntity mob;
    private double speedWhenFar;
    private double speedWhenClose;
    protected T target;
    private float range;
    private Path path;
    private PathNavigation navigation;
    private Class<T> targetType;
    private Predicate<? super T> targetFilter;

    public FleeEntityGoal(PathFinderMobEntity mob, Class<T> targetType, float distance, double speedWhenFar, double speedWhenClose) {
        this(mob, targetType, Predicates.alwaysTrue(), distance, speedWhenFar, speedWhenClose);
    }

    public FleeEntityGoal(
        PathFinderMobEntity mob, Class<T> targetType, Predicate<? super T> targetFilter, float distance, double speedWhenFar, double speedWhenClose
    ) {
        this.mob = mob;
        this.targetType = targetType;
        this.targetFilter = targetFilter;
        this.range = distance;
        this.speedWhenFar = speedWhenFar;
        this.speedWhenClose = speedWhenClose;
        this.navigation = mob.getNavigation();
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        List<T> list = this.mob
            .world
            .getEntitiesOfType(
                this.targetType,
                this.mob.getShape().grown(this.range, 3.0, this.range),
                Predicates.and(EntityFilter.NOT_SPECTATOR, this.entityFilter, this.targetFilter)
            );
        if (list.isEmpty()) {
            return false;
        }

        this.target = list.get(0);
        Vec3d vec3d = TargetFinder.getTargetAwayFromEntity(this.mob, 16, 7, new Vec3d(this.target.x, this.target.y, this.target.z));
        if (vec3d == null) {
            return false;
        }

        if (this.target.squaredDistanceTo(vec3d.x, vec3d.y, vec3d.z) < this.target.squaredDistanceTo(this.mob)) {
            return false;
        }

        this.path = this.navigation.findPath(vec3d.x, vec3d.y, vec3d.z);
        return this.path != null && this.path.isOnTarget(vec3d);
    }

    @Override
    public boolean shouldContinue() {
        return !this.navigation.isDone();
    }

    @Override
    public void start() {
        this.navigation.moveAlong(this.path, this.speedWhenFar);
    }

    @Override
    public void stop() {
        this.target = null;
    }

    @Override
    public void tick() {
        if (this.mob.squaredDistanceTo(this.target) < 49.0) {
            this.mob.getNavigation().setSpeed(this.speedWhenClose);
        } else {
            this.mob.getNavigation().setSpeed(this.speedWhenFar);
        }
    }
}
