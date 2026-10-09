package net.minecraft.crafting;

import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class WeaponRecipes {
    private String[][] patterns = new String[][]{{"X", "X", "#"}};
    private Object[][] recipes = new Object[][]{
        {Blocks.PLANKS, Blocks.COBBLESTONE, Items.IRON_INGOT, Items.DIAMOND, Items.GOLD_INGOT},
        {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.GOLDEN_SWORD}
    };

    public void register(CraftingManager manager) {
        for (int i = 0; i < this.recipes[0].length; i++) {
            Object object = this.recipes[0][i];

            for (int j = 0; j < this.recipes.length - 1; j++) {
                Item item = (Item)this.recipes[j + 1][i];
                manager.registerShaped(new ItemStack(item), this.patterns[j], '#', Items.STICK, 'X', object);
            }
        }

        manager.registerShaped(new ItemStack(Items.BOW, 1), " #X", "# X", " #X", 'X', Items.STRING, '#', Items.STICK);
        manager.registerShaped(new ItemStack(Items.ARROW, 4), "X", "#", "Y", 'Y', Items.FEATHER, 'X', Items.FLINT, '#', Items.STICK);
    }
}
