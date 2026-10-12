package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CactusFeature extends Feature {
    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        for (int i = 0; i < 10; i++) {
            BlockPos blockpos = pos.add(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4), random.nextInt(8) - random.nextInt(8));
            if (world.isAir(blockpos)) {
                int j = 1 + random.nextInt(random.nextInt(3) + 1);

                for (int k = 0; k < j; k++) {
                    if (Blocks.CACTUS.canSurvive(world, blockpos)) {
                        world.setBlockState(blockpos.up(k), Blocks.CACTUS.defaultState(), 2);
                    }
                }
            }
        }

        return true;
    }
}
