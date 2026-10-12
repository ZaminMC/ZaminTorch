package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class IcePatchFeature extends Feature {
    private Block ice = Blocks.PACKED_ICE;
    private int size;

    public IcePatchFeature(int size) {
        this.size = size;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        while (world.isAir(pos) && pos.getY() > 2) {
            pos = pos.down();
        }

        if (world.getBlockState(pos).getBlock() != Blocks.SNOW) {
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
                        if (block == Blocks.DIRT || block == Blocks.SNOW || block == Blocks.ICE) {
                            world.setBlockState(blockpos, this.ice.defaultState(), 2);
                        }
                    }
                }
            }
        }

        return true;
    }
}
