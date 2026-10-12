package net.minecraft.item;

import net.minecraft.block.material.Material;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class GlassBottleItem extends Item {
    public GlassBottleItem() {
        this.setCreativeModeTab(CreativeModeTab.BREWING);
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        HitResult hitresult = this.getUseTarget(world, player, true);
        if (hitresult == null) {
            return stack;
        }

        if (hitresult.type == HitResult.Type.BLOCK) {
            BlockPos blockpos = hitresult.getPos();
            if (!world.canModify(player, blockpos)) {
                return stack;
            }

            if (!player.canUseItemOn(blockpos.offset(hitresult.face), hitresult.face, stack)) {
                return stack;
            }

            if (world.getBlockState(blockpos).getBlock().getMaterial() == Material.WATER) {
                stack.size--;
                player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
                if (stack.size <= 0) {
                    return new ItemStack(Items.POTION);
                }

                if (!player.inventory.addItem(new ItemStack(Items.POTION))) {
                    player.dropItem(new ItemStack(Items.POTION, 1, 0), false);
                }
            }
        }

        return stack;
    }
}
