package net.minecraft.enchantment;

import java.util.Random;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;

public class UnbreakingEnchantment extends Enchantment {
    protected UnbreakingEnchantment(int id, Identifier key, int type) {
        super(id, key, type, EnchantmentCategory.BREAKABLE);
        this.setKey("durability");
    }

    @Override
    public int getMinXpRequirement(int level) {
        return 5 + (level - 1) * 8;
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
    public boolean canEnchant(ItemStack item) {
        return item.isDamageable() || super.canEnchant(item);
    }

    public static boolean shouldReduceDamage(ItemStack item, int level, Random random) {
        return (!(item.getItem() instanceof ArmorItem) || !(random.nextFloat() < 0.6F)) && random.nextInt(level + 1) > 0;
    }
}
