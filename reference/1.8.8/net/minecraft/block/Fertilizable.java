package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface Fertilizable {
    boolean canGrow(World world, BlockPos pos, BlockState state, boolean isClient);

    boolean canBeFertilized(World world, Random rand, BlockPos pos, BlockState state);

    void grow(World world, Random rand, BlockPos pos, BlockState state);
}
