package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public abstract class ButtonBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.of("facing");
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    private final boolean wooden;

    protected ButtonBlock(boolean wooden) {
        super(Material.DECORATION);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(POWERED, false));
        this.setTicksRandomly(true);
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
        this.wooden = wooden;
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    public int getTickRate(World world) {
        return this.wooden ? 30 : 20;
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
        return canSurvive(world, pos, face.getOpposite());
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (canSurvive(world, pos, direction)) {
                return true;
            }
        }

        return false;
    }

    protected static boolean canSurvive(World world, BlockPos pos, Direction facing) {
        BlockPos blockpos = pos.offset(facing);
        return facing == Direction.DOWN ? World.hasSolidTop(world, blockpos) : world.getBlockState(blockpos).getBlock().isSolid();
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return canSurvive(world, pos, dir.getOpposite())
            ? this.defaultState().set(FACING, dir).set(POWERED, false)
            : this.defaultState().set(FACING, Direction.DOWN).set(POWERED, false);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (this.canSurviveOrBreak(world, pos, state) && !canSurvive(world, pos, state.get(FACING).getOpposite())) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
        }
    }

    private boolean canSurviveOrBreak(World world, BlockPos pos, BlockState state) {
        if (this.canBePlaced(world, pos)) {
            return true;
        }

        this.dropItems(world, pos, state, 0);
        world.removeBlock(pos);
        return false;
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        this.updateShape(world.getBlockState(pos));
    }

    private void updateShape(BlockState state) {
        Direction direction = state.get(FACING);
        boolean flag = state.get(POWERED);
        float f = 0.25F;
        float f1 = 0.375F;
        float f2 = (flag ? 1 : 2) / 16.0F;
        float f3 = 0.125F;
        float f4 = 0.1875F;
        switch (direction) {
            case EAST:
                this.setShape(0.0F, 0.375F, 0.3125F, f2, 0.625F, 0.6875F);
                break;
            case WEST:
                this.setShape(1.0F - f2, 0.375F, 0.3125F, 1.0F, 0.625F, 0.6875F);
                break;
            case SOUTH:
                this.setShape(0.3125F, 0.375F, 0.0F, 0.6875F, 0.625F, f2);
                break;
            case NORTH:
                this.setShape(0.3125F, 0.375F, 1.0F - f2, 0.6875F, 0.625F, 1.0F);
                break;
            case UP:
                this.setShape(0.3125F, 0.0F, 0.375F, 0.6875F, 0.0F + f2, 0.625F);
                break;
            case DOWN:
                this.setShape(0.3125F, 1.0F - f2, 0.375F, 0.6875F, 1.0F, 0.625F);
        }
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (state.get(POWERED)) {
            return true;
        }

        world.setBlockState(pos, state.set(POWERED, true), 3);
        world.notifyRegionChanged(pos, pos);
        world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "random.click", 0.3F, 0.6F);
        this.updateNeighbors(world, pos, state.get(FACING));
        world.scheduleTick(pos, this, this.getTickRate(world));
        return true;
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        if (state.get(POWERED)) {
            this.updateNeighbors(world, pos, state.get(FACING));
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
    public void randomTick(World world, BlockPos pos, BlockState state, Random random) {
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!world.isClient) {
            if (state.get(POWERED)) {
                if (this.wooden) {
                    this.updatePressed(world, pos, state);
                } else {
                    world.setBlockState(pos, state.set(POWERED, false));
                    this.updateNeighbors(world, pos, state.get(FACING));
                    world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "random.click", 0.3F, 0.5F);
                    world.notifyRegionChanged(pos, pos);
                }
            }
        }
    }

    @Override
    public void resetShape() {
        float f = 0.1875F;
        float f1 = 0.125F;
        float f2 = 0.125F;
        this.setShape(0.5F - f, 0.5F - f1, 0.5F - f2, 0.5F + f, 0.5F + f1, 0.5F + f2);
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient) {
            if (this.wooden) {
                if (!state.get(POWERED)) {
                    this.updatePressed(world, pos, state);
                }
            }
        }
    }

    private void updatePressed(World world, BlockPos pos, BlockState state) {
        this.updateShape(state);
        List<? extends Entity> list = world.getEntitiesOfType(
            ArrowEntity.class,
            new Box(
                pos.getX() + this.minX, pos.getY() + this.minY, pos.getZ() + this.minZ, pos.getX() + this.maxX, pos.getY() + this.maxY, pos.getZ() + this.maxZ
            )
        );
        boolean flag = !list.isEmpty();
        boolean flag1 = state.get(POWERED);
        if (flag && !flag1) {
            world.setBlockState(pos, state.set(POWERED, true));
            this.updateNeighbors(world, pos, state.get(FACING));
            world.notifyRegionChanged(pos, pos);
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "random.click", 0.3F, 0.6F);
        }

        if (!flag && flag1) {
            world.setBlockState(pos, state.set(POWERED, false));
            this.updateNeighbors(world, pos, state.get(FACING));
            world.notifyRegionChanged(pos, pos);
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "random.click", 0.3F, 0.5F);
        }

        if (flag) {
            world.scheduleTick(pos, this, this.getTickRate(world));
        }
    }

    private void updateNeighbors(World world, BlockPos pos, Direction dir) {
        world.updateNeighbors(pos, this);
        world.updateNeighbors(pos.offset(dir.getOpposite()), this);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        Direction direction;
        switch (metadata & 7) {
            case 0:
                direction = Direction.DOWN;
                break;
            case 1:
                direction = Direction.EAST;
                break;
            case 2:
                direction = Direction.WEST;
                break;
            case 3:
                direction = Direction.SOUTH;
                break;
            case 4:
                direction = Direction.NORTH;
                break;
            case 5:
            default:
                direction = Direction.UP;
        }

        return this.defaultState().set(FACING, direction).set(POWERED, (metadata & 8) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i;
        switch ((Direction)state.get(FACING)) {
            case EAST:
                i = 1;
                break;
            case WEST:
                i = 2;
                break;
            case SOUTH:
                i = 3;
                break;
            case NORTH:
                i = 4;
                break;
            case UP:
            default:
                i = 5;
                break;
            case DOWN:
                i = 0;
        }

        if (state.get(POWERED)) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, POWERED);
    }
}
