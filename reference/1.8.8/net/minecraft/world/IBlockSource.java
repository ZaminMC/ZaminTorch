package net.minecraft.world;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public interface IBlockSource extends WorldPosition {
    @Override
    double getX();

    @Override
    double getY();

    @Override
    double getZ();

    BlockPos getPos();

    int getBlockMetadata();

    <T extends BlockEntity> T getBlockEntity();
}
