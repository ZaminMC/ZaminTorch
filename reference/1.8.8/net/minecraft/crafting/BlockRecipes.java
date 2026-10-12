package net.minecraft.crafting;

import net.minecraft.block.Blocks;
import net.minecraft.block.DirtBlock;
import net.minecraft.block.PrismarineBlock;
import net.minecraft.block.QuartzBlock;
import net.minecraft.block.RedSandstoneBlock;
import net.minecraft.block.RedSandstoneSlab;
import net.minecraft.block.SandBlock;
import net.minecraft.block.SandstoneBlock;
import net.minecraft.block.StoneBlock;
import net.minecraft.block.StoneSlabBlock;
import net.minecraft.block.StonebrickBlock;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class BlockRecipes {
    public void register(CraftingManager manager) {
        manager.registerShaped(new ItemStack(Blocks.CHEST), "###", "# #", "###", '#', Blocks.PLANKS);
        manager.registerShaped(new ItemStack(Blocks.TRAPPED_CHEST), "#-", '#', Blocks.CHEST, '-', Blocks.TRIPWIRE_HOOK);
        manager.registerShaped(new ItemStack(Blocks.ENDER_CHEST), "###", "#E#", "###", '#', Blocks.OBSIDIAN, 'E', Items.ENDER_EYE);
        manager.registerShaped(new ItemStack(Blocks.FURNACE), "###", "# #", "###", '#', Blocks.COBBLESTONE);
        manager.registerShaped(new ItemStack(Blocks.CRAFTING_TABLE), "##", "##", '#', Blocks.PLANKS);
        manager.registerShaped(new ItemStack(Blocks.SANDSTONE), "##", "##", '#', new ItemStack(Blocks.SAND, 1, SandBlock.Variant.SAND.getId()));
        manager.registerShaped(new ItemStack(Blocks.RED_SANDSTONE), "##", "##", '#', new ItemStack(Blocks.SAND, 1, SandBlock.Variant.RED_SAND.getId()));
        manager.registerShaped(
            new ItemStack(Blocks.SANDSTONE, 4, SandstoneBlock.Type.SMOOTH.getId()),
            "##",
            "##",
            '#',
            new ItemStack(Blocks.SANDSTONE, 1, SandstoneBlock.Type.DEFAULT.getId())
        );
        manager.registerShaped(
            new ItemStack(Blocks.RED_SANDSTONE, 4, RedSandstoneBlock.Type.SMOOTH.getId()),
            "##",
            "##",
            '#',
            new ItemStack(Blocks.RED_SANDSTONE, 1, RedSandstoneBlock.Type.DEFAULT.getId())
        );
        manager.registerShaped(
            new ItemStack(Blocks.SANDSTONE, 1, SandstoneBlock.Type.CHISELED.getId()),
            "#",
            "#",
            '#',
            new ItemStack(Blocks.STONE_SLAB, 1, StoneSlabBlock.Variant.SAND.getId())
        );
        manager.registerShaped(
            new ItemStack(Blocks.RED_SANDSTONE, 1, RedSandstoneBlock.Type.CHISELED.getId()),
            "#",
            "#",
            '#',
            new ItemStack(Blocks.RED_SANDSTONE_SLAB, 1, RedSandstoneSlab.Variant.RED_SANDSTONE.getId())
        );
        manager.registerShaped(
            new ItemStack(Blocks.QUARTZ_BLOCK, 1, QuartzBlock.Variant.CHISELED.getId()),
            "#",
            "#",
            '#',
            new ItemStack(Blocks.STONE_SLAB, 1, StoneSlabBlock.Variant.QUARTZ.getId())
        );
        manager.registerShaped(
            new ItemStack(Blocks.QUARTZ_BLOCK, 2, QuartzBlock.Variant.LINES_Y.getId()),
            "#",
            "#",
            '#',
            new ItemStack(Blocks.QUARTZ_BLOCK, 1, QuartzBlock.Variant.DEFAULT.getId())
        );
        manager.registerShaped(new ItemStack(Blocks.STONE_BRICKS, 4), "##", "##", '#', new ItemStack(Blocks.STONE, 1, StoneBlock.Variant.STONE.getId()));
        manager.registerShaped(
            new ItemStack(Blocks.STONE_BRICKS, 1, StonebrickBlock.CHISELED_ID),
            "#",
            "#",
            '#',
            new ItemStack(Blocks.STONE_SLAB, 1, StoneSlabBlock.Variant.SMOOTHBRICK.getId())
        );
        manager.registerShapeless(new ItemStack(Blocks.STONE_BRICKS, 1, StonebrickBlock.MOSSY_ID), Blocks.STONE_BRICKS, Blocks.VINE);
        manager.registerShapeless(new ItemStack(Blocks.MOSSY_COBBLESTONE, 1), Blocks.COBBLESTONE, Blocks.VINE);
        manager.registerShaped(new ItemStack(Blocks.IRON_BARS, 16), "###", "###", '#', Items.IRON_INGOT);
        manager.registerShaped(new ItemStack(Blocks.GLASS_PANE, 16), "###", "###", '#', Blocks.GLASS);
        manager.registerShaped(new ItemStack(Blocks.REDSTONE_LAMP, 1), " R ", "RGR", " R ", 'R', Items.REDSTONE, 'G', Blocks.GLOWSTONE);
        manager.registerShaped(new ItemStack(Blocks.BEACON, 1), "GGG", "GSG", "OOO", 'G', Blocks.GLASS, 'S', Items.NETHER_STAR, 'O', Blocks.OBSIDIAN);
        manager.registerShaped(new ItemStack(Blocks.NETHER_BRICKS, 1), "NN", "NN", 'N', Items.NETHERBRICK);
        manager.registerShaped(new ItemStack(Blocks.STONE, 2, StoneBlock.Variant.DIORITE.getId()), "CQ", "QC", 'C', Blocks.COBBLESTONE, 'Q', Items.QUARTZ);
        manager.registerShapeless(
            new ItemStack(Blocks.STONE, 1, StoneBlock.Variant.GRANITE.getId()),
            new ItemStack(Blocks.STONE, 1, StoneBlock.Variant.DIORITE.getId()),
            Items.QUARTZ
        );
        manager.registerShapeless(
            new ItemStack(Blocks.STONE, 2, StoneBlock.Variant.ANDESITE.getId()),
            new ItemStack(Blocks.STONE, 1, StoneBlock.Variant.DIORITE.getId()),
            Blocks.COBBLESTONE
        );
        manager.registerShaped(
            new ItemStack(Blocks.DIRT, 4, DirtBlock.Variant.COARSE_DIRT.getId()),
            "DG",
            "GD",
            'D',
            new ItemStack(Blocks.DIRT, 1, DirtBlock.Variant.DIRT.getId()),
            'G',
            Blocks.GRAVEL
        );
        manager.registerShaped(
            new ItemStack(Blocks.STONE, 4, StoneBlock.Variant.DIORITE_SMOOTH.getId()),
            "SS",
            "SS",
            'S',
            new ItemStack(Blocks.STONE, 1, StoneBlock.Variant.DIORITE.getId())
        );
        manager.registerShaped(
            new ItemStack(Blocks.STONE, 4, StoneBlock.Variant.GRANITE_SMOOTH.getId()),
            "SS",
            "SS",
            'S',
            new ItemStack(Blocks.STONE, 1, StoneBlock.Variant.GRANITE.getId())
        );
        manager.registerShaped(
            new ItemStack(Blocks.STONE, 4, StoneBlock.Variant.ANDESITE_SMOOTH.getId()),
            "SS",
            "SS",
            'S',
            new ItemStack(Blocks.STONE, 1, StoneBlock.Variant.ANDESITE.getId())
        );
        manager.registerShaped(new ItemStack(Blocks.PRISMARINE, 1, PrismarineBlock.ROUGH_VARIANT), "SS", "SS", 'S', Items.PRISMARINE_SHARD);
        manager.registerShaped(new ItemStack(Blocks.PRISMARINE, 1, PrismarineBlock.BRICKS_VARIANT), "SSS", "SSS", "SSS", 'S', Items.PRISMARINE_SHARD);
        manager.registerShaped(
            new ItemStack(Blocks.PRISMARINE, 1, PrismarineBlock.DARK_VARIANT),
            "SSS",
            "SIS",
            "SSS",
            'S',
            Items.PRISMARINE_SHARD,
            'I',
            new ItemStack(Items.DYE, 1, DyeColor.BLACK.getMetadata())
        );
        manager.registerShaped(new ItemStack(Blocks.SEA_LANTERN, 1, 0), "SCS", "CCC", "SCS", 'S', Items.PRISMARINE_SHARD, 'C', Items.PRISMARINE_CRYSTALS);
    }
}
