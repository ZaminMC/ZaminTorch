package net.minecraft.enchantment;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;

public enum EnchantmentCategory {
    ALL,
    ARMOR,
    ARMOR_FEET,
    ARMOR_LEGS,
    ARMOR_TORSO,
    ARMOR_HEAD,
    WEAPON,
    DIGGER,
    FISHING_ROD,
    BREAKABLE,
    BOW;

    public boolean canEnchant(Item item) {
        if (this == ALL) {
            return true;
        }

        if (this == BREAKABLE && item.isDamageable()) {
            return true;
        }

        if (item instanceof ArmorItem) {
            if (this == ARMOR) {
                return true;
            } else {
                ArmorItem armoritem = (ArmorItem)item;
                if (armoritem.slot == 0) {
                    return this == ARMOR_HEAD;
                } else if (armoritem.slot == 2) {
                    return this == ARMOR_LEGS;
                } else {
                    return armoritem.slot == 1 ? this == ARMOR_TORSO : armoritem.slot == 3 && this == ARMOR_FEET;
                }
            }
        } else if (item instanceof SwordItem) {
            return this == WEAPON;
        } else if (item instanceof ToolItem) {
            return this == DIGGER;
        } else {
            return item instanceof BowItem ? this == BOW : item instanceof FishingRodItem && this == FISHING_ROD;
        }
    }
}
