package net.minecraft.enchantment;

import net.minecraft.resource.Identifier;

public class KnockbackEnchantment extends Enchantment {
    protected KnockbackEnchantment(int id, Identifier key, int type) {
        super(id, key, type, EnchantmentCategory.WEAPON);
        this.setKey("knockback");
    }

    @Override
    public int getMinXpRequirement(int level) {
        return 5 + 20 * (level - 1);
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
