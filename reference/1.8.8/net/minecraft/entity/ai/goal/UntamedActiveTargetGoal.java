package net.minecraft.entity.ai.goal;

import com.google.common.base.Predicate;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.TameableEntity;

public class UntamedActiveTargetGoal<T extends LivingEntity> extends ActiveTargetGoal {
    private TameableEntity pet;

    public UntamedActiveTargetGoal(TameableEntity pet, Class<T> targetType, boolean chanceToStartGoal, Predicate<? super T> targetFilter) {
        super(pet, targetType, 10, chanceToStartGoal, false, targetFilter);
        this.pet = pet;
    }

    @Override
    public boolean canStart() {
        return !this.pet.isTamed() && super.canStart();
    }
}
