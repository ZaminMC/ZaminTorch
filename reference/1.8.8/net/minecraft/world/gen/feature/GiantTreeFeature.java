package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class GiantTreeFeature extends AbstractTreeFeature {
    protected final int baseHeight;
    protected final BlockState log;
    protected final BlockState leaves;
    protected int bonusHeight;

    public GiantTreeFeature(boolean notifyNeighbors, int baseHeight, int bonusHeight, BlockState log, BlockState leaves) {
        super(notifyNeighbors);
        this.baseHeight = baseHeight;
        this.bonusHeight = bonusHeight;
        this.log = log;
        this.leaves = leaves;
    }

    protected int getRandomHeight(Random random) {
        int i = random.nextInt(3) + this.baseHeight;
        if (this.bonusHeight > 1) {
            i += random.nextInt(this.bonusHeight);
        }

        return i;
    }

    private boolean canGrow(World world, BlockPos pos, int height) {
        boolean flag = true;
        if (pos.getY() >= 1 && pos.getY() + height + 1 <= 256) {
            for (int i = 0; i <= 1 + height; i++) {
                int j = 2;
                if (i == 0) {
                    j = 1;
                } else if (i >= 1 + height - 2) {
                    j = 2;
                }

                for (int k = -j; k <= j && flag; k++) {
                    for (int l = -j; l <= j && flag; l++) {
                        if (pos.getY() + i < 0 || pos.getY() + i >= 256 || !this.canReplace(world.getBlockState(pos.add(k, i, l)).getBlock())) {
                            flag = false;
                        }
                    }
                }
            }

            return flag;
        } else {
            return false;
        }
    }

    private boolean checkAndPlaceDirtUnderneath(BlockPos pos, World world) {
        BlockPos blockpos = pos.down();
        Block block = world.getBlockState(blockpos).getBlock();
        if ((block == Blocks.GRASS || block == Blocks.DIRT) && pos.getY() >= 2) {
            this.placeDirt(world, blockpos);
            this.placeDirt(world, blockpos.east());
            this.placeDirt(world, blockpos.south());
            this.placeDirt(world, blockpos.south().east());
            return true;
        } else {
            return false;
        }
    }

    protected boolean canGrow(World world, Random random, BlockPos pos, int height) {
        return this.canGrow(world, pos, height) && this.checkAndPlaceDirtUnderneath(pos, world);
    }

    protected void placeLeavesRingStrict(World world, BlockPos pos, int radius) {
        int i = radius * radius;

        for (int j = -radius; j <= radius + 1; j++) {
            for (int k = -radius; k <= radius + 1; k++) {
                int l = j - 1;
                int i1 = k - 1;
                if (j * j + k * k <= i || l * l + i1 * i1 <= i || j * j + i1 * i1 <= i || l * l + k * k <= i) {
                    BlockPos blockpos = pos.add(j, 0, k);
                    Material material = world.getBlockState(blockpos).getBlock().getMaterial();
                    if (material == Material.AIR || material == Material.LEAVES) {
                        this.setBlockState(world, blockpos, this.leaves);
                    }
                }
            }
        }
    }

    protected void placeLeavesRing(World world, BlockPos pos, int radius) {
        int i = radius * radius;

        for (int j = -radius; j <= radius; j++) {
            for (int k = -radius; k <= radius; k++) {
                if (j * j + k * k <= i) {
                    BlockPos blockpos = pos.add(j, 0, k);
                    Material material = world.getBlockState(blockpos).getBlock().getMaterial();
                    if (material == Material.AIR || material == Material.LEAVES) {
                        this.setBlockState(world, blockpos, this.leaves);
                    }
                }
            }
        }
    }
}
