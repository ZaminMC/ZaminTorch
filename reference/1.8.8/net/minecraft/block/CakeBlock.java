package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class CakeBlock extends Block {
    public static final IntegerProperty BITES = IntegerProperty.of("bites", 0, 6);

    protected CakeBlock() {
        super(Material.CAKE);
        this.setDefaultState(this.stateDefinition.any().set(BITES, 0));
        this.setTicksRandomly(true);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        float f = 0.0625F;
        float f1 = (1 + world.getBlockState(pos).get(BITES) * 2) / 16.0F;
        float f2 = 0.5F;
        this.setShape(f1, 0.0F, f, 1.0F - f, f2, 1.0F - f);
    }

    @Override
    public void resetShape() {
        float f = 0.0625F;
        float f1 = 0.5F;
        this.setShape(f, 0.0F, f, 1.0F - f, f1, 1.0F - f);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        float f = 0.0625F;
        float f1 = (1 + state.get(BITES) * 2) / 16.0F;
        float f2 = 0.5F;
        return new Box(pos.getX() + f1, pos.getY(), pos.getZ() + f, pos.getX() + 1 - f, pos.getY() + f2, pos.getZ() + 1 - f);
    }

    @Override
    public Box getOutlineShape(World world, BlockPos pos) {
        return this.getCollisionShape(world, pos, world.getBlockState(pos));
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
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        this.eat(world, pos, state, player);
        return true;
    }

    @Override
    public void startMining(World world, BlockPos pos, PlayerEntity player) {
        this.eat(world, pos, world.getBlockState(pos), player);
    }

    private void eat(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (player.canEat(false)) {
            player.incrementStat(Stats.CAKE_SLICES_EATEN);
            player.getHungerManager().add(2, 0.1F);
            int i = state.get(BITES);
            if (i < 6) {
                world.setBlockState(pos, state.set(BITES, i + 1), 3);
            } else {
                world.removeBlock(pos);
            }
        }
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return super.canBePlaced(world, pos) && this.canSurvive(world, pos);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!this.canSurvive(world, pos)) {
            world.removeBlock(pos);
        }
    }

    private boolean canSurvive(World world, BlockPos pos) {
        return world.getBlockState(pos.down()).getBlock().getMaterial().isSolid();
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 0;
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return null;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.CAKE;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(BITES, metadata);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(BITES);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, BITES);
    }

    @Override
    public int getAnalogSignal(World world, BlockPos pos) {
        return (7 - world.getBlockState(pos).get(BITES)) * 2;
    }

    @Override
    public boolean isAnalogSignalSource() {
        return true;
    }
}
