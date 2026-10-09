package net.minecraft.entity.ai.goal;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;

public class LookAtEntityGoal extends Goal {
    protected MobEntity mob;
    protected Entity targetEntity;
    protected float range;
    private int lookTime;
    private float chance;
    protected Class<? extends Entity> tagetType;

    public LookAtEntityGoal(MobEntity mob, Class<? extends Entity> targetType, float range) {
        this.mob = mob;
        this.tagetType = targetType;
        this.range = range;
        this.chance = 0.02F;
        this.setControls(2);
    }

    public LookAtEntityGoal(MobEntity mob, Class<? extends Entity> targetType, float range, float chance) {
        this.mob = mob;
        this.tagetType = targetType;
        this.range = range;
        this.chance = chance;
        this.setControls(2);
    }

    @Override
    public boolean canStart() {
        if (this.mob.getRandom().nextFloat() >= this.chance) {
            return false;
        }

        if (this.mob.getAttackTarget() != null) {
            this.targetEntity = this.mob.getAttackTarget();
        }

        if (this.tagetType == PlayerEntity.class) {
            this.targetEntity = this.mob.world.getNearestPlayer(this.mob, this.range);
        } else {
            this.targetEntity = this.mob.world.getNearestEntity(this.tagetType, this.mob.getShape().grown(this.range, 3.0, this.range), this.mob);
        }

        return this.targetEntity != null;
    }

    @Override
    public boolean shouldContinue() {
        return this.targetEntity.isAlive() && !(this.mob.squaredDistanceTo(this.targetEntity) > this.range * this.range) && this.lookTime > 0;
    }

    @Override
    public void start() {
        this.lookTime = 40 + this.mob.getRandom().nextInt(40);
    }

    @Override
    public void stop() {
        this.targetEntity = null;
    }

    @Override
    public void tick() {
        this.mob
            .getLookControl()
            .lookAt(this.targetEntity.x, this.targetEntity.y + this.targetEntity.getEyeHeight(), this.targetEntity.z, 10.0F, this.mob.getLookPitchSpeed());
        this.lookTime--;
    }
}
