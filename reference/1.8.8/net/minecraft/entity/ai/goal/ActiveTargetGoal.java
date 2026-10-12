package net.minecraft.entity.ai.goal;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.entity.living.player.PlayerEntity;

public class ActiveTargetGoal<T extends LivingEntity> extends TrackTargetGoal {
    protected final Class<T> targetType;
    private final int chanceToStartGoal;
    protected final ActiveTargetGoal.EntityDistanceComparator comparator;
    protected Predicate<? super T> targetFilter;
    protected LivingEntity target;

    public ActiveTargetGoal(PathFinderMobEntity entity, Class<T> targetType, boolean checkVisibility) {
        this(entity, targetType, checkVisibility, false);
    }

    public ActiveTargetGoal(PathFinderMobEntity entity, Class<T> targetType, boolean checkVisibility, boolean checkCanNavigate) {
        this(entity, targetType, 10, checkVisibility, checkCanNavigate, null);
    }

    public ActiveTargetGoal(
        PathFinderMobEntity entity,
        Class<T> targetType,
        int chanceToStartGoal,
        boolean checkVisibility,
        boolean checkCanNavigate,
        Predicate<? super T> targetFilter
    ) {
        super(entity, checkVisibility, checkCanNavigate);
        this.targetType = targetType;
        this.chanceToStartGoal = chanceToStartGoal;
        this.comparator = new ActiveTargetGoal.EntityDistanceComparator(entity);
        this.setControls(1);
        this.targetFilter = new Predicate<T>() {
            public boolean apply(T livingEntity) {
                if (targetFilter != null && !targetFilter.apply(livingEntity)) {
                    return false;
                }

                if (livingEntity instanceof PlayerEntity) {
                    double d0 = ActiveTargetGoal.this.getFollowRange();
                    if (livingEntity.isSneaking()) {
                        d0 *= 0.8F;
                    }

                    if (livingEntity.isInvisible()) {
                        float f = ((PlayerEntity)livingEntity).getArmorEquippedRatio();
                        if (f < 0.1F) {
                            f = 0.1F;
                        }

                        d0 *= 0.7F * f;
                    }

                    if (livingEntity.distanceTo(ActiveTargetGoal.this.mob) > d0) {
                        return false;
                    }
                }

                return ActiveTargetGoal.this.canTarget(livingEntity, false);
            }
        };
    }

    @Override
    public boolean canStart() {
        if (this.chanceToStartGoal > 0 && this.mob.getRandom().nextInt(this.chanceToStartGoal) != 0) {
            return false;
        }

        double d0 = this.getFollowRange();
        List<T> list = this.mob
            .world
            .getEntitiesOfType(this.targetType, this.mob.getShape().grown(d0, 4.0, d0), Predicates.and(this.targetFilter, EntityFilter.NOT_SPECTATOR));
        Collections.sort(list, this.comparator);
        if (list.isEmpty()) {
            return false;
        }

        this.target = list.get(0);
        return true;
    }

    @Override
    public void start() {
        this.mob.setAttackTarget(this.target);
        super.start();
    }

    public static class EntityDistanceComparator implements Comparator<Entity> {
        private final Entity entity;

        public EntityDistanceComparator(Entity entity) {
            this.entity = entity;
        }

        public int compare(Entity entity, Entity entity2) {
            double d0 = this.entity.squaredDistanceTo(entity);
            double d1 = this.entity.squaredDistanceTo(entity2);
            if (d0 < d1) {
                return -1;
            } else {
                return d0 > d1 ? 1 : 0;
            }
        }
    }
}
