package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class LadderBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", Direction.Plane.HORIZONTAL);

    protected LadderBlock() {
        super(Material.DECORATION);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH));
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        this.updateShape(world, pos);
        return super.getCollisionShape(world, pos, state);
    }

    @Override
    public Box getOutlineShape(World world, BlockPos pos) {
        this.updateShape(world, pos);
        return super.getOutlineShape(world, pos);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() == this) {
            float f = 0.125F;
            switch ((Direction)blockstate.get(FACING)) {
                case NORTH:
                    this.setShape(0.0F, 0.0F, 1.0F - f, 1.0F, 1.0F, 1.0F);
                    break;
                case SOUTH:
                    this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, f);
                    break;
                case WEST:
                    this.setShape(1.0F - f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
                    break;
                case EAST:
                default:
                    this.setShape(0.0F, 0.0F, 0.0F, f, 1.0F, 1.0F);
            }
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
    public boolean canBePlaced(World world, BlockPos pos) {
        return world.getBlockState(pos.west()).getBlock().isSolid()
            || world.getBlockState(pos.east()).getBlock().isSolid()
            || world.getBlockState(pos.north()).getBlock().isSolid()
            || world.getBlockState(pos.south()).getBlock().isSolid();
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        if (dir.getAxis().isHorizontal() && this.canSurvive(world, pos, dir)) {
            return this.defaultState().set(FACING, dir);
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (this.canSurvive(world, pos, direction)) {
                return this.defaultState().set(FACING, direction);
            }
        }

        return this.defaultState();
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        Direction direction = state.get(FACING);
        if (!this.canSurvive(world, pos, direction)) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
        }

        super.neighborChanged(world, pos, state, neighborBlock);
    }

    protected boolean canSurvive(World world, BlockPos pos, Direction facing) {
        return world.getBlockState(pos.offset(facing.getOpposite())).getBlock().isSolid();
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
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
