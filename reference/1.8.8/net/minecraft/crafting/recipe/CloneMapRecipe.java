package net.minecraft.crafting.recipe;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

public class CloneMapRecipe implements Recipe {
    @Override
    public boolean matches(CraftingInventory inventory, World world) {
        int i = 0;
        ItemStack itemstack = null;

        for (int j = 0; j < inventory.getSize(); j++) {
            ItemStack itemstack1 = inventory.getItem(j);
            if (itemstack1 != null) {
                if (itemstack1.getItem() == Items.FILLED_MAP) {
                    if (itemstack != null) {
                        return false;
                    }

                    itemstack = itemstack1;
                } else {
                    if (itemstack1.getItem() != Items.EMPTY_MAP) {
                        return false;
                    }

                    i++;
                }
            }
        }

        return itemstack != null && i > 0;
    }

    @Override
    public ItemStack getResult(CraftingInventory inventory) {
        int i = 0;
        ItemStack itemstack = null;

        for (int j = 0; j < inventory.getSize(); j++) {
            ItemStack itemstack1 = inventory.getItem(j);
            if (itemstack1 != null) {
                if (itemstack1.getItem() == Items.FILLED_MAP) {
                    if (itemstack != null) {
                        return null;
                    }

                    itemstack = itemstack1;
                } else {
                    if (itemstack1.getItem() != Items.EMPTY_MAP) {
                        return null;
                    }

                    i++;
                }
            }
        }

        if (itemstack != null && i >= 1) {
            ItemStack itemstack2 = new ItemStack(Items.FILLED_MAP, i + 1, itemstack.getMetadata());
            if (itemstack.hasCustomHoverName()) {
                itemstack2.setHoverName(itemstack.getHoverName());
            }

            return itemstack2;
        } else {
            return null;
        }
    }

    @Override
    public int size() {
        return 9;
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
