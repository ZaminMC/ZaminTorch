package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

public class TransparentBlock extends Block {
    private boolean culling;

    protected TransparentBlock(Material material, boolean culling) {
        this(material, culling, material.getColor());
    }

    protected TransparentBlock(Material material, boolean sideVisible, MapColor mapColor) {
        super(material, mapColor);
        this.culling = sideVisible;
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        if (this == Blocks.GLASS || this == Blocks.STAINED_GLASS) {
            if (world.getBlockState(pos.offset(face.getOpposite())) != blockstate) {
                return true;
            }

            if (block == this) {
                return false;
            }
        }

        return (this.culling || block != this) && super.shouldRenderFace(world, pos, face);
    }
}
