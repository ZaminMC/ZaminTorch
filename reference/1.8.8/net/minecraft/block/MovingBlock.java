package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MovingBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class MovingBlock extends BlockWithBlockEntity {
    public static final DirectionProperty FACING = PistonHeadBlock.FACING;
    public static final EnumProperty<PistonHeadBlock.Type> TYPE = PistonHeadBlock.TYPE;

    public MovingBlock() {
        super(Material.PISTON);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(TYPE, PistonHeadBlock.Type.DEFAULT));
        this.setStrength(-1.0F);
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return null;
    }

    public static BlockEntity createMovingBlockEntity(BlockState movedState, Direction facing, boolean extending, boolean source) {
        return new MovingBlockEntity(movedState, facing, extending, source);
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof MovingBlockEntity) {
            ((MovingBlockEntity)blockentity).finish();
        } else {
            super.onRemoved(world, pos, state);
        }
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return false;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos, Direction face) {
        return false;
    }

    @Override
    public void onBroken(World world, BlockPos pos, BlockState state) {
        BlockPos blockpos = pos.offset(state.get(FACING).getOpposite());
        BlockState blockstate = world.getBlockState(blockpos);
        if (blockstate.getBlock() instanceof PistonBaseBlock && blockstate.get(PistonBaseBlock.EXTENDED)) {
            world.removeBlock(blockpos);
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
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (!world.isClient && world.getBlockEntity(pos) == null) {
            world.removeBlock(pos);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return null;
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        if (!world.isClient) {
            MovingBlockEntity movingblockentity = this.getMovingBlockEntity(world, pos);
            if (movingblockentity != null) {
                BlockState blockstate = movingblockentity.getMovedState();
                blockstate.getBlock().dropItems(world, pos, blockstate, 0);
            }
        }
    }

    @Override
    public HitResult rayTrace(World world, BlockPos pos, Vec3d start, Vec3d end) {
        return null;
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!world.isClient) {
            world.getBlockEntity(pos);
        }
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        MovingBlockEntity movingblockentity = this.getMovingBlockEntity(world, pos);
        if (movingblockentity == null) {
            return null;
        }

        float f = movingblockentity.getProgress(0.0F);
        if (movingblockentity.isExtending()) {
            f = 1.0F - f;
        }

        return this.getCollisionShape(world, pos, movingblockentity.getMovedState(), f, movingblockentity.getFacing());
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        MovingBlockEntity movingblockentity = this.getMovingBlockEntity(world, pos);
        if (movingblockentity != null) {
            BlockState blockstate = movingblockentity.getMovedState();
            Block block = blockstate.getBlock();
            if (block == this || block.getMaterial() == Material.AIR) {
                return;
            }

            float f = movingblockentity.getProgress(0.0F);
            if (movingblockentity.isExtending()) {
                f = 1.0F - f;
            }

            block.updateShape(world, pos);
            if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON) {
                f = 0.0F;
            }

            Direction direction = movingblockentity.getFacing();
            this.minX = block.getMinX() - direction.getOffsetX() * f;
            this.minY = block.getMinY() - direction.getOffsetY() * f;
            this.minZ = block.getMinZ() - direction.getOffsetZ() * f;
            this.maxX = block.getMaxX() - direction.getOffsetX() * f;
            this.maxY = block.getMaxY() - direction.getOffsetY() * f;
            this.maxZ = block.getMaxZ() - direction.getOffsetZ() * f;
        }
    }

    public Box getCollisionShape(World world, BlockPos pos, BlockState movedState, float progress, Direction facing) {
        if (movedState.getBlock() != this && movedState.getBlock().getMaterial() != Material.AIR) {
            Box box = movedState.getBlock().getCollisionShape(world, pos, movedState);
            if (box == null) {
                return null;
            }

            double d0 = box.minX;
            double d1 = box.minY;
            double d2 = box.minZ;
            double d3 = box.maxX;
            double d4 = box.maxY;
            double d5 = box.maxZ;
            if (facing.getOffsetX() < 0) {
                d0 -= facing.getOffsetX() * progress;
            } else {
                d3 -= facing.getOffsetX() * progress;
            }

            if (facing.getOffsetY() < 0) {
                d1 -= facing.getOffsetY() * progress;
            } else {
                d4 -= facing.getOffsetY() * progress;
            }

            if (facing.getOffsetZ() < 0) {
                d2 -= facing.getOffsetZ() * progress;
            } else {
                d5 -= facing.getOffsetZ() * progress;
            }

            return new Box(d0, d1, d2, d3, d4, d5);
        } else {
            return null;
        }
    }

    private MovingBlockEntity getMovingBlockEntity(WorldView world, BlockPos pos) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        return blockentity instanceof MovingBlockEntity ? (MovingBlockEntity)blockentity : null;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState()
            .set(FACING, PistonHeadBlock.getFacing(metadata))
            .set(TYPE, (metadata & 8) > 0 ? PistonHeadBlock.Type.STICKY : PistonHeadBlock.Type.DEFAULT);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getId();
        if (state.get(TYPE) == PistonHeadBlock.Type.STICKY) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, TYPE);
    }
}
