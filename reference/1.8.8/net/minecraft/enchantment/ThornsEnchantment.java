package net.minecraft.enchantment;

import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;

public class ThornsEnchantment extends Enchantment {
    public ThornsEnchantment(int id, Identifier key, int type) {
        super(id, key, type, EnchantmentCategory.ARMOR_TORSO);
        this.setKey("thorns");
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
        return 3;
    }

    @Override
    public boolean canEnchant(ItemStack item) {
        return item.getItem() instanceof ArmorItem || super.canEnchant(item);
    }

    @Override
    public void applyProtectionWildcard(LivingEntity attacker, Entity target, int level) {
        Random random = attacker.getRandom();
        ItemStack itemstack = EnchantmentHelper.getEquipmentWithEnchantment(Enchantment.THORNS, attacker);
        if (shouldDamageAttacker(level, random)) {
            if (target != null) {
                target.takeDamage(DamageSource.thorns(attacker), getDamageAmount(level, random));
                target.playSound("damage.thorns", 0.5F, 1.0F);
            }

            if (itemstack != null) {
                itemstack.takeDamageAndBreak(3, attacker);
            }
        } else if (itemstack != null) {
            itemstack.takeDamageAndBreak(1, attacker);
        }
    }

    public static boolean shouldDamageAttacker(int level, Random random) {
        return level > 0 && random.nextFloat() < 0.15F * level;
    }

    public static int getDamageAmount(int level, Random random) {
        return level > 10 ? level - 10 : 1 + random.nextInt(4);
    }
}
