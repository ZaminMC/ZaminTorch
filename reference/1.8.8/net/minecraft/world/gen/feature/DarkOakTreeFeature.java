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

public class DarkOakTreeFeature extends AbstractTreeFeature {
    private static final BlockState LOG = Blocks.LOG2.defaultState().set(Log2Block.VARIANT, PlanksBlock.Variant.DARK_OAK);
    private static final BlockState LEAVES = Blocks.LEAVES2
        .defaultState()
        .set(Leaves2Block.VARIANT, PlanksBlock.Variant.DARK_OAK)
        .set(AbstractLeavesBlock.CHECK_DECAY, false);

    public DarkOakTreeFeature(boolean bl) {
        super(bl);
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        int i = random.nextInt(3) + random.nextInt(2) + 6;
        int j = pos.getX();
        int k = pos.getY();
        int l = pos.getZ();
        if (k >= 1 && k + i + 1 < 256) {
            BlockPos blockpos = pos.down();
            Block block = world.getBlockState(blockpos).getBlock();
            if (block != Blocks.GRASS && block != Blocks.DIRT) {
                return false;
            }

            if (!this.isValidHeight(world, pos, i)) {
                return false;
            }

            this.placeDirt(world, blockpos);
            this.placeDirt(world, blockpos.east());
            this.placeDirt(world, blockpos.south());
            this.placeDirt(world, blockpos.south().east());
            Direction direction = Direction.Plane.HORIZONTAL.pick(random);
            int i1 = i - random.nextInt(4);
            int j1 = 2 - random.nextInt(3);
            int k1 = j;
            int l1 = l;
            int i2 = k + i - 1;

            for (int j2 = 0; j2 < i; j2++) {
                if (j2 >= i1 && j1 > 0) {
                    k1 += direction.getOffsetX();
                    l1 += direction.getOffsetZ();
                    j1--;
                }

                int k2 = k + j2;
                BlockPos blockpos1 = new BlockPos(k1, k2, l1);
                Material material = world.getBlockState(blockpos1).getBlock().getMaterial();
                if (material == Material.AIR || material == Material.LEAVES) {
                    this.placeLog(world, blockpos1);
                    this.placeLog(world, blockpos1.east());
                    this.placeLog(world, blockpos1.south());
                    this.placeLog(world, blockpos1.east().south());
                }
            }

            for (int i3 = -2; i3 <= 0; i3++) {
                for (int l3 = -2; l3 <= 0; l3++) {
                    int k4 = -1;
                    this.placeLeaves(world, k1 + i3, i2 + k4, l1 + l3);
                    this.placeLeaves(world, 1 + k1 - i3, i2 + k4, l1 + l3);
                    this.placeLeaves(world, k1 + i3, i2 + k4, 1 + l1 - l3);
                    this.placeLeaves(world, 1 + k1 - i3, i2 + k4, 1 + l1 - l3);
                    if ((i3 > -2 || l3 > -1) && (i3 != -1 || l3 != -2)) {
                        int b0 = 1;
                        this.placeLeaves(world, k1 + i3, i2 + b0, l1 + l3);
                        this.placeLeaves(world, 1 + k1 - i3, i2 + b0, l1 + l3);
                        this.placeLeaves(world, k1 + i3, i2 + b0, 1 + l1 - l3);
                        this.placeLeaves(world, 1 + k1 - i3, i2 + b0, 1 + l1 - l3);
                    }
                }
            }

            if (random.nextBoolean()) {
                this.placeLeaves(world, k1, i2 + 2, l1);
                this.placeLeaves(world, k1 + 1, i2 + 2, l1);
                this.placeLeaves(world, k1 + 1, i2 + 2, l1 + 1);
                this.placeLeaves(world, k1, i2 + 2, l1 + 1);
            }

            for (int j3 = -3; j3 <= 4; j3++) {
                for (int i4 = -3; i4 <= 4; i4++) {
                    if ((j3 != -3 || i4 != -3)
                        && (j3 != -3 || i4 != 4)
                        && (j3 != 4 || i4 != -3)
                        && (j3 != 4 || i4 != 4)
                        && (Math.abs(j3) < 3 || Math.abs(i4) < 3)) {
                        this.placeLeaves(world, k1 + j3, i2, l1 + i4);
                    }
                }
            }

            for (int k3 = -1; k3 <= 2; k3++) {
                for (int j4 = -1; j4 <= 2; j4++) {
                    if ((k3 < 0 || k3 > 1 || j4 < 0 || j4 > 1) && random.nextInt(3) <= 0) {
                        int l4 = random.nextInt(3) + 2;

                        for (int i5 = 0; i5 < l4; i5++) {
                            this.placeLog(world, new BlockPos(j + k3, i2 - i5 - 1, l + j4));
                        }

                        for (int j5 = -1; j5 <= 1; j5++) {
                            for (int l2 = -1; l2 <= 1; l2++) {
                                this.placeLeaves(world, k1 + k3 + j5, i2, l1 + j4 + l2);
                            }
                        }

                        for (int k5 = -2; k5 <= 2; k5++) {
                            for (int l5 = -2; l5 <= 2; l5++) {
                                if (Math.abs(k5) != 2 || Math.abs(l5) != 2) {
                                    this.placeLeaves(world, k1 + k3 + k5, i2 - 1, l1 + j4 + l5);
                                }
                            }
                        }
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean isValidHeight(World world, BlockPos pos, int height) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int l = 0; l <= height + 1; l++) {
            int i1 = 1;
            if (l == 0) {
                i1 = 0;
            }

            if (l >= height - 1) {
                i1 = 2;
            }

            for (int j1 = -i1; j1 <= i1; j1++) {
                for (int k1 = -i1; k1 <= i1; k1++) {
                    if (!this.canReplace(world.getBlockState(blockpos$mutable.set(i + j1, j + l, k + k1)).getBlock())) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void placeLog(World world, BlockPos pos) {
        if (this.canReplace(world.getBlockState(pos).getBlock())) {
            this.setBlockState(world, pos, LOG);
        }
    }

    private void placeLeaves(World world, int x, int y, int z) {
        BlockPos blockpos = new BlockPos(x, y, z);
        Block block = world.getBlockState(blockpos).getBlock();
        if (block.getMaterial() == Material.AIR) {
            this.setBlockState(world, blockpos, LEAVES);
        }
    }
}
