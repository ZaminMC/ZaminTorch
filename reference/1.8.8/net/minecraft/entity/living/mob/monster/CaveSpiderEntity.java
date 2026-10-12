package net.minecraft.entity.living.mob.monster;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

public class CaveSpiderEntity extends SpiderEntity {
    public CaveSpiderEntity(World world) {
        super(world);
        this.setSize(0.7F, 0.5F);
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(12.0);
    }

    @Override
    public boolean tryDamage(Entity target) {
        if (super.tryDamage(target)) {
            if (target instanceof LivingEntity) {
                int i = 0;
                if (this.world.getDifficulty() == Difficulty.NORMAL) {
                    i = 7;
                } else if (this.world.getDifficulty() == Difficulty.HARD) {
                    i = 15;
                }

                if (i > 0) {
                    ((LivingEntity)target).addStatusEffect(new StatusEffectInstance(StatusEffect.POISON.id, i * 20, 0));
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        return data;
    }

    @Override
    public float getEyeHeight() {
        return 0.45F;
    }
}
