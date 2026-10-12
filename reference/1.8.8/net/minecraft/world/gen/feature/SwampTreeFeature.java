package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LogBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SwampTreeFeature extends AbstractTreeFeature {
    private static final BlockState LOG = Blocks.LOG.defaultState().set(LogBlock.VARIANT, PlanksBlock.Variant.OAK);
    private static final BlockState LEAVES = Blocks.LEAVES.defaultState().set(LeavesBlock.VARIANT, PlanksBlock.Variant.OAK).set(LeavesBlock.CHECK_DECAY, false);

    public SwampTreeFeature() {
        super(false);
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        int i = random.nextInt(4) + 5;

        while (world.getBlockState(pos.down()).getBlock().getMaterial() == Material.WATER) {
            pos = pos.down();
        }

        boolean flag = true;
        if (pos.getY() >= 1 && pos.getY() + i + 1 <= 256) {
            for (int j = pos.getY(); j <= pos.getY() + 1 + i; j++) {
                int k = 1;
                if (j == pos.getY()) {
                    k = 0;
                }

                if (j >= pos.getY() + 1 + i - 2) {
                    k = 3;
                }

                BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

                for (int l = pos.getX() - k; l <= pos.getX() + k && flag; l++) {
                    for (int i1 = pos.getZ() - k; i1 <= pos.getZ() + k && flag; i1++) {
                        if (j >= 0 && j < 256) {
                            Block block = world.getBlockState(blockpos$mutable.set(l, j, i1)).getBlock();
                            if (block.getMaterial() != Material.AIR && block.getMaterial() != Material.LEAVES) {
                                if (block != Blocks.WATER && block != Blocks.FLOWING_WATER) {
                                    flag = false;
                                } else if (j > pos.getY()) {
                                    flag = false;
                                }
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
            if ((block1 == Blocks.GRASS || block1 == Blocks.DIRT) && pos.getY() < 256 - i - 1) {
                this.placeDirt(world, pos.down());

                for (int l1 = pos.getY() - 3 + i; l1 <= pos.getY() + i; l1++) {
                    int k2 = l1 - (pos.getY() + i);
                    int i3 = 2 - k2 / 2;

                    for (int k3 = pos.getX() - i3; k3 <= pos.getX() + i3; k3++) {
                        int l3 = k3 - pos.getX();

                        for (int j1 = pos.getZ() - i3; j1 <= pos.getZ() + i3; j1++) {
                            int k1 = j1 - pos.getZ();
                            if (Math.abs(l3) != i3 || Math.abs(k1) != i3 || random.nextInt(2) != 0 && k2 != 0) {
                                BlockPos blockpos = new BlockPos(k3, l1, j1);
                                if (!world.getBlockState(blockpos).getBlock().isOpaque()) {
                                    this.setBlockState(world, blockpos, LEAVES);
                                }
                            }
                        }
                    }
                }

                for (int i2 = 0; i2 < i; i2++) {
                    Block block2 = world.getBlockState(pos.up(i2)).getBlock();
                    if (block2.getMaterial() == Material.AIR
                        || block2.getMaterial() == Material.LEAVES
                        || block2 == Blocks.FLOWING_WATER
                        || block2 == Blocks.WATER) {
                        this.setBlockState(world, pos.up(i2), LOG);
                    }
                }

                for (int j2 = pos.getY() - 3 + i; j2 <= pos.getY() + i; j2++) {
                    int l2 = j2 - (pos.getY() + i);
                    int j3 = 2 - l2 / 2;
                    BlockPos.Mutable blockpos$mutable1 = new BlockPos.Mutable();

                    for (int i4 = pos.getX() - j3; i4 <= pos.getX() + j3; i4++) {
                        for (int j4 = pos.getZ() - j3; j4 <= pos.getZ() + j3; j4++) {
                            blockpos$mutable1.set(i4, j2, j4);
                            if (world.getBlockState(blockpos$mutable1).getBlock().getMaterial() == Material.LEAVES) {
                                BlockPos blockpos3 = blockpos$mutable1.west();
                                BlockPos blockpos4 = blockpos$mutable1.east();
                                BlockPos blockpos1 = blockpos$mutable1.north();
                                BlockPos blockpos2 = blockpos$mutable1.south();
                                if (random.nextInt(4) == 0 && world.getBlockState(blockpos3).getBlock().getMaterial() == Material.AIR) {
                                    this.placeVines(world, blockpos3, VineBlock.EAST);
                                }

                                if (random.nextInt(4) == 0 && world.getBlockState(blockpos4).getBlock().getMaterial() == Material.AIR) {
                                    this.placeVines(world, blockpos4, VineBlock.WEST);
                                }

                                if (random.nextInt(4) == 0 && world.getBlockState(blockpos1).getBlock().getMaterial() == Material.AIR) {
                                    this.placeVines(world, blockpos1, VineBlock.SOUTH);
                                }

                                if (random.nextInt(4) == 0 && world.getBlockState(blockpos2).getBlock().getMaterial() == Material.AIR) {
                                    this.placeVines(world, blockpos2, VineBlock.NORTH);
                                }
                            }
                        }
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

    private void placeVines(World world, BlockPos pos, BooleanProperty face) {
        BlockState blockstate = Blocks.VINE.defaultState().set(face, true);
        this.setBlockState(world, pos, blockstate);
        int i = 4;

        for (BlockPos blockpos = pos.down(); world.getBlockState(blockpos).getBlock().getMaterial() == Material.AIR && i > 0; i--) {
            this.setBlockState(world, blockpos, blockstate);
            blockpos = blockpos.down();
        }
    }
}
