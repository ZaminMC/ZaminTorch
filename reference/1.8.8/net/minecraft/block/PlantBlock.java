package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class PlantBlock extends Block {
    protected PlantBlock() {
        this(Material.PLANT);
    }

    protected PlantBlock(Material material) {
        this(material, material.getColor());
    }

    protected PlantBlock(Material material, MapColor mapColor) {
        super(material, mapColor);
        this.setTicksRandomly(true);
        float f = 0.2F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, f * 3.0F, 0.5F + f);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return super.canBePlaced(world, pos) && this.canBePlacedOn(world.getBlockState(pos.down()).getBlock());
    }

    protected boolean canBePlacedOn(Block block) {
        return block == Blocks.GRASS || block == Blocks.DIRT || block == Blocks.FARMLAND;
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        super.neighborChanged(world, pos, state, neighborBlock);
        this.canSurviveOrBreak(world, pos, state);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        this.canSurviveOrBreak(world, pos, state);
    }

    protected void canSurviveOrBreak(World world, BlockPos pos, BlockState state) {
        if (!this.canSurvive(world, pos, state)) {
            this.dropItems(world, pos, state, 0);
            world.setBlockState(pos, Blocks.AIR.defaultState(), 3);
        }
    }

    public boolean canSurvive(World world, BlockPos pos, BlockState state) {
        return this.canBePlacedOn(world.getBlockState(pos.down()).getBlock());
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
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }
}
