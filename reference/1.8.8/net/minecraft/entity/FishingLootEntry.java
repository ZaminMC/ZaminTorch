package net.minecraft.entity;

import java.util.Random;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedPicker;

public class FishingLootEntry extends WeightedPicker.Entry {
    private final ItemStack item;
    private float damage;
    private boolean enchantable;

    public FishingLootEntry(ItemStack item, int weight) {
        super(weight);
        this.item = item;
    }

    public ItemStack getItem(Random random) {
        ItemStack itemstack = this.item.copy();
        if (this.damage > 0.0F) {
            int i = (int)(this.damage * this.item.getMaxDamage());
            int j = itemstack.getMaxDamage() - random.nextInt(random.nextInt(i) + 1);
            if (j > i) {
                j = i;
            }

            if (j < 1) {
                j = 1;
            }

            itemstack.setDamage(j);
        }

        if (this.enchantable) {
            EnchantmentHelper.addRandomEnchantment(random, itemstack, 30);
        }

        return itemstack;
    }

    public FishingLootEntry setDamage(float damage) {
        this.damage = damage;
        return this;
    }

    public FishingLootEntry setEnchantable() {
        this.enchantable = true;
        return this;
    }
}
