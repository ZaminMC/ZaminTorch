package net.minecraft.block;

import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class WallSignBlock extends SignBlock {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", Direction.Plane.HORIZONTAL);

    public WallSignBlock() {
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH));
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        Direction direction = world.getBlockState(pos).get(FACING);
        float f = 0.28125F;
        float f1 = 0.78125F;
        float f2 = 0.0F;
        float f3 = 1.0F;
        float f4 = 0.125F;
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        switch (direction) {
            case NORTH:
                this.setShape(f2, f, 1.0F - f4, f3, f1, 1.0F);
                break;
            case SOUTH:
                this.setShape(f2, f, 0.0F, f3, f1, f4);
                break;
            case WEST:
                this.setShape(1.0F - f4, f, f2, 1.0F, f1, f3);
                break;
            case EAST:
                this.setShape(0.0F, f, f2, f4, f1, f3);
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        Direction direction = state.get(FACING);
        if (!world.getBlockState(pos.offset(direction.getOpposite())).getBlock().getMaterial().isSolid()) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
        }

        super.neighborChanged(world, pos, state, neighborBlock);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        Direction direction = Direction.byId(metadata);
        if (direction.getAxis() == Direction.Axis.Y) {
            direction = Direction.NORTH;
        }

        return this.defaultState().set(FACING, direction);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(FACING).getId();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING);
    }
}
