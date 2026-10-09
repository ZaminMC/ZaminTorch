package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.AbstractLeavesBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LogBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PineTreeFeature extends AbstractTreeFeature {
    private static final BlockState LOG = Blocks.LOG.defaultState().set(LogBlock.VARIANT, PlanksBlock.Variant.SPRUCE);
    private static final BlockState LEAVES = Blocks.LEAVES
        .defaultState()
        .set(LeavesBlock.VARIANT, PlanksBlock.Variant.SPRUCE)
        .set(AbstractLeavesBlock.CHECK_DECAY, false);

    public PineTreeFeature() {
        super(false);
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        int i = random.nextInt(5) + 7;
        int j = i - random.nextInt(2) - 3;
        int k = i - j;
        int l = 1 + random.nextInt(k + 1);
        boolean flag = true;
        if (pos.getY() >= 1 && pos.getY() + i + 1 <= 256) {
            for (int i1 = pos.getY(); i1 <= pos.getY() + 1 + i && flag; i1++) {
                int j1 = 1;
                if (i1 - pos.getY() < j) {
                    j1 = 0;
                } else {
                    j1 = l;
                }

                BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

                for (int k1 = pos.getX() - j1; k1 <= pos.getX() + j1 && flag; k1++) {
                    for (int l1 = pos.getZ() - j1; l1 <= pos.getZ() + j1 && flag; l1++) {
                        if (i1 < 0 || i1 >= 256) {
                            flag = false;
                        } else if (!this.canReplace(world.getBlockState(blockpos$mutable.set(k1, i1, l1)).getBlock())) {
                            flag = false;
                        }
                    }
                }
            }

            if (!flag) {
                return false;
            }

            Block block = world.getBlockState(pos.down()).getBlock();
            if ((block == Blocks.GRASS || block == Blocks.DIRT) && pos.getY() < 256 - i - 1) {
                this.placeDirt(world, pos.down());
                int k2 = 0;

                for (int l2 = pos.getY() + i; l2 >= pos.getY() + j; l2--) {
                    for (int j3 = pos.getX() - k2; j3 <= pos.getX() + k2; j3++) {
                        int k3 = j3 - pos.getX();

                        for (int i2 = pos.getZ() - k2; i2 <= pos.getZ() + k2; i2++) {
                            int j2 = i2 - pos.getZ();
                            if (Math.abs(k3) != k2 || Math.abs(j2) != k2 || k2 <= 0) {
                                BlockPos blockpos = new BlockPos(j3, l2, i2);
                                if (!world.getBlockState(blockpos).getBlock().isOpaque()) {
                                    this.setBlockState(world, blockpos, LEAVES);
                                }
                            }
                        }
                    }

                    if (k2 >= 1 && l2 == pos.getY() + j + 1) {
                        k2--;
                    } else if (k2 < l) {
                        k2++;
                    }
                }

                for (int i3 = 0; i3 < i - 1; i3++) {
                    Block block1 = world.getBlockState(pos.up(i3)).getBlock();
                    if (block1.getMaterial() == Material.AIR || block1.getMaterial() == Material.LEAVES) {
                        this.setBlockState(world, pos.up(i3), LOG);
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
