package net.minecraft.entity.living.mob.monster;

import net.minecraft.entity.living.LivingEntity;

public interface RangedAttackMob {
    void doRangedAttack(LivingEntity target, float range);
}
