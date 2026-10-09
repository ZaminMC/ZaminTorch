package net.minecraft.block;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityProvider;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public abstract class BlockWithBlockEntity extends Block implements BlockEntityProvider {
    protected BlockWithBlockEntity(Material material) {
        this(material, material.getColor());
    }

    protected BlockWithBlockEntity(Material material, MapColor mapColor) {
        super(material, mapColor);
        this.hasBlockEntity = true;
    }

    protected boolean isNeighboringCactus(World world, BlockPos pos, Direction dir) {
        return world.getBlockState(pos.offset(dir)).getBlock().getMaterial() == Material.CACTUS;
    }

    protected boolean isNeighboringCactus(World world, BlockPos pos) {
        return this.isNeighboringCactus(world, pos, Direction.NORTH)
            || this.isNeighboringCactus(world, pos, Direction.SOUTH)
            || this.isNeighboringCactus(world, pos, Direction.WEST)
            || this.isNeighboringCactus(world, pos, Direction.EAST);
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        super.onRemoved(world, pos, state);
        world.removeBlockEntity(pos);
    }

    @Override
    public boolean doEvent(World world, BlockPos pos, BlockState state, int type, int data) {
        super.doEvent(world, pos, state, type, data);
        BlockEntity blockentity = world.getBlockEntity(pos);
        return blockentity != null && blockentity.doEvent(type, data);
    }
}
