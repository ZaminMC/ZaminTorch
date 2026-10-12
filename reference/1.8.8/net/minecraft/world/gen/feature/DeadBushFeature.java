package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class DeadBushFeature extends Feature {
    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        Block block;
        while (((block = world.getBlockState(pos).getBlock()).getMaterial() == Material.AIR || block.getMaterial() == Material.LEAVES) && pos.getY() > 0) {
            pos = pos.down();
        }

        for (int i = 0; i < 4; i++) {
            BlockPos blockpos = pos.add(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4), random.nextInt(8) - random.nextInt(8));
            if (world.isAir(blockpos) && Blocks.DEADBUSH.canSurvive(world, blockpos, Blocks.DEADBUSH.defaultState())) {
                world.setBlockState(blockpos, Blocks.DEADBUSH.defaultState(), 2);
            }
        }

        return true;
    }
}
