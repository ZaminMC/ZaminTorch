package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.VineBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class GiantJungleTreeFeature extends GiantTreeFeature {
    public GiantJungleTreeFeature(boolean bl, int i, int j, BlockState blockState, BlockState blockState2) {
        super(bl, i, j, blockState, blockState2);
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        int i = this.getRandomHeight(random);
        if (!this.canGrow(world, random, pos, i)) {
            return false;
        }

        this.placeLeaves(world, pos.up(i), 2);

        for (int j = pos.getY() + i - 2 - random.nextInt(4); j > pos.getY() + i / 2; j -= 2 + random.nextInt(4)) {
            float f = random.nextFloat() * (float) Math.PI * 2.0F;
            int k = pos.getX() + (int)(0.5F + MathHelper.cos(f) * 4.0F);
            int l = pos.getZ() + (int)(0.5F + MathHelper.sin(f) * 4.0F);

            for (int i1 = 0; i1 < 5; i1++) {
                k = pos.getX() + (int)(1.5F + MathHelper.cos(f) * i1);
                l = pos.getZ() + (int)(1.5F + MathHelper.sin(f) * i1);
                this.setBlockState(world, new BlockPos(k, j - 3 + i1 / 2, l), this.log);
            }

            int j2 = 1 + random.nextInt(2);
            int j1 = j;

            for (int k1 = j1 - j2; k1 <= j1; k1++) {
                int l1 = k1 - j1;
                this.placeLeavesRing(world, new BlockPos(k, k1, l), 1 - l1);
            }
        }

        for (int i2 = 0; i2 < i; i2++) {
            BlockPos blockpos = pos.up(i2);
            if (this.canReplace(world.getBlockState(blockpos).getBlock())) {
                this.setBlockState(world, blockpos, this.log);
                if (i2 > 0) {
                    this.placeVine(world, random, blockpos.west(), VineBlock.EAST);
                    this.placeVine(world, random, blockpos.north(), VineBlock.SOUTH);
                }
            }

            if (i2 < i - 1) {
                BlockPos blockpos1 = blockpos.east();
                if (this.canReplace(world.getBlockState(blockpos1).getBlock())) {
                    this.setBlockState(world, blockpos1, this.log);
                    if (i2 > 0) {
                        this.placeVine(world, random, blockpos1.east(), VineBlock.WEST);
                        this.placeVine(world, random, blockpos1.north(), VineBlock.SOUTH);
                    }
                }

                BlockPos blockpos2 = blockpos.south().east();
                if (this.canReplace(world.getBlockState(blockpos2).getBlock())) {
                    this.setBlockState(world, blockpos2, this.log);
                    if (i2 > 0) {
                        this.placeVine(world, random, blockpos2.east(), VineBlock.WEST);
                        this.placeVine(world, random, blockpos2.south(), VineBlock.NORTH);
                    }
                }

                BlockPos blockpos3 = blockpos.south();
                if (this.canReplace(world.getBlockState(blockpos3).getBlock())) {
                    this.setBlockState(world, blockpos3, this.log);
                    if (i2 > 0) {
                        this.placeVine(world, random, blockpos3.west(), VineBlock.EAST);
                        this.placeVine(world, random, blockpos3.south(), VineBlock.NORTH);
                    }
                }
            }
        }

        return true;
    }

    private void placeVine(World world, Random random, BlockPos pos, BooleanProperty face) {
        if (random.nextInt(3) > 0 && world.isAir(pos)) {
            this.setBlockState(world, pos, Blocks.VINE.defaultState().set(face, true));
        }
    }

    private void placeLeaves(World world, BlockPos pos, int radius) {
        int i = 2;

        for (int j = -i; j <= 0; j++) {
            this.placeLeavesRingStrict(world, pos.up(j), radius + 1 - j);
        }
    }
}
