package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class PaneBlock extends Block {
    public static final BooleanProperty NORTH = BooleanProperty.of("north");
    public static final BooleanProperty EAST = BooleanProperty.of("east");
    public static final BooleanProperty SOUTH = BooleanProperty.of("south");
    public static final BooleanProperty WEST = BooleanProperty.of("west");
    private final boolean hasDrops;

    protected PaneBlock(Material material, boolean hasDrops) {
        super(material);
        this.setDefaultState(this.stateDefinition.any().set(NORTH, false).set(EAST, false).set(SOUTH, false).set(WEST, false));
        this.hasDrops = hasDrops;
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        return state.set(NORTH, this.shouldConnectTo(world.getBlockState(pos.north()).getBlock()))
            .set(SOUTH, this.shouldConnectTo(world.getBlockState(pos.south()).getBlock()))
            .set(WEST, this.shouldConnectTo(world.getBlockState(pos.west()).getBlock()))
            .set(EAST, this.shouldConnectTo(world.getBlockState(pos.east()).getBlock()));
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return !this.hasDrops ? null : super.getDropItem(state, random, fortuneLevel);
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
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return world.getBlockState(pos).getBlock() != this && super.shouldRenderFace(world, pos, face);
    }

    @Override
    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
        boolean flag = this.shouldConnectTo(world.getBlockState(pos.north()).getBlock());
        boolean flag1 = this.shouldConnectTo(world.getBlockState(pos.south()).getBlock());
        boolean flag2 = this.shouldConnectTo(world.getBlockState(pos.west()).getBlock());
        boolean flag3 = this.shouldConnectTo(world.getBlockState(pos.east()).getBlock());
        if ((!flag2 || !flag3) && (flag2 || flag3 || flag || flag1)) {
            if (flag2) {
                this.setShape(0.0F, 0.0F, 0.4375F, 0.5F, 1.0F, 0.5625F);
                super.addCollisions(world, pos, state, shape, collisions, entity);
            } else if (flag3) {
                this.setShape(0.5F, 0.0F, 0.4375F, 1.0F, 1.0F, 0.5625F);
                super.addCollisions(world, pos, state, shape, collisions, entity);
            }
        } else {
            this.setShape(0.0F, 0.0F, 0.4375F, 1.0F, 1.0F, 0.5625F);
            super.addCollisions(world, pos, state, shape, collisions, entity);
        }

        if ((!flag || !flag1) && (flag2 || flag3 || flag || flag1)) {
            if (flag) {
                this.setShape(0.4375F, 0.0F, 0.0F, 0.5625F, 1.0F, 0.5F);
                super.addCollisions(world, pos, state, shape, collisions, entity);
            } else if (flag1) {
                this.setShape(0.4375F, 0.0F, 0.5F, 0.5625F, 1.0F, 1.0F);
                super.addCollisions(world, pos, state, shape, collisions, entity);
            }
        } else {
            this.setShape(0.4375F, 0.0F, 0.0F, 0.5625F, 1.0F, 1.0F);
            super.addCollisions(world, pos, state, shape, collisions, entity);
        }
    }

    @Override
    public void resetShape() {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        float f = 0.4375F;
        float f1 = 0.5625F;
        float f2 = 0.4375F;
        float f3 = 0.5625F;
        boolean flag = this.shouldConnectTo(world.getBlockState(pos.north()).getBlock());
        boolean flag1 = this.shouldConnectTo(world.getBlockState(pos.south()).getBlock());
        boolean flag2 = this.shouldConnectTo(world.getBlockState(pos.west()).getBlock());
        boolean flag3 = this.shouldConnectTo(world.getBlockState(pos.east()).getBlock());
        if ((!flag2 || !flag3) && (flag2 || flag3 || flag || flag1)) {
            if (flag2) {
                f = 0.0F;
            } else if (flag3) {
                f1 = 1.0F;
            }
        } else {
            f = 0.0F;
            f1 = 1.0F;
        }

        if ((!flag || !flag1) && (flag2 || flag3 || flag || flag1)) {
            if (flag) {
                f2 = 0.0F;
            } else if (flag1) {
                f3 = 1.0F;
            }
        } else {
            f2 = 0.0F;
            f3 = 1.0F;
        }

        this.setShape(f, 0.0F, f2, f1, 1.0F, f3);
    }

    public final boolean shouldConnectTo(Block block) {
        return block.isOpaque()
            || block == this
            || block == Blocks.GLASS
            || block == Blocks.STAINED_GLASS
            || block == Blocks.STAINED_GLASS_PANE
            || block instanceof PaneBlock;
    }

    @Override
    protected boolean hasSilkTouchDrops() {
        return true;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT_MIPPED;
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return 0;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, NORTH, EAST, WEST, SOUTH);
    }
}
