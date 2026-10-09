package net.minecraft.enchantment;

import net.minecraft.resource.Identifier;

public class DepthStriderEnchantment extends Enchantment {
    public DepthStriderEnchantment(int id, Identifier key, int type) {
        super(id, key, type, EnchantmentCategory.ARMOR_FEET);
        this.setKey("waterWalker");
    }

    @Override
    public int getMinXpRequirement(int level) {
        return level * 10;
    }

    @Override
    public int getMaxXpRequirement(int level) {
        return this.getMinXpRequirement(level) + 15;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }
}
