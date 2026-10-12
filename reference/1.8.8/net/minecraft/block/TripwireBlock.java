package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class TripwireBlock extends Block {
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    public static final BooleanProperty SUSPENDED = BooleanProperty.of("suspended");
    public static final BooleanProperty ATTACHED = BooleanProperty.of("attached");
    public static final BooleanProperty DISARMED = BooleanProperty.of("disarmed");
    public static final BooleanProperty NORTH = BooleanProperty.of("north");
    public static final BooleanProperty EAST = BooleanProperty.of("east");
    public static final BooleanProperty SOUTH = BooleanProperty.of("south");
    public static final BooleanProperty WEST = BooleanProperty.of("west");

    public TripwireBlock() {
        super(Material.DECORATION);
        this.setDefaultState(
            this.stateDefinition
                .any()
                .set(POWERED, false)
                .set(SUSPENDED, false)
                .set(ATTACHED, false)
                .set(DISARMED, false)
                .set(NORTH, false)
                .set(EAST, false)
                .set(SOUTH, false)
                .set(WEST, false)
        );
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.15625F, 1.0F);
        this.setTicksRandomly(true);
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        return state.set(NORTH, shouldConnectTo(world, pos, state, Direction.NORTH))
            .set(EAST, shouldConnectTo(world, pos, state, Direction.EAST))
            .set(SOUTH, shouldConnectTo(world, pos, state, Direction.SOUTH))
            .set(WEST, shouldConnectTo(world, pos, state, Direction.WEST));
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
    public BlockLayer getRenderLayer() {
        return BlockLayer.TRANSLUCENT;
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.STRING;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.STRING;
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        boolean flag = state.get(SUSPENDED);
        boolean flag1 = !World.hasSolidTop(world, pos.down());
        if (flag != flag1) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
        }
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        boolean flag = blockstate.get(ATTACHED);
        boolean flag1 = blockstate.get(SUSPENDED);
        if (!flag1) {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.09375F, 1.0F);
        } else if (!flag) {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.5F, 1.0F);
        } else {
            this.setShape(0.0F, 0.0625F, 0.0F, 1.0F, 0.15625F, 1.0F);
        }
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        state = state.set(SUSPENDED, !World.hasSolidTop(world, pos.down()));
        world.setBlockState(pos, state, 3);
        this.updateTripwireHooks(world, pos, state);
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        this.updateTripwireHooks(world, pos, state.set(POWERED, true));
    }

    @Override
    public void beforeMinedByPlayer(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            if (player.getItemInHand() != null && player.getItemInHand().getItem() == Items.SHEARS) {
                world.setBlockState(pos, state.set(DISARMED, true), 4);
            }
        }
    }

    private void updateTripwireHooks(World world, BlockPos pos, BlockState state) {
        for (Direction direction : new Direction[]{Direction.SOUTH, Direction.WEST}) {
            for (int i = 1; i < 42; i++) {
                BlockPos blockpos = pos.offset(direction, i);
                BlockState blockstate = world.getBlockState(blockpos);
                if (blockstate.getBlock() == Blocks.TRIPWIRE_HOOK) {
                    if (blockstate.get(TripwireHookBlock.FACING) == direction.getOpposite()) {
                        Blocks.TRIPWIRE_HOOK.updateState(world, blockpos, blockstate, false, true, i, state);
                    }
                    break;
                }

                if (blockstate.getBlock() != Blocks.TRIPWIRE) {
                    break;
                }
            }
        }
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient) {
            if (!state.get(POWERED)) {
                this.updateOutputState(world, pos);
            }
        }
    }

    @Override
    public void randomTick(World world, BlockPos pos, BlockState state, Random random) {
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!world.isClient) {
            if (world.getBlockState(pos).get(POWERED)) {
                this.updateOutputState(world, pos);
            }
        }
    }

    private void updateOutputState(World world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        boolean flag = blockstate.get(POWERED);
        boolean flag1 = false;
        List<? extends Entity> list = world.getEntities(
            null,
            new Box(
                pos.getX() + this.minX, pos.getY() + this.minY, pos.getZ() + this.minZ, pos.getX() + this.maxX, pos.getY() + this.maxY, pos.getZ() + this.maxZ
            )
        );
        if (!list.isEmpty()) {
            for (Entity entity : list) {
                if (!entity.canAvoidTraps()) {
                    flag1 = true;
                    break;
                }
            }
        }

        if (flag1 != flag) {
            blockstate = blockstate.set(POWERED, flag1);
            world.setBlockState(pos, blockstate, 3);
            this.updateTripwireHooks(world, pos, blockstate);
        }

        if (flag1) {
            world.scheduleTick(pos, this, this.getTickRate(world));
        }
    }

    public static boolean shouldConnectTo(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        BlockPos blockpos = pos.offset(dir);
        BlockState blockstate = world.getBlockState(blockpos);
        Block block = blockstate.getBlock();
        if (block == Blocks.TRIPWIRE_HOOK) {
            Direction direction = dir.getOpposite();
            return blockstate.get(TripwireHookBlock.FACING) == direction;
        } else if (block == Blocks.TRIPWIRE) {
            boolean flag = state.get(SUSPENDED);
            boolean flag1 = blockstate.get(SUSPENDED);
            return flag == flag1;
        } else {
            return false;
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState()
            .set(POWERED, (metadata & 1) > 0)
            .set(SUSPENDED, (metadata & 2) > 0)
            .set(ATTACHED, (metadata & 4) > 0)
            .set(DISARMED, (metadata & 8) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        if (state.get(POWERED)) {
            i |= 1;
        }

        if (state.get(SUSPENDED)) {
            i |= 2;
        }

        if (state.get(ATTACHED)) {
            i |= 4;
        }

        if (state.get(DISARMED)) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, POWERED, SUSPENDED, ATTACHED, DISARMED, NORTH, EAST, WEST, SOUTH);
    }
}
