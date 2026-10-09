package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EndPortalBlockEntity;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class EndPortalBlock extends BlockWithBlockEntity {
    protected EndPortalBlock(Material material) {
        super(material);
        this.setLight(1.0F);
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new EndPortalBlockEntity();
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        float f = 0.0625F;
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, f, 1.0F);
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return face == Direction.DOWN && super.shouldRenderFace(world, pos, face);
    }

    @Override
    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
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
    public int getBaseDropCount(Random random) {
        return 0;
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
        if (entity.vehicle == null && entity.rider == null && !world.isClient) {
            entity.changeDimension(1);
        }
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        double d0 = pos.getX() + random.nextFloat();
        double d1 = pos.getY() + 0.8F;
        double d2 = pos.getZ() + random.nextFloat();
        double d3 = 0.0;
        double d4 = 0.0;
        double d5 = 0.0;
        world.addParticle(ParticleType.SMOKE_NORMAL, d0, d1, d2, d3, d4, d5);
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return null;
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return MapColor.BLACK;
    }
}
