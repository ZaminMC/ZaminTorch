package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class LiquidFallFeature extends Feature {
    private Block liquid;

    public LiquidFallFeature(Block liquid) {
        this.liquid = liquid;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        if (world.getBlockState(pos.up()).getBlock() != Blocks.STONE) {
            return false;
        }

        if (world.getBlockState(pos.down()).getBlock() != Blocks.STONE) {
            return false;
        }

        if (world.getBlockState(pos).getBlock().getMaterial() != Material.AIR && world.getBlockState(pos).getBlock() != Blocks.STONE) {
            return false;
        }

        int i = 0;
        if (world.getBlockState(pos.west()).getBlock() == Blocks.STONE) {
            i++;
        }

        if (world.getBlockState(pos.east()).getBlock() == Blocks.STONE) {
            i++;
        }

        if (world.getBlockState(pos.north()).getBlock() == Blocks.STONE) {
            i++;
        }

        if (world.getBlockState(pos.south()).getBlock() == Blocks.STONE) {
            i++;
        }

        int j = 0;
        if (world.isAir(pos.west())) {
            j++;
        }

        if (world.isAir(pos.east())) {
            j++;
        }

        if (world.isAir(pos.north())) {
            j++;
        }

        if (world.isAir(pos.south())) {
            j++;
        }

        if (i == 3 && j == 1) {
            world.setBlockState(pos, this.liquid.defaultState(), 2);
            world.tickBlockNow(this.liquid, pos, random);
        }

        return true;
    }
}
