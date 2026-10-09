package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class SoulSandBlock extends Block {
    public SoulSandBlock() {
        super(Material.SAND, MapColor.BROWN);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        float f = 0.125F;
        return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1 - f, pos.getZ() + 1);
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
        entity.velocityX *= 0.4;
        entity.velocityZ *= 0.4;
    }
}
