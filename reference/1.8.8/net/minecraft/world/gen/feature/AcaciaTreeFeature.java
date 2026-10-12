package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.AbstractLeavesBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.Leaves2Block;
import net.minecraft.block.Log2Block;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class AcaciaTreeFeature extends AbstractTreeFeature {
    private static final BlockState LOG = Blocks.LOG2.defaultState().set(Log2Block.VARIANT, PlanksBlock.Variant.ACACIA);
    private static final BlockState LEAVES = Blocks.LEAVES2
        .defaultState()
        .set(Leaves2Block.VARIANT, PlanksBlock.Variant.ACACIA)
        .set(AbstractLeavesBlock.CHECK_DECAY, false);

    public AcaciaTreeFeature(boolean bl) {
        super(bl);
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        int i = random.nextInt(3) + random.nextInt(3) + 5;
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

            Block block = world.getBlockState(pos.down()).getBlock();
            if ((block == Blocks.GRASS || block == Blocks.DIRT) && pos.getY() < 256 - i - 1) {
                this.placeDirt(world, pos.down());
                Direction direction = Direction.Plane.HORIZONTAL.pick(random);
                int k2 = i - random.nextInt(4) - 1;
                int l2 = 3 - random.nextInt(3);
                int i3 = pos.getX();
                int j1 = pos.getZ();
                int k1 = 0;

                for (int l1 = 0; l1 < i; l1++) {
                    int i2 = pos.getY() + l1;
                    if (l1 >= k2 && l2 > 0) {
                        i3 += direction.getOffsetX();
                        j1 += direction.getOffsetZ();
                        l2--;
                    }

                    BlockPos blockpos = new BlockPos(i3, i2, j1);
                    Material material = world.getBlockState(blockpos).getBlock().getMaterial();
                    if (material == Material.AIR || material == Material.LEAVES) {
                        this.placeLog(world, blockpos);
                        k1 = i2;
                    }
                }

                BlockPos blockpos2 = new BlockPos(i3, k1, j1);

                for (int j3 = -3; j3 <= 3; j3++) {
                    for (int i4 = -3; i4 <= 3; i4++) {
                        if (Math.abs(j3) != 3 || Math.abs(i4) != 3) {
                            this.placeLeaves(world, blockpos2.add(j3, 0, i4));
                        }
                    }
                }

                blockpos2 = blockpos2.up();

                for (int k3 = -1; k3 <= 1; k3++) {
                    for (int j4 = -1; j4 <= 1; j4++) {
                        this.placeLeaves(world, blockpos2.add(k3, 0, j4));
                    }
                }

                this.placeLeaves(world, blockpos2.east(2));
                this.placeLeaves(world, blockpos2.west(2));
                this.placeLeaves(world, blockpos2.south(2));
                this.placeLeaves(world, blockpos2.north(2));
                i3 = pos.getX();
                j1 = pos.getZ();
                Direction direction1 = Direction.Plane.HORIZONTAL.pick(random);
                if (direction1 != direction) {
                    int l3 = k2 - random.nextInt(2) - 1;
                    int k4 = 1 + random.nextInt(3);
                    k1 = 0;

                    for (int l4 = l3; l4 < i && k4 > 0; k4--) {
                        if (l4 >= 1) {
                            int j2 = pos.getY() + l4;
                            i3 += direction1.getOffsetX();
                            j1 += direction1.getOffsetZ();
                            BlockPos blockpos1 = new BlockPos(i3, j2, j1);
                            Material material1 = world.getBlockState(blockpos1).getBlock().getMaterial();
                            if (material1 == Material.AIR || material1 == Material.LEAVES) {
                                this.placeLog(world, blockpos1);
                                k1 = j2;
                            }
                        }

                        l4++;
                    }

                    if (k1 > 0) {
                        BlockPos blockpos3 = new BlockPos(i3, k1, j1);

                        for (int i5 = -2; i5 <= 2; i5++) {
                            for (int k5 = -2; k5 <= 2; k5++) {
                                if (Math.abs(i5) != 2 || Math.abs(k5) != 2) {
                                    this.placeLeaves(world, blockpos3.add(i5, 0, k5));
                                }
                            }
                        }

                        blockpos3 = blockpos3.up();

                        for (int j5 = -1; j5 <= 1; j5++) {
                            for (int l5 = -1; l5 <= 1; l5++) {
                                this.placeLeaves(world, blockpos3.add(j5, 0, l5));
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

    private void placeLog(World world, BlockPos pos) {
        this.setBlockState(world, pos, LOG);
    }

    private void placeLeaves(World world, BlockPos pos) {
        Material material = world.getBlockState(pos).getBlock().getMaterial();
        if (material == Material.AIR || material == Material.LEAVES) {
            this.setBlockState(world, pos, LEAVES);
        }
    }
}
