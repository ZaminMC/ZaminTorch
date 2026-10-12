package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

public class RedstoneBlock extends Block {
    public RedstoneBlock(Material material, MapColor mapColor) {
        super(material, mapColor);
    }

    @Override
    public boolean isSignalSource() {
        return true;
    }

    @Override
    public int getSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return 15;
    }
}
