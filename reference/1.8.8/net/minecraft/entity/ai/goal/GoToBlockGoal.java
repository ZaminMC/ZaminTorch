package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class GoToBlockGoal extends Goal {
    private final PathFinderMobEntity entity;
    private final double speed;
    protected int cooldown;
    private int activeTicks;
    private int maxActiveTicks;
    protected BlockPos target = BlockPos.ORIGIN;
    private boolean reachedTarget;
    private int range;

    public GoToBlockGoal(PathFinderMobEntity entity, double speed, int range) {
        this.entity = entity;
        this.speed = speed;
        this.range = range;
        this.setControls(5);
    }

    @Override
    public boolean canStart() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        } else {
            this.cooldown = 200 + this.entity.getRandom().nextInt(200);
            return this.findNearestBlock();
        }
    }

    @Override
    public boolean shouldContinue() {
        return this.activeTicks >= -this.maxActiveTicks && this.activeTicks <= 1200 && this.isValidTarget(this.entity.world, this.target);
    }

    @Override
    public void start() {
        this.entity.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY() + 1, this.target.getZ() + 0.5, this.speed);
        this.activeTicks = 0;
        this.maxActiveTicks = this.entity.getRandom().nextInt(this.entity.getRandom().nextInt(1200) + 1200) + 1200;
    }

    @Override
    public void stop() {
    }

    @Override
    public void tick() {
        if (this.entity.getSquaredDistanceToCenter(this.target.up()) > 1.0) {
            this.reachedTarget = false;
            this.activeTicks++;
            if (this.activeTicks % 40 == 0) {
                this.entity.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY() + 1, this.target.getZ() + 0.5, this.speed);
            }
        } else {
            this.reachedTarget = true;
            this.activeTicks--;
        }
    }

    protected boolean hasReachedTarget() {
        return this.reachedTarget;
    }

    private boolean findNearestBlock() {
        int i = this.range;
        int j = 1;
        BlockPos blockpos = new BlockPos(this.entity);

        for (int k = 0; k <= 1; k = k > 0 ? -k : 1 - k) {
            for (int l = 0; l < i; l++) {
                for (int i1 = 0; i1 <= l; i1 = i1 > 0 ? -i1 : 1 - i1) {
                    for (int j1 = i1 < l && i1 > -l ? l : 0; j1 <= l; j1 = j1 > 0 ? -j1 : 1 - j1) {
                        BlockPos blockpos1 = blockpos.add(i1, k - 1, j1);
                        if (this.entity.isValidGoalTarget(blockpos1) && this.isValidTarget(this.entity.world, blockpos1)) {
                            this.target = blockpos1;
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    protected abstract boolean isValidTarget(World world, BlockPos pos);
}
