package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MobSpawnerBlock extends BlockWithBlockEntity {
    protected MobSpawnerBlock() {
        super(Material.STONE);
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new MobSpawnerBlockEntity();
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return null;
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 0;
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        super.dropItems(world, pos, state, luck, fortuneLevel);
        int i = 15 + world.random.nextInt(15) + world.random.nextInt(15);
        this.dropXp(world, pos, i);
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public int getRenderType() {
        return 3;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return null;
    }
}
