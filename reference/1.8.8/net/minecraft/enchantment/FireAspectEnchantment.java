package net.minecraft.enchantment;

import net.minecraft.resource.Identifier;

public class FireAspectEnchantment extends Enchantment {
    protected FireAspectEnchantment(int id, Identifier key, int type) {
        super(id, key, type, EnchantmentCategory.WEAPON);
        this.setKey("fire");
    }

    @Override
    public int getMinXpRequirement(int level) {
        return 10 + 20 * (level - 1);
    }

    @Override
    public int getMaxXpRequirement(int level) {
        return super.getMinXpRequirement(level) + 50;
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }
}
