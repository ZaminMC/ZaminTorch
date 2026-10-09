package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class LiquidPocketFeature extends Feature {
    private final Block liquid;
    private final boolean canBeExposedToAir;

    public LiquidPocketFeature(Block liquid, boolean canBeExposedToAir) {
        this.liquid = liquid;
        this.canBeExposedToAir = canBeExposedToAir;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        if (world.getBlockState(pos.up()).getBlock() != Blocks.NETHERRACK) {
            return false;
        }

        if (world.getBlockState(pos).getBlock().getMaterial() != Material.AIR && world.getBlockState(pos).getBlock() != Blocks.NETHERRACK) {
            return false;
        }

        int i = 0;
        if (world.getBlockState(pos.west()).getBlock() == Blocks.NETHERRACK) {
            i++;
        }

        if (world.getBlockState(pos.east()).getBlock() == Blocks.NETHERRACK) {
            i++;
        }

        if (world.getBlockState(pos.north()).getBlock() == Blocks.NETHERRACK) {
            i++;
        }

        if (world.getBlockState(pos.south()).getBlock() == Blocks.NETHERRACK) {
            i++;
        }

        if (world.getBlockState(pos.down()).getBlock() == Blocks.NETHERRACK) {
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

        if (world.isAir(pos.down())) {
            j++;
        }

        if (!this.canBeExposedToAir && i == 4 && j == 1 || i == 5) {
            world.setBlockState(pos, this.liquid.defaultState(), 2);
            world.tickBlockNow(this.liquid, pos, random);
        }

        return true;
    }
}
