package net.minecraft.world;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.WorldGeneratorType;

public interface WorldView {
    BlockEntity getBlockEntity(BlockPos pos);

    int getLightColor(BlockPos pos, int blockLight);

    BlockState getBlockState(BlockPos pos);

    boolean isAir(BlockPos pos);

    Biome getBiome(BlockPos pos);

    boolean isEmpty();

    /**
     * Returns the direct redstone signal the block at the given position is emitting in the given direction.
     * This roughly equates to what is colloquially known as 'hard' or 'strong' power.
     * 
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so this method
     * returns the signal emitted in the direction <i>opposite</i> of the one given.
     */
    int getDirectSignal(BlockPos pos, Direction dir);

    WorldGeneratorType getGeneratorType();
}
