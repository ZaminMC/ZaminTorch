package net.minecraft.enchantment;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.WeightedPicker;

public class EnchantmentHelper {
    private static final Random RANDOM = new Random();
    private static final EnchantmentHelper.ProtectionModifier PROTECTION_MODIFIER = new EnchantmentHelper.ProtectionModifier();
    private static final EnchantmentHelper.DamageModifier DAMAGE_MODIFIER = new EnchantmentHelper.DamageModifier();
    private static final EnchantmentHelper.ProtectionWildcard PROTECTION_WILDCARD = new EnchantmentHelper.ProtectionWildcard();
    private static final EnchantmentHelper.DamageWildcard DAMAGE_WILDCARD = new EnchantmentHelper.DamageWildcard();

    public static int getLevel(int id, ItemStack item) {
        if (item == null) {
            return 0;
        }

        NbtList nbtlist = item.getEnchantments();
        if (nbtlist == null) {
            return 0;
        }

        for (int i = 0; i < nbtlist.size(); i++) {
            int j = nbtlist.getCompound(i).getShort("id");
            int k = nbtlist.getCompound(i).getShort("lvl");
            if (j == id) {
                return k;
            }
        }

        return 0;
    }

    public static Map<Integer, Integer> getEnchantments(ItemStack item) {
        Map<Integer, Integer> map = Maps.newLinkedHashMap();
        NbtList nbtlist = item.getItem() == Items.ENCHANTED_BOOK ? Items.ENCHANTED_BOOK.getStoredEnchantments(item) : item.getEnchantments();
        if (nbtlist != null) {
            for (int i = 0; i < nbtlist.size(); i++) {
                int j = nbtlist.getCompound(i).getShort("id");
                int k = nbtlist.getCompound(i).getShort("lvl");
                map.put(j, k);
            }
        }

        return map;
    }

    public static void setEnchantments(Map<Integer, Integer> enchantments, ItemStack item) {
        NbtList nbtlist = new NbtList();

        for (int i : enchantments.keySet()) {
            Enchantment enchantment = Enchantment.byId(i);
            if (enchantment != null) {
                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putShort("id", (short)i);
                nbtcompound.putShort("lvl", (short)enchantments.get(i).intValue());
                nbtlist.addElement(nbtcompound);
                if (item.getItem() == Items.ENCHANTED_BOOK) {
                    Items.ENCHANTED_BOOK.addEnchantment(item, new EnchantmentEntry(enchantment, enchantments.get(i)));
                }
            }
        }

        if (nbtlist.size() > 0) {
            if (item.getItem() != Items.ENCHANTED_BOOK) {
                item.addToNbt("ench", nbtlist);
            }
        } else if (item.hasNbt()) {
            item.getNbt().remove("ench");
        }
    }

    public static int getHighestEnchantmentLevel(int id, ItemStack[] items) {
        if (items == null) {
            return 0;
        }

        int i = 0;

        for (ItemStack itemstack : items) {
            int j = getLevel(id, itemstack);
            if (j > i) {
                i = j;
            }
        }

        return i;
    }

    private static void applyAttackModifier(EnchantmentHelper.AttackModifier modifier, ItemStack item) {
        if (item != null) {
            NbtList nbtlist = item.getEnchantments();
            if (nbtlist != null) {
                for (int i = 0; i < nbtlist.size(); i++) {
                    int j = nbtlist.getCompound(i).getShort("id");
                    int k = nbtlist.getCompound(i).getShort("lvl");
                    if (Enchantment.byId(j) != null) {
                        modifier.apply(Enchantment.byId(j), k);
                    }
                }
            }
        }
    }

    private static void applyAttackModifier(EnchantmentHelper.AttackModifier modifier, ItemStack[] items) {
        for (ItemStack itemstack : items) {
            applyAttackModifier(modifier, itemstack);
        }
    }

    /**
     * Returns the increase in protection from the damage source due to the armor stacks' enchantments.
     */
    public static int modifyProtection(ItemStack[] armor, DamageSource source) {
        PROTECTION_MODIFIER.protection = 0;
        PROTECTION_MODIFIER.source = source;
        applyAttackModifier(PROTECTION_MODIFIER, armor);
        if (PROTECTION_MODIFIER.protection > 25) {
            PROTECTION_MODIFIER.protection = 25;
        } else if (PROTECTION_MODIFIER.protection < 0) {
            PROTECTION_MODIFIER.protection = 0;
        }

        return (PROTECTION_MODIFIER.protection + 1 >> 1) + RANDOM.nextInt((PROTECTION_MODIFIER.protection >> 1) + 1);
    }

    /**
     * Returns the increase in damage to the target due to the attacker's enchantments.
     */
    public static float modifyDamage(ItemStack weapon, MobType target) {
        DAMAGE_MODIFIER.damage = 0.0F;
        DAMAGE_MODIFIER.mobType = target;
        applyAttackModifier(DAMAGE_MODIFIER, weapon);
        return DAMAGE_MODIFIER.damage;
    }

    public static void applyProtectionWildcard(LivingEntity attacker, Entity target) {
        PROTECTION_WILDCARD.target = target;
        PROTECTION_WILDCARD.attacker = attacker;
        if (attacker != null) {
            applyAttackModifier(PROTECTION_WILDCARD, attacker.getEquipment());
        }

        if (target instanceof PlayerEntity) {
            applyAttackModifier(PROTECTION_WILDCARD, attacker.getDisplayItemInHand());
        }
    }

    public static void applyDamageWildcard(LivingEntity attacker, Entity target) {
        DAMAGE_WILDCARD.attacker = attacker;
        DAMAGE_WILDCARD.target = target;
        if (attacker != null) {
            applyAttackModifier(DAMAGE_WILDCARD, attacker.getEquipment());
        }

        if (attacker instanceof PlayerEntity) {
            applyAttackModifier(DAMAGE_WILDCARD, attacker.getDisplayItemInHand());
        }
    }

    public static int getKnockbackLevel(LivingEntity attacker) {
        return getLevel(Enchantment.KNOCKBACK.id, attacker.getDisplayItemInHand());
    }

    public static int getFireAspectLevel(LivingEntity entity) {
        return getLevel(Enchantment.FIRE_ASPECT.id, entity.getDisplayItemInHand());
    }

    public static int getRespirationLevel(Entity entity) {
        return getHighestEnchantmentLevel(Enchantment.RESPIRATION.id, entity.getEquipment());
    }

    public static int getDepthStriderLevel(Entity entity) {
        return getHighestEnchantmentLevel(Enchantment.DEPTH_STRIDER.id, entity.getEquipment());
    }

    public static int getEfficiencyLevel(LivingEntity entity) {
        return getLevel(Enchantment.EFFICIENCY.id, entity.getDisplayItemInHand());
    }

    public static boolean hasSilkTouch(LivingEntity entity) {
        return getLevel(Enchantment.SILK_TOUCH.id, entity.getDisplayItemInHand()) > 0;
    }

    public static int getFortuneLevel(LivingEntity entity) {
        return getLevel(Enchantment.FORTUNE.id, entity.getDisplayItemInHand());
    }

    public static int getLuckOfTheSeaLevel(LivingEntity entity) {
        return getLevel(Enchantment.LUCK_OF_THE_SEA.id, entity.getDisplayItemInHand());
    }

    public static int getLureLevel(LivingEntity entity) {
        return getLevel(Enchantment.LURE.id, entity.getDisplayItemInHand());
    }

    public static int getLootingLevel(LivingEntity entity) {
        return getLevel(Enchantment.LOOTING.id, entity.getDisplayItemInHand());
    }

    public static boolean getAquaAffinityLevel(LivingEntity entity) {
        return getHighestEnchantmentLevel(Enchantment.AQUA_AFFINITY.id, entity.getEquipment()) > 0;
    }

    public static ItemStack getEquipmentWithEnchantment(Enchantment enchantment, LivingEntity entity) {
        for (ItemStack itemstack : entity.getEquipment()) {
            if (itemstack != null && getLevel(enchantment.id, itemstack) > 0) {
                return itemstack;
            }
        }

        return null;
    }

    public static int getRequiredXpLevel(Random random, int entry, int max, ItemStack item) {
        Item itemx = item.getItem();
        int i = itemx.getEnchantability();
        if (i <= 0) {
            return 0;
        }

        if (max > 15) {
            max = 15;
        }

        int j = random.nextInt(8) + 1 + (max >> 1) + random.nextInt(max + 1);
        if (entry == 0) {
            return Math.max(j / 3, 1);
        } else {
            return entry == 1 ? j * 2 / 3 + 1 : Math.max(j, max * 2);
        }
    }

    public static ItemStack addRandomEnchantment(Random random, ItemStack item, int xpLevel) {
        List<EnchantmentEntry> list = getEnchantmentEntries(random, item, xpLevel);
        boolean flag = item.getItem() == Items.BOOK;
        if (flag) {
            item.setItem(Items.ENCHANTED_BOOK);
        }

        if (list != null) {
            for (EnchantmentEntry enchantmententry : list) {
                if (flag) {
                    Items.ENCHANTED_BOOK.addEnchantment(item, enchantmententry);
                } else {
                    item.addEnchantment(enchantmententry.enchantment, enchantmententry.level);
                }
            }
        }

        return item;
    }

    public static List<EnchantmentEntry> getEnchantmentEntries(Random random, ItemStack item, int xpLevel) {
        Item itemx = item.getItem();
        int i = itemx.getEnchantability();
        if (i <= 0) {
            return null;
        }

        i /= 2;
        i = 1 + random.nextInt((i >> 1) + 1) + random.nextInt((i >> 1) + 1);
        int j = i + xpLevel;
        float f = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
        int k = (int)(j * (1.0F + f) + 0.5F);
        if (k < 1) {
            k = 1;
        }

        List<EnchantmentEntry> list = null;
        Map<Integer, EnchantmentEntry> map = getAvailableEnchantmentEntries(k, item);
        if (map != null && !map.isEmpty()) {
            EnchantmentEntry enchantmententry = WeightedPicker.pick(random, map.values());
            if (enchantmententry != null) {
                list = Lists.newArrayList();
                list.add(enchantmententry);

                for (int l = k; random.nextInt(50) <= l; l >>= 1) {
                    Iterator<Integer> iterator = map.keySet().iterator();

                    while (iterator.hasNext()) {
                        Integer integer = iterator.next();
                        boolean flag = true;

                        for (EnchantmentEntry enchantmententry1 : list) {
                            if (!enchantmententry1.enchantment.isCompatible(Enchantment.byId(integer))) {
                                flag = false;
                                break;
                            }
                        }

                        if (!flag) {
                            iterator.remove();
                        }
                    }

                    if (!map.isEmpty()) {
                        EnchantmentEntry enchantmententry2 = WeightedPicker.pick(random, map.values());
                        list.add(enchantmententry2);
                    }
                }
            }
        }

        return list;
    }

    public static Map<Integer, EnchantmentEntry> getAvailableEnchantmentEntries(int xp, ItemStack item) {
        Item itemx = item.getItem();
        Map<Integer, EnchantmentEntry> map = null;
        boolean flag = item.getItem() == Items.BOOK;

        for (Enchantment enchantment : Enchantment.ALL) {
            if (enchantment != null && (enchantment.category.canEnchant(itemx) || flag)) {
                for (int i = enchantment.getMinLevel(); i <= enchantment.getMaxLevel(); i++) {
                    if (xp >= enchantment.getMinXpRequirement(i) && xp <= enchantment.getMaxXpRequirement(i)) {
                        if (map == null) {
                            map = Maps.newHashMap();
                        }

                        map.put(enchantment.id, new EnchantmentEntry(enchantment, i));
                    }
                }
            }
        }

        return map;
    }

    interface AttackModifier {
        void apply(Enchantment enchantment, int level);
    }

    static final class DamageModifier implements EnchantmentHelper.AttackModifier {
        public float damage;
        public MobType mobType;

        private DamageModifier() {
        }

        @Override
        public void apply(Enchantment enchantment, int level) {
            this.damage = this.damage + enchantment.getExtraDamage(level, this.mobType);
        }
    }

    static final class DamageWildcard implements EnchantmentHelper.AttackModifier {
        public LivingEntity attacker;
        public Entity target;

        private DamageWildcard() {
        }

        @Override
        public void apply(Enchantment enchantment, int level) {
            enchantment.applyDamageWildcard(this.attacker, this.target, level);
        }
    }

    static final class ProtectionModifier implements EnchantmentHelper.AttackModifier {
        public int protection;
        public DamageSource source;

        private ProtectionModifier() {
        }

        @Override
        public void apply(Enchantment enchantment, int level) {
            this.protection = this.protection + enchantment.getExtraProtection(level, this.source);
        }
    }

    static final class ProtectionWildcard implements EnchantmentHelper.AttackModifier {
        public LivingEntity attacker;
        public Entity target;

        private ProtectionWildcard() {
        }

        @Override
        public void apply(Enchantment enchantment, int level) {
            enchantment.applyProtectionWildcard(this.attacker, this.target, level);
        }
    }
}
