package net.minecraft.crafting.recipe;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.minecraft.world.map.SavedMapData;

public class UpscaleMapRecipe extends ShapedRecipe {
    public UpscaleMapRecipe() {
        super(
            3,
            3,
            new ItemStack[]{
                new ItemStack(Items.PAPER),
                new ItemStack(Items.PAPER),
                new ItemStack(Items.PAPER),
                new ItemStack(Items.PAPER),
                new ItemStack(Items.FILLED_MAP, 0, 32767),
                new ItemStack(Items.PAPER),
                new ItemStack(Items.PAPER),
                new ItemStack(Items.PAPER),
                new ItemStack(Items.PAPER)
            },
            new ItemStack(Items.EMPTY_MAP, 0, 0)
        );
    }

    @Override
    public boolean matches(CraftingInventory inventory, World world) {
        if (!super.matches(inventory, world)) {
            return false;
        }

        ItemStack itemstack = null;

        for (int i = 0; i < inventory.getSize() && itemstack == null; i++) {
            ItemStack itemstack1 = inventory.getItem(i);
            if (itemstack1 != null && itemstack1.getItem() == Items.FILLED_MAP) {
                itemstack = itemstack1;
            }
        }

        if (itemstack == null) {
            return false;
        }

        SavedMapData savedmapdata = Items.FILLED_MAP.getSavedMapData(itemstack, world);
        return savedmapdata != null && savedmapdata.scale < 4;
    }

    @Override
    public ItemStack getResult(CraftingInventory inventory) {
        ItemStack itemstack = null;

        for (int i = 0; i < inventory.getSize() && itemstack == null; i++) {
            ItemStack itemstack1 = inventory.getItem(i);
            if (itemstack1 != null && itemstack1.getItem() == Items.FILLED_MAP) {
                itemstack = itemstack1;
            }
        }

        itemstack = itemstack.copy();
        itemstack.size = 1;
        if (itemstack.getNbt() == null) {
            itemstack.setNbt(new NbtCompound());
        }

        itemstack.getNbt().putBoolean("map_is_scaling", true);
        return itemstack;
    }
}
