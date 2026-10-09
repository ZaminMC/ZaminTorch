package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class AirBlock extends Block {
    protected AirBlock() {
        super(Material.AIR);
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean canRayTrace(BlockState state, boolean allowLiquids) {
        return false;
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
    }

    @Override
    public boolean canBeReplaced(World world, BlockPos pos) {
        return true;
    }
}
