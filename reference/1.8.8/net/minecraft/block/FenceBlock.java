package net.minecraft.block;

import java.util.List;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.LeadItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class FenceBlock extends Block {
    public static final BooleanProperty NORTH = BooleanProperty.of("north");
    public static final BooleanProperty EAST = BooleanProperty.of("east");
    public static final BooleanProperty SOUTH = BooleanProperty.of("south");
    public static final BooleanProperty WEST = BooleanProperty.of("west");

    public FenceBlock(Material material) {
        this(material, material.getColor());
    }

    public FenceBlock(Material material, MapColor mapColor) {
        super(material, mapColor);
        this.setDefaultState(this.stateDefinition.any().set(NORTH, false).set(EAST, false).set(SOUTH, false).set(WEST, false));
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
        boolean flag = this.shouldConnectTo(world, pos.north());
        boolean flag1 = this.shouldConnectTo(world, pos.south());
        boolean flag2 = this.shouldConnectTo(world, pos.west());
        boolean flag3 = this.shouldConnectTo(world, pos.east());
        float f = 0.375F;
        float f1 = 0.625F;
        float f2 = 0.375F;
        float f3 = 0.625F;
        if (flag) {
            f2 = 0.0F;
        }

        if (flag1) {
            f3 = 1.0F;
        }

        if (flag || flag1) {
            this.setShape(f, 0.0F, f2, f1, 1.5F, f3);
            super.addCollisions(world, pos, state, shape, collisions, entity);
        }

        f2 = 0.375F;
        f3 = 0.625F;
        if (flag2) {
            f = 0.0F;
        }

        if (flag3) {
            f1 = 1.0F;
        }

        if (flag2 || flag3 || !flag && !flag1) {
            this.setShape(f, 0.0F, f2, f1, 1.5F, f3);
            super.addCollisions(world, pos, state, shape, collisions, entity);
        }

        if (flag) {
            f2 = 0.0F;
        }

        if (flag1) {
            f3 = 1.0F;
        }

        this.setShape(f, 0.0F, f2, f1, 1.0F, f3);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        boolean flag = this.shouldConnectTo(world, pos.north());
        boolean flag1 = this.shouldConnectTo(world, pos.south());
        boolean flag2 = this.shouldConnectTo(world, pos.west());
        boolean flag3 = this.shouldConnectTo(world, pos.east());
        float f = 0.375F;
        float f1 = 0.625F;
        float f2 = 0.375F;
        float f3 = 0.625F;
        if (flag) {
            f2 = 0.0F;
        }

        if (flag1) {
            f3 = 1.0F;
        }

        if (flag2) {
            f = 0.0F;
        }

        if (flag3) {
            f1 = 1.0F;
        }

        this.setShape(f, 0.0F, f2, f1, 1.0F, f3);
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
        return false;
    }

    public boolean shouldConnectTo(WorldView world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return block != Blocks.BARRIER
            && (
                block instanceof FenceBlock && block.material == this.material
                    || block instanceof FenceGateBlock
                    || block.material.isSolidBlocking() && block.isCube() && block.material != Material.PUMPKIN
            );
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return true;
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        return world.isClient || LeadItem.attachLead(player, world, pos);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return 0;
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        return state.set(NORTH, this.shouldConnectTo(world, pos.north()))
            .set(EAST, this.shouldConnectTo(world, pos.east()))
            .set(SOUTH, this.shouldConnectTo(world, pos.south()))
            .set(WEST, this.shouldConnectTo(world, pos.west()));
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, NORTH, EAST, WEST, SOUTH);
    }
}
