package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.TameableEntity;

public class SitGoal extends Goal {
    private TameableEntity pet;
    private boolean enabledWithOwner;

    public SitGoal(TameableEntity pet) {
        this.pet = pet;
        this.setControls(5);
    }

    @Override
    public boolean canStart() {
        if (!this.pet.isTamed()) {
            return false;
        }

        if (this.pet.isInWater()) {
            return false;
        }

        if (!this.pet.onGround) {
            return false;
        }

        LivingEntity livingentity = this.pet.getOwner();
        return livingentity == null || (!(this.pet.squaredDistanceTo(livingentity) < 144.0) || livingentity.getAttacker() == null) && this.enabledWithOwner;
    }

    @Override
    public void start() {
        this.pet.getNavigation().stop();
        this.pet.setSitting(true);
    }

    @Override
    public void stop() {
        this.pet.setSitting(false);
    }

    public void setEnabledWithOwner(boolean enabledWithOwner) {
        this.enabledWithOwner = enabledWithOwner;
    }
}
