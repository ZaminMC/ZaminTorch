package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class AbstractTreeFeature extends Feature {
    public AbstractTreeFeature(boolean bl) {
        super(bl);
    }

    protected boolean canReplace(Block block) {
        Material material = block.getMaterial();
        return material == Material.AIR
            || material == Material.LEAVES
            || block == Blocks.GRASS
            || block == Blocks.DIRT
            || block == Blocks.LOG
            || block == Blocks.LOG2
            || block == Blocks.SAPLING
            || block == Blocks.VINE;
    }

    public void placeSoil(World world, Random random, BlockPos pos) {
    }

    protected void placeDirt(World world, BlockPos pos) {
        if (world.getBlockState(pos).getBlock() != Blocks.DIRT) {
            this.setBlockState(world, pos, Blocks.DIRT.defaultState());
        }
    }
}
