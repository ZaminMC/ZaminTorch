package net.minecraft.item;

import java.util.List;
import java.util.Random;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentEntry;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.gen.structure.LootEntry;

public class EnchantedBookItem extends Item {
    @Override
    public boolean hasEnchantmentGlint(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return this.getStoredEnchantments(stack).size() > 0 ? Rarity.UNCOMMON : super.getRarity(stack);
    }

    public NbtList getStoredEnchantments(ItemStack stack) {
        NbtCompound nbtcompound = stack.getNbt();
        return nbtcompound != null && nbtcompound.contains("StoredEnchantments", 9) ? (NbtList)nbtcompound.get("StoredEnchantments") : new NbtList();
    }

    @Override
    public void addHoverText(ItemStack stack, PlayerEntity player, List<String> tooltip, boolean advanced) {
        super.addHoverText(stack, player, tooltip, advanced);
        NbtList nbtlist = this.getStoredEnchantments(stack);
        if (nbtlist != null) {
            for (int i = 0; i < nbtlist.size(); i++) {
                int j = nbtlist.getCompound(i).getShort("id");
                int k = nbtlist.getCompound(i).getShort("lvl");
                if (Enchantment.byId(j) != null) {
                    tooltip.add(Enchantment.byId(j).getName(k));
                }
            }
        }
    }

    public void addEnchantment(ItemStack stack, EnchantmentEntry enchantment) {
        NbtList nbtlist = this.getStoredEnchantments(stack);
        boolean flag = true;

        for (int i = 0; i < nbtlist.size(); i++) {
            NbtCompound nbtcompound = nbtlist.getCompound(i);
            if (nbtcompound.getShort("id") == enchantment.enchantment.id) {
                if (nbtcompound.getShort("lvl") < enchantment.level) {
                    nbtcompound.putShort("lvl", (short)enchantment.level);
                }

                flag = false;
                break;
            }
        }

        if (flag) {
            NbtCompound nbtcompound1 = new NbtCompound();
            nbtcompound1.putShort("id", (short)enchantment.enchantment.id);
            nbtcompound1.putShort("lvl", (short)enchantment.level);
            nbtlist.addElement(nbtcompound1);
        }

        if (!stack.hasNbt()) {
            stack.setNbt(new NbtCompound());
        }

        stack.getNbt().put("StoredEnchantments", nbtlist);
    }

    public ItemStack withEnchantment(EnchantmentEntry enchantment) {
        ItemStack itemstack = new ItemStack(this);
        this.addEnchantment(itemstack, enchantment);
        return itemstack;
    }

    public void addToCreativeMenu(Enchantment enchantment, List<ItemStack> inventory) {
        for (int i = enchantment.getMinLevel(); i <= enchantment.getMaxLevel(); i++) {
            inventory.add(this.withEnchantment(new EnchantmentEntry(enchantment, i)));
        }
    }

    public LootEntry createLoot(Random random) {
        return this.createLoot(random, 1, 1, 1);
    }

    public LootEntry createLoot(Random random, int minItemChance, int maxItemChance, int weight) {
        ItemStack itemstack = new ItemStack(Items.BOOK, 1, 0);
        EnchantmentHelper.addRandomEnchantment(random, itemstack, 30);
        return new LootEntry(itemstack, minItemChance, maxItemChance, weight);
    }
}
