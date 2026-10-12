package net.minecraft.entity.ai.goal;

import com.google.common.base.Predicate;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MobEntityPlayerTargetGoal extends Goal {
    private static final Logger LOGGER = LogManager.getLogger();
    private MobEntity mob;
    private final Predicate<Entity> targetFilter;
    private final ActiveTargetGoal.EntityDistanceComparator comparator;
    private LivingEntity target;

    public MobEntityPlayerTargetGoal(MobEntity mob) {
        this.mob = mob;
        if (mob instanceof PathFinderMobEntity) {
            LOGGER.warn("Use NearestAttackableTargetGoal.class for PathfinerMob mobs!");
        }

        this.targetFilter = new Predicate<Entity>() {
            public boolean apply(Entity entity) {
                if (!(entity instanceof PlayerEntity)) {
                    return false;
                }

                if (((PlayerEntity)entity).abilities.invulnerable) {
                    return false;
                }

                double d0 = MobEntityPlayerTargetGoal.this.getFollowRange();
                if (entity.isSneaking()) {
                    d0 *= 0.8F;
                }

                if (entity.isInvisible()) {
                    float f = ((PlayerEntity)entity).getArmorEquippedRatio();
                    if (f < 0.1F) {
                        f = 0.1F;
                    }

                    d0 *= 0.7F * f;
                }

                return !(entity.distanceTo(MobEntityPlayerTargetGoal.this.mob) > d0)
                    && TrackTargetGoal.isTarget(MobEntityPlayerTargetGoal.this.mob, (LivingEntity)entity, false, true);
            }
        };
        this.comparator = new ActiveTargetGoal.EntityDistanceComparator(mob);
    }

    @Override
    public boolean canStart() {
        double d0 = this.getFollowRange();
        List<PlayerEntity> list = this.mob.world.getEntitiesOfType(PlayerEntity.class, this.mob.getShape().grown(d0, 4.0, d0), this.targetFilter);
        Collections.sort(list, this.comparator);
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

        if (livingentity instanceof PlayerEntity && ((PlayerEntity)livingentity).abilities.invulnerable) {
            return false;
        }

        AbstractTeam abstractteam = this.mob.getScoreboardTeam();
        AbstractTeam abstractteam1 = livingentity.getScoreboardTeam();
        if (abstractteam != null && abstractteam1 == abstractteam) {
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
