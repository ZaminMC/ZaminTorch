package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.TameableEntity;

public class AttackWithOwnerGoal extends TrackTargetGoal {
    TameableEntity pet;
    LivingEntity targetEntity;
    private int lasAttackTime;

    public AttackWithOwnerGoal(TameableEntity pet) {
        super(pet, false);
        this.pet = pet;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        if (!this.pet.isTamed()) {
            return false;
        }

        LivingEntity livingentity = this.pet.getOwner();
        if (livingentity == null) {
            return false;
        }

        this.targetEntity = livingentity.getAttacker();
        int i = livingentity.getLastAttackedTime();
        return i != this.lasAttackTime && this.canTarget(this.targetEntity, false) && this.pet.shouldAttack(this.targetEntity, livingentity);
    }

    @Override
    public void start() {
        this.mob.setAttackTarget(this.targetEntity);
        LivingEntity livingentity = this.pet.getOwner();
        if (livingentity != null) {
            this.lasAttackTime = livingentity.getLastAttackedTime();
        }

        super.start();
    }
}
