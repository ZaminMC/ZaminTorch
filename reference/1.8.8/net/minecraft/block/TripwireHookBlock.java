package net.minecraft.block;

import com.google.common.base.Objects;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class TripwireHookBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    public static final BooleanProperty ATTACHED = BooleanProperty.of("attached");
    public static final BooleanProperty SUSPENDED = BooleanProperty.of("suspended");

    public TripwireHookBlock() {
        super(Material.DECORATION);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(POWERED, false).set(ATTACHED, false).set(SUSPENDED, false));
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
        this.setTicksRandomly(true);
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        return state.set(SUSPENDED, !World.hasSolidTop(world, pos.down()));
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return null;
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
    public boolean canBePlaced(World world, BlockPos pos, Direction face) {
        return face.getAxis().isHorizontal() && world.getBlockState(pos.offset(face.getOpposite())).getBlock().isSolid();
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (world.getBlockState(pos.offset(direction)).getBlock().isSolid()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        BlockState blockstate = this.defaultState().set(POWERED, false).set(ATTACHED, false).set(SUSPENDED, false);
        if (dir.getAxis().isHorizontal()) {
            blockstate = blockstate.set(FACING, dir);
        }

        return blockstate;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        this.updateState(world, pos, state, false, false, -1, null);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (neighborBlock != this) {
            if (this.canSurviveOrBreak(world, pos, state)) {
                Direction direction = state.get(FACING);
                if (!world.getBlockState(pos.offset(direction.getOpposite())).getBlock().isSolid()) {
                    this.dropItems(world, pos, state, 0);
                    world.removeBlock(pos);
                }
            }
        }
    }

    public void updateState(World world, BlockPos pos, BlockState state, boolean removed, boolean updateNeighbors, int distanceToTripwire, BlockState tripwire) {
        Direction direction = state.get(FACING);
        boolean flag = state.get(ATTACHED);
        boolean flag1 = state.get(POWERED);
        boolean flag2 = !World.hasSolidTop(world, pos.down());
        boolean flag3 = !removed;
        boolean flag4 = false;
        int i = 0;
        BlockState[] ablockstate = new BlockState[42];

        for (int j = 1; j < 42; j++) {
            BlockPos blockpos = pos.offset(direction, j);
            BlockState blockstate = world.getBlockState(blockpos);
            if (blockstate.getBlock() == Blocks.TRIPWIRE_HOOK) {
                if (blockstate.get(FACING) == direction.getOpposite()) {
                    i = j;
                }
                break;
            }

            if (blockstate.getBlock() != Blocks.TRIPWIRE && j != distanceToTripwire) {
                ablockstate[j] = null;
                flag3 = false;
            } else {
                if (j == distanceToTripwire) {
                    blockstate = Objects.firstNonNull(tripwire, blockstate);
                }

                boolean flag5 = !blockstate.get(TripwireBlock.DISARMED);
                boolean flag6 = blockstate.get(TripwireBlock.POWERED);
                boolean flag7 = blockstate.get(TripwireBlock.SUSPENDED);
                flag3 &= flag7 == flag2;
                flag4 |= flag5 && flag6;
                ablockstate[j] = blockstate;
                if (j == distanceToTripwire) {
                    world.scheduleTick(pos, this, this.getTickRate(world));
                    flag3 &= flag5;
                }
            }
        }

        flag3 &= i > 1;
        flag4 &= flag3;
        BlockState blockstate1 = this.defaultState().set(ATTACHED, flag3).set(POWERED, flag4);
        if (i > 0) {
            BlockPos blockpos1 = pos.offset(direction, i);
            Direction direction1 = direction.getOpposite();
            world.setBlockState(blockpos1, blockstate1.set(FACING, direction1), 3);
            this.updateNeighbors(world, blockpos1, direction1);
            this.playClickSound(world, blockpos1, flag3, flag4, flag, flag1);
        }

        this.playClickSound(world, pos, flag3, flag4, flag, flag1);
        if (!removed) {
            world.setBlockState(pos, blockstate1.set(FACING, direction), 3);
            if (updateNeighbors) {
                this.updateNeighbors(world, pos, direction);
            }
        }

        if (flag != flag3) {
            for (int k = 1; k < i; k++) {
                BlockPos blockpos2 = pos.offset(direction, k);
                BlockState blockstate2 = ablockstate[k];
                if (blockstate2 != null && world.getBlockState(blockpos2).getBlock() != Blocks.AIR) {
                    world.setBlockState(blockpos2, blockstate2.set(ATTACHED, flag3), 3);
                }
            }
        }
    }

    @Override
    public void randomTick(World world, BlockPos pos, BlockState state, Random random) {
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        this.updateState(world, pos, state, false, true, -1, null);
    }

    private void playClickSound(World world, BlockPos pos, boolean attached, boolean turnedOn, boolean wasAttached, boolean wasTurnedOn) {
        if (turnedOn && !wasTurnedOn) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, "random.click", 0.4F, 0.6F);
        } else if (!turnedOn && wasTurnedOn) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, "random.click", 0.4F, 0.5F);
        } else if (attached && !wasAttached) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, "random.click", 0.4F, 0.7F);
        } else if (!attached && wasAttached) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, "random.bowhit", 0.4F, 1.2F / (world.random.nextFloat() * 0.2F + 0.9F));
        }
    }

    private void updateNeighbors(World world, BlockPos pos, Direction facing) {
        world.updateNeighbors(pos, this);
        world.updateNeighbors(pos.offset(facing.getOpposite()), this);
    }

    private boolean canSurviveOrBreak(World world, BlockPos pos, BlockState state) {
        if (!this.canBePlaced(world, pos)) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        float f = 0.1875F;
        switch ((Direction)world.getBlockState(pos).get(FACING)) {
            case EAST:
                this.setShape(0.0F, 0.2F, 0.5F - f, f * 2.0F, 0.8F, 0.5F + f);
                break;
            case WEST:
                this.setShape(1.0F - f * 2.0F, 0.2F, 0.5F - f, 1.0F, 0.8F, 0.5F + f);
                break;
            case SOUTH:
                this.setShape(0.5F - f, 0.2F, 0.0F, 0.5F + f, 0.8F, f * 2.0F);
                break;
            case NORTH:
                this.setShape(0.5F - f, 0.2F, 1.0F - f * 2.0F, 0.5F + f, 0.8F, 1.0F);
        }
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        boolean flag = state.get(ATTACHED);
        boolean flag1 = state.get(POWERED);
        if (flag || flag1) {
            this.updateState(world, pos, state, true, false, -1, null);
        }

        if (flag1) {
            world.updateNeighbors(pos, this);
            world.updateNeighbors(pos.offset(state.get(FACING).getOpposite()), this);
        }

        super.onRemoved(world, pos, state);
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
            return state.get(FACING) == dir ? 15 : 0;
        }
    }

    @Override
    public boolean isSignalSource() {
        return true;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT_MIPPED;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(FACING, Direction.byIdHorizontal(metadata & 3)).set(POWERED, (metadata & 8) > 0).set(ATTACHED, (metadata & 4) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getIdHorizontal();
        if (state.get(POWERED)) {
            i |= 8;
        }

        if (state.get(ATTACHED)) {
            i |= 4;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, POWERED, ATTACHED, SUSPENDED);
    }
}
