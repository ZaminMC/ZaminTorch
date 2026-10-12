package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.PumpkinBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class PumpkinPatchFeature extends Feature {
    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        for (int i = 0; i < 64; i++) {
            BlockPos blockpos = pos.add(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4), random.nextInt(8) - random.nextInt(8));
            if (world.isAir(blockpos) && world.getBlockState(blockpos.down()).getBlock() == Blocks.GRASS && Blocks.PUMPKIN.canBePlaced(world, blockpos)) {
                world.setBlockState(blockpos, Blocks.PUMPKIN.defaultState().set(PumpkinBlock.FACING, Direction.Plane.HORIZONTAL.pick(random)), 2);
            }
        }

        return true;
    }
}
