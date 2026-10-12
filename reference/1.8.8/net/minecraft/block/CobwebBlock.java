package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class CobwebBlock extends Block {
    public CobwebBlock() {
        super(Material.COBWEB);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
        entity.onCobwebCollision();
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.STRING;
    }

    @Override
    protected boolean hasSilkTouchDrops() {
        return true;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }
}
