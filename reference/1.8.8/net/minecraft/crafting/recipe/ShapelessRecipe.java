package net.minecraft.crafting.recipe;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class ShapelessRecipe implements Recipe {
    private final ItemStack result;
    private final List<ItemStack> ingredients;

    public ShapelessRecipe(ItemStack result, List<ItemStack> ingredients) {
        this.result = result;
        this.ingredients = ingredients;
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
        List<ItemStack> list = Lists.newArrayList(this.ingredients);

        for (int i = 0; i < inventory.getHeight(); i++) {
            for (int j = 0; j < inventory.getWidth(); j++) {
                ItemStack itemstack = inventory.getItem(j, i);
                if (itemstack != null) {
                    boolean flag = false;

                    for (ItemStack itemstack1 : list) {
                        if (itemstack.getItem() == itemstack1.getItem()
                            && (itemstack1.getMetadata() == 32767 || itemstack.getMetadata() == itemstack1.getMetadata())) {
                            flag = true;
                            list.remove(itemstack1);
                            break;
                        }
                    }

                    if (!flag) {
                        return false;
                    }
                }
            }
        }

        return list.isEmpty();
    }

    @Override
    public ItemStack getResult(CraftingInventory inventory) {
        return this.result.copy();
    }

    @Override
    public int size() {
        return this.ingredients.size();
    }
}
