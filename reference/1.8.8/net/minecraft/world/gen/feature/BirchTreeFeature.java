package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LogBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BirchTreeFeature extends AbstractTreeFeature {
    private static final BlockState LOG = Blocks.LOG.defaultState().set(LogBlock.VARIANT, PlanksBlock.Variant.BIRCH);
    private static final BlockState LEAVES = Blocks.LEAVES
        .defaultState()
        .set(LeavesBlock.VARIANT, PlanksBlock.Variant.BIRCH)
        .set(LeavesBlock.CHECK_DECAY, false);
    private boolean tall;

    public BirchTreeFeature(boolean notifyNeighbors, boolean tall) {
        super(notifyNeighbors);
        this.tall = tall;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        int i = random.nextInt(3) + 5;
        if (this.tall) {
            i += random.nextInt(7);
        }

        boolean flag = true;
        if (pos.getY() >= 1 && pos.getY() + i + 1 <= 256) {
            for (int j = pos.getY(); j <= pos.getY() + 1 + i; j++) {
                int k = 1;
                if (j == pos.getY()) {
                    k = 0;
                }

                if (j >= pos.getY() + 1 + i - 2) {
                    k = 2;
                }

                BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

                for (int l = pos.getX() - k; l <= pos.getX() + k && flag; l++) {
                    for (int i1 = pos.getZ() - k; i1 <= pos.getZ() + k && flag; i1++) {
                        if (j < 0 || j >= 256) {
                            flag = false;
                        } else if (!this.canReplace(world.getBlockState(blockpos$mutable.set(l, j, i1)).getBlock())) {
                            flag = false;
                        }
                    }
                }
            }

            if (!flag) {
                return false;
            }

            Block block1 = world.getBlockState(pos.down()).getBlock();
            if ((block1 == Blocks.GRASS || block1 == Blocks.DIRT || block1 == Blocks.FARMLAND) && pos.getY() < 256 - i - 1) {
                this.placeDirt(world, pos.down());

                for (int i2 = pos.getY() - 3 + i; i2 <= pos.getY() + i; i2++) {
                    int k2 = i2 - (pos.getY() + i);
                    int l2 = 1 - k2 / 2;

                    for (int i3 = pos.getX() - l2; i3 <= pos.getX() + l2; i3++) {
                        int j1 = i3 - pos.getX();

                        for (int k1 = pos.getZ() - l2; k1 <= pos.getZ() + l2; k1++) {
                            int l1 = k1 - pos.getZ();
                            if (Math.abs(j1) != l2 || Math.abs(l1) != l2 || random.nextInt(2) != 0 && k2 != 0) {
                                BlockPos blockpos = new BlockPos(i3, i2, k1);
                                Block block = world.getBlockState(blockpos).getBlock();
                                if (block.getMaterial() == Material.AIR || block.getMaterial() == Material.LEAVES) {
                                    this.setBlockState(world, blockpos, LEAVES);
                                }
                            }
                        }
                    }
                }

                for (int j2 = 0; j2 < i; j2++) {
                    Block block2 = world.getBlockState(pos.up(j2)).getBlock();
                    if (block2.getMaterial() == Material.AIR || block2.getMaterial() == Material.LEAVES) {
                        this.setBlockState(world, pos.up(j2), LOG);
                    }
                }

                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
