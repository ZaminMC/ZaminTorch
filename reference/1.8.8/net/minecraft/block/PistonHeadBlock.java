package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class PistonHeadBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.of("facing");
    public static final EnumProperty<PistonHeadBlock.Type> TYPE = EnumProperty.of("type", PistonHeadBlock.Type.class);
    public static final BooleanProperty SHORT = BooleanProperty.of("short");

    public PistonHeadBlock() {
        super(Material.PISTON);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(TYPE, PistonHeadBlock.Type.DEFAULT).set(SHORT, false));
        this.setSounds(STONE_SOUNDS);
        this.setStrength(0.5F);
    }

    @Override
    public void beforeMinedByPlayer(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (player.abilities.creativeMode) {
            Direction direction = state.get(FACING);
            if (direction != null) {
                BlockPos blockpos = pos.offset(direction.getOpposite());
                Block block = world.getBlockState(blockpos).getBlock();
                if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON) {
                    world.removeBlock(blockpos);
                }
            }
        }

        super.beforeMinedByPlayer(world, pos, state, player);
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        super.onRemoved(world, pos, state);
        Direction direction = state.get(FACING).getOpposite();
        pos = pos.offset(direction);
        BlockState blockstate = world.getBlockState(pos);
        if ((blockstate.getBlock() == Blocks.PISTON || blockstate.getBlock() == Blocks.STICKY_PISTON) && blockstate.get(PistonBaseBlock.EXTENDED)) {
            blockstate.getBlock().dropItems(world, pos, blockstate, 0);
            world.removeBlock(pos);
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
    public boolean canBePlaced(World world, BlockPos pos) {
        return false;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos, Direction face) {
        return false;
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 0;
    }

    @Override
    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
        this.setHeadShapeForCollisions(state);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        this.setArmShapeForCollisions(state);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    private void setArmShapeForCollisions(BlockState state) {
        float f = 0.25F;
        float f1 = 0.375F;
        float f2 = 0.625F;
        float f3 = 0.25F;
        float f4 = 0.75F;
        switch ((Direction)state.get(FACING)) {
            case DOWN:
                this.setShape(0.375F, 0.25F, 0.375F, 0.625F, 1.0F, 0.625F);
                break;
            case UP:
                this.setShape(0.375F, 0.0F, 0.375F, 0.625F, 0.75F, 0.625F);
                break;
            case NORTH:
                this.setShape(0.25F, 0.375F, 0.25F, 0.75F, 0.625F, 1.0F);
                break;
            case SOUTH:
                this.setShape(0.25F, 0.375F, 0.0F, 0.75F, 0.625F, 0.75F);
                break;
            case WEST:
                this.setShape(0.375F, 0.25F, 0.25F, 0.625F, 0.75F, 1.0F);
                break;
            case EAST:
                this.setShape(0.0F, 0.375F, 0.25F, 0.75F, 0.625F, 0.75F);
        }
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        this.setHeadShapeForCollisions(world.getBlockState(pos));
    }

    public void setHeadShapeForCollisions(BlockState state) {
        float f = 0.25F;
        Direction direction = state.get(FACING);
        if (direction != null) {
            switch (direction) {
                case DOWN:
                    this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.25F, 1.0F);
                    break;
                case UP:
                    this.setShape(0.0F, 0.75F, 0.0F, 1.0F, 1.0F, 1.0F);
                    break;
                case NORTH:
                    this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.25F);
                    break;
                case SOUTH:
                    this.setShape(0.0F, 0.0F, 0.75F, 1.0F, 1.0F, 1.0F);
                    break;
                case WEST:
                    this.setShape(0.0F, 0.0F, 0.0F, 0.25F, 1.0F, 1.0F);
                    break;
                case EAST:
                    this.setShape(0.75F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        Direction direction = state.get(FACING);
        BlockPos blockpos = pos.offset(direction.getOpposite());
        BlockState blockstate = world.getBlockState(blockpos);
        if (blockstate.getBlock() != Blocks.PISTON && blockstate.getBlock() != Blocks.STICKY_PISTON) {
            world.removeBlock(pos);
        } else {
            blockstate.getBlock().neighborChanged(world, blockpos, blockstate, neighborBlock);
        }
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return true;
    }

    public static Direction getFacing(int metadata) {
        int i = metadata & 7;
        return i > 5 ? null : Direction.byId(i);
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return world.getBlockState(pos).get(TYPE) == PistonHeadBlock.Type.STICKY ? Item.byBlock(Blocks.STICKY_PISTON) : Item.byBlock(Blocks.PISTON);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(FACING, getFacing(metadata)).set(TYPE, (metadata & 8) > 0 ? PistonHeadBlock.Type.STICKY : PistonHeadBlock.Type.DEFAULT);
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
        return new StateDefinition(this, FACING, TYPE, SHORT);
    }

    public enum Type implements StringSerializable {
        DEFAULT("normal"),
        STICKY("sticky");

        private final String key;

        Type(String key) {
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
