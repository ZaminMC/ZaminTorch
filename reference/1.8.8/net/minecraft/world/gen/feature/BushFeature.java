package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BushFeature extends TreeFeature {
    private final BlockState leaves;
    private final BlockState log;

    public BushFeature(BlockState log, BlockState leaves) {
        super(false);
        this.log = log;
        this.leaves = leaves;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        Block block;
        while (((block = world.getBlockState(pos).getBlock()).getMaterial() == Material.AIR || block.getMaterial() == Material.LEAVES) && pos.getY() > 0) {
            pos = pos.down();
        }

        Block block1 = world.getBlockState(pos).getBlock();
        if (block1 == Blocks.DIRT || block1 == Blocks.GRASS) {
            pos = pos.up();
            this.setBlockState(world, pos, this.log);

            for (int i = pos.getY(); i <= pos.getY() + 2; i++) {
                int j = i - pos.getY();
                int k = 2 - j;

                for (int l = pos.getX() - k; l <= pos.getX() + k; l++) {
                    int i1 = l - pos.getX();

                    for (int j1 = pos.getZ() - k; j1 <= pos.getZ() + k; j1++) {
                        int k1 = j1 - pos.getZ();
                        if (Math.abs(i1) != k || Math.abs(k1) != k || random.nextInt(2) != 0) {
                            BlockPos blockpos = new BlockPos(l, i, j1);
                            if (!world.getBlockState(blockpos).getBlock().isOpaque()) {
                                this.setBlockState(world, blockpos, this.leaves);
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}
