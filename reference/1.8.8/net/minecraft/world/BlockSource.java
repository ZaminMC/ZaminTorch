package net.minecraft.world;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;

public class BlockSource implements IBlockSource {
    private final World world;
    private final BlockPos pos;

    public BlockSource(World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
    }

    @Override
    public World getWorld() {
        return this.world;
    }

    @Override
    public double getX() {
        return this.pos.getX() + 0.5;
    }

    @Override
    public double getY() {
        return this.pos.getY() + 0.5;
    }

    @Override
    public double getZ() {
        return this.pos.getZ() + 0.5;
    }

    @Override
    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public int getBlockMetadata() {
        BlockState blockstate = this.world.getBlockState(this.pos);
        return blockstate.getBlock().getMetadataFromState(blockstate);
    }

    @Override
    public <T extends BlockEntity> T getBlockEntity() {
        return (T)this.world.getBlockEntity(this.pos);
    }
}
