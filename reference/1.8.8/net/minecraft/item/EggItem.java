package net.minecraft.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;

public class EggItem extends Item {
    public EggItem() {
        this.maxStackSize = 16;
        this.setCreativeModeTab(CreativeModeTab.MATERIALS);
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        if (!player.abilities.creativeMode) {
            stack.size--;
        }

        world.playSound((Entity)player, "random.bow", 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
        if (!world.isClient) {
            world.addEntity(new EggEntity(world, player));
        }

        player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
        return stack;
    }
}
