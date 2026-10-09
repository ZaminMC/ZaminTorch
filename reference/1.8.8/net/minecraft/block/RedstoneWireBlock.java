package net.minecraft.block;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class RedstoneWireBlock extends Block {
    public static final EnumProperty<RedstoneWireBlock.ConnectionSide> NORTH = EnumProperty.of("north", RedstoneWireBlock.ConnectionSide.class);
    public static final EnumProperty<RedstoneWireBlock.ConnectionSide> EAST = EnumProperty.of("east", RedstoneWireBlock.ConnectionSide.class);
    public static final EnumProperty<RedstoneWireBlock.ConnectionSide> SOUTH = EnumProperty.of("south", RedstoneWireBlock.ConnectionSide.class);
    public static final EnumProperty<RedstoneWireBlock.ConnectionSide> WEST = EnumProperty.of("west", RedstoneWireBlock.ConnectionSide.class);
    public static final IntegerProperty POWER = IntegerProperty.of("power", 0, 15);
    private boolean shouldSignal = true;
    private final Set<BlockPos> neighborsToUpdate = Sets.newHashSet();

    public RedstoneWireBlock() {
        super(Material.DECORATION);
        this.setDefaultState(
            this.stateDefinition
                .any()
                .set(NORTH, RedstoneWireBlock.ConnectionSide.NONE)
                .set(EAST, RedstoneWireBlock.ConnectionSide.NONE)
                .set(SOUTH, RedstoneWireBlock.ConnectionSide.NONE)
                .set(WEST, RedstoneWireBlock.ConnectionSide.NONE)
                .set(POWER, 0)
        );
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.0625F, 1.0F);
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        state = state.set(WEST, this.getConnection(world, pos, Direction.WEST));
        state = state.set(EAST, this.getConnection(world, pos, Direction.EAST));
        state = state.set(NORTH, this.getConnection(world, pos, Direction.NORTH));
        return state.set(SOUTH, this.getConnection(world, pos, Direction.SOUTH));
    }

    private RedstoneWireBlock.ConnectionSide getConnection(WorldView world, BlockPos pos, Direction dir) {
        BlockPos blockpos = pos.offset(dir);
        Block block = world.getBlockState(pos.offset(dir)).getBlock();
        if (!shouldConnectTo(world.getBlockState(blockpos), dir) && (block.blocksAmbientLight() || !shouldConnectTo(world.getBlockState(blockpos.down())))) {
            Block block1 = world.getBlockState(pos.up()).getBlock();
            return !block1.blocksAmbientLight() && block.blocksAmbientLight() && shouldConnectTo(world.getBlockState(blockpos.up()))
                ? RedstoneWireBlock.ConnectionSide.UP
                : RedstoneWireBlock.ConnectionSide.NONE;
        } else {
            return RedstoneWireBlock.ConnectionSide.SIDE;
        }
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
    public int getColor(WorldView world, BlockPos pos, int tint) {
        BlockState blockstate = world.getBlockState(pos);
        return blockstate.getBlock() != this ? super.getColor(world, pos, tint) : this.getColorForPower(blockstate.get(POWER));
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return World.hasSolidTop(world, pos.down()) || world.getBlockState(pos.down()).getBlock() == Blocks.GLOWSTONE;
    }

    private BlockState updatePower(World world, BlockPos pos, BlockState state) {
        state = this.doUpdatePower(world, pos, pos, state);
        List<BlockPos> list = Lists.newArrayList(this.neighborsToUpdate);
        this.neighborsToUpdate.clear();

        for (BlockPos blockpos : list) {
            world.updateNeighbors(blockpos, this);
        }

        return state;
    }

    private BlockState doUpdatePower(World world, BlockPos pos, BlockPos source, BlockState state) {
        BlockState blockstate = state;
        int i = blockstate.get(POWER);
        int j = 0;
        j = this.getHighestWirePower(world, source, j);
        this.shouldSignal = false;
        int k = world.getNeighborSignal(pos);
        this.shouldSignal = true;
        if (k > 0 && k > j - 1) {
            j = k;
        }

        int l = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.offset(direction);
            boolean flag = blockpos.getX() != source.getX() || blockpos.getZ() != source.getZ();
            if (flag) {
                l = this.getHighestWirePower(world, blockpos, l);
            }

            if (world.getBlockState(blockpos).getBlock().isSolid() && !world.getBlockState(pos.up()).getBlock().isSolid()) {
                if (flag && pos.getY() >= source.getY()) {
                    l = this.getHighestWirePower(world, blockpos.up(), l);
                }
            } else if (!world.getBlockState(blockpos).getBlock().isSolid() && flag && pos.getY() <= source.getY()) {
                l = this.getHighestWirePower(world, blockpos.down(), l);
            }
        }

        if (l > j) {
            j = l - 1;
        } else if (j > 0) {
            j--;
        } else {
            j = 0;
        }

        if (k > j - 1) {
            j = k;
        }

        if (i != j) {
            state = state.set(POWER, j);
            if (world.getBlockState(pos) == blockstate) {
                world.setBlockState(pos, state, 2);
            }

            this.neighborsToUpdate.add(pos);

            for (Direction direction1 : Direction.values()) {
                this.neighborsToUpdate.add(pos.offset(direction1));
            }
        }

        return state;
    }

    private void updateNeighborsOfWire(World world, BlockPos pos) {
        if (world.getBlockState(pos).getBlock() == this) {
            world.updateNeighbors(pos, this);

            for (Direction direction : Direction.values()) {
                world.updateNeighbors(pos.offset(direction), this);
            }
        }
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        if (!world.isClient) {
            this.updatePower(world, pos, state);

            for (Direction direction : Direction.Plane.VERTICAL) {
                world.updateNeighbors(pos.offset(direction), this);
            }

            for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                this.updateNeighborsOfWire(world, pos.offset(direction1));
            }

            for (Direction direction2 : Direction.Plane.HORIZONTAL) {
                BlockPos blockpos = pos.offset(direction2);
                if (world.getBlockState(blockpos).getBlock().isSolid()) {
                    this.updateNeighborsOfWire(world, blockpos.up());
                } else {
                    this.updateNeighborsOfWire(world, blockpos.down());
                }
            }
        }
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        super.onRemoved(world, pos, state);
        if (!world.isClient) {
            for (Direction direction : Direction.values()) {
                world.updateNeighbors(pos.offset(direction), this);
            }

            this.updatePower(world, pos, state);

            for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                this.updateNeighborsOfWire(world, pos.offset(direction1));
            }

            for (Direction direction2 : Direction.Plane.HORIZONTAL) {
                BlockPos blockpos = pos.offset(direction2);
                if (world.getBlockState(blockpos).getBlock().isSolid()) {
                    this.updateNeighborsOfWire(world, blockpos.up());
                } else {
                    this.updateNeighborsOfWire(world, blockpos.down());
                }
            }
        }
    }

    private int getHighestWirePower(World world, BlockPos pos, int power) {
        if (world.getBlockState(pos).getBlock() != this) {
            return power;
        }

        int i = world.getBlockState(pos).get(POWER);
        return i > power ? i : power;
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!world.isClient) {
            if (this.canBePlaced(world, pos)) {
                this.updatePower(world, pos, state);
            } else {
                this.dropItems(world, pos, state, 0);
                world.removeBlock(pos);
            }
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.REDSTONE;
    }

    @Override
    public int getDirectSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return !this.shouldSignal ? 0 : this.getSignal(world, pos, state, dir);
    }

    @Override
    public int getSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        if (!this.shouldSignal) {
            return 0;
        }

        int i = state.get(POWER);
        if (i == 0) {
            return 0;
        }

        if (dir == Direction.UP) {
            return i;
        }

        EnumSet<Direction> enumset = EnumSet.noneOf(Direction.class);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (this.connectsTo(world, pos, direction)) {
                enumset.add(direction);
            }
        }

        if (dir.getAxis().isHorizontal() && enumset.isEmpty()) {
            return i;
        } else {
            return enumset.contains(dir) && !enumset.contains(dir.counterClockwiseY()) && !enumset.contains(dir.clockwiseY()) ? i : 0;
        }
    }

    private boolean connectsTo(WorldView world, BlockPos pos, Direction dir) {
        BlockPos blockpos = pos.offset(dir);
        BlockState blockstate = world.getBlockState(blockpos);
        Block block = blockstate.getBlock();
        boolean flag = block.isSolid();
        boolean flag1 = world.getBlockState(pos.up()).getBlock().isSolid();
        return !flag1 && flag && shouldConnectTo(world, blockpos.up())
            || shouldConnectTo(blockstate, dir)
            || block == Blocks.POWERED_REPEATER && blockstate.get(DiodeBlock.FACING) == dir
            || !flag && shouldConnectTo(world, blockpos.down());
    }

    protected static boolean shouldConnectTo(WorldView world, BlockPos pos) {
        return shouldConnectTo(world.getBlockState(pos));
    }

    protected static boolean shouldConnectTo(BlockState state) {
        return shouldConnectTo(state, null);
    }

    protected static boolean shouldConnectTo(BlockState state, Direction dir) {
        Block block = state.getBlock();
        if (block == Blocks.REDSTONE_WIRE) {
            return true;
        } else if (Blocks.REPEATER.isSameDiode(block)) {
            Direction direction = state.get(RepeaterBlock.FACING);
            return direction == dir || direction.getOpposite() == dir;
        } else {
            return block.isSignalSource() && dir != null;
        }
    }

    @Override
    public boolean isSignalSource() {
        return this.shouldSignal;
    }

    private int getColorForPower(int power) {
        float f = power / 15.0F;
        float f1 = f * 0.6F + 0.4F;
        if (power == 0) {
            f1 = 0.3F;
        }

        float f2 = f * f * 0.7F - 0.5F;
        float f3 = f * f * 0.6F - 0.7F;
        if (f2 < 0.0F) {
            f2 = 0.0F;
        }

        if (f3 < 0.0F) {
            f3 = 0.0F;
        }

        int i = MathHelper.clamp((int)(f1 * 255.0F), 0, 255);
        int j = MathHelper.clamp((int)(f2 * 255.0F), 0, 255);
        int k = MathHelper.clamp((int)(f3 * 255.0F), 0, 255);
        return 0xFF000000 | i << 16 | j << 8 | k;
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        int i = state.get(POWER);
        if (i != 0) {
            double d0 = pos.getX() + 0.5 + (random.nextFloat() - 0.5) * 0.2;
            double d1 = pos.getY() + 0.0625F;
            double d2 = pos.getZ() + 0.5 + (random.nextFloat() - 0.5) * 0.2;
            float f = i / 15.0F;
            float f1 = f * 0.6F + 0.4F;
            float f2 = Math.max(0.0F, f * f * 0.7F - 0.5F);
            float f3 = Math.max(0.0F, f * f * 0.6F - 0.7F);
            world.addParticle(ParticleType.REDSTONE, d0, d1, d2, f1, f2, f3);
        }
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.REDSTONE;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(POWER, metadata);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(POWER);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, NORTH, EAST, SOUTH, WEST, POWER);
    }

    enum ConnectionSide implements StringSerializable {
        UP("up"),
        SIDE("side"),
        NONE("none");

        private final String name;

        ConnectionSide(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.serializeToString();
        }

        @Override
        public String serializeToString() {
            return this.name;
        }
    }
}
