package net.minecraft.block;

import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.block.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RailBlock extends AbstractRailBlock {
    public static final EnumProperty<AbstractRailBlock.Shape> SHAPE = EnumProperty.of("shape", AbstractRailBlock.Shape.class);

    protected RailBlock() {
        super(false);
        this.setDefaultState(this.stateDefinition.any().set(SHAPE, AbstractRailBlock.Shape.NORTH_SOUTH));
    }

    @Override
    protected void updateState(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (neighborBlock.isSignalSource() && new AbstractRailBlock.RailNode(world, pos, state).countConnections() == 3) {
            this.updateShape(world, pos, state, false);
        }
    }

    @Override
    public Property<AbstractRailBlock.Shape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(SHAPE, AbstractRailBlock.Shape.byId(metadata));
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(SHAPE).getId();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, SHAPE);
    }
}
