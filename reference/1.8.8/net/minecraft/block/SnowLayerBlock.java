package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class SnowLayerBlock extends Block {
    public static final IntegerProperty LAYERS = IntegerProperty.of("layers", 1, 8);

    protected SnowLayerBlock() {
        super(Material.SNOW_LAYER);
        this.setDefaultState(this.stateDefinition.any().set(LAYERS, 1));
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.125F, 1.0F);
        this.setTicksRandomly(true);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
        this.resetShape();
    }

    @Override
    public boolean canWalkThrough(WorldView world, BlockPos pos) {
        return world.getBlockState(pos).get(LAYERS) < 5;
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        int i = state.get(LAYERS) - 1;
        float f = 0.125F;
        return new Box(
            pos.getX() + this.minX, pos.getY() + this.minY, pos.getZ() + this.minZ, pos.getX() + this.maxX, pos.getY() + i * f, pos.getZ() + this.maxZ
        );
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
    public void resetShape() {
        this.updateShape(0);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        this.updateShape(blockstate.get(LAYERS));
    }

    protected void updateShape(int layers) {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, layers / 8.0F, 1.0F);
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos.down());
        Block block = blockstate.getBlock();
        return block != Blocks.ICE
            && block != Blocks.PACKED_ICE
            && (
                block.getMaterial() == Material.LEAVES
                    || block == this && blockstate.get(LAYERS) >= 7
                    || block.isSolidRender() && block.material.blocksMovement()
            );
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        this.canSurviveOrBreak(world, pos, state);
    }

    private boolean canSurviveOrBreak(World world, BlockPos pos, BlockState state) {
        if (!this.canBePlaced(world, pos)) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void afterMinedByPlayer(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        dropItem(world, pos, new ItemStack(Items.SNOWBALL, state.get(LAYERS) + 1, 0));
        world.removeBlock(pos);
        player.incrementStat(Stats.BLOCKS_MINED[Block.getId(this)]);
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.SNOWBALL;
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 0;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (world.getLight(LightType.BLOCK, pos) > 11) {
            this.dropItems(world, pos, world.getBlockState(pos), 0);
            world.removeBlock(pos);
        }
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return face == Direction.UP || super.shouldRenderFace(world, pos, face);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(LAYERS, (metadata & 7) + 1);
    }

    @Override
    public boolean canBeReplaced(World world, BlockPos pos) {
        return world.getBlockState(pos).get(LAYERS) == 1;
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(LAYERS) - 1;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, LAYERS);
    }
}
