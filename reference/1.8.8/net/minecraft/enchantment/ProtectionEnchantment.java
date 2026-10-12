package net.minecraft.enchantment;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class ProtectionEnchantment extends Enchantment {
    private static final String[] TYPE_NAMES = new String[]{"all", "fire", "fall", "explosion", "projectile"};
    private static final int[] MIN_XP = new int[]{1, 10, 5, 5, 3};
    private static final int[] XP_MODIFIER = new int[]{11, 8, 6, 8, 6};
    private static final int[] MAX_XP = new int[]{20, 12, 10, 12, 15};
    /**
     * Defines what type of damage this enchantment offers extra protection from.
     * 
     * <p>
     * Accepted values:
     * <br>- 0: all
     * <br>- 1: fire
     * <br>- 2: falling
     * <br>- 3: explosions
     * <br>- 4: projectiles
     */
    public final int type;

    public ProtectionEnchantment(int id, Identifier key, int type, int protectionType) {
        super(id, key, type, EnchantmentCategory.ARMOR);
        this.type = protectionType;
        if (protectionType == 2) {
            this.category = EnchantmentCategory.ARMOR_FEET;
        }
    }

    @Override
    public int getMinXpRequirement(int level) {
        return MIN_XP[this.type] + (level - 1) * XP_MODIFIER[this.type];
    }

    @Override
    public int getMaxXpRequirement(int level) {
        return this.getMinXpRequirement(level) + MAX_XP[this.type];
    }

    @Override
    public int getMaxLevel() {
        return 4;
    }

    @Override
    public int getExtraProtection(int level, DamageSource source) {
        if (source.isOutOfWorld()) {
            return 0;
        } else {
            float f = (6 + level * level) / 3.0F;
            if (this.type == 0) {
                return MathHelper.floor(f * 0.75F);
            } else if (this.type == 1 && source.isFire()) {
                return MathHelper.floor(f * 1.25F);
            } else if (this.type == 2 && source == DamageSource.FALL) {
                return MathHelper.floor(f * 2.5F);
            } else if (this.type == 3 && source.isExplosive()) {
                return MathHelper.floor(f * 1.5F);
            } else {
                return this.type == 4 && source.isProjectile() ? MathHelper.floor(f * 1.5F) : 0;
            }
        }
    }

    @Override
    public String getTranslationKey() {
        return "enchantment.protect." + TYPE_NAMES[this.type];
    }

    @Override
    public boolean isCompatible(Enchantment other) {
        if (other instanceof ProtectionEnchantment) {
            ProtectionEnchantment protectionenchantment = (ProtectionEnchantment)other;
            return protectionenchantment.type != this.type && (this.type == 2 || protectionenchantment.type == 2);
        } else {
            return super.isCompatible(other);
        }
    }

    public static int modifyOnFireTimer(Entity entity, int ticks) {
        int i = EnchantmentHelper.getHighestEnchantmentLevel(Enchantment.FIRE_PROTECTION.id, entity.getEquipment());
        if (i > 0) {
            ticks -= MathHelper.floor(ticks * (i * 0.15F));
        }

        return ticks;
    }

    public static double modifyExplosionDamage(Entity entity, double damage) {
        int i = EnchantmentHelper.getHighestEnchantmentLevel(Enchantment.BLAST_PROTECTION.id, entity.getEquipment());
        if (i > 0) {
            damage -= MathHelper.floor(damage * (i * 0.15F));
        }

        return damage;
    }
}
