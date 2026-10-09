package net.minecraft.item;

import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class FlintAndSteelItem extends Item {
    public FlintAndSteelItem() {
        this.maxStackSize = 1;
        this.setMaxDamage(64);
        this.setCreativeModeTab(CreativeModeTab.TOOLS);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        pos = pos.offset(face);
        if (!player.canUseItemOn(pos, face, stack)) {
            return false;
        }

        if (world.getBlockState(pos).getBlock().getMaterial() == Material.AIR) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "fire.ignite", 1.0F, random.nextFloat() * 0.4F + 0.8F);
            world.setBlockState(pos, Blocks.FIRE.defaultState());
        }

        stack.takeDamageAndBreak(1, player);
        return true;
    }
}
