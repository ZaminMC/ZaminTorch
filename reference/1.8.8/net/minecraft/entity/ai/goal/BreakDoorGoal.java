package net.minecraft.entity.ai.goal;

import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.world.Difficulty;

public class BreakDoorGoal extends AbstractDoorInteractGoal {
    private int breakProgress;
    private int previousBreakProgress = -1;

    public BreakDoorGoal(MobEntity mobEntity) {
        super(mobEntity);
    }

    @Override
    public boolean canStart() {
        return super.canStart() && this.mob.world.getGameRules().getBoolean("mobGriefing") && !DoorBlock.isOpen(this.mob.world, this.doorPos);
    }

    @Override
    public void start() {
        super.start();
        this.breakProgress = 0;
    }

    @Override
    public boolean shouldContinue() {
        double d0 = this.mob.getSquaredDistanceTo(this.doorPos);
        return this.breakProgress <= 240 && !DoorBlock.isOpen(this.mob.world, this.doorPos) && d0 < 4.0;
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.world.updateBlockMiningProgress(this.mob.getNetworkId(), this.doorPos, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.mob.getRandom().nextInt(20) == 0) {
            this.mob.world.doEvent(1010, this.doorPos, 0);
        }

        this.breakProgress++;
        int i = (int)(this.breakProgress / 240.0F * 10.0F);
        if (i != this.previousBreakProgress) {
            this.mob.world.updateBlockMiningProgress(this.mob.getNetworkId(), this.doorPos, i);
            this.previousBreakProgress = i;
        }

        if (this.breakProgress == 240 && this.mob.world.getDifficulty() == Difficulty.HARD) {
            this.mob.world.removeBlock(this.doorPos);
            this.mob.world.doEvent(1012, this.doorPos, 0);
            this.mob.world.doEvent(2001, this.doorPos, Block.getId(this.doorBlock));
        }
    }
}
