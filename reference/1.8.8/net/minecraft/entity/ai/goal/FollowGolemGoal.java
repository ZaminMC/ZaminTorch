package net.minecraft.entity.ai.goal;

import java.util.List;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;

public class FollowGolemGoal extends Goal {
    private VillagerEntity villager;
    private IronGolemEntity golem;
    private int golemLookingTicks;
    private boolean isFollowingGolem;

    public FollowGolemGoal(VillagerEntity villager) {
        this.villager = villager;
        this.setControls(3);
    }

    @Override
    public boolean canStart() {
        if (this.villager.getBreedingAge() >= 0) {
            return false;
        }

        if (!this.villager.world.isSunny()) {
            return false;
        }

        List<IronGolemEntity> list = this.villager.world.getEntitiesOfType(IronGolemEntity.class, this.villager.getShape().grown(6.0, 2.0, 6.0));
        if (list.isEmpty()) {
            return false;
        }

        for (IronGolemEntity irongolementity : list) {
            if (irongolementity.getLookingAtVillagerTicks() > 0) {
                this.golem = irongolementity;
                break;
            }
        }

        return this.golem != null;
    }

    @Override
    public boolean shouldContinue() {
        return this.golem.getLookingAtVillagerTicks() > 0;
    }

    @Override
    public void start() {
        this.golemLookingTicks = this.villager.getRandom().nextInt(320);
        this.isFollowingGolem = false;
        this.golem.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.golem = null;
        this.villager.getNavigation().stop();
    }

    @Override
    public void tick() {
        this.villager.getLookControl().setLookatValues(this.golem, 30.0F, 30.0F);
        if (this.golem.getLookingAtVillagerTicks() == this.golemLookingTicks) {
            this.villager.getNavigation().moveTo(this.golem, 0.5);
            this.isFollowingGolem = true;
        }

        if (this.isFollowingGolem && this.villager.squaredDistanceTo(this.golem) < 4.0) {
            this.golem.setLookingAtVillager(false);
            this.villager.getNavigation().stop();
        }
    }
}
