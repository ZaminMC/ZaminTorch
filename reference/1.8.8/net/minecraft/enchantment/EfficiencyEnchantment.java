package net.minecraft.enchantment;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.Identifier;

public class EfficiencyEnchantment extends Enchantment {
    protected EfficiencyEnchantment(int id, Identifier key, int type) {
        super(id, key, type, EnchantmentCategory.DIGGER);
        this.setKey("digging");
    }

    @Override
    public int getMinXpRequirement(int level) {
        return 1 + 10 * (level - 1);
    }

    @Override
    public int getMaxXpRequirement(int level) {
        return super.getMinXpRequirement(level) + 50;
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public boolean canEnchant(ItemStack item) {
        return item.getItem() == Items.SHEARS || super.canEnchant(item);
    }
}
