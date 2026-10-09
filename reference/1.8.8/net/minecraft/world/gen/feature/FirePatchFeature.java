package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FirePatchFeature extends Feature {
    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        for (int i = 0; i < 64; i++) {
            BlockPos blockpos = pos.add(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4), random.nextInt(8) - random.nextInt(8));
            if (world.isAir(blockpos) && world.getBlockState(blockpos.down()).getBlock() == Blocks.NETHERRACK) {
                world.setBlockState(blockpos, Blocks.FIRE.defaultState(), 2);
            }
        }

        return true;
    }
}
