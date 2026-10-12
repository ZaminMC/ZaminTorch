package net.minecraft.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ExperienceBottleEntity;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;

public class ExperienceBottleItem extends Item {
    public ExperienceBottleItem() {
        this.setCreativeModeTab(CreativeModeTab.MISC);
    }

    @Override
    public boolean hasEnchantmentGlint(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        if (!player.abilities.creativeMode) {
            stack.size--;
        }

        world.playSound((Entity)player, "random.bow", 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
        if (!world.isClient) {
            world.addEntity(new ExperienceBottleEntity(world, player));
        }

        player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
        return stack;
    }
}
