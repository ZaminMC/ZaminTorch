package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockVeinFeature extends Feature {
    private final Block block;
    private final int size;

    public BlockVeinFeature(Block block, int size) {
        super(false);
        this.block = block;
        this.size = size;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        while (pos.getY() > 3) {
            if (!world.isAir(pos.down())) {
                Block block = world.getBlockState(pos.down()).getBlock();
                if (block == Blocks.GRASS || block == Blocks.DIRT || block == Blocks.STONE) {
                    break;
                }
            }

            pos = pos.down();
        }

        if (pos.getY() <= 3) {
            return false;
        }

        int i1 = this.size;

        for (int i = 0; i1 >= 0 && i < 3; i++) {
            int j = i1 + random.nextInt(2);
            int k = i1 + random.nextInt(2);
            int l = i1 + random.nextInt(2);
            float f = (j + k + l) * 0.333F + 0.5F;

            for (BlockPos blockpos : BlockPos.iterateRegion(pos.add(-j, -k, -l), pos.add(j, k, l))) {
                if (blockpos.squaredDistanceTo(pos) <= f * f) {
                    world.setBlockState(blockpos, this.block.defaultState(), 4);
                }
            }

            pos = pos.add(-(i1 + 1) + random.nextInt(2 + i1 * 2), 0 - random.nextInt(2), -(i1 + 1) + random.nextInt(2 + i1 * 2));
        }

        return true;
    }
}
