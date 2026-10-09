package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class TrapdoorBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty OPEN = BooleanProperty.of("open");
    public static final EnumProperty<TrapdoorBlock.Half> HALF = EnumProperty.of("half", TrapdoorBlock.Half.class);

    protected TrapdoorBlock(Material material) {
        super(material);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(OPEN, false).set(HALF, TrapdoorBlock.Half.BOTTOM));
        float f = 0.5F;
        float f1 = 1.0F;
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
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
    public boolean canWalkThrough(WorldView world, BlockPos pos) {
        return !world.getBlockState(pos).get(OPEN);
    }

    @Override
    public Box getOutlineShape(World world, BlockPos pos) {
        this.updateShape(world, pos);
        return super.getOutlineShape(world, pos);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        this.updateShape(world, pos);
        return super.getCollisionShape(world, pos, state);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        this.updateShape(world.getBlockState(pos));
    }

    @Override
    public void resetShape() {
        float f = 0.1875F;
        this.setShape(0.0F, 0.40625F, 0.0F, 1.0F, 0.59375F, 1.0F);
    }

    public void updateShape(BlockState state) {
        if (state.getBlock() == this) {
            boolean flag = state.get(HALF) == TrapdoorBlock.Half.TOP;
            Boolean obool = state.get(OPEN);
            Direction direction = state.get(FACING);
            float f = 0.1875F;
            if (flag) {
                this.setShape(0.0F, 0.8125F, 0.0F, 1.0F, 1.0F, 1.0F);
            } else {
                this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.1875F, 1.0F);
            }

            if (obool) {
                if (direction == Direction.NORTH) {
                    this.setShape(0.0F, 0.0F, 0.8125F, 1.0F, 1.0F, 1.0F);
                }

                if (direction == Direction.SOUTH) {
                    this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.1875F);
                }

                if (direction == Direction.WEST) {
                    this.setShape(0.8125F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
                }

                if (direction == Direction.EAST) {
                    this.setShape(0.0F, 0.0F, 0.0F, 0.1875F, 1.0F, 1.0F);
                }
            }
        }
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (this.material == Material.IRON) {
            return true;
        }

        state = state.next(OPEN);
        world.setBlockState(pos, state, 2);
        world.doEvent(player, state.get(OPEN) ? 1003 : 1006, pos, 0);
        return true;
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!world.isClient) {
            BlockPos blockpos = pos.offset(state.get(FACING).getOpposite());
            if (!canBePlacedOn(world.getBlockState(blockpos).getBlock())) {
                world.removeBlock(pos);
                this.dropItems(world, pos, state, 0);
            } else {
                boolean flag = world.hasNeighborSignal(pos);
                if (flag || neighborBlock.isSignalSource()) {
                    boolean flag1 = state.get(OPEN);
                    if (flag1 != flag) {
                        world.setBlockState(pos, state.set(OPEN, flag), 2);
                        world.doEvent(null, flag ? 1003 : 1006, pos, 0);
                    }
                }
            }
        }
    }

    @Override
    public HitResult rayTrace(World world, BlockPos pos, Vec3d start, Vec3d end) {
        this.updateShape(world, pos);
        return super.rayTrace(world, pos, start, end);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        BlockState blockstate = this.defaultState();
        if (dir.getAxis().isHorizontal()) {
            blockstate = blockstate.set(FACING, dir).set(OPEN, false);
            blockstate = blockstate.set(HALF, dy > 0.5F ? TrapdoorBlock.Half.TOP : TrapdoorBlock.Half.BOTTOM);
        }

        return blockstate;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos, Direction face) {
        return !face.getAxis().isVertical() && canBePlacedOn(world.getBlockState(pos.offset(face.getOpposite())).getBlock());
    }

    protected static Direction getFacing(int metadata) {
        switch (metadata & 3) {
            case 0:
                return Direction.NORTH;
            case 1:
                return Direction.SOUTH;
            case 2:
                return Direction.WEST;
            case 3:
            default:
                return Direction.EAST;
        }
    }

    protected static int getMetadataClosed(Direction facing) {
        switch (facing) {
            case NORTH:
                return 0;
            case SOUTH:
                return 1;
            case WEST:
                return 2;
            case EAST:
            default:
                return 3;
        }
    }

    private static boolean canBePlacedOn(Block block) {
        return block.material.isSolidBlocking() && block.isCube() || block == Blocks.GLOWSTONE || block instanceof SlabBlock || block instanceof StairsBlock;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState()
            .set(FACING, getFacing(metadata))
            .set(OPEN, (metadata & 4) != 0)
            .set(HALF, (metadata & 8) == 0 ? TrapdoorBlock.Half.BOTTOM : TrapdoorBlock.Half.TOP);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= getMetadataClosed(state.get(FACING));
        if (state.get(OPEN)) {
            i |= 4;
        }

        if (state.get(HALF) == TrapdoorBlock.Half.TOP) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, OPEN, HALF);
    }

    public enum Half implements StringSerializable {
        TOP("top"),
        BOTTOM("bottom");

        private final String key;

        Half(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return this.key;
        }

        @Override
        public String serializeToString() {
            return this.key;
        }
    }
}
