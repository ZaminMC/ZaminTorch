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

public class SpruceTreeFeature extends AbstractTreeFeature {
    private static final BlockState LOG = Blocks.LOG.defaultState().set(LogBlock.VARIANT, PlanksBlock.Variant.SPRUCE);
    private static final BlockState LEAVES = Blocks.LEAVES
        .defaultState()
        .set(LeavesBlock.VARIANT, PlanksBlock.Variant.SPRUCE)
        .set(AbstractLeavesBlock.CHECK_DECAY, false);

    public SpruceTreeFeature(boolean bl) {
        super(bl);
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        int i = random.nextInt(4) + 6;
        int j = 1 + random.nextInt(2);
        int k = i - j;
        int l = 2 + random.nextInt(2);
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
                        if (i1 >= 0 && i1 < 256) {
                            Block block = world.getBlockState(blockpos$mutable.set(k1, i1, l1)).getBlock();
                            if (block.getMaterial() != Material.AIR && block.getMaterial() != Material.LEAVES) {
                                flag = false;
                            }
                        } else {
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
                int i3 = random.nextInt(2);
                int j3 = 1;
                int k3 = 0;

                for (int l3 = 0; l3 <= k; l3++) {
                    int j4 = pos.getY() + i - l3;

                    for (int i2 = pos.getX() - i3; i2 <= pos.getX() + i3; i2++) {
                        int j2 = i2 - pos.getX();

                        for (int k2 = pos.getZ() - i3; k2 <= pos.getZ() + i3; k2++) {
                            int l2 = k2 - pos.getZ();
                            if (Math.abs(j2) != i3 || Math.abs(l2) != i3 || i3 <= 0) {
                                BlockPos blockpos = new BlockPos(i2, j4, k2);
                                if (!world.getBlockState(blockpos).getBlock().isOpaque()) {
                                    this.setBlockState(world, blockpos, LEAVES);
                                }
                            }
                        }
                    }

                    if (i3 >= j3) {
                        i3 = k3;
                        k3 = 1;
                        if (++j3 > l) {
                            j3 = l;
                        }
                    } else {
                        i3++;
                    }
                }

                int i4 = random.nextInt(3);

                for (int k4 = 0; k4 < i - i4; k4++) {
                    Block block2 = world.getBlockState(pos.up(k4)).getBlock();
                    if (block2.getMaterial() == Material.AIR || block2.getMaterial() == Material.LEAVES) {
                        this.setBlockState(world, pos.up(k4), LOG);
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
