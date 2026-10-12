package net.minecraft.crafting;

import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ToolRecipes {
    private String[][] patterns = new String[][]{{"XXX", " # ", " # "}, {"X", "#", "#"}, {"XX", "X#", " #"}, {"XX", " #", " #"}};
    private Object[][] recipes = new Object[][]{
        {Blocks.PLANKS, Blocks.COBBLESTONE, Items.IRON_INGOT, Items.DIAMOND, Items.GOLD_INGOT},
        {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.GOLDEN_PICKAXE},
        {Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.GOLDEN_SHOVEL},
        {Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.GOLDEN_AXE},
        {Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE, Items.GOLDEN_HOE}
    };

    public void register(CraftingManager manager) {
        for (int i = 0; i < this.recipes[0].length; i++) {
            Object object = this.recipes[0][i];

            for (int j = 0; j < this.recipes.length - 1; j++) {
                Item item = (Item)this.recipes[j + 1][i];
                manager.registerShaped(new ItemStack(item), this.patterns[j], '#', Items.STICK, 'X', object);
            }
        }

        manager.registerShaped(new ItemStack(Items.SHEARS), " #", "# ", '#', Items.IRON_INGOT);
    }
}
