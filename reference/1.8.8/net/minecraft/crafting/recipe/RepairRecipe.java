package net.minecraft.crafting.recipe;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class RepairRecipe implements Recipe {
    @Override
    public boolean matches(CraftingInventory inventory, World world) {
        List<ItemStack> list = Lists.newArrayList();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack itemstack = inventory.getItem(i);
            if (itemstack != null) {
                list.add(itemstack);
                if (list.size() > 1) {
                    ItemStack itemstack1 = list.get(0);
                    if (itemstack.getItem() != itemstack1.getItem() || itemstack1.size != 1 || itemstack.size != 1 || !itemstack1.getItem().isDamageable()) {
                        return false;
                    }
                }
            }
        }

        return list.size() == 2;
    }

    @Override
    public ItemStack getResult(CraftingInventory inventory) {
        List<ItemStack> list = Lists.newArrayList();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack itemstack = inventory.getItem(i);
            if (itemstack != null) {
                list.add(itemstack);
                if (list.size() > 1) {
                    ItemStack itemstack1 = list.get(0);
                    if (itemstack.getItem() != itemstack1.getItem() || itemstack1.size != 1 || itemstack.size != 1 || !itemstack1.getItem().isDamageable()) {
                        return null;
                    }
                }
            }
        }

        if (list.size() == 2) {
            ItemStack itemstack2 = list.get(0);
            ItemStack itemstack3 = list.get(1);
            if (itemstack2.getItem() == itemstack3.getItem() && itemstack2.size == 1 && itemstack3.size == 1 && itemstack2.getItem().isDamageable()) {
                Item item = itemstack2.getItem();
                int j = item.getMaxDamage() - itemstack2.getDamage();
                int k = item.getMaxDamage() - itemstack3.getDamage();
                int l = j + k + item.getMaxDamage() * 5 / 100;
                int i1 = item.getMaxDamage() - l;
                if (i1 < 0) {
                    i1 = 0;
                }

                return new ItemStack(itemstack2.getItem(), 1, i1);
            }
        }

        return null;
    }

    @Override
    public int size() {
        return 4;
    }

    @Override
    public ItemStack getResult() {
        return null;
    }

    @Override
    public ItemStack[] getRemainder(CraftingInventory inventory) {
        ItemStack[] aitemstack = new ItemStack[inventory.getSize()];

        for (int i = 0; i < aitemstack.length; i++) {
            ItemStack itemstack = inventory.getItem(i);
            if (itemstack != null && itemstack.getItem().hasRecipeRemainder()) {
                aitemstack[i] = new ItemStack(itemstack.getItem().getRecipeRemainder());
            }
        }

        return aitemstack;
    }
}
