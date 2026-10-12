package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MelonPatchFeature extends Feature {
    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        for (int i = 0; i < 64; i++) {
            BlockPos blockpos = pos.add(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4), random.nextInt(8) - random.nextInt(8));
            if (Blocks.MELON_BLOCK.canBePlaced(world, blockpos) && world.getBlockState(blockpos.down()).getBlock() == Blocks.GRASS) {
                world.setBlockState(blockpos, Blocks.MELON_BLOCK.defaultState(), 2);
            }
        }

        return true;
    }
}
