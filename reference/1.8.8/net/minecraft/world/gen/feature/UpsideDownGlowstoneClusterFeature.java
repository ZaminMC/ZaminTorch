package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class UpsideDownGlowstoneClusterFeature extends Feature {
    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        if (!world.isAir(pos)) {
            return false;
        }

        if (world.getBlockState(pos.up()).getBlock() != Blocks.NETHERRACK) {
            return false;
        }

        world.setBlockState(pos, Blocks.GLOWSTONE.defaultState(), 2);

        for (int i = 0; i < 1500; i++) {
            BlockPos blockpos = pos.add(random.nextInt(8) - random.nextInt(8), -random.nextInt(12), random.nextInt(8) - random.nextInt(8));
            if (world.getBlockState(blockpos).getBlock().getMaterial() == Material.AIR) {
                int j = 0;

                for (Direction direction : Direction.values()) {
                    if (world.getBlockState(blockpos.offset(direction)).getBlock() == Blocks.GLOWSTONE) {
                        j++;
                    }

                    if (j > 1) {
                        break;
                    }
                }

                if (j == 1) {
                    world.setBlockState(blockpos, Blocks.GLOWSTONE.defaultState(), 2);
                }
            }
        }

        return true;
    }
}
