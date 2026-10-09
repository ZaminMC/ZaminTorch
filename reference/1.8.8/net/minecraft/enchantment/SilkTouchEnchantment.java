package net.minecraft.enchantment;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.Identifier;

public class SilkTouchEnchantment extends Enchantment {
    protected SilkTouchEnchantment(int id, Identifier key, int type) {
        super(id, key, type, EnchantmentCategory.DIGGER);
        this.setKey("untouching");
    }

    @Override
    public int getMinXpRequirement(int level) {
        return 15;
    }

    @Override
    public int getMaxXpRequirement(int level) {
        return super.getMinXpRequirement(level) + 50;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean isCompatible(Enchantment other) {
        return super.isCompatible(other) && other.id != FORTUNE.id;
    }

    @Override
    public boolean canEnchant(ItemStack item) {
        return item.getItem() == Items.SHEARS || super.canEnchant(item);
    }
}
