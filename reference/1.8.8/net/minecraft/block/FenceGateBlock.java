package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class FenceGateBlock extends HorizontalFacingBlock {
    public static final BooleanProperty OPEN = BooleanProperty.of("open");
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    public static final BooleanProperty IN_WALL = BooleanProperty.of("in_wall");

    public FenceGateBlock(PlanksBlock.Variant variant) {
        super(Material.WOOD, variant.getColor());
        this.setDefaultState(this.stateDefinition.any().set(OPEN, false).set(POWERED, false).set(IN_WALL, false));
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        Direction.Axis direction$axis = state.get(FACING).getAxis();
        if (direction$axis == Direction.Axis.Z
                && (
                    world.getBlockState(pos.west()).getBlock() == Blocks.COBBLESTONE_WALL
                        || world.getBlockState(pos.east()).getBlock() == Blocks.COBBLESTONE_WALL
                )
            || direction$axis == Direction.Axis.X
                && (
                    world.getBlockState(pos.north()).getBlock() == Blocks.COBBLESTONE_WALL
                        || world.getBlockState(pos.south()).getBlock() == Blocks.COBBLESTONE_WALL
                )) {
            state = state.set(IN_WALL, true);
        }

        return state;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return world.getBlockState(pos.down()).getBlock().getMaterial().isSolid() && super.canBePlaced(world, pos);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        if (state.get(OPEN)) {
            return null;
        }

        Direction.Axis direction$axis = state.get(FACING).getAxis();
        return direction$axis == Direction.Axis.Z
            ? new Box(pos.getX(), pos.getY(), pos.getZ() + 0.375F, pos.getX() + 1, pos.getY() + 1.5F, pos.getZ() + 0.625F)
            : new Box(pos.getX() + 0.375F, pos.getY(), pos.getZ(), pos.getX() + 0.625F, pos.getY() + 1.5F, pos.getZ() + 1);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        Direction.Axis direction$axis = world.getBlockState(pos).get(FACING).getAxis();
        if (direction$axis == Direction.Axis.Z) {
            this.setShape(0.0F, 0.0F, 0.375F, 1.0F, 1.0F, 0.625F);
        } else {
            this.setShape(0.375F, 0.0F, 0.0F, 0.625F, 1.0F, 1.0F);
        }
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public boolean canWalkThrough(WorldView world, BlockPos pos) {
        return world.getBlockState(pos).get(OPEN);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState().set(FACING, entity.getHorizontalFacing()).set(OPEN, false).set(POWERED, false).set(IN_WALL, false);
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (state.get(OPEN)) {
            state = state.set(OPEN, false);
            world.setBlockState(pos, state, 2);
        } else {
            Direction direction = Direction.byRotation(player.yaw);
            if (state.get(FACING) == direction.getOpposite()) {
                state = state.set(FACING, direction);
            }

            state = state.set(OPEN, true);
            world.setBlockState(pos, state, 2);
        }

        world.doEvent(player, state.get(OPEN) ? 1003 : 1006, pos, 0);
        return true;
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!world.isClient) {
            boolean flag = world.hasNeighborSignal(pos);
            if (flag || neighborBlock.isSignalSource()) {
                if (flag && !state.get(OPEN) && !state.get(POWERED)) {
                    world.setBlockState(pos, state.set(OPEN, true).set(POWERED, true), 2);
                    world.doEvent(null, 1003, pos, 0);
                } else if (!flag && state.get(OPEN) && state.get(POWERED)) {
                    world.setBlockState(pos, state.set(OPEN, false).set(POWERED, false), 2);
                    world.doEvent(null, 1006, pos, 0);
                } else if (flag != state.get(POWERED)) {
                    world.setBlockState(pos, state.set(POWERED, flag), 2);
                }
            }
        }
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return true;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(FACING, Direction.byIdHorizontal(metadata)).set(OPEN, (metadata & 4) != 0).set(POWERED, (metadata & 8) != 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getIdHorizontal();
        if (state.get(POWERED)) {
            i |= 8;
        }

        if (state.get(OPEN)) {
            i |= 4;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, OPEN, POWERED, IN_WALL);
    }
}
