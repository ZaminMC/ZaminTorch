package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SlimeBlock extends TransparentBlock {
    public SlimeBlock() {
        super(Material.CLAY, false, MapColor.GRASS);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
        this.slipperiness = 0.8F;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.TRANSLUCENT;
    }

    @Override
    public void onFallenOn(World world, BlockPos pos, Entity entity, float fallDistance) {
        if (entity.isSneaking()) {
            super.onFallenOn(world, pos, entity, fallDistance);
        } else {
            entity.takeFallDamage(fallDistance, 0.0F);
        }
    }

    @Override
    public void beforeCollision(World world, Entity entity) {
        if (entity.isSneaking()) {
            super.beforeCollision(world, entity);
        } else if (entity.velocityY < 0.0) {
            entity.velocityY = -entity.velocityY;
        }
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, Entity entity) {
        if (Math.abs(entity.velocityY) < 0.1 && !entity.isSneaking()) {
            double d0 = 0.4 + Math.abs(entity.velocityY) * 0.2;
            entity.velocityX *= d0;
            entity.velocityZ *= d0;
        }

        super.onSteppedOn(world, pos, entity);
    }
}
