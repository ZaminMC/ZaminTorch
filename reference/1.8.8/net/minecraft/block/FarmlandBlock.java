package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class FarmlandBlock extends Block {
    public static final IntegerProperty MOISTURE = IntegerProperty.of("moisture", 0, 7);

    protected FarmlandBlock() {
        super(Material.DIRT);
        this.setDefaultState(this.stateDefinition.any().set(MOISTURE, 0));
        this.setTicksRandomly(true);
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.9375F, 1.0F);
        this.setOpacity(255);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
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
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        int i = state.get(MOISTURE);
        if (!this.isNearWater(world, pos) && !world.isRaining(pos.up())) {
            if (i > 0) {
                world.setBlockState(pos, state.set(MOISTURE, i - 1), 2);
            } else if (!this.isUnderCrop(world, pos)) {
                world.setBlockState(pos, Blocks.DIRT.defaultState());
            }
        } else if (i < 7) {
            world.setBlockState(pos, state.set(MOISTURE, 7), 2);
        }
    }

    @Override
    public void onFallenOn(World world, BlockPos pos, Entity entity, float fallDistance) {
        if (entity instanceof LivingEntity) {
            if (!world.isClient && world.random.nextFloat() < fallDistance - 0.5F) {
                if (!(entity instanceof PlayerEntity) && !world.getGameRules().getBoolean("mobGriefing")) {
                    return;
                }

                world.setBlockState(pos, Blocks.DIRT.defaultState());
            }

            super.onFallenOn(world, pos, entity, fallDistance);
        }
    }

    private boolean isUnderCrop(World world, BlockPos pos) {
        Block block = world.getBlockState(pos.up()).getBlock();
        return block instanceof WheatBlock || block instanceof StemBlock;
    }

    private boolean isNearWater(World world, BlockPos pos) {
        for (BlockPos.Mutable blockpos$mutable : BlockPos.iterateRegionMutable(pos.add(-4, 0, -4), pos.add(4, 1, 4))) {
            if (world.getBlockState(blockpos$mutable).getBlock().getMaterial() == Material.WATER) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        super.neighborChanged(world, pos, state, neighborBlock);
        if (world.getBlockState(pos.up()).getBlock().getMaterial().isSolid()) {
            world.setBlockState(pos, Blocks.DIRT.defaultState());
        }
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        switch (face) {
            case UP:
                return true;
            case NORTH:
            case SOUTH:
            case WEST:
            case EAST:
                Block block = world.getBlockState(pos).getBlock();
                return !block.isSolidRender() && block != Blocks.FARMLAND;
            default:
                return super.shouldRenderFace(world, pos, face);
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Blocks.DIRT.getDropItem(Blocks.DIRT.defaultState().set(DirtBlock.VARIANT, DirtBlock.Variant.DIRT), random, fortuneLevel);
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Item.byBlock(Blocks.DIRT);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(MOISTURE, metadata & 7);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(MOISTURE);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, MOISTURE);
    }
}
