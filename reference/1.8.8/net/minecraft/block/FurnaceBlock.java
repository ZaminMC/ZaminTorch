package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.inventory.InventoryUtils;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class FurnaceBlock extends BlockWithBlockEntity {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", Direction.Plane.HORIZONTAL);
    private final boolean lit;
    private static boolean ignoreBlockRemoval;

    protected FurnaceBlock(boolean lit) {
        super(Material.STONE);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH));
        this.lit = lit;
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(Blocks.FURNACE);
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        this.updateFacing(world, pos, state);
    }

    private void updateFacing(World world, BlockPos pos, BlockState state) {
        if (!world.isClient) {
            Block block = world.getBlockState(pos.north()).getBlock();
            Block block1 = world.getBlockState(pos.south()).getBlock();
            Block block2 = world.getBlockState(pos.west()).getBlock();
            Block block3 = world.getBlockState(pos.east()).getBlock();
            Direction direction = state.get(FACING);
            if (direction == Direction.NORTH && block.isOpaque() && !block1.isOpaque()) {
                direction = Direction.SOUTH;
            } else if (direction == Direction.SOUTH && block1.isOpaque() && !block.isOpaque()) {
                direction = Direction.NORTH;
            } else if (direction == Direction.WEST && block2.isOpaque() && !block3.isOpaque()) {
                direction = Direction.EAST;
            } else if (direction == Direction.EAST && block3.isOpaque() && !block2.isOpaque()) {
                direction = Direction.WEST;
            }

            world.setBlockState(pos, state.set(FACING, direction), 2);
        }
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        if (this.lit) {
            Direction direction = state.get(FACING);
            double d0 = pos.getX() + 0.5;
            double d1 = pos.getY() + random.nextDouble() * 6.0 / 16.0;
            double d2 = pos.getZ() + 0.5;
            double d3 = 0.52;
            double d4 = random.nextDouble() * 0.6 - 0.3;
            switch (direction) {
                case WEST:
                    world.addParticle(ParticleType.SMOKE_NORMAL, d0 - d3, d1, d2 + d4, 0.0, 0.0, 0.0);
                    world.addParticle(ParticleType.FLAME, d0 - d3, d1, d2 + d4, 0.0, 0.0, 0.0);
                    break;
                case EAST:
                    world.addParticle(ParticleType.SMOKE_NORMAL, d0 + d3, d1, d2 + d4, 0.0, 0.0, 0.0);
                    world.addParticle(ParticleType.FLAME, d0 + d3, d1, d2 + d4, 0.0, 0.0, 0.0);
                    break;
                case NORTH:
                    world.addParticle(ParticleType.SMOKE_NORMAL, d0 + d4, d1, d2 - d3, 0.0, 0.0, 0.0);
                    world.addParticle(ParticleType.FLAME, d0 + d4, d1, d2 - d3, 0.0, 0.0, 0.0);
                    break;
                case SOUTH:
                    world.addParticle(ParticleType.SMOKE_NORMAL, d0 + d4, d1, d2 + d3, 0.0, 0.0, 0.0);
                    world.addParticle(ParticleType.FLAME, d0 + d4, d1, d2 + d3, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof FurnaceBlockEntity) {
            player.openChestMenu((FurnaceBlockEntity)blockentity);
            player.incrementStat(Stats.FURNACE_INTERACTIONS);
        }

        return true;
    }

    public static void updateLitState(boolean lit, World world, BlockPos x) {
        BlockState blockstate = world.getBlockState(x);
        BlockEntity blockentity = world.getBlockEntity(x);
        ignoreBlockRemoval = true;
        if (lit) {
            world.setBlockState(x, Blocks.LIT_FURNACE.defaultState().set(FACING, blockstate.get(FACING)), 3);
            world.setBlockState(x, Blocks.LIT_FURNACE.defaultState().set(FACING, blockstate.get(FACING)), 3);
        } else {
            world.setBlockState(x, Blocks.FURNACE.defaultState().set(FACING, blockstate.get(FACING)), 3);
            world.setBlockState(x, Blocks.FURNACE.defaultState().set(FACING, blockstate.get(FACING)), 3);
        }

        ignoreBlockRemoval = false;
        if (blockentity != null) {
            blockentity.cancelRemoval();
            world.setBlockEntity(x, blockentity);
        }
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new FurnaceBlockEntity();
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState().set(FACING, entity.getHorizontalFacing().getOpposite());
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        world.setBlockState(pos, state.set(FACING, entity.getHorizontalFacing().getOpposite()), 2);
        if (item.hasCustomHoverName()) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof FurnaceBlockEntity) {
                ((FurnaceBlockEntity)blockentity).setCustomName(item.getHoverName());
            }
        }
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        if (!ignoreBlockRemoval) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof FurnaceBlockEntity) {
                InventoryUtils.dropItems(world, pos, (FurnaceBlockEntity)blockentity);
                world.updateNeighborComparators(pos, this);
            }
        }

        super.onRemoved(world, pos, state);
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
    public Item getPickItem(World world, BlockPos pos) {
        return Item.byBlock(Blocks.FURNACE);
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
