package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.TameableEntity;

public class OwnerHurtGoal extends TrackTargetGoal {
    TameableEntity pet;
    LivingEntity target;
    private int ownerLastAttackedTime;

    public OwnerHurtGoal(TameableEntity pet) {
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

        this.target = livingentity.getLastAttackedMob();
        int i = livingentity.getLastAttackTime();
        return i != this.ownerLastAttackedTime && this.canTarget(this.target, false) && this.pet.shouldAttack(this.target, livingentity);
    }

    @Override
    public void start() {
        this.mob.setAttackTarget(this.target);
        LivingEntity livingentity = this.pet.getOwner();
        if (livingentity != null) {
            this.ownerLastAttackedTime = livingentity.getLastAttackTime();
        }

        super.start();
    }
}
