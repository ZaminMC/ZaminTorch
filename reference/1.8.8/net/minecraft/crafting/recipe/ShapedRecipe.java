package net.minecraft.crafting.recipe;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

public class ShapedRecipe implements Recipe {
    private final int width;
    private final int height;
    private final ItemStack[] ingredients;
    private final ItemStack result;
    private boolean copyNbt;

    public ShapedRecipe(int width, int height, ItemStack[] ingredients, ItemStack result) {
        this.width = width;
        this.height = height;
        this.ingredients = ingredients;
        this.result = result;
    }

    @Override
    public ItemStack getResult() {
        return this.result;
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

    @Override
    public boolean matches(CraftingInventory inventory, World world) {
        for (int i = 0; i <= 3 - this.width; i++) {
            for (int j = 0; j <= 3 - this.height; j++) {
                if (this.matches(inventory, i, j, true)) {
                    return true;
                }

                if (this.matches(inventory, i, j, false)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean matches(CraftingInventory inventory, int x, int y, boolean rotate) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int k = i - x;
                int l = j - y;
                ItemStack itemstack = null;
                if (k >= 0 && l >= 0 && k < this.width && l < this.height) {
                    if (rotate) {
                        itemstack = this.ingredients[this.width - k - 1 + l * this.width];
                    } else {
                        itemstack = this.ingredients[k + l * this.width];
                    }
                }

                ItemStack itemstack1 = inventory.getItem(i, j);
                if (itemstack1 != null || itemstack != null) {
                    if (itemstack1 == null && itemstack != null || itemstack1 != null && itemstack == null) {
                        return false;
                    }

                    if (itemstack.getItem() != itemstack1.getItem()) {
                        return false;
                    }

                    if (itemstack.getMetadata() != 32767 && itemstack.getMetadata() != itemstack1.getMetadata()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public ItemStack getResult(CraftingInventory inventory) {
        ItemStack itemstack = this.getResult().copy();
        if (this.copyNbt) {
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack itemstack1 = inventory.getItem(i);
                if (itemstack1 != null && itemstack1.hasNbt()) {
                    itemstack.setNbt((NbtCompound)itemstack1.getNbt().copy());
                }
            }
        }

        return itemstack;
    }

    @Override
    public int size() {
        return this.width * this.height;
    }
}
