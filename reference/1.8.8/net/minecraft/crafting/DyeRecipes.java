package net.minecraft.crafting;

import net.minecraft.block.Blocks;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class DyeRecipes {
    public void register(CraftingManager manager) {
        for (int i = 0; i < 16; i++) {
            manager.registerShapeless(new ItemStack(Blocks.WOOL, 1, i), new ItemStack(Items.DYE, 1, 15 - i), new ItemStack(Item.byBlock(Blocks.WOOL), 1, 0));
            manager.registerShaped(
                new ItemStack(Blocks.STAINED_HARDENED_CLAY, 8, 15 - i),
                "###",
                "#X#",
                "###",
                '#',
                new ItemStack(Blocks.HARDENED_CLAY),
                'X',
                new ItemStack(Items.DYE, 1, i)
            );
            manager.registerShaped(
                new ItemStack(Blocks.STAINED_GLASS, 8, 15 - i), "###", "#X#", "###", '#', new ItemStack(Blocks.GLASS), 'X', new ItemStack(Items.DYE, 1, i)
            );
            manager.registerShaped(new ItemStack(Blocks.STAINED_GLASS_PANE, 16, i), "###", "###", '#', new ItemStack(Blocks.STAINED_GLASS, 1, i));
        }

        manager.registerShapeless(
            new ItemStack(Items.DYE, 1, DyeColor.YELLOW.getMetadata()), new ItemStack(Blocks.YELLOW_FLOWER, 1, FlowerBlock.Type.DANDELION.getId())
        );
        manager.registerShapeless(new ItemStack(Items.DYE, 1, DyeColor.RED.getMetadata()), new ItemStack(Blocks.RED_FLOWER, 1, FlowerBlock.Type.POPPY.getId()));
        manager.registerShapeless(new ItemStack(Items.DYE, 3, DyeColor.WHITE.getMetadata()), Items.BONE);
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.PINK.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.RED.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.WHITE.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.ORANGE.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.RED.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.YELLOW.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.LIME.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.GREEN.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.WHITE.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.GRAY.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.BLACK.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.WHITE.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.SILVER.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.GRAY.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.WHITE.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 3, DyeColor.SILVER.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.BLACK.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.WHITE.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.WHITE.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.LIGHT_BLUE.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.BLUE.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.WHITE.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.CYAN.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.BLUE.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.GREEN.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.PURPLE.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.BLUE.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.RED.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.MAGENTA.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.PURPLE.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.PINK.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 3, DyeColor.MAGENTA.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.BLUE.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.RED.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.PINK.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 4, DyeColor.MAGENTA.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.BLUE.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.RED.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.RED.getMetadata()),
            new ItemStack(Items.DYE, 1, DyeColor.WHITE.getMetadata())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 1, DyeColor.LIGHT_BLUE.getMetadata()), new ItemStack(Blocks.RED_FLOWER, 1, FlowerBlock.Type.BLUE_ORCHID.getId())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 1, DyeColor.MAGENTA.getMetadata()), new ItemStack(Blocks.RED_FLOWER, 1, FlowerBlock.Type.ALLIUM.getId())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 1, DyeColor.SILVER.getMetadata()), new ItemStack(Blocks.RED_FLOWER, 1, FlowerBlock.Type.HOUSTONIA.getId())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 1, DyeColor.RED.getMetadata()), new ItemStack(Blocks.RED_FLOWER, 1, FlowerBlock.Type.RED_TULIP.getId())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 1, DyeColor.ORANGE.getMetadata()), new ItemStack(Blocks.RED_FLOWER, 1, FlowerBlock.Type.ORANGE_TULIP.getId())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 1, DyeColor.SILVER.getMetadata()), new ItemStack(Blocks.RED_FLOWER, 1, FlowerBlock.Type.WHITE_TULIP.getId())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 1, DyeColor.PINK.getMetadata()), new ItemStack(Blocks.RED_FLOWER, 1, FlowerBlock.Type.PINK_TULIP.getId())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 1, DyeColor.SILVER.getMetadata()), new ItemStack(Blocks.RED_FLOWER, 1, FlowerBlock.Type.OXEY_DAISY.getId())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.YELLOW.getMetadata()), new ItemStack(Blocks.DOUBLE_PLANT, 1, DoublePlantBlock.Variant.SUNFLOWER.getId())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.MAGENTA.getMetadata()), new ItemStack(Blocks.DOUBLE_PLANT, 1, DoublePlantBlock.Variant.SYRINGA.getId())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.RED.getMetadata()), new ItemStack(Blocks.DOUBLE_PLANT, 1, DoublePlantBlock.Variant.ROSE.getId())
        );
        manager.registerShapeless(
            new ItemStack(Items.DYE, 2, DyeColor.PINK.getMetadata()), new ItemStack(Blocks.DOUBLE_PLANT, 1, DoublePlantBlock.Variant.PAEONIA.getId())
        );

        for (int j = 0; j < 16; j++) {
            manager.registerShaped(new ItemStack(Blocks.CARPET, 3, j), "##", '#', new ItemStack(Blocks.WOOL, 1, j));
        }
    }
}
