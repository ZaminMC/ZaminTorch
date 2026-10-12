package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.dispenser.DispenseBehavior;
import net.minecraft.block.dispenser.DispenseItemBehavior;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.InventoryUtils;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.IPosition;
import net.minecraft.util.math.Position;
import net.minecraft.util.registry.DefaultedRegistry;
import net.minecraft.world.BlockSource;
import net.minecraft.world.IBlockSource;
import net.minecraft.world.World;

public class DispenserBlock extends BlockWithBlockEntity {
    public static final DirectionProperty FACING = DirectionProperty.of("facing");
    public static final BooleanProperty TRIGGERED = BooleanProperty.of("triggered");
    public static final DefaultedRegistry<Item, DispenseBehavior> BEHAVIORS = new DefaultedRegistry<>(new DispenseItemBehavior());
    protected Random random = new Random();

    protected DispenserBlock() {
        super(Material.STONE);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(TRIGGERED, false));
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
    }

    @Override
    public int getTickRate(World world) {
        return 4;
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        super.onAdded(world, pos, state);
        this.updateFacing(world, pos, state);
    }

    private void updateFacing(World world, BlockPos pos, BlockState state) {
        if (!world.isClient) {
            Direction direction = state.get(FACING);
            boolean flag = world.getBlockState(pos.north()).getBlock().isOpaque();
            boolean flag1 = world.getBlockState(pos.south()).getBlock().isOpaque();
            if (direction == Direction.NORTH && flag && !flag1) {
                direction = Direction.SOUTH;
            } else if (direction == Direction.SOUTH && flag1 && !flag) {
                direction = Direction.NORTH;
            } else {
                boolean flag2 = world.getBlockState(pos.west()).getBlock().isOpaque();
                boolean flag3 = world.getBlockState(pos.east()).getBlock().isOpaque();
                if (direction == Direction.WEST && flag2 && !flag3) {
                    direction = Direction.EAST;
                } else if (direction == Direction.EAST && flag3 && !flag2) {
                    direction = Direction.WEST;
                }
            }

            world.setBlockState(pos, state.set(FACING, direction).set(TRIGGERED, false), 2);
        }
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof DispenserBlockEntity) {
            player.openChestMenu((DispenserBlockEntity)blockentity);
            if (blockentity instanceof DropperBlockEntity) {
                player.incrementStat(Stats.DROPPERS_INSPECTED);
            } else {
                player.incrementStat(Stats.DISPENSERS_INSPECTED);
            }
        }

        return true;
    }

    protected void dispense(World world, BlockPos pos) {
        BlockSource blocksource = new BlockSource(world, pos);
        DispenserBlockEntity dispenserblockentity = blocksource.getBlockEntity();
        if (dispenserblockentity != null) {
            int i = dispenserblockentity.pickNonEmptySlot();
            if (i < 0) {
                world.doEvent(1001, pos, 0);
            } else {
                ItemStack itemstack = dispenserblockentity.getItem(i);
                DispenseBehavior dispensebehavior = this.getDispenseBehavior(itemstack);
                if (dispensebehavior != DispenseBehavior.NONE) {
                    ItemStack itemstack1 = dispensebehavior.dispense(blocksource, itemstack);
                    dispenserblockentity.setItem(i, itemstack1.size <= 0 ? null : itemstack1);
                }
            }
        }
    }

    protected DispenseBehavior getDispenseBehavior(ItemStack item) {
        return BEHAVIORS.get(item == null ? null : item.getItem());
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        boolean flag = world.hasNeighborSignal(pos) || world.hasNeighborSignal(pos.up());
        boolean flag1 = state.get(TRIGGERED);
        if (flag && !flag1) {
            world.scheduleTick(pos, this, this.getTickRate(world));
            world.setBlockState(pos, state.set(TRIGGERED, true), 4);
        } else if (!flag && flag1) {
            world.setBlockState(pos, state.set(TRIGGERED, false), 4);
        }
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!world.isClient) {
            this.dispense(world, pos);
        }
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new DispenserBlockEntity();
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState().set(FACING, PistonBaseBlock.getFacingForPlacement(world, pos, entity)).set(TRIGGERED, false);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        world.setBlockState(pos, state.set(FACING, PistonBaseBlock.getFacingForPlacement(world, pos, entity)), 2);
        if (item.hasCustomHoverName()) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof DispenserBlockEntity) {
                ((DispenserBlockEntity)blockentity).setCustomName(item.getHoverName());
            }
        }
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof DispenserBlockEntity) {
            InventoryUtils.dropItems(world, pos, (DispenserBlockEntity)blockentity);
            world.updateNeighborComparators(pos, this);
        }

        super.onRemoved(world, pos, state);
    }

    public static IPosition getDispensePos(IBlockSource block) {
        Direction direction = getDirection(block.getBlockMetadata());
        double d0 = block.getX() + 0.7 * direction.getOffsetX();
        double d1 = block.getY() + 0.7 * direction.getOffsetY();
        double d2 = block.getZ() + 0.7 * direction.getOffsetZ();
        return new Position(d0, d1, d2);
    }

    public static Direction getDirection(int id) {
        return Direction.byId(id & 7);
    }

    @Override
    public boolean isAnalogSignalSource() {
        return true;
    }

    @Override
    public int getAnalogSignal(World world, BlockPos pos) {
        return InventoryMenu.getAnalogSignal(world.getBlockEntity(pos));
    }

    @Override
    public int getRenderType() {
        return 3;
    }

    @Override
    public BlockState getStateForRendering(BlockState state) {
        return this.defaultState().set(FACING, Direction.SOUTH);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(FACING, getDirection(metadata)).set(TRIGGERED, (metadata & 8) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getId();
        if (state.get(TRIGGERED)) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, TRIGGERED);
    }
}
