package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.AbstractLeavesBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LogBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class TreeFeature extends AbstractTreeFeature {
    private static final BlockState DEFAULT_LOG = Blocks.LOG.defaultState().set(LogBlock.VARIANT, PlanksBlock.Variant.OAK);
    private static final BlockState DEFAULT_LEAVES = Blocks.LEAVES
        .defaultState()
        .set(LeavesBlock.VARIANT, PlanksBlock.Variant.OAK)
        .set(AbstractLeavesBlock.CHECK_DECAY, false);
    private final int baseHeight;
    private final boolean placeVines;
    private final BlockState log;
    private final BlockState leaves;

    public TreeFeature(boolean bl) {
        this(bl, 4, DEFAULT_LOG, DEFAULT_LEAVES, false);
    }

    public TreeFeature(boolean notifyNeighbors, int baseHeight, BlockState log, BlockState leaves, boolean placeVines) {
        super(notifyNeighbors);
        this.baseHeight = baseHeight;
        this.log = log;
        this.leaves = leaves;
        this.placeVines = placeVines;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        int i = random.nextInt(3) + this.baseHeight;
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
                int k2 = 3;
                int l2 = 0;

                for (int i3 = pos.getY() - k2 + i; i3 <= pos.getY() + i; i3++) {
                    int i4 = i3 - (pos.getY() + i);
                    int j1 = l2 + 1 - i4 / 2;

                    for (int k1 = pos.getX() - j1; k1 <= pos.getX() + j1; k1++) {
                        int l1 = k1 - pos.getX();

                        for (int i2 = pos.getZ() - j1; i2 <= pos.getZ() + j1; i2++) {
                            int j2 = i2 - pos.getZ();
                            if (Math.abs(l1) != j1 || Math.abs(j2) != j1 || random.nextInt(2) != 0 && i4 != 0) {
                                BlockPos blockpos = new BlockPos(k1, i3, i2);
                                Block block = world.getBlockState(blockpos).getBlock();
                                if (block.getMaterial() == Material.AIR
                                    || block.getMaterial() == Material.LEAVES
                                    || block.getMaterial() == Material.REPLACEABLE_PLANT) {
                                    this.setBlockState(world, blockpos, this.leaves);
                                }
                            }
                        }
                    }
                }

                for (int j3 = 0; j3 < i; j3++) {
                    Block block2 = world.getBlockState(pos.up(j3)).getBlock();
                    if (block2.getMaterial() == Material.AIR || block2.getMaterial() == Material.LEAVES || block2.getMaterial() == Material.REPLACEABLE_PLANT) {
                        this.setBlockState(world, pos.up(j3), this.log);
                        if (this.placeVines && j3 > 0) {
                            if (random.nextInt(3) > 0 && world.isAir(pos.add(-1, j3, 0))) {
                                this.placeVine(world, pos.add(-1, j3, 0), VineBlock.EAST);
                            }

                            if (random.nextInt(3) > 0 && world.isAir(pos.add(1, j3, 0))) {
                                this.placeVine(world, pos.add(1, j3, 0), VineBlock.WEST);
                            }

                            if (random.nextInt(3) > 0 && world.isAir(pos.add(0, j3, -1))) {
                                this.placeVine(world, pos.add(0, j3, -1), VineBlock.SOUTH);
                            }

                            if (random.nextInt(3) > 0 && world.isAir(pos.add(0, j3, 1))) {
                                this.placeVine(world, pos.add(0, j3, 1), VineBlock.NORTH);
                            }
                        }
                    }
                }

                if (this.placeVines) {
                    for (int k3 = pos.getY() - 3 + i; k3 <= pos.getY() + i; k3++) {
                        int j4 = k3 - (pos.getY() + i);
                        int k4 = 2 - j4 / 2;
                        BlockPos.Mutable blockpos$mutable1 = new BlockPos.Mutable();

                        for (int l4 = pos.getX() - k4; l4 <= pos.getX() + k4; l4++) {
                            for (int i5 = pos.getZ() - k4; i5 <= pos.getZ() + k4; i5++) {
                                blockpos$mutable1.set(l4, k3, i5);
                                if (world.getBlockState(blockpos$mutable1).getBlock().getMaterial() == Material.LEAVES) {
                                    BlockPos blockpos2 = blockpos$mutable1.west();
                                    BlockPos blockpos3 = blockpos$mutable1.east();
                                    BlockPos blockpos4 = blockpos$mutable1.north();
                                    BlockPos blockpos1 = blockpos$mutable1.south();
                                    if (random.nextInt(4) == 0 && world.getBlockState(blockpos2).getBlock().getMaterial() == Material.AIR) {
                                        this.placeVines(world, blockpos2, VineBlock.EAST);
                                    }

                                    if (random.nextInt(4) == 0 && world.getBlockState(blockpos3).getBlock().getMaterial() == Material.AIR) {
                                        this.placeVines(world, blockpos3, VineBlock.WEST);
                                    }

                                    if (random.nextInt(4) == 0 && world.getBlockState(blockpos4).getBlock().getMaterial() == Material.AIR) {
                                        this.placeVines(world, blockpos4, VineBlock.SOUTH);
                                    }

                                    if (random.nextInt(4) == 0 && world.getBlockState(blockpos1).getBlock().getMaterial() == Material.AIR) {
                                        this.placeVines(world, blockpos1, VineBlock.NORTH);
                                    }
                                }
                            }
                        }
                    }

                    if (random.nextInt(5) == 0 && i > 5) {
                        for (int l3 = 0; l3 < 2; l3++) {
                            for (Direction direction : Direction.Plane.HORIZONTAL) {
                                if (random.nextInt(4 - l3) == 0) {
                                    Direction direction1 = direction.getOpposite();
                                    this.placeCocoa(world, random.nextInt(3), pos.add(direction1.getOffsetX(), i - 5 + l3, direction1.getOffsetZ()), direction);
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

    private void placeCocoa(World world, int age, BlockPos pos, Direction facing) {
        this.setBlockState(world, pos, Blocks.COCOA.defaultState().set(CocoaBlock.AGE, age).set(CocoaBlock.FACING, facing));
    }

    private void placeVine(World world, BlockPos pos, BooleanProperty face) {
        this.setBlockState(world, pos, Blocks.VINE.defaultState().set(face, true));
    }

    private void placeVines(World world, BlockPos pos, BooleanProperty face) {
        this.placeVine(world, pos, face);
        int i = 4;

        for (BlockPos blockpos = pos.down(); world.getBlockState(blockpos).getBlock().getMaterial() == Material.AIR && i > 0; i--) {
            this.placeVine(world, blockpos, face);
            blockpos = blockpos.down();
        }
    }
}
