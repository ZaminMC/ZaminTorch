package net.minecraft.block;

import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class StandingSignBlock extends SignBlock {
    public static final IntegerProperty ROTATION = IntegerProperty.of("rotation", 0, 15);

    public StandingSignBlock() {
        this.setDefaultState(this.stateDefinition.any().set(ROTATION, 0));
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!world.getBlockState(pos.down()).getBlock().getMaterial().isSolid()) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
        }

        super.neighborChanged(world, pos, state, neighborBlock);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(ROTATION, metadata);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(ROTATION);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, ROTATION);
    }
}
