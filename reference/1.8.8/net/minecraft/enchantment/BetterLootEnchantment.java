package net.minecraft.enchantment;

import net.minecraft.resource.Identifier;

public class BetterLootEnchantment extends Enchantment {
    protected BetterLootEnchantment(int i, Identifier identifier, int j, EnchantmentCategory enchantmentCategory) {
        super(i, identifier, j, enchantmentCategory);
        if (enchantmentCategory == EnchantmentCategory.DIGGER) {
            this.setKey("lootBonusDigger");
        } else if (enchantmentCategory == EnchantmentCategory.FISHING_ROD) {
            this.setKey("lootBonusFishing");
        } else {
            this.setKey("lootBonus");
        }
    }

    @Override
    public int getMinXpRequirement(int level) {
        return 15 + (level - 1) * 9;
    }

    @Override
    public int getMaxXpRequirement(int level) {
        return super.getMinXpRequirement(level) + 50;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public boolean isCompatible(Enchantment other) {
        return super.isCompatible(other) && other.id != SILK_TOUCH.id;
    }
}
