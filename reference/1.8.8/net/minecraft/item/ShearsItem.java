package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ShearsItem extends Item {
    public ShearsItem() {
        this.setMaxStackSize(1);
        this.setMaxDamage(238);
        this.setCreativeModeTab(CreativeModeTab.TOOLS);
    }

    @Override
    public boolean mineBlock(ItemStack stack, World world, Block block, BlockPos pos, LivingEntity entity) {
        if (block.getMaterial() != Material.LEAVES
            && block != Blocks.WEB
            && block != Blocks.TALLGRASS
            && block != Blocks.VINE
            && block != Blocks.TRIPWIRE
            && block != Blocks.WOOL) {
            return super.mineBlock(stack, world, block, pos, entity);
        }

        stack.takeDamageAndBreak(1, entity);
        return true;
    }

    @Override
    public boolean canMineBlock(Block block) {
        return block == Blocks.WEB || block == Blocks.REDSTONE_WIRE || block == Blocks.TRIPWIRE;
    }

    @Override
    public float getMiningSpeed(ItemStack stack, Block block) {
        if (block == Blocks.WEB || block.getMaterial() == Material.LEAVES) {
            return 15.0F;
        } else {
            return block == Blocks.WOOL ? 5.0F : super.getMiningSpeed(stack, block);
        }
    }
}
