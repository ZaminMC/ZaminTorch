package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.menu.InventoryMenu;

public class StopFollowingCustomerGoal extends Goal {
    private VillagerEntity villager;

    public StopFollowingCustomerGoal(VillagerEntity villager) {
        this.villager = villager;
        this.setControls(5);
    }

    @Override
    public boolean canStart() {
        if (!this.villager.isAlive()) {
            return false;
        }

        if (this.villager.isInWater()) {
            return false;
        }

        if (!this.villager.onGround) {
            return false;
        }

        if (this.villager.damaged) {
            return false;
        }

        PlayerEntity playerentity = this.villager.getCustomer();
        return playerentity != null && !(this.villager.squaredDistanceTo(playerentity) > 16.0) && playerentity.menu instanceof InventoryMenu;
    }

    @Override
    public void start() {
        this.villager.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.villager.setCustomer(null);
    }
}
