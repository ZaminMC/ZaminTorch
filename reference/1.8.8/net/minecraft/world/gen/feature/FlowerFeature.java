package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FlowerFeature extends Feature {
    private FlowerBlock flower;
    private BlockState state;

    public FlowerFeature(FlowerBlock flower, FlowerBlock.Type type) {
        this.set(flower, type);
    }

    public void set(FlowerBlock flower, FlowerBlock.Type type) {
        this.flower = flower;
        this.state = flower.defaultState().set(flower.getTypeProperty(), type);
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        for (int i = 0; i < 64; i++) {
            BlockPos blockpos = pos.add(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4), random.nextInt(8) - random.nextInt(8));
            if (world.isAir(blockpos) && (!world.dimension.hasNoSky() || blockpos.getY() < 255) && this.flower.canSurvive(world, blockpos, this.state)) {
                world.setBlockState(blockpos, this.state, 2);
            }
        }

        return true;
    }
}
