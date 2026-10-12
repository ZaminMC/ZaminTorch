package net.minecraft.entity.ai.goal;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.predicate.BlockStatePredicate;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class EatGrassGoal extends Goal {
    private static final Predicate<BlockState> IS_TALLGRASS = BlockStatePredicate.of(Blocks.TALLGRASS)
        .with(TallPlantBlock.TYPE, Predicates.equalTo(TallPlantBlock.Type.GRASS));
    private MobEntity mob;
    private World world;
    int timer;

    public EatGrassGoal(MobEntity mob) {
        this.mob = mob;
        this.world = mob.world;
        this.setControls(7);
    }

    @Override
    public boolean canStart() {
        if (this.mob.getRandom().nextInt(this.mob.isBaby() ? 50 : 1000) != 0) {
            return false;
        }

        BlockPos blockpos = new BlockPos(this.mob.x, this.mob.y, this.mob.z);
        return IS_TALLGRASS.apply(this.world.getBlockState(blockpos)) || this.world.getBlockState(blockpos.down()).getBlock() == Blocks.GRASS;
    }

    @Override
    public void start() {
        this.timer = 40;
        this.world.doEntityEvent(this.mob, (byte)10);
        this.mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.timer = 0;
    }

    @Override
    public boolean shouldContinue() {
        return this.timer > 0;
    }

    public int getTimer() {
        return this.timer;
    }

    @Override
    public void tick() {
        this.timer = Math.max(0, this.timer - 1);
        if (this.timer == 4) {
            BlockPos blockpos = new BlockPos(this.mob.x, this.mob.y, this.mob.z);
            if (IS_TALLGRASS.apply(this.world.getBlockState(blockpos))) {
                if (this.world.getGameRules().getBoolean("mobGriefing")) {
                    this.world.breakBlock(blockpos, false);
                }

                this.mob.onEatingGrass();
            } else {
                BlockPos blockpos1 = blockpos.down();
                if (this.world.getBlockState(blockpos1).getBlock() == Blocks.GRASS) {
                    if (this.world.getGameRules().getBoolean("mobGriefing")) {
                        this.world.doEvent(2001, blockpos1, Block.getId(Blocks.GRASS));
                        this.world.setBlockState(blockpos1, Blocks.DIRT.defaultState(), 2);
                    }

                    this.mob.onEatingGrass();
                }
            }
        }
    }
}
