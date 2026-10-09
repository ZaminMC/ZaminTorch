package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.player.PlayerEntity;

public class LookAtCustomerGoal extends LookAtEntityGoal {
    private final VillagerEntity villager;

    public LookAtCustomerGoal(VillagerEntity villager) {
        super(villager, PlayerEntity.class, 8.0F);
        this.villager = villager;
    }

    @Override
    public boolean canStart() {
        if (this.villager.hasCustomer()) {
            this.targetEntity = this.villager.getCustomer();
            return true;
        } else {
            return false;
        }
    }
}
