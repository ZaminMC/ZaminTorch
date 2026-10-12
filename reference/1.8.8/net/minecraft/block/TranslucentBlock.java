package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

public class TranslucentBlock extends Block {
    protected boolean culling;

    protected TranslucentBlock(Material material, boolean culling) {
        super(material);
        this.culling = culling;
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return (this.culling || world.getBlockState(pos).getBlock() != this) && super.shouldRenderFace(world, pos, face);
    }
}
