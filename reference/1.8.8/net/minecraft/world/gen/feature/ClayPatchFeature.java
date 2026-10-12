package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ClayPatchFeature extends Feature {
    private Block clay = Blocks.CLAY;
    private int size;

    public ClayPatchFeature(int size) {
        this.size = size;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        if (world.getBlockState(pos).getBlock().getMaterial() != Material.WATER) {
            return false;
        }

        int i = random.nextInt(this.size - 2) + 2;
        int j = 1;

        for (int k = pos.getX() - i; k <= pos.getX() + i; k++) {
            for (int l = pos.getZ() - i; l <= pos.getZ() + i; l++) {
                int i1 = k - pos.getX();
                int j1 = l - pos.getZ();
                if (i1 * i1 + j1 * j1 <= i * i) {
                    for (int k1 = pos.getY() - j; k1 <= pos.getY() + j; k1++) {
                        BlockPos blockpos = new BlockPos(k, k1, l);
                        Block block = world.getBlockState(blockpos).getBlock();
                        if (block == Blocks.DIRT || block == Blocks.CLAY) {
                            world.setBlockState(blockpos, this.clay.defaultState(), 2);
                        }
                    }
                }
            }
        }

        return true;
    }
}
