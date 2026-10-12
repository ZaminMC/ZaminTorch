package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class SugarCaneBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.of("age", 0, 15);

    protected SugarCaneBlock() {
        super(Material.PLANT);
        this.setDefaultState(this.stateDefinition.any().set(AGE, 0));
        float f = 0.375F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, 1.0F, 0.5F + f);
        this.setTicksRandomly(true);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (world.getBlockState(pos.down()).getBlock() == Blocks.REEDS || this.canSurviveOrBreak(world, pos, state)) {
            if (world.isAir(pos.up())) {
                int i = 1;

                while (world.getBlockState(pos.down(i)).getBlock() == this) {
                    i++;
                }

                if (i < 3) {
                    int j = state.get(AGE);
                    if (j == 15) {
                        world.setBlockState(pos.up(), this.defaultState());
                        world.setBlockState(pos, state.set(AGE, 0), 4);
                    } else {
                        world.setBlockState(pos, state.set(AGE, j + 1), 4);
                    }
                }
            }
        }
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        Block block = world.getBlockState(pos.down()).getBlock();
        if (block == this) {
            return true;
        }

        if (block != Blocks.GRASS && block != Blocks.DIRT && block != Blocks.SAND) {
            return false;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (world.getBlockState(pos.offset(direction).down()).getBlock().getMaterial() == Material.WATER) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        this.canSurviveOrBreak(world, pos, state);
    }

    protected final boolean canSurviveOrBreak(World world, BlockPos pos, BlockState state) {
        if (this.canSurvive(world, pos)) {
            return true;
        }

        this.dropItems(world, pos, state, 0);
        world.removeBlock(pos);
        return false;
    }

    public boolean canSurvive(World world, BlockPos pos) {
        return this.canBePlaced(world, pos);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.REEDS;
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
    public Item getPickItem(World world, BlockPos pos) {
        return Items.REEDS;
    }

    @Override
    public int getColor(WorldView world, BlockPos pos, int tint) {
        return world.getBiome(pos).getGrassColor(pos);
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(AGE, metadata);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(AGE);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, AGE);
    }
}
