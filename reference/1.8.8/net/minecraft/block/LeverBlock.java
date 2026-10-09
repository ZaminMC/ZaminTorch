package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class LeverBlock extends Block {
    public static final EnumProperty<LeverBlock.Facing> FACING = EnumProperty.of("facing", LeverBlock.Facing.class);
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");

    protected LeverBlock() {
        super(Material.DECORATION);
        this.setDefaultState(this.stateDefinition.any().set(FACING, LeverBlock.Facing.NORTH).set(POWERED, false));
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
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
    public boolean canBePlaced(World world, BlockPos pos, Direction face) {
        return canSurvive(world, pos, face.getOpposite());
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (canSurvive(world, pos, direction)) {
                return true;
            }
        }

        return false;
    }

    protected static boolean canSurvive(World world, BlockPos pos, Direction facing) {
        return ButtonBlock.canSurvive(world, pos, facing);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        BlockState blockstate = this.defaultState().set(POWERED, false);
        if (canSurvive(world, pos, dir.getOpposite())) {
            return blockstate.set(FACING, LeverBlock.Facing.byAttachmentAndFacing(dir, entity.getHorizontalFacing()));
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction != dir && canSurvive(world, pos, direction.getOpposite())) {
                return blockstate.set(FACING, LeverBlock.Facing.byAttachmentAndFacing(direction, entity.getHorizontalFacing()));
            }
        }

        return World.hasSolidTop(world, pos.down())
            ? blockstate.set(FACING, LeverBlock.Facing.byAttachmentAndFacing(Direction.UP, entity.getHorizontalFacing()))
            : blockstate;
    }

    public static int getMetadataUnpowered(Direction facing) {
        switch (facing) {
            case DOWN:
                return 0;
            case UP:
                return 5;
            case NORTH:
                return 4;
            case SOUTH:
                return 3;
            case WEST:
                return 2;
            case EAST:
                return 1;
            default:
                return -1;
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (this.canSurviveOrBreak(world, pos, state) && !canSurvive(world, pos, state.get(FACING).getAttachmentDirection().getOpposite())) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
        }
    }

    private boolean canSurviveOrBreak(World world, BlockPos pos, BlockState state) {
        if (this.canBePlaced(world, pos)) {
            return true;
        }

        this.dropItems(world, pos, state, 0);
        world.removeBlock(pos);
        return false;
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        float f = 0.1875F;
        switch ((LeverBlock.Facing)world.getBlockState(pos).get(FACING)) {
            case EAST:
                this.setShape(0.0F, 0.2F, 0.5F - f, f * 2.0F, 0.8F, 0.5F + f);
                break;
            case WEST:
                this.setShape(1.0F - f * 2.0F, 0.2F, 0.5F - f, 1.0F, 0.8F, 0.5F + f);
                break;
            case SOUTH:
                this.setShape(0.5F - f, 0.2F, 0.0F, 0.5F + f, 0.8F, f * 2.0F);
                break;
            case NORTH:
                this.setShape(0.5F - f, 0.2F, 1.0F - f * 2.0F, 0.5F + f, 0.8F, 1.0F);
                break;
            case UP_Z:
            case UP_X:
                f = 0.25F;
                this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, 0.6F, 0.5F + f);
                break;
            case DOWN_X:
            case DOWN_Z:
                f = 0.25F;
                this.setShape(0.5F - f, 0.4F, 0.5F - f, 0.5F + f, 1.0F, 0.5F + f);
        }
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        state = state.next(POWERED);
        world.setBlockState(pos, state, 3);
        world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "random.click", 0.3F, state.get(POWERED) ? 0.6F : 0.5F);
        world.updateNeighbors(pos, this);
        Direction direction = state.get(FACING).getAttachmentDirection();
        world.updateNeighbors(pos.offset(direction.getOpposite()), this);
        return true;
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        if (state.get(POWERED)) {
            world.updateNeighbors(pos, this);
            Direction direction = state.get(FACING).getAttachmentDirection();
            world.updateNeighbors(pos.offset(direction.getOpposite()), this);
        }

        super.onRemoved(world, pos, state);
    }

    @Override
    public int getSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return state.get(POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        if (!state.get(POWERED)) {
            return 0;
        } else {
            return state.get(FACING).getAttachmentDirection() == dir ? 15 : 0;
        }
    }

    @Override
    public boolean isSignalSource() {
        return true;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(FACING, LeverBlock.Facing.byId(metadata & 7)).set(POWERED, (metadata & 8) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getId();
        if (state.get(POWERED)) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, POWERED);
    }

    public enum Facing implements StringSerializable {
        DOWN_X(0, "down_x", Direction.DOWN),
        EAST(1, "east", Direction.EAST),
        WEST(2, "west", Direction.WEST),
        SOUTH(3, "south", Direction.SOUTH),
        NORTH(4, "north", Direction.NORTH),
        UP_Z(5, "up_z", Direction.UP),
        UP_X(6, "up_x", Direction.UP),
        DOWN_Z(7, "down_z", Direction.DOWN);

        private static final LeverBlock.Facing[] BY_ID = new LeverBlock.Facing[values().length];
        private final int id;
        private final String key;
        private final Direction attachmentDir;

        Facing(int id, String key, Direction attachmentDir) {
            this.id = id;
            this.key = key;
            this.attachmentDir = attachmentDir;
        }

        public int getId() {
            return this.id;
        }

        public Direction getAttachmentDirection() {
            return this.attachmentDir;
        }

        @Override
        public String toString() {
            return this.key;
        }

        public static LeverBlock.Facing byId(int id) {
            if (id < 0 || id >= BY_ID.length) {
                id = 0;
            }

            return BY_ID[id];
        }

        public static LeverBlock.Facing byAttachmentAndFacing(Direction attachmentDir, Direction facing) {
            switch (attachmentDir) {
                case DOWN:
                    switch (facing.getAxis()) {
                        case X:
                            return DOWN_X;
                        case Z:
                            return DOWN_Z;
                        default:
                            throw new IllegalArgumentException("Invalid entityFacing " + facing + " for facing " + attachmentDir);
                    }
                case UP:
                    switch (facing.getAxis()) {
                        case X:
                            return UP_X;
                        case Z:
                            return UP_Z;
                        default:
                            throw new IllegalArgumentException("Invalid entityFacing " + facing + " for facing " + attachmentDir);
                    }
                case NORTH:
                    return NORTH;
                case SOUTH:
                    return SOUTH;
                case WEST:
                    return WEST;
                case EAST:
                    return EAST;
                default:
                    throw new IllegalArgumentException("Invalid facing: " + attachmentDir);
            }
        }

        @Override
        public String serializeToString() {
            return this.key;
        }

        static {
            for (LeverBlock.Facing leverblock$facing : values()) {
                BY_ID[leverblock$facing.getId()] = leverblock$facing;
            }
        }
    }
}
