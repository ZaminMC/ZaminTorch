package net.minecraft.enchantment;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MobType;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;

public class DamageEnchantment extends Enchantment {
    private static final String[] TARGET_NAMES = new String[]{"all", "undead", "arthropods"};
    private static final int[] MIN_XP = new int[]{1, 5, 5};
    private static final int[] XP_MODIFIER = new int[]{11, 8, 8};
    private static final int[] MAX_XP = new int[]{20, 20, 20};
    /**
     * Defines what type of mobs this enchantment does extra damage to. This field is also
     * used to determine the min and max xp requirements of this enchantment.
     * 
     * <p>
     * Accepted values:
     * <br>- 0: all
     * <br>- 1: undead
     * <br>- 2: arthropods
     */
    public final int target;

    public DamageEnchantment(int id, Identifier key, int type, int target) {
        super(id, key, type, EnchantmentCategory.WEAPON);
        this.target = target;
    }

    @Override
    public int getMinXpRequirement(int level) {
        return MIN_XP[this.target] + (level - 1) * XP_MODIFIER[this.target];
    }

    @Override
    public int getMaxXpRequirement(int level) {
        return this.getMinXpRequirement(level) + MAX_XP[this.target];
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public float getExtraDamage(int level, MobType mobType) {
        if (this.target == 0) {
            return level * 1.25F;
        } else if (this.target == 1 && mobType == MobType.UNDEAD) {
            return level * 2.5F;
        } else {
            return this.target == 2 && mobType == MobType.ARTHROPOD ? level * 2.5F : 0.0F;
        }
    }

    @Override
    public String getTranslationKey() {
        return "enchantment.damage." + TARGET_NAMES[this.target];
    }

    @Override
    public boolean isCompatible(Enchantment other) {
        return !(other instanceof DamageEnchantment);
    }

    @Override
    public boolean canEnchant(ItemStack item) {
        return item.getItem() instanceof AxeItem || super.canEnchant(item);
    }

    @Override
    public void applyDamageWildcard(LivingEntity attacker, Entity target, int level) {
        if (target instanceof LivingEntity) {
            LivingEntity livingentity = (LivingEntity)target;
            if (this.target == 2 && livingentity.getMobType() == MobType.ARTHROPOD) {
                int i = 20 + attacker.getRandom().nextInt(10 * level);
                livingentity.addStatusEffect(new StatusEffectInstance(StatusEffect.SLOWNESS.id, i, 3));
            }
        }
    }
}
