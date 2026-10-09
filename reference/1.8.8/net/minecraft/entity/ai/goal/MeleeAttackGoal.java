package net.minecraft.entity.ai.goal;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MeleeAttackGoal extends Goal {
    World world;
    protected PathFinderMobEntity entity;
    int attackCooldown;
    double speed;
    boolean pauseWhenMobIdle;
    Path path;
    Class<? extends Entity> targetType;
    private int updateCountdownTicks;
    private double targetX;
    private double targetY;
    private double targetZ;

    public MeleeAttackGoal(PathFinderMobEntity entity, Class<? extends Entity> targetType, double speed, boolean pauseWhenMobIdle) {
        this(entity, speed, pauseWhenMobIdle);
        this.targetType = targetType;
    }

    public MeleeAttackGoal(PathFinderMobEntity entity, double speed, boolean pauseWhenMobIdle) {
        this.entity = entity;
        this.world = entity.world;
        this.speed = speed;
        this.pauseWhenMobIdle = pauseWhenMobIdle;
        this.setControls(3);
    }

    @Override
    public boolean canStart() {
        LivingEntity livingentity = this.entity.getAttackTarget();
        if (livingentity == null) {
            return false;
        }

        if (!livingentity.isAlive()) {
            return false;
        }

        if (this.targetType != null && !this.targetType.isAssignableFrom(livingentity.getClass())) {
            return false;
        }

        this.path = this.entity.getNavigation().findPath(livingentity);
        return this.path != null;
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity livingentity = this.entity.getAttackTarget();
        if (livingentity == null) {
            return false;
        } else if (!livingentity.isAlive()) {
            return false;
        } else {
            return !this.pauseWhenMobIdle ? !this.entity.getNavigation().isDone() : this.entity.isValidGoalTarget(new BlockPos(livingentity));
        }
    }

    @Override
    public void start() {
        this.entity.getNavigation().moveAlong(this.path, this.speed);
        this.updateCountdownTicks = 0;
    }

    @Override
    public void stop() {
        this.entity.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity livingentity = this.entity.getAttackTarget();
        this.entity.getLookControl().setLookatValues(livingentity, 30.0F, 30.0F);
        double d0 = this.entity.squaredDistanceTo(livingentity.x, livingentity.getShape().minY, livingentity.z);
        double d1 = this.getReach(livingentity);
        this.updateCountdownTicks--;
        if ((this.pauseWhenMobIdle || this.entity.getMobVisibilityCache().canSee(livingentity))
            && this.updateCountdownTicks <= 0
            && (
                this.targetX == 0.0 && this.targetY == 0.0 && this.targetZ == 0.0
                    || livingentity.squaredDistanceTo(this.targetX, this.targetY, this.targetZ) >= 1.0
                    || this.entity.getRandom().nextFloat() < 0.05F
            )) {
            this.targetX = livingentity.x;
            this.targetY = livingentity.getShape().minY;
            this.targetZ = livingentity.z;
            this.updateCountdownTicks = 4 + this.entity.getRandom().nextInt(7);
            if (d0 > 1024.0) {
                this.updateCountdownTicks += 10;
            } else if (d0 > 256.0) {
                this.updateCountdownTicks += 5;
            }

            if (!this.entity.getNavigation().moveTo(livingentity, this.speed)) {
                this.updateCountdownTicks += 15;
            }
        }

        this.attackCooldown = Math.max(this.attackCooldown - 1, 0);
        if (d0 <= d1 && this.attackCooldown <= 0) {
            this.attackCooldown = 20;
            if (this.entity.getDisplayItemInHand() != null) {
                this.entity.swingArm();
            }

            this.entity.tryDamage(livingentity);
        }
    }

    protected double getReach(LivingEntity target) {
        return this.entity.width * 2.0F * (this.entity.width * 2.0F) + target.width;
    }
}
