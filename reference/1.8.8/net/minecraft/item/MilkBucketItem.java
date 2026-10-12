package net.minecraft.item;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;

public class MilkBucketItem extends Item {
    public MilkBucketItem() {
        this.setMaxStackSize(1);
        this.setCreativeModeTab(CreativeModeTab.MISC);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, PlayerEntity player) {
        if (!player.abilities.creativeMode) {
            stack.size--;
        }

        if (!world.isClient) {
            player.clearStatusEffects();
        }

        player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
        return stack.size <= 0 ? new ItemStack(Items.BUCKET) : stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        player.setItemInUse(stack, this.getUseDuration(stack));
        return stack;
    }
}
