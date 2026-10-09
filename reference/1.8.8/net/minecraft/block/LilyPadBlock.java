package net.minecraft.block;

import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class LilyPadBlock extends PlantBlock {
    protected LilyPadBlock() {
        float f = 0.5F;
        float f1 = 0.015625F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, f1, 0.5F + f);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
        if (entity == null || !(entity instanceof BoatEntity)) {
            super.addCollisions(world, pos, state, shape, collisions, entity);
        }
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return new Box(
            pos.getX() + this.minX, pos.getY() + this.minY, pos.getZ() + this.minZ, pos.getX() + this.maxX, pos.getY() + this.maxY, pos.getZ() + this.maxZ
        );
    }

    @Override
    public int getColor() {
        return 7455580;
    }

    @Override
    public int getColor(BlockState state) {
        return 7455580;
    }

    @Override
    public int getColor(WorldView world, BlockPos pos, int tint) {
        return 2129968;
    }

    @Override
    protected boolean canBePlacedOn(Block block) {
        return block == Blocks.WATER;
    }

    @Override
    public boolean canSurvive(World world, BlockPos pos, BlockState state) {
        if (pos.getY() >= 0 && pos.getY() < 256) {
            BlockState blockstate = world.getBlockState(pos.down());
            return blockstate.getBlock().getMaterial() == Material.WATER && blockstate.get(LiquidBlock.LEVEL) == 0;
        } else {
            return false;
        }
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return 0;
    }
}
