package net.minecraft.item;

import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class FireChargeItem extends Item {
    public FireChargeItem() {
        this.setCreativeModeTab(CreativeModeTab.MISC);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        pos = pos.offset(face);
        if (!player.canUseItemOn(pos, face, stack)) {
            return false;
        }

        if (world.getBlockState(pos).getBlock().getMaterial() == Material.AIR) {
            world.playSound(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "item.fireCharge.use", 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F
            );
            world.setBlockState(pos, Blocks.FIRE.defaultState());
        }

        if (!player.abilities.creativeMode) {
            stack.size--;
        }

        return true;
    }
}
