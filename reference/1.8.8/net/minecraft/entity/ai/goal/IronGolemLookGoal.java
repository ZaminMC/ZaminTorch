package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;

public class IronGolemLookGoal extends Goal {
    private IronGolemEntity golem;
    private VillagerEntity villager;
    private int lookCountdown;

    public IronGolemLookGoal(IronGolemEntity golem) {
        this.golem = golem;
        this.setControls(3);
    }

    @Override
    public boolean canStart() {
        if (!this.golem.world.isSunny()) {
            return false;
        }

        if (this.golem.getRandom().nextInt(8000) != 0) {
            return false;
        }

        this.villager = this.golem.world.getNearestEntity(VillagerEntity.class, this.golem.getShape().grown(6.0, 2.0, 6.0), this.golem);
        return this.villager != null;
    }

    @Override
    public boolean shouldContinue() {
        return this.lookCountdown > 0;
    }

    @Override
    public void start() {
        this.lookCountdown = 400;
        this.golem.setLookingAtVillager(true);
    }

    @Override
    public void stop() {
        this.golem.setLookingAtVillager(false);
        this.villager = null;
    }

    @Override
    public void tick() {
        this.golem.getLookControl().setLookatValues(this.villager, 30.0F, 30.0F);
        this.lookCountdown--;
    }
}
