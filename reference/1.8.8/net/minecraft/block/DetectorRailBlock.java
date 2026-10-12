package net.minecraft.block;

import com.google.common.base.Predicate;
import java.util.List;
import java.util.Random;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.block.state.property.Property;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.vehicle.CommandBlockMinecartEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class DetectorRailBlock extends AbstractRailBlock {
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

    public DetectorRailBlock() {
        super(true);
        this.setDefaultState(this.stateDefinition.any().set(POWERED, false).set(SHAPE, AbstractRailBlock.Shape.NORTH_SOUTH));
        this.setTicksRandomly(true);
    }

    @Override
    public int getTickRate(World world) {
        return 20;
    }

    @Override
    public boolean isSignalSource() {
        return true;
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient) {
            if (!state.get(POWERED)) {
                this.updateOutputState(world, pos, state);
            }
        }
    }

    @Override
    public void randomTick(World world, BlockPos pos, BlockState state, Random random) {
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!world.isClient && state.get(POWERED)) {
            this.updateOutputState(world, pos, state);
        }
    }

    @Override
    public int getSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return state.get(POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        if (!state.get(POWERED)) {
            return 0;
        } else {
            return dir == Direction.UP ? 15 : 0;
        }
    }

    private void updateOutputState(World world, BlockPos pos, BlockState state) {
        boolean flag = state.get(POWERED);
        boolean flag1 = false;
        List<MinecartEntity> list = this.getMinecarts(world, pos, MinecartEntity.class);
        if (!list.isEmpty()) {
            flag1 = true;
        }

        if (flag1 && !flag) {
            world.setBlockState(pos, state.set(POWERED, true), 3);
            world.updateNeighbors(pos, this);
            world.updateNeighbors(pos.down(), this);
            world.notifyRegionChanged(pos, pos);
        }

        if (!flag1 && flag) {
            world.setBlockState(pos, state.set(POWERED, false), 3);
            world.updateNeighbors(pos, this);
            world.updateNeighbors(pos.down(), this);
            world.notifyRegionChanged(pos, pos);
        }

        if (flag1) {
            world.scheduleTick(pos, this, this.getTickRate(world));
        }

        world.updateNeighborComparators(pos, this);
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        super.onAdded(world, pos, state);
        this.updateOutputState(world, pos, state);
    }

    @Override
    public Property<AbstractRailBlock.Shape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    public boolean isAnalogSignalSource() {
        return true;
    }

    @Override
    public int getAnalogSignal(World world, BlockPos pos) {
        if (world.getBlockState(pos).get(POWERED)) {
            List<CommandBlockMinecartEntity> list = this.getMinecarts(world, pos, CommandBlockMinecartEntity.class);
            if (!list.isEmpty()) {
                return list.get(0).getCommandExecutor().getSuccessCount();
            }

            List<MinecartEntity> list1 = this.getMinecarts(world, pos, MinecartEntity.class, EntityFilter.INVENTORY);
            if (!list1.isEmpty()) {
                return InventoryMenu.getAnalogSignal((Inventory)list1.get(0));
            }
        }

        return 0;
    }

    protected <T extends MinecartEntity> List<T> getMinecarts(World world, BlockPos pos, Class<T> type, Predicate<Entity>... predicates) {
        Box box = this.getBoundsForMinecarts(pos);
        return predicates.length != 1 ? world.getEntitiesOfType(type, box) : world.getEntitiesOfType(type, box, predicates[0]);
    }

    private Box getBoundsForMinecarts(BlockPos pos) {
        float f = 0.2F;
        return new Box(pos.getX() + 0.2F, pos.getY(), pos.getZ() + 0.2F, pos.getX() + 1 - 0.2F, pos.getY() + 1 - 0.2F, pos.getZ() + 1 - 0.2F);
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
