package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public abstract class DiodeBlock extends HorizontalFacingBlock {
    protected final boolean powered;

    protected DiodeBlock(boolean powered) {
        super(Material.DECORATION);
        this.powered = powered;
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.125F, 1.0F);
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return World.hasSolidTop(world, pos.down()) && super.canBePlaced(world, pos);
    }

    public boolean canSurvive(World world, BlockPos pos) {
        return World.hasSolidTop(world, pos.down());
    }

    @Override
    public void randomTick(World world, BlockPos pos, BlockState state, Random random) {
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!this.isLocked(world, pos, state)) {
            boolean flag = this.shouldBePowered(world, pos, state);
            if (this.powered && !flag) {
                world.setBlockState(pos, this.setPowered(state), 2);
            } else if (!this.powered) {
                world.setBlockState(pos, this.setUnpowered(state), 2);
                if (!flag) {
                    world.scheduleTick(pos, this.setUnpowered(state).getBlock(), this.getTickingDelay(state), -1);
                }
            }
        }
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return face.getAxis() != Direction.Axis.Y;
    }

    protected boolean isPowered(BlockState state) {
        return this.powered;
    }

    @Override
    public int getDirectSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return this.getSignal(world, pos, state, dir);
    }

    @Override
    public int getSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        if (!this.isPowered(state)) {
            return 0;
        } else {
            return state.get(FACING) == dir ? this.getOutputSignal(world, pos, state) : 0;
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (this.canSurvive(world, pos)) {
            this.checkOutputState(world, pos, state);
        } else {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);

            for (Direction direction : Direction.values()) {
                world.updateNeighbors(pos.offset(direction), this);
            }
        }
    }

    protected void checkOutputState(World world, BlockPos pos, BlockState state) {
        if (!this.isLocked(world, pos, state)) {
            boolean flag = this.shouldBePowered(world, pos, state);
            if ((this.powered && !flag || !this.powered && flag) && !world.willTickThisTick(pos, this)) {
                int i = -1;
                if (this.shouldPrioritize(world, pos, state)) {
                    i = -3;
                } else if (this.powered) {
                    i = -2;
                }

                world.scheduleTick(pos, this, this.getDelay(state), i);
            }
        }
    }

    public boolean isLocked(WorldView world, BlockPos pos, BlockState state) {
        return false;
    }

    protected boolean shouldBePowered(World world, BlockPos pos, BlockState state) {
        return this.getInputSignal(world, pos, state) > 0;
    }

    protected int getInputSignal(World world, BlockPos pos, BlockState state) {
        Direction direction = state.get(FACING);
        BlockPos blockpos = pos.offset(direction);
        int i = world.getSignal(blockpos, direction);
        if (i >= 15) {
            return i;
        }

        BlockState blockstate = world.getBlockState(blockpos);
        return Math.max(i, blockstate.getBlock() == Blocks.REDSTONE_WIRE ? blockstate.get(RedstoneWireBlock.POWER) : 0);
    }

    protected int getInputSignalFromSides(WorldView world, BlockPos pos, BlockState state) {
        Direction direction = state.get(FACING);
        Direction direction1 = direction.clockwiseY();
        Direction direction2 = direction.counterClockwiseY();
        return Math.max(
            this.getInputSignalFromSide(world, pos.offset(direction1), direction1), this.getInputSignalFromSide(world, pos.offset(direction2), direction2)
        );
    }

    protected int getInputSignalFromSide(WorldView world, BlockPos pos, Direction dir) {
        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        if (this.isValidSideInput(block)) {
            return block == Blocks.REDSTONE_WIRE ? blockstate.get(RedstoneWireBlock.POWER) : world.getDirectSignal(pos, dir);
        } else {
            return 0;
        }
    }

    @Override
    public boolean isSignalSource() {
        return true;
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState().set(FACING, entity.getHorizontalFacing().getOpposite());
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        if (this.shouldBePowered(world, pos, state)) {
            world.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        this.updateNeighbors(world, pos, state);
    }

    protected void updateNeighbors(World world, BlockPos x, BlockState state) {
        Direction direction = state.get(FACING);
        BlockPos blockpos = x.offset(direction.getOpposite());
        world.neighborChanged(blockpos, this);
        world.updateNeighborsExcept(blockpos, this, direction);
    }

    @Override
    public void onBroken(World world, BlockPos pos, BlockState state) {
        if (this.powered) {
            for (Direction direction : Direction.values()) {
                world.updateNeighbors(pos.offset(direction), this);
            }
        }

        super.onBroken(world, pos, state);
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    protected boolean isValidSideInput(Block block) {
        return block.isSignalSource();
    }

    protected int getOutputSignal(WorldView world, BlockPos pos, BlockState state) {
        return 15;
    }

    public static boolean isDiode(Block block) {
        return Blocks.REPEATER.isSameDiode(block) || Blocks.COMPARATOR.isSameDiode(block);
    }

    public boolean isSameDiode(Block block) {
        return block == this.setUnpowered(this.defaultState()).getBlock() || block == this.setPowered(this.defaultState()).getBlock();
    }

    public boolean shouldPrioritize(World world, BlockPos pos, BlockState state) {
        Direction direction = state.get(FACING).getOpposite();
        BlockPos blockpos = pos.offset(direction);
        return isDiode(world.getBlockState(blockpos).getBlock()) && world.getBlockState(blockpos).get(FACING) != direction;
    }

    protected int getTickingDelay(BlockState state) {
        return this.getDelay(state);
    }

    protected abstract int getDelay(BlockState state);

    protected abstract BlockState setUnpowered(BlockState state);

    protected abstract BlockState setPowered(BlockState state);

    @Override
    public boolean is(Block block) {
        return this.isSameDiode(block);
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }
}
