package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SugarcaneFeature extends Feature {
    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        for (int i = 0; i < 20; i++) {
            BlockPos blockpos = pos.add(random.nextInt(4) - random.nextInt(4), 0, random.nextInt(4) - random.nextInt(4));
            if (world.isAir(blockpos)) {
                BlockPos blockpos1 = blockpos.down();
                if (world.getBlockState(blockpos1.west()).getBlock().getMaterial() == Material.WATER
                    || world.getBlockState(blockpos1.east()).getBlock().getMaterial() == Material.WATER
                    || world.getBlockState(blockpos1.north()).getBlock().getMaterial() == Material.WATER
                    || world.getBlockState(blockpos1.south()).getBlock().getMaterial() == Material.WATER) {
                    int j = 2 + random.nextInt(random.nextInt(3) + 1);

                    for (int k = 0; k < j; k++) {
                        if (Blocks.REEDS.canSurvive(world, blockpos)) {
                            world.setBlockState(blockpos.up(k), Blocks.REEDS.defaultState(), 2);
                        }
                    }
                }
            }
        }

        return true;
    }
}
