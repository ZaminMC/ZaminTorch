package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class CocoaBlock extends HorizontalFacingBlock implements Fertilizable {
    public static final IntegerProperty AGE = IntegerProperty.of("age", 0, 2);

    public CocoaBlock() {
        super(Material.PLANT);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(AGE, 0));
        this.setTicksRandomly(true);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!this.isSupported(world, pos, state)) {
            this.breakCocao(world, pos, state);
        } else if (world.random.nextInt(5) == 0) {
            int i = state.get(AGE);
            if (i < 2) {
                world.setBlockState(pos, state.set(AGE, i + 1), 2);
            }
        }
    }

    public boolean isSupported(World world, BlockPos pos, BlockState state) {
        pos = pos.offset(state.get(FACING));
        BlockState blockstate = world.getBlockState(pos);
        return blockstate.getBlock() == Blocks.LOG && blockstate.get(PlanksBlock.VARIANT) == PlanksBlock.Variant.JUNGLE;
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        this.updateShape(world, pos);
        return super.getCollisionShape(world, pos, state);
    }

    @Override
    public Box getOutlineShape(World world, BlockPos pos) {
        this.updateShape(world, pos);
        return super.getOutlineShape(world, pos);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        Direction direction = blockstate.get(FACING);
        int i = blockstate.get(AGE);
        int j = 4 + i * 2;
        int k = 5 + i * 2;
        float f = j / 2.0F;
        switch (direction) {
            case SOUTH:
                this.setShape((8.0F - f) / 16.0F, (12.0F - k) / 16.0F, (15.0F - j) / 16.0F, (8.0F + f) / 16.0F, 0.75F, 0.9375F);
                break;
            case NORTH:
                this.setShape((8.0F - f) / 16.0F, (12.0F - k) / 16.0F, 0.0625F, (8.0F + f) / 16.0F, 0.75F, (1.0F + j) / 16.0F);
                break;
            case WEST:
                this.setShape(0.0625F, (12.0F - k) / 16.0F, (8.0F - f) / 16.0F, (1.0F + j) / 16.0F, 0.75F, (8.0F + f) / 16.0F);
                break;
            case EAST:
                this.setShape((15.0F - j) / 16.0F, (12.0F - k) / 16.0F, (8.0F - f) / 16.0F, 0.9375F, 0.75F, (8.0F + f) / 16.0F);
        }
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        Direction direction = Direction.byRotation(entity.yaw);
        world.setBlockState(pos, state.set(FACING, direction), 2);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        if (!dir.getAxis().isHorizontal()) {
            dir = Direction.NORTH;
        }

        return this.defaultState().set(FACING, dir.getOpposite()).set(AGE, 0);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!this.isSupported(world, pos, state)) {
            this.breakCocao(world, pos, state);
        }
    }

    private void breakCocao(World world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, Blocks.AIR.defaultState(), 3);
        this.dropItems(world, pos, state, 0);
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        int i = state.get(AGE);
        int j = 1;
        if (i >= 2) {
            j = 3;
        }

        for (int k = 0; k < j; k++) {
            dropItem(world, pos, new ItemStack(Items.DYE, 1, DyeColor.BROWN.getMetadata()));
        }
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.DYE;
    }

    @Override
    public int getPickItemMetadata(World world, BlockPos pos) {
        return DyeColor.BROWN.getMetadata();
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, BlockState state, boolean isClient) {
        return state.get(AGE) < 2;
    }

    @Override
    public boolean canBeFertilized(World world, Random rand, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state.set(AGE, state.get(AGE) + 1), 2);
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(FACING, Direction.byIdHorizontal(metadata)).set(AGE, (metadata & 15) >> 2);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getIdHorizontal();
        return i | state.get(AGE) << 2;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, AGE);
    }
}
