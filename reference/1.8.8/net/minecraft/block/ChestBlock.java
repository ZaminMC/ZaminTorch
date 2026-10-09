package net.minecraft.block;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.OcelotEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryUtils;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.menu.LockableMenuProvider;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class ChestBlock extends BlockWithBlockEntity {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", Direction.Plane.HORIZONTAL);
    public final int type;

    protected ChestBlock(int type) {
        super(Material.WOOD);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH));
        this.type = type;
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
        this.setShape(0.0625F, 0.0F, 0.0625F, 0.9375F, 0.875F, 0.9375F);
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
    public int getRenderType() {
        return 2;
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        if (world.getBlockState(pos.north()).getBlock() == this) {
            this.setShape(0.0625F, 0.0F, 0.0F, 0.9375F, 0.875F, 0.9375F);
        } else if (world.getBlockState(pos.south()).getBlock() == this) {
            this.setShape(0.0625F, 0.0F, 0.0625F, 0.9375F, 0.875F, 1.0F);
        } else if (world.getBlockState(pos.west()).getBlock() == this) {
            this.setShape(0.0F, 0.0F, 0.0625F, 0.9375F, 0.875F, 0.9375F);
        } else if (world.getBlockState(pos.east()).getBlock() == this) {
            this.setShape(0.0625F, 0.0F, 0.0625F, 1.0F, 0.875F, 0.9375F);
        } else {
            this.setShape(0.0625F, 0.0F, 0.0625F, 0.9375F, 0.875F, 0.9375F);
        }
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        this.updateState(world, pos, state);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.offset(direction);
            BlockState blockstate = world.getBlockState(blockpos);
            if (blockstate.getBlock() == this) {
                this.updateState(world, blockpos, blockstate);
            }
        }
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState().set(FACING, entity.getHorizontalFacing());
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        Direction direction = Direction.byIdHorizontal(MathHelper.floor(entity.yaw * 4.0F / 360.0F + 0.5) & 3).getOpposite();
        state = state.set(FACING, direction);
        BlockPos blockpos = pos.north();
        BlockPos blockpos1 = pos.south();
        BlockPos blockpos2 = pos.west();
        BlockPos blockpos3 = pos.east();
        boolean flag = this == world.getBlockState(blockpos).getBlock();
        boolean flag1 = this == world.getBlockState(blockpos1).getBlock();
        boolean flag2 = this == world.getBlockState(blockpos2).getBlock();
        boolean flag3 = this == world.getBlockState(blockpos3).getBlock();
        if (!flag && !flag1 && !flag2 && !flag3) {
            world.setBlockState(pos, state, 3);
        } else if (direction.getAxis() != Direction.Axis.X || !flag && !flag1) {
            if (direction.getAxis() == Direction.Axis.Z && (flag2 || flag3)) {
                if (flag2) {
                    world.setBlockState(blockpos2, state, 3);
                } else {
                    world.setBlockState(blockpos3, state, 3);
                }

                world.setBlockState(pos, state, 3);
            }
        } else {
            if (flag) {
                world.setBlockState(blockpos, state, 3);
            } else {
                world.setBlockState(blockpos1, state, 3);
            }

            world.setBlockState(pos, state, 3);
        }

        if (item.hasCustomHoverName()) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof ChestBlockEntity) {
                ((ChestBlockEntity)blockentity).setCustomName(item.getHoverName());
            }
        }
    }

    public BlockState updateState(World world, BlockPos pos, BlockState state) {
        if (world.isClient) {
            return state;
        }

        BlockState blockstate = world.getBlockState(pos.north());
        BlockState blockstate1 = world.getBlockState(pos.south());
        BlockState blockstate2 = world.getBlockState(pos.west());
        BlockState blockstate3 = world.getBlockState(pos.east());
        Direction direction = state.get(FACING);
        Block block = blockstate.getBlock();
        Block block1 = blockstate1.getBlock();
        Block block2 = blockstate2.getBlock();
        Block block3 = blockstate3.getBlock();
        if (block != this && block1 != this) {
            boolean flag = block.isOpaque();
            boolean flag1 = block1.isOpaque();
            if (block2 == this || block3 == this) {
                BlockPos blockpos1 = block2 == this ? pos.west() : pos.east();
                BlockState blockstate6 = world.getBlockState(blockpos1.north());
                BlockState blockstate7 = world.getBlockState(blockpos1.south());
                direction = Direction.SOUTH;
                Direction direction2;
                if (block2 == this) {
                    direction2 = blockstate2.get(FACING);
                } else {
                    direction2 = blockstate3.get(FACING);
                }

                if (direction2 == Direction.NORTH) {
                    direction = Direction.NORTH;
                }

                Block block6 = blockstate6.getBlock();
                Block block7 = blockstate7.getBlock();
                if ((flag || block6.isOpaque()) && !flag1 && !block7.isOpaque()) {
                    direction = Direction.SOUTH;
                }

                if ((flag1 || block7.isOpaque()) && !flag && !block6.isOpaque()) {
                    direction = Direction.NORTH;
                }
            }
        } else {
            BlockPos blockpos = block == this ? pos.north() : pos.south();
            BlockState blockstate4 = world.getBlockState(blockpos.west());
            BlockState blockstate5 = world.getBlockState(blockpos.east());
            direction = Direction.EAST;
            Direction direction1;
            if (block == this) {
                direction1 = blockstate.get(FACING);
            } else {
                direction1 = blockstate1.get(FACING);
            }

            if (direction1 == Direction.WEST) {
                direction = Direction.WEST;
            }

            Block block4 = blockstate4.getBlock();
            Block block5 = blockstate5.getBlock();
            if ((block2.isOpaque() || block4.isOpaque()) && !block3.isOpaque() && !block5.isOpaque()) {
                direction = Direction.EAST;
            }

            if ((block3.isOpaque() || block5.isOpaque()) && !block2.isOpaque() && !block4.isOpaque()) {
                direction = Direction.WEST;
            }
        }

        state = state.set(FACING, direction);
        world.setBlockState(pos, state, 3);
        return state;
    }

    public BlockState updateFacing(World world, BlockPos pos, BlockState state) {
        Direction direction = null;

        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
            BlockState blockstate = world.getBlockState(pos.offset(direction1));
            if (blockstate.getBlock() == this) {
                return state;
            }

            if (blockstate.getBlock().isOpaque()) {
                if (direction != null) {
                    direction = null;
                    break;
                }

                direction = direction1;
            }
        }

        if (direction != null) {
            return state.set(FACING, direction.getOpposite());
        }

        Direction direction2 = state.get(FACING);
        if (world.getBlockState(pos.offset(direction2)).getBlock().isOpaque()) {
            direction2 = direction2.getOpposite();
        }

        if (world.getBlockState(pos.offset(direction2)).getBlock().isOpaque()) {
            direction2 = direction2.clockwiseY();
        }

        if (world.getBlockState(pos.offset(direction2)).getBlock().isOpaque()) {
            direction2 = direction2.getOpposite();
        }

        return state.set(FACING, direction2);
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        int i = 0;
        BlockPos blockpos = pos.west();
        BlockPos blockpos1 = pos.east();
        BlockPos blockpos2 = pos.north();
        BlockPos blockpos3 = pos.south();
        if (world.getBlockState(blockpos).getBlock() == this) {
            if (this.isDoubleChest(world, blockpos)) {
                return false;
            }

            i++;
        }

        if (world.getBlockState(blockpos1).getBlock() == this) {
            if (this.isDoubleChest(world, blockpos1)) {
                return false;
            }

            i++;
        }

        if (world.getBlockState(blockpos2).getBlock() == this) {
            if (this.isDoubleChest(world, blockpos2)) {
                return false;
            }

            i++;
        }

        if (world.getBlockState(blockpos3).getBlock() == this) {
            if (this.isDoubleChest(world, blockpos3)) {
                return false;
            }

            i++;
        }

        return i <= 1;
    }

    private boolean isDoubleChest(World world, BlockPos pos) {
        if (world.getBlockState(pos).getBlock() != this) {
            return false;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (world.getBlockState(pos.offset(direction)).getBlock() == this) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        super.neighborChanged(world, pos, state, neighborBlock);
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof ChestBlockEntity) {
            blockentity.clearBlockCache();
        }
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof Inventory) {
            InventoryUtils.dropItems(world, pos, (Inventory)blockentity);
            world.updateNeighborComparators(pos, this);
        }

        super.onRemoved(world, pos, state);
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        LockableMenuProvider lockablemenuprovider = this.getInventory(world, pos);
        if (lockablemenuprovider != null) {
            player.openChestMenu(lockablemenuprovider);
            if (this.type == 0) {
                player.incrementStat(Stats.CHESTS_OPENED);
            } else if (this.type == 1) {
                player.incrementStat(Stats.TRAPPED_CHESTS_TRIGGERED);
            }
        }

        return true;
    }

    public LockableMenuProvider getInventory(World world, BlockPos pos) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (!(blockentity instanceof ChestBlockEntity)) {
            return null;
        }

        LockableMenuProvider lockablemenuprovider = (ChestBlockEntity)blockentity;
        if (this.isLocked(world, pos)) {
            return null;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.offset(direction);
            Block block = world.getBlockState(blockpos).getBlock();
            if (block == this) {
                if (this.isLocked(world, blockpos)) {
                    return null;
                }

                BlockEntity blockentity1 = world.getBlockEntity(blockpos);
                if (blockentity1 instanceof ChestBlockEntity) {
                    if (direction != Direction.WEST && direction != Direction.NORTH) {
                        lockablemenuprovider = new DoubleInventory("container.chestDouble", lockablemenuprovider, (ChestBlockEntity)blockentity1);
                    } else {
                        lockablemenuprovider = new DoubleInventory("container.chestDouble", (ChestBlockEntity)blockentity1, lockablemenuprovider);
                    }
                }
            }
        }

        return lockablemenuprovider;
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new ChestBlockEntity();
    }

    @Override
    public boolean isSignalSource() {
        return this.type == 1;
    }

    @Override
    public int getSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        if (!this.isSignalSource()) {
            return 0;
        }

        int i = 0;
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof ChestBlockEntity) {
            i = ((ChestBlockEntity)blockentity).viewerCount;
        }

        return MathHelper.clamp(i, 0, 15);
    }

    @Override
    public int getDirectSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return dir == Direction.UP ? this.getSignal(world, pos, state, dir) : 0;
    }

    private boolean isLocked(World world, BlockPos pos) {
        return this.isLockedByBlock(world, pos) || this.isLockedByOcelot(world, pos);
    }

    private boolean isLockedByBlock(World world, BlockPos pos) {
        return world.getBlockState(pos.up()).getBlock().isSolid();
    }

    private boolean isLockedByOcelot(World world, BlockPos pos) {
        for (Entity entity : world.getEntitiesOfType(
            OcelotEntity.class, new Box(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1)
        )) {
            OcelotEntity ocelotentity = (OcelotEntity)entity;
            if (ocelotentity.isSitting()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isAnalogSignalSource() {
        return true;
    }

    @Override
    public int getAnalogSignal(World world, BlockPos pos) {
        return InventoryMenu.getAnalogSignal(this.getInventory(world, pos));
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
