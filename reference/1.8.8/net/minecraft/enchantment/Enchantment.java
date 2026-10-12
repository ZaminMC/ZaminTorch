package net.minecraft.enchantment;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.locale.I18n;
import net.minecraft.resource.Identifier;

public abstract class Enchantment {
    private static final Enchantment[] BY_ID = new Enchantment[256];
    public static final Enchantment[] ALL;
    private static final Map<Identifier, Enchantment> BY_KEY = Maps.newHashMap();
    public static final Enchantment PROTECTION = new ProtectionEnchantment(0, new Identifier("protection"), 10, 0);
    public static final Enchantment FIRE_PROTECTION = new ProtectionEnchantment(1, new Identifier("fire_protection"), 5, 1);
    public static final Enchantment FEATHER_FALLING = new ProtectionEnchantment(2, new Identifier("feather_falling"), 5, 2);
    public static final Enchantment BLAST_PROTECTION = new ProtectionEnchantment(3, new Identifier("blast_protection"), 2, 3);
    public static final Enchantment PROJECTILE_PROTECTION = new ProtectionEnchantment(4, new Identifier("projectile_protection"), 5, 4);
    public static final Enchantment RESPIRATION = new RespirationEnchantment(5, new Identifier("respiration"), 2);
    public static final Enchantment AQUA_AFFINITY = new AquaAffinityEnchantment(6, new Identifier("aqua_affinity"), 2);
    public static final Enchantment THORNS = new ThornsEnchantment(7, new Identifier("thorns"), 1);
    public static final Enchantment DEPTH_STRIDER = new DepthStriderEnchantment(8, new Identifier("depth_strider"), 2);
    public static final Enchantment SHARPNESS = new DamageEnchantment(16, new Identifier("sharpness"), 10, 0);
    public static final Enchantment SMITE = new DamageEnchantment(17, new Identifier("smite"), 5, 1);
    public static final Enchantment BANE_OF_ARTHROPODS = new DamageEnchantment(18, new Identifier("bane_of_arthropods"), 5, 2);
    public static final Enchantment KNOCKBACK = new KnockbackEnchantment(19, new Identifier("knockback"), 5);
    public static final Enchantment FIRE_ASPECT = new FireAspectEnchantment(20, new Identifier("fire_aspect"), 2);
    public static final Enchantment LOOTING = new BetterLootEnchantment(21, new Identifier("looting"), 2, EnchantmentCategory.WEAPON);
    public static final Enchantment EFFICIENCY = new EfficiencyEnchantment(32, new Identifier("efficiency"), 10);
    public static final Enchantment SILK_TOUCH = new SilkTouchEnchantment(33, new Identifier("silk_touch"), 1);
    public static final Enchantment UNBREAKING = new UnbreakingEnchantment(34, new Identifier("unbreaking"), 5);
    public static final Enchantment FORTUNE = new BetterLootEnchantment(35, new Identifier("fortune"), 2, EnchantmentCategory.DIGGER);
    public static final Enchantment POWER = new PowerEnchantment(48, new Identifier("power"), 10);
    public static final Enchantment PUNCH = new PunchEnchantment(49, new Identifier("punch"), 2);
    public static final Enchantment FLAME = new FlameEnchantment(50, new Identifier("flame"), 2);
    public static final Enchantment INFINITY = new InfinityEnchantment(51, new Identifier("infinity"), 1);
    public static final Enchantment LUCK_OF_THE_SEA = new BetterLootEnchantment(61, new Identifier("luck_of_the_sea"), 2, EnchantmentCategory.FISHING_ROD);
    public static final Enchantment LURE = new LureEnchantment(62, new Identifier("lure"), 2, EnchantmentCategory.FISHING_ROD);
    public final int id;
    private final int type;
    public EnchantmentCategory category;
    protected String key;

    public static Enchantment byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }

    protected Enchantment(int id, Identifier key, int type, EnchantmentCategory category) {
        this.id = id;
        this.type = type;
        this.category = category;
        if (BY_ID[id] != null) {
            throw new IllegalArgumentException("Duplicate enchantment id!");
        }

        BY_ID[id] = this;
        BY_KEY.put(key, this);
    }

    public static Enchantment byKey(String key) {
        return BY_KEY.get(new Identifier(key));
    }

    public static Set<Identifier> getKeys() {
        return BY_KEY.keySet();
    }

    public int getType() {
        return this.type;
    }

    public int getMinLevel() {
        return 1;
    }

    public int getMaxLevel() {
        return 1;
    }

    public int getMinXpRequirement(int level) {
        return 1 + level * 10;
    }

    public int getMaxXpRequirement(int level) {
        return this.getMinXpRequirement(level) + 5;
    }

    public int getExtraProtection(int level, DamageSource source) {
        return 0;
    }

    public float getExtraDamage(int level, MobType mobType) {
        return 0.0F;
    }

    public boolean isCompatible(Enchantment other) {
        return this != other;
    }

    public Enchantment setKey(String key) {
        this.key = key;
        return this;
    }

    public String getTranslationKey() {
        return "enchantment." + this.key;
    }

    public String getName(int level) {
        String s = I18n.translate(this.getTranslationKey());
        return s + " " + I18n.translate("enchantment.level." + level);
    }

    public boolean canEnchant(ItemStack item) {
        return this.category.canEnchant(item.getItem());
    }

    public void applyDamageWildcard(LivingEntity attacker, Entity target, int level) {
    }

    public void applyProtectionWildcard(LivingEntity attacker, Entity target, int level) {
    }

    static {
        List<Enchantment> list = Lists.newArrayList();

        for (Enchantment enchantment : BY_ID) {
            if (enchantment != null) {
                list.add(enchantment);
            }
        }

        ALL = list.toArray(new Enchantment[list.size()]);
    }
}
