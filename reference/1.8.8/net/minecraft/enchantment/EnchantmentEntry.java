package net.minecraft.enchantment;

import net.minecraft.util.WeightedPicker;

public class EnchantmentEntry extends WeightedPicker.Entry {
    public final Enchantment enchantment;
    public final int level;

    public EnchantmentEntry(Enchantment enchantment, int level) {
        super(enchantment.getType());
        this.enchantment = enchantment;
        this.level = level;
    }
}
