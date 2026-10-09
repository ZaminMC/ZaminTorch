package net.minecraft.entity.ai.goal;

import com.google.common.base.Predicate;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Used exclusively by SlimeEntity in 1.8, essentially a variation of ActiveTargetGoal for non path finding entities
 */
public class MobEntityActiveTargetGoal extends Goal {
    private static final Logger LOGGER = LogManager.getLogger();
    private MobEntity mob;
    private final Predicate<LivingEntity> targetFilter;
    private final ActiveTargetGoal.EntityDistanceComparator entityDistanceComparator;
    private LivingEntity target;
    private Class<? extends LivingEntity> targetType;

    public MobEntityActiveTargetGoal(MobEntity mob, Class<? extends LivingEntity> targetType) {
        this.mob = mob;
        this.targetType = targetType;
        if (mob instanceof PathFinderMobEntity) {
            LOGGER.warn("Use NearestAttackableTargetGoal.class for PathfinerMob mobs!");
        }

        this.targetFilter = new Predicate<LivingEntity>() {
            public boolean apply(LivingEntity livingEntity) {
                double d0 = MobEntityActiveTargetGoal.this.getFollowRange();
                if (livingEntity.isSneaking()) {
                    d0 *= 0.8F;
                }

                return !livingEntity.isInvisible()
                    && !(livingEntity.distanceTo(MobEntityActiveTargetGoal.this.mob) > d0)
                    && TrackTargetGoal.isTarget(MobEntityActiveTargetGoal.this.mob, livingEntity, false, true);
            }
        };
        this.entityDistanceComparator = new ActiveTargetGoal.EntityDistanceComparator(mob);
    }

    @Override
    public boolean canStart() {
        double d0 = this.getFollowRange();
        List<LivingEntity> list = this.mob.world.getEntitiesOfType(this.targetType, this.mob.getShape().grown(d0, 4.0, d0), this.targetFilter);
        Collections.sort(list, this.entityDistanceComparator);
        if (list.isEmpty()) {
            return false;
        }

        this.target = list.get(0);
        return true;
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity livingentity = this.mob.getAttackTarget();
        if (livingentity == null) {
            return false;
        }

        if (!livingentity.isAlive()) {
            return false;
        }

        double d0 = this.getFollowRange();
        return !(this.mob.squaredDistanceTo(livingentity) > d0 * d0)
            && (!(livingentity instanceof ServerPlayerEntity) || !((ServerPlayerEntity)livingentity).interactionManager.isCreative());
    }

    @Override
    public void start() {
        this.mob.setAttackTarget(this.target);
        super.start();
    }

    @Override
    public void stop() {
        this.mob.setAttackTarget(null);
        super.start();
    }

    protected double getFollowRange() {
        EntityAttributeInstance entityattributeinstance = this.mob.getAttribute(EntityAttributes.FOLLOW_RANGE);
        return entityattributeinstance == null ? 16.0 : entityattributeinstance.get();
    }
}
