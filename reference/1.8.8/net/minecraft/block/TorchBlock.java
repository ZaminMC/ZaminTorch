package net.minecraft.block;

import com.google.common.base.Predicate;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class TorchBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", new Predicate<Direction>() {
        public boolean apply(Direction direction) {
            return direction != Direction.DOWN;
        }
    });

    protected TorchBlock() {
        super(Material.DECORATION);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.UP));
        this.setTicksRandomly(true);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
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

    private boolean canSitOnTop(World world, BlockPos pos) {
        if (World.hasSolidTop(world, pos)) {
            return true;
        }

        Block block = world.getBlockState(pos).getBlock();
        return block instanceof FenceBlock || block == Blocks.GLASS || block == Blocks.COBBLESTONE_WALL || block == Blocks.STAINED_GLASS;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        for (Direction direction : FACING.values()) {
            if (this.canAttach(world, pos, direction)) {
                return true;
            }
        }

        return false;
    }

    private boolean canAttach(World world, BlockPos pos, Direction dir) {
        BlockPos blockpos = pos.offset(dir.getOpposite());
        boolean flag = dir.getAxis().isHorizontal();
        return flag && world.isSolidBlockingCube(blockpos, true) || dir.equals(Direction.UP) && this.canSitOnTop(world, blockpos);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        if (this.canAttach(world, pos, dir)) {
            return this.defaultState().set(FACING, dir);
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (world.isSolidBlockingCube(pos.offset(direction.getOpposite()), true)) {
                return this.defaultState().set(FACING, direction);
            }
        }

        return this.defaultState();
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        this.canSurviveOrBreak(world, pos, state);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        this.tryBreak(world, pos, state);
    }

    protected boolean tryBreak(World world, BlockPos pos, BlockState state) {
        if (!this.canSurviveOrBreak(world, pos, state)) {
            return true;
        }

        Direction direction = state.get(FACING);
        Direction.Axis direction$axis = direction.getAxis();
        Direction direction1 = direction.getOpposite();
        boolean flag = false;
        if (direction$axis.isHorizontal() && !world.isSolidBlockingCube(pos.offset(direction1), true)) {
            flag = true;
        } else if (direction$axis.isVertical() && !this.canSitOnTop(world, pos.offset(direction1))) {
            flag = true;
        }

        if (flag) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
            return true;
        } else {
            return false;
        }
    }

    protected boolean canSurviveOrBreak(World world, BlockPos pos, BlockState state) {
        if (state.getBlock() == this && this.canAttach(world, pos, state.get(FACING))) {
            return true;
        }

        if (world.getBlockState(pos).getBlock() == this) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
        }

        return false;
    }

    @Override
    public HitResult rayTrace(World world, BlockPos pos, Vec3d start, Vec3d end) {
        Direction direction = world.getBlockState(pos).get(FACING);
        float f = 0.15F;
        if (direction == Direction.EAST) {
            this.setShape(0.0F, 0.2F, 0.5F - f, f * 2.0F, 0.8F, 0.5F + f);
        } else if (direction == Direction.WEST) {
            this.setShape(1.0F - f * 2.0F, 0.2F, 0.5F - f, 1.0F, 0.8F, 0.5F + f);
        } else if (direction == Direction.SOUTH) {
            this.setShape(0.5F - f, 0.2F, 0.0F, 0.5F + f, 0.8F, f * 2.0F);
        } else if (direction == Direction.NORTH) {
            this.setShape(0.5F - f, 0.2F, 1.0F - f * 2.0F, 0.5F + f, 0.8F, 1.0F);
        } else {
            f = 0.1F;
            this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, 0.6F, 0.5F + f);
        }

        return super.rayTrace(world, pos, start, end);
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        Direction direction = state.get(FACING);
        double d0 = pos.getX() + 0.5;
        double d1 = pos.getY() + 0.7;
        double d2 = pos.getZ() + 0.5;
        double d3 = 0.22;
        double d4 = 0.27;
        if (direction.getAxis().isHorizontal()) {
            Direction direction1 = direction.getOpposite();
            world.addParticle(ParticleType.SMOKE_NORMAL, d0 + d4 * direction1.getOffsetX(), d1 + d3, d2 + d4 * direction1.getOffsetZ(), 0.0, 0.0, 0.0);
            world.addParticle(ParticleType.FLAME, d0 + d4 * direction1.getOffsetX(), d1 + d3, d2 + d4 * direction1.getOffsetZ(), 0.0, 0.0, 0.0);
        } else {
            world.addParticle(ParticleType.SMOKE_NORMAL, d0, d1, d2, 0.0, 0.0, 0.0);
            world.addParticle(ParticleType.FLAME, d0, d1, d2, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        BlockState blockstate = this.defaultState();
        switch (metadata) {
            case 1:
                blockstate = blockstate.set(FACING, Direction.EAST);
                break;
            case 2:
                blockstate = blockstate.set(FACING, Direction.WEST);
                break;
            case 3:
                blockstate = blockstate.set(FACING, Direction.SOUTH);
                break;
            case 4:
                blockstate = blockstate.set(FACING, Direction.NORTH);
                break;
            case 5:
            default:
                blockstate = blockstate.set(FACING, Direction.UP);
        }

        return blockstate;
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        switch ((Direction)state.get(FACING)) {
            case EAST:
                i |= 1;
                break;
            case WEST:
                i |= 2;
                break;
            case SOUTH:
                i |= 3;
                break;
            case NORTH:
                i |= 4;
                break;
            case DOWN:
            case UP:
            default:
                i |= 5;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING);
    }
}
