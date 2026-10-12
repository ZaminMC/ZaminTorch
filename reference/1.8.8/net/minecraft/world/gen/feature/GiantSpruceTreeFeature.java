package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.AbstractLeavesBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DirtBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LogBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class GiantSpruceTreeFeature extends GiantTreeFeature {
    private static final BlockState LOG = Blocks.LOG.defaultState().set(LogBlock.VARIANT, PlanksBlock.Variant.SPRUCE);
    private static final BlockState LEAVES = Blocks.LEAVES
        .defaultState()
        .set(LeavesBlock.VARIANT, PlanksBlock.Variant.SPRUCE)
        .set(AbstractLeavesBlock.CHECK_DECAY, false);
    private static final BlockState PODZOL = Blocks.DIRT.defaultState().set(DirtBlock.VARIANT, DirtBlock.Variant.PODZOL);
    private boolean sparseLeaves;

    public GiantSpruceTreeFeature(boolean notifyNeighbors, boolean sparseLeaves) {
        super(notifyNeighbors, 13, 15, LOG, LEAVES);
        this.sparseLeaves = sparseLeaves;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        int i = this.getRandomHeight(random);
        if (!this.canGrow(world, random, pos, i)) {
            return false;
        }

        this.placeLeaves(world, pos.getX(), pos.getZ(), pos.getY() + i, 0, random);

        for (int j = 0; j < i; j++) {
            Block block = world.getBlockState(pos.up(j)).getBlock();
            if (block.getMaterial() == Material.AIR || block.getMaterial() == Material.LEAVES) {
                this.setBlockState(world, pos.up(j), this.log);
            }

            if (j < i - 1) {
                block = world.getBlockState(pos.add(1, j, 0)).getBlock();
                if (block.getMaterial() == Material.AIR || block.getMaterial() == Material.LEAVES) {
                    this.setBlockState(world, pos.add(1, j, 0), this.log);
                }

                block = world.getBlockState(pos.add(1, j, 1)).getBlock();
                if (block.getMaterial() == Material.AIR || block.getMaterial() == Material.LEAVES) {
                    this.setBlockState(world, pos.add(1, j, 1), this.log);
                }

                block = world.getBlockState(pos.add(0, j, 1)).getBlock();
                if (block.getMaterial() == Material.AIR || block.getMaterial() == Material.LEAVES) {
                    this.setBlockState(world, pos.add(0, j, 1), this.log);
                }
            }
        }

        return true;
    }

    private void placeLeaves(World world, int x, int z, int y, int baseRadius, Random random) {
        int i = random.nextInt(5) + (this.sparseLeaves ? this.baseHeight : 3);
        int j = 0;

        for (int k = y - i; k <= y; k++) {
            int l = y - k;
            int i1 = baseRadius + MathHelper.floor((float)l / i * 3.5F);
            this.placeLeavesRingStrict(world, new BlockPos(x, k, z), i1 + (l > 0 && i1 == j && (k & 1) == 0 ? 1 : 0));
            j = i1;
        }
    }

    @Override
    public void placeSoil(World world, Random random, BlockPos pos) {
        this.placePodzolPatch(world, pos.west().north());
        this.placePodzolPatch(world, pos.east(2).north());
        this.placePodzolPatch(world, pos.west().south(2));
        this.placePodzolPatch(world, pos.east(2).south(2));

        for (int i = 0; i < 5; i++) {
            int j = random.nextInt(64);
            int k = j % 8;
            int l = j / 8;
            if (k == 0 || k == 7 || l == 0 || l == 7) {
                this.placePodzolPatch(world, pos.add(-3 + k, 0, -3 + l));
            }
        }
    }

    private void placePodzolPatch(World world, BlockPos pos) {
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (Math.abs(i) != 2 || Math.abs(j) != 2) {
                    this.placePodzol(world, pos.add(i, 0, j));
                }
            }
        }
    }

    private void placePodzol(World world, BlockPos pos) {
        for (int i = 2; i >= -3; i--) {
            BlockPos blockpos = pos.up(i);
            Block block = world.getBlockState(blockpos).getBlock();
            if (block == Blocks.GRASS || block == Blocks.DIRT) {
                this.setBlockState(world, blockpos, PODZOL);
                break;
            }

            if (block.getMaterial() != Material.AIR && i < 0) {
                break;
            }
        }
    }
}
