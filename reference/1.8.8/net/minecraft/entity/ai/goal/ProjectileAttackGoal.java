package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.monster.RangedAttackMob;
import net.minecraft.util.math.MathHelper;

public class ProjectileAttackGoal extends Goal {
    private final MobEntity mob;
    private final RangedAttackMob rangedAttackMob;
    private LivingEntity target;
    private int ticksUntilNextAttack = -1;
    private double speed;
    private int seenTargetTicks;
    private int minCooldown;
    private int maxCooldown;
    private float attackRange;
    private float squaredAttackRange;

    public ProjectileAttackGoal(RangedAttackMob mob, double speed, int cooldown, float attackRange) {
        this(mob, speed, cooldown, cooldown, attackRange);
    }

    public ProjectileAttackGoal(RangedAttackMob mob, double speed, int minCooldown, int maxCooldown, float attackRange) {
        if (!(mob instanceof LivingEntity)) {
            throw new IllegalArgumentException("ArrowAttackGoal requires Mob implements RangedAttackMob");
        }

        this.rangedAttackMob = mob;
        this.mob = (MobEntity)mob;
        this.speed = speed;
        this.minCooldown = minCooldown;
        this.maxCooldown = maxCooldown;
        this.attackRange = attackRange;
        this.squaredAttackRange = attackRange * attackRange;
        this.setControls(3);
    }

    @Override
    public boolean canStart() {
        LivingEntity livingentity = this.mob.getAttackTarget();
        if (livingentity == null) {
            return false;
        }

        this.target = livingentity;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return this.canStart() || !this.mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        this.target = null;
        this.seenTargetTicks = 0;
        this.ticksUntilNextAttack = -1;
    }

    @Override
    public void tick() {
        double d0 = this.mob.squaredDistanceTo(this.target.x, this.target.getShape().minY, this.target.z);
        boolean flag = this.mob.getMobVisibilityCache().canSee(this.target);
        if (flag) {
            this.seenTargetTicks++;
        } else {
            this.seenTargetTicks = 0;
        }

        if (!(d0 > this.squaredAttackRange) && this.seenTargetTicks >= 20) {
            this.mob.getNavigation().stop();
        } else {
            this.mob.getNavigation().moveTo(this.target, this.speed);
        }

        this.mob.getLookControl().setLookatValues(this.target, 30.0F, 30.0F);
        if (--this.ticksUntilNextAttack == 0) {
            if (d0 > this.squaredAttackRange || !flag) {
                return;
            }

            float f = MathHelper.sqrt(d0) / this.attackRange;
            float f1 = f;
            f1 = MathHelper.clamp(f1, 0.1F, 1.0F);
            this.rangedAttackMob.doRangedAttack(this.target, f1);
            this.ticksUntilNextAttack = MathHelper.floor(f * (this.maxCooldown - this.minCooldown) + this.minCooldown);
        } else if (this.ticksUntilNextAttack < 0) {
            float f2 = MathHelper.sqrt(d0) / this.attackRange;
            this.ticksUntilNextAttack = MathHelper.floor(f2 * (this.maxCooldown - this.minCooldown) + this.minCooldown);
        }
    }
}
