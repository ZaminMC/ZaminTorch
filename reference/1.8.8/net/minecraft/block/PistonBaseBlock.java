package net.minecraft.block;

import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityProvider;
import net.minecraft.block.entity.MovingBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.piston.PistonMoveStructureResolver;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class PistonBaseBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.of("facing");
    public static final BooleanProperty EXTENDED = BooleanProperty.of("extended");
    private final boolean sticky;

    public PistonBaseBlock(boolean sticky) {
        super(Material.PISTON);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(EXTENDED, false));
        this.sticky = sticky;
        this.setSounds(STONE_SOUNDS);
        this.setStrength(0.5F);
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        world.setBlockState(pos, state.set(FACING, getFacingForPlacement(world, pos, entity)), 2);
        if (!world.isClient) {
            this.checkExtended(world, pos, state);
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!world.isClient) {
            this.checkExtended(world, pos, state);
        }
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        if (!world.isClient && world.getBlockEntity(pos) == null) {
            this.checkExtended(world, pos, state);
        }
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState().set(FACING, getFacingForPlacement(world, pos, entity)).set(EXTENDED, false);
    }

    private void checkExtended(World world, BlockPos pos, BlockState state) {
        Direction direction = state.get(FACING);
        boolean flag = this.shouldExtend(world, pos, direction);
        if (flag && !state.get(EXTENDED)) {
            if (new PistonMoveStructureResolver(world, pos, direction, true).resolve()) {
                world.addBlockEvent(pos, this, 0, direction.getId());
            }
        } else if (!flag && state.get(EXTENDED)) {
            world.setBlockState(pos, state.set(EXTENDED, false), 2);
            world.addBlockEvent(pos, this, 1, direction.getId());
        }
    }

    private boolean shouldExtend(World world, BlockPos pos, Direction facing) {
        for (Direction direction : Direction.values()) {
            if (direction != facing && world.hasSignal(pos.offset(direction), direction)) {
                return true;
            }
        }

        if (world.hasSignal(pos, Direction.DOWN)) {
            return true;
        }

        BlockPos blockpos = pos.up();

        for (Direction direction1 : Direction.values()) {
            if (direction1 != Direction.DOWN && world.hasSignal(blockpos.offset(direction1), direction1)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean doEvent(World world, BlockPos pos, BlockState state, int type, int data) {
        Direction direction = state.get(FACING);
        if (!world.isClient) {
            boolean flag = this.shouldExtend(world, pos, direction);
            if (flag && type == 1) {
                world.setBlockState(pos, state.set(EXTENDED, true), 2);
                return false;
            }

            if (!flag && type == 0) {
                return false;
            }
        }

        if (type == 0) {
            if (!this.move(world, pos, direction, true)) {
                return false;
            }

            world.setBlockState(pos, state.set(EXTENDED, true), 2);
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "tile.piston.out", 0.5F, world.random.nextFloat() * 0.25F + 0.6F);
        } else if (type == 1) {
            BlockEntity blockentity1 = world.getBlockEntity(pos.offset(direction));
            if (blockentity1 instanceof MovingBlockEntity) {
                ((MovingBlockEntity)blockentity1).finish();
            }

            world.setBlockState(
                pos,
                Blocks.MOVING_BLOCK
                    .defaultState()
                    .set(MovingBlock.FACING, direction)
                    .set(MovingBlock.TYPE, this.sticky ? PistonHeadBlock.Type.STICKY : PistonHeadBlock.Type.DEFAULT),
                3
            );
            world.setBlockEntity(pos, MovingBlock.createMovingBlockEntity(this.getStateFromMetadata(data), direction, false, true));
            if (this.sticky) {
                BlockPos blockpos = pos.add(direction.getOffsetX() * 2, direction.getOffsetY() * 2, direction.getOffsetZ() * 2);
                Block block = world.getBlockState(blockpos).getBlock();
                boolean flag1 = false;
                if (block == Blocks.MOVING_BLOCK) {
                    BlockEntity blockentity = world.getBlockEntity(blockpos);
                    if (blockentity instanceof MovingBlockEntity) {
                        MovingBlockEntity movingblockentity = (MovingBlockEntity)blockentity;
                        if (movingblockentity.getFacing() == direction && movingblockentity.isExtending()) {
                            movingblockentity.finish();
                            flag1 = true;
                        }
                    }
                }

                if (!flag1
                    && block.getMaterial() != Material.AIR
                    && canMoveBlock(block, world, blockpos, direction.getOpposite(), false)
                    && (block.getPistonMoveBehavior() == 0 || block == Blocks.PISTON || block == Blocks.STICKY_PISTON)) {
                    this.move(world, pos, direction, false);
                }
            } else {
                world.removeBlock(pos.offset(direction));
            }

            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "tile.piston.in", 0.5F, world.random.nextFloat() * 0.15F + 0.6F);
        }

        return true;
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() == this && blockstate.get(EXTENDED)) {
            float f = 0.25F;
            Direction direction = blockstate.get(FACING);
            if (direction != null) {
                switch (direction) {
                    case DOWN:
                        this.setShape(0.0F, 0.25F, 0.0F, 1.0F, 1.0F, 1.0F);
                        break;
                    case UP:
                        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.75F, 1.0F);
                        break;
                    case NORTH:
                        this.setShape(0.0F, 0.0F, 0.25F, 1.0F, 1.0F, 1.0F);
                        break;
                    case SOUTH:
                        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.75F);
                        break;
                    case WEST:
                        this.setShape(0.25F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
                        break;
                    case EAST:
                        this.setShape(0.0F, 0.0F, 0.0F, 0.75F, 1.0F, 1.0F);
                }
            }
        } else {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Override
    public void resetShape() {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        super.addCollisions(world, pos, state, shape, collisions, entity);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        this.updateShape(world, pos);
        return super.getCollisionShape(world, pos, state);
    }

    @Override
    public boolean isCube() {
        return false;
    }

    public static Direction getFacing(int metadata) {
        int i = metadata & 7;
        return i > 5 ? null : Direction.byId(i);
    }

    public static Direction getFacingForPlacement(World world, BlockPos pos, LivingEntity entity) {
        if (MathHelper.abs((float)entity.x - pos.getX()) < 2.0F && MathHelper.abs((float)entity.z - pos.getZ()) < 2.0F) {
            double d0 = entity.y + entity.getEyeHeight();
            if (d0 - pos.getY() > 2.0) {
                return Direction.UP;
            }

            if (pos.getY() - d0 > 0.0) {
                return Direction.DOWN;
            }
        }

        return entity.getHorizontalFacing().getOpposite();
    }

    public static boolean canMoveBlock(Block block, World world, BlockPos pos, Direction dir, boolean allowBreaking) {
        if (block == Blocks.OBSIDIAN) {
            return false;
        }

        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }

        if (pos.getY() >= 0 && (dir != Direction.DOWN || pos.getY() != 0)) {
            if (pos.getY() <= world.getHeight() - 1 && (dir != Direction.UP || pos.getY() != world.getHeight() - 1)) {
                if (block != Blocks.PISTON && block != Blocks.STICKY_PISTON) {
                    if (block.getMiningTime(world, pos) == -1.0F) {
                        return false;
                    }

                    if (block.getPistonMoveBehavior() == 2) {
                        return false;
                    }

                    if (block.getPistonMoveBehavior() == 1) {
                        if (!allowBreaking) {
                            return false;
                        }

                        return true;
                    }
                } else if (world.getBlockState(pos).get(EXTENDED)) {
                    return false;
                }

                return !(block instanceof BlockEntityProvider);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean move(World world, BlockPos pos, Direction facing, boolean extend) {
        if (!extend) {
            world.removeBlock(pos.offset(facing));
        }

        PistonMoveStructureResolver pistonmovestructureresolver = new PistonMoveStructureResolver(world, pos, facing, extend);
        List<BlockPos> list = pistonmovestructureresolver.getToMove();
        List<BlockPos> list1 = pistonmovestructureresolver.getToBreak();
        if (!pistonmovestructureresolver.resolve()) {
            return false;
        }

        int i = list.size() + list1.size();
        Block[] ablock = new Block[i];
        Direction direction = extend ? facing : facing.getOpposite();

        for (int j = list1.size() - 1; j >= 0; j--) {
            BlockPos blockpos = list1.get(j);
            Block block = world.getBlockState(blockpos).getBlock();
            block.dropItems(world, blockpos, world.getBlockState(blockpos), 0);
            world.removeBlock(blockpos);
            ablock[--i] = block;
        }

        for (int l = list.size() - 1; l >= 0; l--) {
            BlockPos blockpos2 = list.get(l);
            BlockState blockstate = world.getBlockState(blockpos2);
            Block block1 = blockstate.getBlock();
            int k = block1.getMetadataFromState(blockstate);
            world.removeBlock(blockpos2);
            blockpos2 = blockpos2.offset(direction);
            world.setBlockState(blockpos2, Blocks.MOVING_BLOCK.defaultState().set(FACING, facing), 4);
            world.setBlockEntity(blockpos2, MovingBlock.createMovingBlockEntity(blockstate, facing, extend, false));
            ablock[--i] = block1;
        }

        BlockPos blockpos1 = pos.offset(facing);
        if (extend) {
            PistonHeadBlock.Type pistonheadblock$type = this.sticky ? PistonHeadBlock.Type.STICKY : PistonHeadBlock.Type.DEFAULT;
            BlockState blockstate1 = Blocks.PISTON_HEAD.defaultState().set(PistonHeadBlock.FACING, facing).set(PistonHeadBlock.TYPE, pistonheadblock$type);
            BlockState blockstate2 = Blocks.MOVING_BLOCK
                .defaultState()
                .set(MovingBlock.FACING, facing)
                .set(MovingBlock.TYPE, this.sticky ? PistonHeadBlock.Type.STICKY : PistonHeadBlock.Type.DEFAULT);
            world.setBlockState(blockpos1, blockstate2, 4);
            world.setBlockEntity(blockpos1, MovingBlock.createMovingBlockEntity(blockstate1, facing, true, false));
        }

        for (int i1 = list1.size() - 1; i1 >= 0; i1--) {
            world.updateNeighbors(list1.get(i1), ablock[i++]);
        }

        for (int j1 = list.size() - 1; j1 >= 0; j1--) {
            world.updateNeighbors(list.get(j1), ablock[i++]);
        }

        if (extend) {
            world.updateNeighbors(blockpos1, Blocks.PISTON_HEAD);
            world.updateNeighbors(pos, this);
        }

        return true;
    }

    @Override
    public BlockState getStateForRendering(BlockState state) {
        return this.defaultState().set(FACING, Direction.UP);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(FACING, getFacing(metadata)).set(EXTENDED, (metadata & 8) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getId();
        if (state.get(EXTENDED)) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, EXTENDED);
    }
}
