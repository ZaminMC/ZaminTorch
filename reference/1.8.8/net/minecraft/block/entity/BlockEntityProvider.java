package net.minecraft.block.entity;

import net.minecraft.world.World;

public interface BlockEntityProvider {
    BlockEntity createBlockEntity(World world, int metadata);
}
