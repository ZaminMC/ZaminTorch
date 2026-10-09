package net.minecraft.crafting;

import net.minecraft.block.Blocks;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class FoodRecipes {
    public void register(CraftingManager manager) {
        manager.registerShapeless(new ItemStack(Items.MUSHROOM_STEW), Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Items.BOWL);
        manager.registerShaped(new ItemStack(Items.COOKIE, 8), "#X#", 'X', new ItemStack(Items.DYE, 1, DyeColor.BROWN.getMetadata()), '#', Items.WHEAT);
        manager.registerShaped(
            new ItemStack(Items.RABBIT_STEW),
            " R ",
            "CPM",
            " B ",
            'R',
            new ItemStack(Items.COOKED_RABBIT),
            'C',
            Items.CARROT,
            'P',
            Items.BAKED_POTATO,
            'M',
            Blocks.BROWN_MUSHROOM,
            'B',
            Items.BOWL
        );
        manager.registerShaped(
            new ItemStack(Items.RABBIT_STEW),
            " R ",
            "CPD",
            " B ",
            'R',
            new ItemStack(Items.COOKED_RABBIT),
            'C',
            Items.CARROT,
            'P',
            Items.BAKED_POTATO,
            'D',
            Blocks.RED_MUSHROOM,
            'B',
            Items.BOWL
        );
        manager.registerShaped(new ItemStack(Blocks.MELON_BLOCK), "MMM", "MMM", "MMM", 'M', Items.MELON);
        manager.registerShaped(new ItemStack(Items.MELON_SEEDS), "M", 'M', Items.MELON);
        manager.registerShaped(new ItemStack(Items.PUMPKIN_SEEDS, 4), "M", 'M', Blocks.PUMPKIN);
        manager.registerShapeless(new ItemStack(Items.PUMPKIN_PIE), Blocks.PUMPKIN, Items.SUGAR, Items.EGG);
        manager.registerShapeless(new ItemStack(Items.FERMENTED_SPIDER_EYE), Items.SPIDER_EYE, Blocks.BROWN_MUSHROOM, Items.SUGAR);
        manager.registerShapeless(new ItemStack(Items.BLAZE_POWDER, 2), Items.BLAZE_ROD);
        manager.registerShapeless(new ItemStack(Items.MAGMA_CREAM), Items.BLAZE_POWDER, Items.SLIME_BALL);
    }
}
