package net.minecraft.block;

import com.google.common.base.Predicate;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.block.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PoweredRailBlock extends AbstractRailBlock {
    public static final EnumProperty<AbstractRailBlock.Shape> SHAPE = EnumProperty.of(
        "shape",
        AbstractRailBlock.Shape.class,
        new Predicate<AbstractRailBlock.Shape>() {
            public boolean apply(AbstractRailBlock.Shape shape) {
                return shape != AbstractRailBlock.Shape.NORTH_EAST
                    && shape != AbstractRailBlock.Shape.NORTH_WEST
                    && shape != AbstractRailBlock.Shape.SOUTH_EAST
                    && shape != AbstractRailBlock.Shape.SOUTH_WEST;
            }
        }
    );
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");

    protected PoweredRailBlock() {
        super(true);
        this.setDefaultState(this.stateDefinition.any().set(SHAPE, AbstractRailBlock.Shape.NORTH_SOUTH).set(POWERED, false));
    }

    protected boolean isPoweredByConnectedRails(World world, BlockPos pos, BlockState state, boolean forward, int depth) {
        if (depth >= 8) {
            return false;
        }

        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        boolean flag = true;
        AbstractRailBlock.Shape abstractrailblock$shape = state.get(SHAPE);
        switch (abstractrailblock$shape) {
            case NORTH_SOUTH:
                if (forward) {
                    k++;
                } else {
                    k--;
                }
                break;
            case EAST_WEST:
                if (forward) {
                    i--;
                } else {
                    i++;
                }
                break;
            case ASCENDING_EAST:
                if (forward) {
                    i--;
                } else {
                    i++;
                    j++;
                    flag = false;
                }

                abstractrailblock$shape = AbstractRailBlock.Shape.EAST_WEST;
                break;
            case ASCENDING_WEST:
                if (forward) {
                    i--;
                    j++;
                    flag = false;
                } else {
                    i++;
                }

                abstractrailblock$shape = AbstractRailBlock.Shape.EAST_WEST;
                break;
            case ASCENDING_NORTH:
                if (forward) {
                    k++;
                } else {
                    k--;
                    j++;
                    flag = false;
                }

                abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_SOUTH;
                break;
            case ASCENDING_SOUTH:
                if (forward) {
                    k++;
                    j++;
                    flag = false;
                } else {
                    k--;
                }

                abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_SOUTH;
        }

        return this.isPoweredByRail(world, new BlockPos(i, j, k), forward, depth, abstractrailblock$shape)
            || flag && this.isPoweredByRail(world, new BlockPos(i, j - 1, k), forward, depth, abstractrailblock$shape);
    }

    protected boolean isPoweredByRail(World world, BlockPos pos, boolean forward, int depth, AbstractRailBlock.Shape shape) {
        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() != this) {
            return false;
        }

        AbstractRailBlock.Shape abstractrailblock$shape = blockstate.get(SHAPE);
        return (
                shape != AbstractRailBlock.Shape.EAST_WEST
                    || abstractrailblock$shape != AbstractRailBlock.Shape.NORTH_SOUTH
                        && abstractrailblock$shape != AbstractRailBlock.Shape.ASCENDING_NORTH
                        && abstractrailblock$shape != AbstractRailBlock.Shape.ASCENDING_SOUTH
            )
            && (
                shape != AbstractRailBlock.Shape.NORTH_SOUTH
                    || abstractrailblock$shape != AbstractRailBlock.Shape.EAST_WEST
                        && abstractrailblock$shape != AbstractRailBlock.Shape.ASCENDING_EAST
                        && abstractrailblock$shape != AbstractRailBlock.Shape.ASCENDING_WEST
            )
            && blockstate.get(POWERED)
            && (world.hasNeighborSignal(pos) || this.isPoweredByConnectedRails(world, pos, blockstate, forward, depth + 1));
    }

    @Override
    protected void updateState(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        boolean flag = state.get(POWERED);
        boolean flag1 = world.hasNeighborSignal(pos)
            || this.isPoweredByConnectedRails(world, pos, state, true, 0)
            || this.isPoweredByConnectedRails(world, pos, state, false, 0);
        if (flag1 != flag) {
            world.setBlockState(pos, state.set(POWERED, flag1), 3);
            world.updateNeighbors(pos.down(), this);
            if (state.get(SHAPE).isAscending()) {
                world.updateNeighbors(pos.up(), this);
            }
        }
    }

    @Override
    public Property<AbstractRailBlock.Shape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(SHAPE, AbstractRailBlock.Shape.byId(metadata & 7)).set(POWERED, (metadata & 8) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(SHAPE).getId();
        if (state.get(POWERED)) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, SHAPE, POWERED);
    }
}
