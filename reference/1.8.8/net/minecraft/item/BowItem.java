package net.minecraft.item;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;

public class BowItem extends Item {
    public static final String[] BOW_PULLING_LEVELS = new String[]{"pulling_0", "pulling_1", "pulling_2"};

    public BowItem() {
        this.maxStackSize = 1;
        this.setMaxDamage(384);
        this.setCreativeModeTab(CreativeModeTab.COMBAT);
    }

    @Override
    public void stopUsing(ItemStack stack, World world, PlayerEntity player, int remainingUseTime) {
        boolean flag = player.abilities.creativeMode || EnchantmentHelper.getLevel(Enchantment.INFINITY.id, stack) > 0;
        if (flag || player.inventory.contains(Items.ARROW)) {
            int i = this.getUseDuration(stack) - remainingUseTime;
            float f = i / 20.0F;
            f = (f * f + f * 2.0F) / 3.0F;
            if (f < 0.1) {
                return;
            }

            if (f > 1.0F) {
                f = 1.0F;
            }

            ArrowEntity arrowentity = new ArrowEntity(world, player, f * 2.0F);
            if (f == 1.0F) {
                arrowentity.setCritical(true);
            }

            int j = EnchantmentHelper.getLevel(Enchantment.POWER.id, stack);
            if (j > 0) {
                arrowentity.setDamage(arrowentity.getDamage() + j * 0.5 + 0.5);
            }

            int k = EnchantmentHelper.getLevel(Enchantment.PUNCH.id, stack);
            if (k > 0) {
                arrowentity.setPunchLevel(k);
            }

            if (EnchantmentHelper.getLevel(Enchantment.FLAME.id, stack) > 0) {
                arrowentity.setOnFireFor(100);
            }

            stack.takeDamageAndBreak(1, player);
            world.playSound((Entity)player, "random.bow", 1.0F, 1.0F / (random.nextFloat() * 0.4F + 1.2F) + f * 0.5F);
            if (flag) {
                arrowentity.pickup = 2;
            } else {
                player.inventory.removeOne(Items.ARROW);
            }

            player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
            if (!world.isClient) {
                world.addEntity(arrowentity);
            }
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, PlayerEntity player) {
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        if (player.abilities.creativeMode || player.inventory.contains(Items.ARROW)) {
            player.setItemInUse(stack, this.getUseDuration(stack));
        }

        return stack;
    }

    @Override
    public int getEnchantability() {
        return 1;
    }
}
