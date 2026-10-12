package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class LilyPadFeature extends Feature {
    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        for (int i = 0; i < 10; i++) {
            int j = pos.getX() + random.nextInt(8) - random.nextInt(8);
            int k = pos.getY() + random.nextInt(4) - random.nextInt(4);
            int l = pos.getZ() + random.nextInt(8) - random.nextInt(8);
            if (world.isAir(new BlockPos(j, k, l)) && Blocks.LILY_PAD.canBePlaced(world, new BlockPos(j, k, l))) {
                world.setBlockState(new BlockPos(j, k, l), Blocks.LILY_PAD.defaultState(), 2);
            }
        }

        return true;
    }
}
