package net.minecraft.crafting.recipe;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public interface Recipe {
    boolean matches(CraftingInventory inventory, World world);

    ItemStack getResult(CraftingInventory inventory);

    int size();

    ItemStack getResult();

    ItemStack[] getRemainder(CraftingInventory inventory);
}
