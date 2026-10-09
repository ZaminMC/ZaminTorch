package net.minecraft.block;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.Property;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public abstract class AbstractRailBlock extends Block {
    protected final boolean alwaysStraight;

    public static boolean isRail(World world, BlockPos pos) {
        return isRail(world.getBlockState(pos));
    }

    public static boolean isRail(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.RAIL || block == Blocks.POWERED_RAIL || block == Blocks.DETECTOR_RAIL || block == Blocks.ACTIVATOR_RAIL;
    }

    protected AbstractRailBlock(boolean alwaysStraight) {
        super(Material.DECORATION);
        this.alwaysStraight = alwaysStraight;
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.125F, 1.0F);
        this.setCreativeModeTab(CreativeModeTab.TRANSPORTATION);
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
    public HitResult rayTrace(World world, BlockPos pos, Vec3d start, Vec3d end) {
        this.updateShape(world, pos);
        return super.rayTrace(world, pos, start, end);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        AbstractRailBlock.Shape abstractrailblock$shape = blockstate.getBlock() == this ? blockstate.get(this.getShapeProperty()) : null;
        if (abstractrailblock$shape != null && abstractrailblock$shape.isAscending()) {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.625F, 1.0F);
        } else {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.125F, 1.0F);
        }
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return World.hasSolidTop(world, pos.down());
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        if (!world.isClient) {
            state = this.updateShape(world, pos, state, true);
            if (this.alwaysStraight) {
                this.neighborChanged(world, pos, state, this);
            }
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!world.isClient) {
            AbstractRailBlock.Shape abstractrailblock$shape = state.get(this.getShapeProperty());
            boolean flag = false;
            if (!World.hasSolidTop(world, pos.down())) {
                flag = true;
            }

            if (abstractrailblock$shape == AbstractRailBlock.Shape.ASCENDING_EAST && !World.hasSolidTop(world, pos.east())) {
                flag = true;
            } else if (abstractrailblock$shape == AbstractRailBlock.Shape.ASCENDING_WEST && !World.hasSolidTop(world, pos.west())) {
                flag = true;
            } else if (abstractrailblock$shape == AbstractRailBlock.Shape.ASCENDING_NORTH && !World.hasSolidTop(world, pos.north())) {
                flag = true;
            } else if (abstractrailblock$shape == AbstractRailBlock.Shape.ASCENDING_SOUTH && !World.hasSolidTop(world, pos.south())) {
                flag = true;
            }

            if (flag) {
                this.dropItems(world, pos, state, 0);
                world.removeBlock(pos);
            } else {
                this.updateState(world, pos, state, neighborBlock);
            }
        }
    }

    protected void updateState(World world, BlockPos pos, BlockState state, Block neighborBlock) {
    }

    protected BlockState updateShape(World world, BlockPos pos, BlockState state, boolean force) {
        return world.isClient ? state : new AbstractRailBlock.RailNode(world, pos, state).updateState(world.hasNeighborSignal(pos), force).getState();
    }

    @Override
    public int getPistonMoveBehavior() {
        return 0;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        super.onRemoved(world, pos, state);
        if (state.get(this.getShapeProperty()).isAscending()) {
            world.updateNeighbors(pos.up(), this);
        }

        if (this.alwaysStraight) {
            world.updateNeighbors(pos, this);
            world.updateNeighbors(pos.down(), this);
        }
    }

    public abstract Property<AbstractRailBlock.Shape> getShapeProperty();

    public class RailNode {
        private final World world;
        private final BlockPos pos;
        private final AbstractRailBlock block;
        private BlockState state;
        private final boolean alwaysStraight;
        private final List<BlockPos> connections = Lists.newArrayList();

        public RailNode(World world, BlockPos pos, BlockState state) {
            this.world = world;
            this.pos = pos;
            this.state = state;
            this.block = (AbstractRailBlock)state.getBlock();
            AbstractRailBlock.Shape abstractrailblock$shape = state.get(AbstractRailBlock.this.getShapeProperty());
            this.alwaysStraight = this.block.alwaysStraight;
            this.updateConnections(abstractrailblock$shape);
        }

        private void updateConnections(AbstractRailBlock.Shape shape) {
            this.connections.clear();
            switch (shape) {
                case NORTH_SOUTH:
                    this.connections.add(this.pos.north());
                    this.connections.add(this.pos.south());
                    break;
                case EAST_WEST:
                    this.connections.add(this.pos.west());
                    this.connections.add(this.pos.east());
                    break;
                case ASCENDING_EAST:
                    this.connections.add(this.pos.west());
                    this.connections.add(this.pos.east().up());
                    break;
                case ASCENDING_WEST:
                    this.connections.add(this.pos.west().up());
                    this.connections.add(this.pos.east());
                    break;
                case ASCENDING_NORTH:
                    this.connections.add(this.pos.north().up());
                    this.connections.add(this.pos.south());
                    break;
                case ASCENDING_SOUTH:
                    this.connections.add(this.pos.north());
                    this.connections.add(this.pos.south().up());
                    break;
                case SOUTH_EAST:
                    this.connections.add(this.pos.east());
                    this.connections.add(this.pos.south());
                    break;
                case SOUTH_WEST:
                    this.connections.add(this.pos.west());
                    this.connections.add(this.pos.south());
                    break;
                case NORTH_WEST:
                    this.connections.add(this.pos.west());
                    this.connections.add(this.pos.north());
                    break;
                case NORTH_EAST:
                    this.connections.add(this.pos.east());
                    this.connections.add(this.pos.north());
            }
        }

        private void removeSoftConnections() {
            for (int i = 0; i < this.connections.size(); i++) {
                AbstractRailBlock.RailNode abstractrailblock$railnode = this.getNeighborRail(this.connections.get(i));
                if (abstractrailblock$railnode != null && abstractrailblock$railnode.connectsTo(this)) {
                    this.connections.set(i, abstractrailblock$railnode.pos);
                } else {
                    this.connections.remove(i--);
                }
            }
        }

        private boolean couldConnectTo(BlockPos pos) {
            return AbstractRailBlock.isRail(this.world, pos)
                || AbstractRailBlock.isRail(this.world, pos.up())
                || AbstractRailBlock.isRail(this.world, pos.down());
        }

        private AbstractRailBlock.RailNode getNeighborRail(BlockPos pos) {
            BlockPos blockpos = pos;
            BlockState blockstate = this.world.getBlockState(blockpos);
            if (AbstractRailBlock.isRail(blockstate)) {
                return AbstractRailBlock.this.new RailNode(this.world, blockpos, blockstate);
            }

            blockpos = pos.up();
            blockstate = this.world.getBlockState(blockpos);
            if (AbstractRailBlock.isRail(blockstate)) {
                return AbstractRailBlock.this.new RailNode(this.world, blockpos, blockstate);
            }

            blockpos = pos.down();
            blockstate = this.world.getBlockState(blockpos);
            return AbstractRailBlock.isRail(blockstate) ? AbstractRailBlock.this.new RailNode(this.world, blockpos, blockstate) : null;
        }

        private boolean connectsTo(AbstractRailBlock.RailNode rail) {
            return this.hasConnection(rail.pos);
        }

        private boolean hasConnection(BlockPos pos) {
            for (int i = 0; i < this.connections.size(); i++) {
                BlockPos blockpos = this.connections.get(i);
                if (blockpos.getX() == pos.getX() && blockpos.getZ() == pos.getZ()) {
                    return true;
                }
            }

            return false;
        }

        protected int countConnections() {
            int i = 0;

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (this.couldConnectTo(this.pos.offset(direction))) {
                    i++;
                }
            }

            return i;
        }

        private boolean canConnectTo(AbstractRailBlock.RailNode rail) {
            return this.connectsTo(rail) || this.connections.size() != 2;
        }

        private void addConnection(AbstractRailBlock.RailNode rail) {
            this.connections.add(rail.pos);
            BlockPos blockpos = this.pos.north();
            BlockPos blockpos1 = this.pos.south();
            BlockPos blockpos2 = this.pos.west();
            BlockPos blockpos3 = this.pos.east();
            boolean flag = this.hasConnection(blockpos);
            boolean flag1 = this.hasConnection(blockpos1);
            boolean flag2 = this.hasConnection(blockpos2);
            boolean flag3 = this.hasConnection(blockpos3);
            AbstractRailBlock.Shape abstractrailblock$shape = null;
            if (flag || flag1) {
                abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_SOUTH;
            }

            if (flag2 || flag3) {
                abstractrailblock$shape = AbstractRailBlock.Shape.EAST_WEST;
            }

            if (!this.alwaysStraight) {
                if (flag1 && flag3 && !flag && !flag2) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.SOUTH_EAST;
                }

                if (flag1 && flag2 && !flag && !flag3) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.SOUTH_WEST;
                }

                if (flag && flag2 && !flag1 && !flag3) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_WEST;
                }

                if (flag && flag3 && !flag1 && !flag2) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_EAST;
                }
            }

            if (abstractrailblock$shape == AbstractRailBlock.Shape.NORTH_SOUTH) {
                if (AbstractRailBlock.isRail(this.world, blockpos.up())) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.ASCENDING_NORTH;
                }

                if (AbstractRailBlock.isRail(this.world, blockpos1.up())) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.ASCENDING_SOUTH;
                }
            }

            if (abstractrailblock$shape == AbstractRailBlock.Shape.EAST_WEST) {
                if (AbstractRailBlock.isRail(this.world, blockpos3.up())) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.ASCENDING_EAST;
                }

                if (AbstractRailBlock.isRail(this.world, blockpos2.up())) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.ASCENDING_WEST;
                }
            }

            if (abstractrailblock$shape == null) {
                abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_SOUTH;
            }

            this.state = this.state.set(this.block.getShapeProperty(), abstractrailblock$shape);
            this.world.setBlockState(this.pos, this.state, 3);
        }

        private boolean hasNeighborRail(BlockPos pos) {
            AbstractRailBlock.RailNode abstractrailblock$railnode = this.getNeighborRail(pos);
            if (abstractrailblock$railnode == null) {
                return false;
            }

            abstractrailblock$railnode.removeSoftConnections();
            return abstractrailblock$railnode.canConnectTo(this);
        }

        public AbstractRailBlock.RailNode updateState(boolean powered, boolean force) {
            BlockPos blockpos = this.pos.north();
            BlockPos blockpos1 = this.pos.south();
            BlockPos blockpos2 = this.pos.west();
            BlockPos blockpos3 = this.pos.east();
            boolean flag = this.hasNeighborRail(blockpos);
            boolean flag1 = this.hasNeighborRail(blockpos1);
            boolean flag2 = this.hasNeighborRail(blockpos2);
            boolean flag3 = this.hasNeighborRail(blockpos3);
            AbstractRailBlock.Shape abstractrailblock$shape = null;
            if ((flag || flag1) && !flag2 && !flag3) {
                abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_SOUTH;
            }

            if ((flag2 || flag3) && !flag && !flag1) {
                abstractrailblock$shape = AbstractRailBlock.Shape.EAST_WEST;
            }

            if (!this.alwaysStraight) {
                if (flag1 && flag3 && !flag && !flag2) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.SOUTH_EAST;
                }

                if (flag1 && flag2 && !flag && !flag3) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.SOUTH_WEST;
                }

                if (flag && flag2 && !flag1 && !flag3) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_WEST;
                }

                if (flag && flag3 && !flag1 && !flag2) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_EAST;
                }
            }

            if (abstractrailblock$shape == null) {
                if (flag || flag1) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_SOUTH;
                }

                if (flag2 || flag3) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.EAST_WEST;
                }

                if (!this.alwaysStraight) {
                    if (powered) {
                        if (flag1 && flag3) {
                            abstractrailblock$shape = AbstractRailBlock.Shape.SOUTH_EAST;
                        }

                        if (flag2 && flag1) {
                            abstractrailblock$shape = AbstractRailBlock.Shape.SOUTH_WEST;
                        }

                        if (flag3 && flag) {
                            abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_EAST;
                        }

                        if (flag && flag2) {
                            abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_WEST;
                        }
                    } else {
                        if (flag && flag2) {
                            abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_WEST;
                        }

                        if (flag3 && flag) {
                            abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_EAST;
                        }

                        if (flag2 && flag1) {
                            abstractrailblock$shape = AbstractRailBlock.Shape.SOUTH_WEST;
                        }

                        if (flag1 && flag3) {
                            abstractrailblock$shape = AbstractRailBlock.Shape.SOUTH_EAST;
                        }
                    }
                }
            }

            if (abstractrailblock$shape == AbstractRailBlock.Shape.NORTH_SOUTH) {
                if (AbstractRailBlock.isRail(this.world, blockpos.up())) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.ASCENDING_NORTH;
                }

                if (AbstractRailBlock.isRail(this.world, blockpos1.up())) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.ASCENDING_SOUTH;
                }
            }

            if (abstractrailblock$shape == AbstractRailBlock.Shape.EAST_WEST) {
                if (AbstractRailBlock.isRail(this.world, blockpos3.up())) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.ASCENDING_EAST;
                }

                if (AbstractRailBlock.isRail(this.world, blockpos2.up())) {
                    abstractrailblock$shape = AbstractRailBlock.Shape.ASCENDING_WEST;
                }
            }

            if (abstractrailblock$shape == null) {
                abstractrailblock$shape = AbstractRailBlock.Shape.NORTH_SOUTH;
            }

            this.updateConnections(abstractrailblock$shape);
            this.state = this.state.set(this.block.getShapeProperty(), abstractrailblock$shape);
            if (force || this.world.getBlockState(this.pos) != this.state) {
                this.world.setBlockState(this.pos, this.state, 3);

                for (int i = 0; i < this.connections.size(); i++) {
                    AbstractRailBlock.RailNode abstractrailblock$railnode = this.getNeighborRail(this.connections.get(i));
                    if (abstractrailblock$railnode != null) {
                        abstractrailblock$railnode.removeSoftConnections();
                        if (abstractrailblock$railnode.canConnectTo(this)) {
                            abstractrailblock$railnode.addConnection(this);
                        }
                    }
                }
            }

            return this;
        }

        public BlockState getState() {
            return this.state;
        }
    }

    public enum Shape implements StringSerializable {
        NORTH_SOUTH(0, "north_south"),
        EAST_WEST(1, "east_west"),
        ASCENDING_EAST(2, "ascending_east"),
        ASCENDING_WEST(3, "ascending_west"),
        ASCENDING_NORTH(4, "ascending_north"),
        ASCENDING_SOUTH(5, "ascending_south"),
        SOUTH_EAST(6, "south_east"),
        SOUTH_WEST(7, "south_west"),
        NORTH_WEST(8, "north_west"),
        NORTH_EAST(9, "north_east");

        private static final AbstractRailBlock.Shape[] BY_ID = new AbstractRailBlock.Shape[values().length];
        private final int id;
        private final String key;

        Shape(int id, String key) {
            this.id = id;
            this.key = key;
        }

        public int getId() {
            return this.id;
        }

        @Override
        public String toString() {
            return this.key;
        }

        public boolean isAscending() {
            return this == ASCENDING_NORTH || this == ASCENDING_EAST || this == ASCENDING_SOUTH || this == ASCENDING_WEST;
        }

        public static AbstractRailBlock.Shape byId(int id) {
            if (id < 0 || id >= BY_ID.length) {
                id = 0;
            }

            return BY_ID[id];
        }

        @Override
        public String serializeToString() {
            return this.key;
        }

        static {
            for (AbstractRailBlock.Shape abstractrailblock$shape : values()) {
                BY_ID[abstractrailblock$shape.getId()] = abstractrailblock$shape;
            }
        }
    }
}
