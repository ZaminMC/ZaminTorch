package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.mob.MobEntity;

public class LongDoorInteractGoal extends AbstractDoorInteractGoal {
    boolean shouldCloseDoor;
    int ticksLeft;

    public LongDoorInteractGoal(MobEntity mob, boolean shouldCloseDoor) {
        super(mob);
        this.mob = mob;
        this.shouldCloseDoor = shouldCloseDoor;
    }

    @Override
    public boolean shouldContinue() {
        return this.shouldCloseDoor && this.ticksLeft > 0 && super.shouldContinue();
    }

    @Override
    public void start() {
        this.ticksLeft = 20;
        this.doorBlock.updateOpenState(this.mob.world, this.doorPos, true);
    }

    @Override
    public void stop() {
        if (this.shouldCloseDoor) {
            this.doorBlock.updateOpenState(this.mob.world, this.doorPos, false);
        }
    }

    @Override
    public void tick() {
        this.ticksLeft--;
        super.tick();
    }
}
