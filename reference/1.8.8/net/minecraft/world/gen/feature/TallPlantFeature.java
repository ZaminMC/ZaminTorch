package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TallPlantFeature extends Feature {
    private final BlockState state;

    public TallPlantFeature(TallPlantBlock.Type type) {
        this.state = Blocks.TALLGRASS.defaultState().set(TallPlantBlock.TYPE, type);
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        Block block;
        while (((block = world.getBlockState(pos).getBlock()).getMaterial() == Material.AIR || block.getMaterial() == Material.LEAVES) && pos.getY() > 0) {
            pos = pos.down();
        }

        for (int i = 0; i < 128; i++) {
            BlockPos blockpos = pos.add(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4), random.nextInt(8) - random.nextInt(8));
            if (world.isAir(blockpos) && Blocks.TALLGRASS.canSurvive(world, blockpos, this.state)) {
                world.setBlockState(blockpos, this.state, 2);
            }
        }

        return true;
    }
}
