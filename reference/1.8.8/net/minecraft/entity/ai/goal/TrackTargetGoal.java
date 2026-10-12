package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.Tameable;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.StringUtils;

public abstract class TrackTargetGoal extends Goal {
    protected final PathFinderMobEntity mob;
    protected boolean checkVisibility;
    private boolean checkCanNavigate;
    private int canNavigateFlag;
    private int checkCanNavigateCooldown;
    private int timeWithoutVisibility;

    public TrackTargetGoal(PathFinderMobEntity entity, boolean checkVisibility) {
        this(entity, checkVisibility, false);
    }

    public TrackTargetGoal(PathFinderMobEntity entity, boolean checkVisibility, boolean checkCanNavigate) {
        this.mob = entity;
        this.checkVisibility = checkVisibility;
        this.checkCanNavigate = checkCanNavigate;
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

        AbstractTeam abstractteam = this.mob.getScoreboardTeam();
        AbstractTeam abstractteam1 = livingentity.getScoreboardTeam();
        if (abstractteam != null && abstractteam1 == abstractteam) {
            return false;
        }

        double d0 = this.getFollowRange();
        if (this.mob.squaredDistanceTo(livingentity) > d0 * d0) {
            return false;
        }

        if (this.checkVisibility) {
            if (this.mob.getMobVisibilityCache().canSee(livingentity)) {
                this.timeWithoutVisibility = 0;
            } else if (++this.timeWithoutVisibility > 60) {
                return false;
            }
        }

        return !(livingentity instanceof PlayerEntity) || !((PlayerEntity)livingentity).abilities.invulnerable;
    }

    protected double getFollowRange() {
        EntityAttributeInstance entityattributeinstance = this.mob.getAttribute(EntityAttributes.FOLLOW_RANGE);
        return entityattributeinstance == null ? 16.0 : entityattributeinstance.get();
    }

    @Override
    public void start() {
        this.canNavigateFlag = 0;
        this.checkCanNavigateCooldown = 0;
        this.timeWithoutVisibility = 0;
    }

    @Override
    public void stop() {
        this.mob.setAttackTarget(null);
    }

    public static boolean isTarget(MobEntity entity, LivingEntity target, boolean isPlayer, boolean checkVisibility) {
        if (target == null) {
            return false;
        }

        if (target == entity) {
            return false;
        }

        if (!target.isAlive()) {
            return false;
        }

        if (!entity.canAttack((Class<? extends LivingEntity>)target.getClass())) {
            return false;
        }

        AbstractTeam abstractteam = entity.getScoreboardTeam();
        AbstractTeam abstractteam1 = target.getScoreboardTeam();
        if (abstractteam != null && abstractteam1 == abstractteam) {
            return false;
        }

        if (entity instanceof Tameable && StringUtils.isNotEmpty(((Tameable)entity).getOwnerName())) {
            if (target instanceof Tameable && ((Tameable)entity).getOwnerName().equals(((Tameable)target).getOwnerName())) {
                return false;
            }

            if (target == ((Tameable)entity).getOwner()) {
                return false;
            }
        } else if (target instanceof PlayerEntity && !isPlayer && ((PlayerEntity)target).abilities.invulnerable) {
            return false;
        }

        return !checkVisibility || entity.getMobVisibilityCache().canSee(target);
    }

    protected boolean canTarget(LivingEntity targetEntity, boolean isPlayer) {
        if (!isTarget(this.mob, targetEntity, isPlayer, this.checkVisibility)) {
            return false;
        }

        if (!this.mob.isValidGoalTarget(new BlockPos(targetEntity))) {
            return false;
        }

        if (this.checkCanNavigate) {
            if (--this.checkCanNavigateCooldown <= 0) {
                this.canNavigateFlag = 0;
            }

            if (this.canNavigateFlag == 0) {
                this.canNavigateFlag = this.canNavigateToTarget(targetEntity) ? 1 : 2;
            }

            if (this.canNavigateFlag == 2) {
                return false;
            }
        }

        return true;
    }

    private boolean canNavigateToTarget(LivingEntity targetEntity) {
        this.checkCanNavigateCooldown = 10 + this.mob.getRandom().nextInt(5);
        Path path = this.mob.getNavigation().findPath(targetEntity);
        if (path == null) {
            return false;
        }

        PathNode pathnode = path.getTarget();
        if (pathnode == null) {
            return false;
        }

        int i = pathnode.x - MathHelper.floor(targetEntity.x);
        int j = pathnode.z - MathHelper.floor(targetEntity.z);
        return i * i + j * j <= 2.25;
    }
}
