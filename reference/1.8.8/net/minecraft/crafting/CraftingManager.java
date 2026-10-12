package net.minecraft.crafting;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.RedSandstoneSlab;
import net.minecraft.block.StoneBlock;
import net.minecraft.block.StoneSlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.crafting.recipe.BannerRecipes;
import net.minecraft.crafting.recipe.CloneBookRecipe;
import net.minecraft.crafting.recipe.CloneMapRecipe;
import net.minecraft.crafting.recipe.DyeArmorRecipe;
import net.minecraft.crafting.recipe.FireworksRecipe;
import net.minecraft.crafting.recipe.Recipe;
import net.minecraft.crafting.recipe.RepairRecipe;
import net.minecraft.crafting.recipe.ShapedRecipe;
import net.minecraft.crafting.recipe.ShapelessRecipe;
import net.minecraft.crafting.recipe.UpscaleMapRecipe;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

public class CraftingManager {
    private static final CraftingManager INSTANCE = new CraftingManager();
    private final List<Recipe> recipes = Lists.newArrayList();

    public static CraftingManager getInstance() {
        return INSTANCE;
    }

    private CraftingManager() {
        new ToolRecipes().register(this);
        new WeaponRecipes().register(this);
        new MineralRecipes().register(this);
        new FoodRecipes().register(this);
        new BlockRecipes().register(this);
        new ArmorRecipes().register(this);
        new DyeRecipes().register(this);
        this.recipes.add(new DyeArmorRecipe());
        this.recipes.add(new CloneBookRecipe());
        this.recipes.add(new CloneMapRecipe());
        this.recipes.add(new UpscaleMapRecipe());
        this.recipes.add(new FireworksRecipe());
        this.recipes.add(new RepairRecipe());
        new BannerRecipes().register(this);
        this.registerShaped(new ItemStack(Items.PAPER, 3), "###", '#', Items.REEDS);
        this.registerShapeless(new ItemStack(Items.BOOK, 1), Items.PAPER, Items.PAPER, Items.PAPER, Items.LEATHER);
        this.registerShapeless(new ItemStack(Items.WRITABLE_BOOK, 1), Items.BOOK, new ItemStack(Items.DYE, 1, DyeColor.BLACK.getMetadata()), Items.FEATHER);
        this.registerShaped(
            new ItemStack(Blocks.FENCE, 3), "W#W", "W#W", '#', Items.STICK, 'W', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.OAK.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.BIRCH_FENCE, 3), "W#W", "W#W", '#', Items.STICK, 'W', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.BIRCH.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.SPRUCE_FENCE, 3), "W#W", "W#W", '#', Items.STICK, 'W', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.SPRUCE.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.JUNGLE_FENCE, 3), "W#W", "W#W", '#', Items.STICK, 'W', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.JUNGLE.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.ACACIA_FENCE, 3),
            "W#W",
            "W#W",
            '#',
            Items.STICK,
            'W',
            new ItemStack(Blocks.PLANKS, 1, 4 + PlanksBlock.Variant.ACACIA.getId() - 4)
        );
        this.registerShaped(
            new ItemStack(Blocks.DARK_OAK_FENCE, 3),
            "W#W",
            "W#W",
            '#',
            Items.STICK,
            'W',
            new ItemStack(Blocks.PLANKS, 1, 4 + PlanksBlock.Variant.DARK_OAK.getId() - 4)
        );
        this.registerShaped(new ItemStack(Blocks.COBBLESTONE_WALL, 6, WallBlock.Variant.NORMAL.getId()), "###", "###", '#', Blocks.COBBLESTONE);
        this.registerShaped(new ItemStack(Blocks.COBBLESTONE_WALL, 6, WallBlock.Variant.MOSSY.getId()), "###", "###", '#', Blocks.MOSSY_COBBLESTONE);
        this.registerShaped(new ItemStack(Blocks.NETHER_BRICK_FENCE, 6), "###", "###", '#', Blocks.NETHER_BRICKS);
        this.registerShaped(
            new ItemStack(Blocks.FENCE_GATE, 1), "#W#", "#W#", '#', Items.STICK, 'W', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.OAK.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.BIRCH_FENCE_GATE, 1), "#W#", "#W#", '#', Items.STICK, 'W', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.BIRCH.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.SPRUCE_FENCE_GATE, 1),
            "#W#",
            "#W#",
            '#',
            Items.STICK,
            'W',
            new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.SPRUCE.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.JUNGLE_FENCE_GATE, 1),
            "#W#",
            "#W#",
            '#',
            Items.STICK,
            'W',
            new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.JUNGLE.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.ACACIA_FENCE_GATE, 1),
            "#W#",
            "#W#",
            '#',
            Items.STICK,
            'W',
            new ItemStack(Blocks.PLANKS, 1, 4 + PlanksBlock.Variant.ACACIA.getId() - 4)
        );
        this.registerShaped(
            new ItemStack(Blocks.DARK_OAK_FENCE_GATE, 1),
            "#W#",
            "#W#",
            '#',
            Items.STICK,
            'W',
            new ItemStack(Blocks.PLANKS, 1, 4 + PlanksBlock.Variant.DARK_OAK.getId() - 4)
        );
        this.registerShaped(new ItemStack(Blocks.JUKEBOX, 1), "###", "#X#", "###", '#', Blocks.PLANKS, 'X', Items.DIAMOND);
        this.registerShaped(new ItemStack(Items.LEAD, 2), "~~ ", "~O ", "  ~", '~', Items.STRING, 'O', Items.SLIME_BALL);
        this.registerShaped(new ItemStack(Blocks.NOTEBLOCK, 1), "###", "#X#", "###", '#', Blocks.PLANKS, 'X', Items.REDSTONE);
        this.registerShaped(new ItemStack(Blocks.BOOKSHELF, 1), "###", "XXX", "###", '#', Blocks.PLANKS, 'X', Items.BOOK);
        this.registerShaped(new ItemStack(Blocks.SNOW, 1), "##", "##", '#', Items.SNOWBALL);
        this.registerShaped(new ItemStack(Blocks.SNOW_LAYER, 6), "###", '#', Blocks.SNOW);
        this.registerShaped(new ItemStack(Blocks.CLAY, 1), "##", "##", '#', Items.CLAY_BALL);
        this.registerShaped(new ItemStack(Blocks.BRICKS, 1), "##", "##", '#', Items.BRICK);
        this.registerShaped(new ItemStack(Blocks.GLOWSTONE, 1), "##", "##", '#', Items.GLOWSTONE_DUST);
        this.registerShaped(new ItemStack(Blocks.QUARTZ_BLOCK, 1), "##", "##", '#', Items.QUARTZ);
        this.registerShaped(new ItemStack(Blocks.WOOL, 1), "##", "##", '#', Items.STRING);
        this.registerShaped(new ItemStack(Blocks.TNT, 1), "X#X", "#X#", "X#X", 'X', Items.GUNPOWDER, '#', Blocks.SAND);
        this.registerShaped(new ItemStack(Blocks.STONE_SLAB, 6, StoneSlabBlock.Variant.COBBLESTONE.getId()), "###", '#', Blocks.COBBLESTONE);
        this.registerShaped(
            new ItemStack(Blocks.STONE_SLAB, 6, StoneSlabBlock.Variant.STONE.getId()),
            "###",
            '#',
            new ItemStack(Blocks.STONE, StoneBlock.Variant.STONE.getId())
        );
        this.registerShaped(new ItemStack(Blocks.STONE_SLAB, 6, StoneSlabBlock.Variant.SAND.getId()), "###", '#', Blocks.SANDSTONE);
        this.registerShaped(new ItemStack(Blocks.STONE_SLAB, 6, StoneSlabBlock.Variant.BRICK.getId()), "###", '#', Blocks.BRICKS);
        this.registerShaped(new ItemStack(Blocks.STONE_SLAB, 6, StoneSlabBlock.Variant.SMOOTHBRICK.getId()), "###", '#', Blocks.STONE_BRICKS);
        this.registerShaped(new ItemStack(Blocks.STONE_SLAB, 6, StoneSlabBlock.Variant.NETHERBRICK.getId()), "###", '#', Blocks.NETHER_BRICKS);
        this.registerShaped(new ItemStack(Blocks.STONE_SLAB, 6, StoneSlabBlock.Variant.QUARTZ.getId()), "###", '#', Blocks.QUARTZ_BLOCK);
        this.registerShaped(new ItemStack(Blocks.RED_SANDSTONE_SLAB, 6, RedSandstoneSlab.Variant.RED_SANDSTONE.getId()), "###", '#', Blocks.RED_SANDSTONE);
        this.registerShaped(new ItemStack(Blocks.WOODEN_SLAB, 6, 0), "###", '#', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.OAK.getId()));
        this.registerShaped(
            new ItemStack(Blocks.WOODEN_SLAB, 6, PlanksBlock.Variant.BIRCH.getId()),
            "###",
            '#',
            new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.BIRCH.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.WOODEN_SLAB, 6, PlanksBlock.Variant.SPRUCE.getId()),
            "###",
            '#',
            new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.SPRUCE.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.WOODEN_SLAB, 6, PlanksBlock.Variant.JUNGLE.getId()),
            "###",
            '#',
            new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.JUNGLE.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.WOODEN_SLAB, 6, 4 + PlanksBlock.Variant.ACACIA.getId() - 4),
            "###",
            '#',
            new ItemStack(Blocks.PLANKS, 1, 4 + PlanksBlock.Variant.ACACIA.getId() - 4)
        );
        this.registerShaped(
            new ItemStack(Blocks.WOODEN_SLAB, 6, 4 + PlanksBlock.Variant.DARK_OAK.getId() - 4),
            "###",
            '#',
            new ItemStack(Blocks.PLANKS, 1, 4 + PlanksBlock.Variant.DARK_OAK.getId() - 4)
        );
        this.registerShaped(new ItemStack(Blocks.LADDER, 3), "# #", "###", "# #", '#', Items.STICK);
        this.registerShaped(new ItemStack(Items.WOODEN_DOOR, 3), "##", "##", "##", '#', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.OAK.getId()));
        this.registerShaped(new ItemStack(Items.SPRUCE_DOOR, 3), "##", "##", "##", '#', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.SPRUCE.getId()));
        this.registerShaped(new ItemStack(Items.BIRCH_DOOR, 3), "##", "##", "##", '#', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.BIRCH.getId()));
        this.registerShaped(new ItemStack(Items.JUNGLE_DOOR, 3), "##", "##", "##", '#', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.JUNGLE.getId()));
        this.registerShaped(new ItemStack(Items.ACACIA_DOOR, 3), "##", "##", "##", '#', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.ACACIA.getId()));
        this.registerShaped(new ItemStack(Items.DARK_OAK_DOOR, 3), "##", "##", "##", '#', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.DARK_OAK.getId()));
        this.registerShaped(new ItemStack(Blocks.TRAPDOOR, 2), "###", "###", '#', Blocks.PLANKS);
        this.registerShaped(new ItemStack(Items.IRON_DOOR, 3), "##", "##", "##", '#', Items.IRON_INGOT);
        this.registerShaped(new ItemStack(Blocks.IRON_TRAPDOOR, 1), "##", "##", '#', Items.IRON_INGOT);
        this.registerShaped(new ItemStack(Items.SIGN, 3), "###", "###", " X ", '#', Blocks.PLANKS, 'X', Items.STICK);
        this.registerShaped(new ItemStack(Items.CAKE, 1), "AAA", "BEB", "CCC", 'A', Items.MILK_BUCKET, 'B', Items.SUGAR, 'C', Items.WHEAT, 'E', Items.EGG);
        this.registerShaped(new ItemStack(Items.SUGAR, 1), "#", '#', Items.REEDS);
        this.registerShaped(
            new ItemStack(Blocks.PLANKS, 4, PlanksBlock.Variant.OAK.getId()), "#", '#', new ItemStack(Blocks.LOG, 1, PlanksBlock.Variant.OAK.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.PLANKS, 4, PlanksBlock.Variant.SPRUCE.getId()), "#", '#', new ItemStack(Blocks.LOG, 1, PlanksBlock.Variant.SPRUCE.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.PLANKS, 4, PlanksBlock.Variant.BIRCH.getId()), "#", '#', new ItemStack(Blocks.LOG, 1, PlanksBlock.Variant.BIRCH.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.PLANKS, 4, PlanksBlock.Variant.JUNGLE.getId()), "#", '#', new ItemStack(Blocks.LOG, 1, PlanksBlock.Variant.JUNGLE.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.PLANKS, 4, 4 + PlanksBlock.Variant.ACACIA.getId() - 4),
            "#",
            '#',
            new ItemStack(Blocks.LOG2, 1, PlanksBlock.Variant.ACACIA.getId() - 4)
        );
        this.registerShaped(
            new ItemStack(Blocks.PLANKS, 4, 4 + PlanksBlock.Variant.DARK_OAK.getId() - 4),
            "#",
            '#',
            new ItemStack(Blocks.LOG2, 1, PlanksBlock.Variant.DARK_OAK.getId() - 4)
        );
        this.registerShaped(new ItemStack(Items.STICK, 4), "#", "#", '#', Blocks.PLANKS);
        this.registerShaped(new ItemStack(Blocks.TORCH, 4), "X", "#", 'X', Items.COAL, '#', Items.STICK);
        this.registerShaped(new ItemStack(Blocks.TORCH, 4), "X", "#", 'X', new ItemStack(Items.COAL, 1, 1), '#', Items.STICK);
        this.registerShaped(new ItemStack(Items.BOWL, 4), "# #", " # ", '#', Blocks.PLANKS);
        this.registerShaped(new ItemStack(Items.GLASS_BOTTLE, 3), "# #", " # ", '#', Blocks.GLASS);
        this.registerShaped(new ItemStack(Blocks.RAIL, 16), "X X", "X#X", "X X", 'X', Items.IRON_INGOT, '#', Items.STICK);
        this.registerShaped(new ItemStack(Blocks.POWERED_RAIL, 6), "X X", "X#X", "XRX", 'X', Items.GOLD_INGOT, 'R', Items.REDSTONE, '#', Items.STICK);
        this.registerShaped(new ItemStack(Blocks.ACTIVATOR_RAIL, 6), "XSX", "X#X", "XSX", 'X', Items.IRON_INGOT, '#', Blocks.REDSTONE_TORCH, 'S', Items.STICK);
        this.registerShaped(
            new ItemStack(Blocks.DETECTOR_RAIL, 6), "X X", "X#X", "XRX", 'X', Items.IRON_INGOT, 'R', Items.REDSTONE, '#', Blocks.STONE_PRESSURE_PLATE
        );
        this.registerShaped(new ItemStack(Items.MINECART, 1), "# #", "###", '#', Items.IRON_INGOT);
        this.registerShaped(new ItemStack(Items.CAULDRON, 1), "# #", "# #", "###", '#', Items.IRON_INGOT);
        this.registerShaped(new ItemStack(Items.BREWING_STAND, 1), " B ", "###", '#', Blocks.COBBLESTONE, 'B', Items.BLAZE_ROD);
        this.registerShaped(new ItemStack(Blocks.LIT_PUMPKIN, 1), "A", "B", 'A', Blocks.PUMPKIN, 'B', Blocks.TORCH);
        this.registerShaped(new ItemStack(Items.CHEST_MINECART, 1), "A", "B", 'A', Blocks.CHEST, 'B', Items.MINECART);
        this.registerShaped(new ItemStack(Items.FURNACE_MINECART, 1), "A", "B", 'A', Blocks.FURNACE, 'B', Items.MINECART);
        this.registerShaped(new ItemStack(Items.TNT_MINECART, 1), "A", "B", 'A', Blocks.TNT, 'B', Items.MINECART);
        this.registerShaped(new ItemStack(Items.HOPPER_MINECART, 1), "A", "B", 'A', Blocks.HOPPER, 'B', Items.MINECART);
        this.registerShaped(new ItemStack(Items.BOAT, 1), "# #", "###", '#', Blocks.PLANKS);
        this.registerShaped(new ItemStack(Items.BUCKET, 1), "# #", " # ", '#', Items.IRON_INGOT);
        this.registerShaped(new ItemStack(Items.FLOWER_POT, 1), "# #", " # ", '#', Items.BRICK);
        this.registerShapeless(new ItemStack(Items.FLINT_AND_STEEL, 1), new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.FLINT, 1));
        this.registerShaped(new ItemStack(Items.BREAD, 1), "###", '#', Items.WHEAT);
        this.registerShaped(new ItemStack(Blocks.OAK_STAIRS, 4), "#  ", "## ", "###", '#', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.OAK.getId()));
        this.registerShaped(new ItemStack(Blocks.BIRCH_STAIRS, 4), "#  ", "## ", "###", '#', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.BIRCH.getId()));
        this.registerShaped(
            new ItemStack(Blocks.SPRUCE_STAIRS, 4), "#  ", "## ", "###", '#', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.SPRUCE.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.JUNGLE_STAIRS, 4), "#  ", "## ", "###", '#', new ItemStack(Blocks.PLANKS, 1, PlanksBlock.Variant.JUNGLE.getId())
        );
        this.registerShaped(
            new ItemStack(Blocks.ACACIA_STAIRS, 4), "#  ", "## ", "###", '#', new ItemStack(Blocks.PLANKS, 1, 4 + PlanksBlock.Variant.ACACIA.getId() - 4)
        );
        this.registerShaped(
            new ItemStack(Blocks.DARK_OAK_STAIRS, 4), "#  ", "## ", "###", '#', new ItemStack(Blocks.PLANKS, 1, 4 + PlanksBlock.Variant.DARK_OAK.getId() - 4)
        );
        this.registerShaped(new ItemStack(Items.FISHING_ROD, 1), "  #", " #X", "# X", '#', Items.STICK, 'X', Items.STRING);
        this.registerShaped(new ItemStack(Items.CARROT_ON_A_STICK, 1), "# ", " X", '#', Items.FISHING_ROD, 'X', Items.CARROT);
        this.registerShaped(new ItemStack(Blocks.STONE_STAIRS, 4), "#  ", "## ", "###", '#', Blocks.COBBLESTONE);
        this.registerShaped(new ItemStack(Blocks.BRICK_STAIRS, 4), "#  ", "## ", "###", '#', Blocks.BRICKS);
        this.registerShaped(new ItemStack(Blocks.STONE_BRICK_STAIRS, 4), "#  ", "## ", "###", '#', Blocks.STONE_BRICKS);
        this.registerShaped(new ItemStack(Blocks.NETHER_BRICK_STAIRS, 4), "#  ", "## ", "###", '#', Blocks.NETHER_BRICKS);
        this.registerShaped(new ItemStack(Blocks.SANDSTONE_STAIRS, 4), "#  ", "## ", "###", '#', Blocks.SANDSTONE);
        this.registerShaped(new ItemStack(Blocks.RED_SANDSTONE_STAIRS, 4), "#  ", "## ", "###", '#', Blocks.RED_SANDSTONE);
        this.registerShaped(new ItemStack(Blocks.QUARTZ_STAIRS, 4), "#  ", "## ", "###", '#', Blocks.QUARTZ_BLOCK);
        this.registerShaped(new ItemStack(Items.PAINTING, 1), "###", "#X#", "###", '#', Items.STICK, 'X', Blocks.WOOL);
        this.registerShaped(new ItemStack(Items.ITEM_FRAME, 1), "###", "#X#", "###", '#', Items.STICK, 'X', Items.LEATHER);
        this.registerShaped(new ItemStack(Items.GOLDEN_APPLE, 1, 0), "###", "#X#", "###", '#', Items.GOLD_INGOT, 'X', Items.APPLE);
        this.registerShaped(new ItemStack(Items.GOLDEN_APPLE, 1, 1), "###", "#X#", "###", '#', Blocks.GOLD_BLOCK, 'X', Items.APPLE);
        this.registerShaped(new ItemStack(Items.GOLDEN_CARROT, 1, 0), "###", "#X#", "###", '#', Items.GOLD_NUGGET, 'X', Items.CARROT);
        this.registerShaped(new ItemStack(Items.SPECKLED_MELON, 1), "###", "#X#", "###", '#', Items.GOLD_NUGGET, 'X', Items.MELON);
        this.registerShaped(new ItemStack(Blocks.LEVER, 1), "X", "#", '#', Blocks.COBBLESTONE, 'X', Items.STICK);
        this.registerShaped(new ItemStack(Blocks.TRIPWIRE_HOOK, 2), "I", "S", "#", '#', Blocks.PLANKS, 'S', Items.STICK, 'I', Items.IRON_INGOT);
        this.registerShaped(new ItemStack(Blocks.REDSTONE_TORCH, 1), "X", "#", '#', Items.STICK, 'X', Items.REDSTONE);
        this.registerShaped(
            new ItemStack(Items.REPEATER, 1),
            "#X#",
            "III",
            '#',
            Blocks.REDSTONE_TORCH,
            'X',
            Items.REDSTONE,
            'I',
            new ItemStack(Blocks.STONE, 1, StoneBlock.Variant.STONE.getId())
        );
        this.registerShaped(
            new ItemStack(Items.COMPARATOR, 1),
            " # ",
            "#X#",
            "III",
            '#',
            Blocks.REDSTONE_TORCH,
            'X',
            Items.QUARTZ,
            'I',
            new ItemStack(Blocks.STONE, 1, StoneBlock.Variant.STONE.getId())
        );
        this.registerShaped(new ItemStack(Items.CLOCK, 1), " # ", "#X#", " # ", '#', Items.GOLD_INGOT, 'X', Items.REDSTONE);
        this.registerShaped(new ItemStack(Items.COMPASS, 1), " # ", "#X#", " # ", '#', Items.IRON_INGOT, 'X', Items.REDSTONE);
        this.registerShaped(new ItemStack(Items.EMPTY_MAP, 1), "###", "#X#", "###", '#', Items.PAPER, 'X', Items.COMPASS);
        this.registerShaped(new ItemStack(Blocks.STONE_BUTTON, 1), "#", '#', new ItemStack(Blocks.STONE, 1, StoneBlock.Variant.STONE.getId()));
        this.registerShaped(new ItemStack(Blocks.WOODEN_BUTTON, 1), "#", '#', Blocks.PLANKS);
        this.registerShaped(new ItemStack(Blocks.STONE_PRESSURE_PLATE, 1), "##", '#', new ItemStack(Blocks.STONE, 1, StoneBlock.Variant.STONE.getId()));
        this.registerShaped(new ItemStack(Blocks.WOODEN_PRESSURE_PLATE, 1), "##", '#', Blocks.PLANKS);
        this.registerShaped(new ItemStack(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, 1), "##", '#', Items.IRON_INGOT);
        this.registerShaped(new ItemStack(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, 1), "##", '#', Items.GOLD_INGOT);
        this.registerShaped(new ItemStack(Blocks.DISPENSER, 1), "###", "#X#", "#R#", '#', Blocks.COBBLESTONE, 'X', Items.BOW, 'R', Items.REDSTONE);
        this.registerShaped(new ItemStack(Blocks.DROPPER, 1), "###", "# #", "#R#", '#', Blocks.COBBLESTONE, 'R', Items.REDSTONE);
        this.registerShaped(
            new ItemStack(Blocks.PISTON, 1), "TTT", "#X#", "#R#", '#', Blocks.COBBLESTONE, 'X', Items.IRON_INGOT, 'R', Items.REDSTONE, 'T', Blocks.PLANKS
        );
        this.registerShaped(new ItemStack(Blocks.STICKY_PISTON, 1), "S", "P", 'S', Items.SLIME_BALL, 'P', Blocks.PISTON);
        this.registerShaped(new ItemStack(Items.BED, 1), "###", "XXX", '#', Blocks.WOOL, 'X', Blocks.PLANKS);
        this.registerShaped(new ItemStack(Blocks.ENCHANTING_TABLE, 1), " B ", "D#D", "###", '#', Blocks.OBSIDIAN, 'B', Items.BOOK, 'D', Items.DIAMOND);
        this.registerShaped(new ItemStack(Blocks.ANVIL, 1), "III", " i ", "iii", 'I', Blocks.IRON_BLOCK, 'i', Items.IRON_INGOT);
        this.registerShaped(new ItemStack(Items.LEATHER), "##", "##", '#', Items.RABBIT_HIDE);
        this.registerShapeless(new ItemStack(Items.ENDER_EYE, 1), Items.ENDER_PEARL, Items.BLAZE_POWDER);
        this.registerShapeless(new ItemStack(Items.FIRE_CHARGE, 3), Items.GUNPOWDER, Items.BLAZE_POWDER, Items.COAL);
        this.registerShapeless(new ItemStack(Items.FIRE_CHARGE, 3), Items.GUNPOWDER, Items.BLAZE_POWDER, new ItemStack(Items.COAL, 1, 1));
        this.registerShaped(new ItemStack(Blocks.DAYLIGHT_DETECTOR), "GGG", "QQQ", "WWW", 'G', Blocks.GLASS, 'Q', Items.QUARTZ, 'W', Blocks.WOODEN_SLAB);
        this.registerShaped(new ItemStack(Blocks.HOPPER), "I I", "ICI", " I ", 'I', Items.IRON_INGOT, 'C', Blocks.CHEST);
        this.registerShaped(
            new ItemStack(Items.ARMOR_STAND, 1),
            "///",
            " / ",
            "/_/",
            '/',
            Items.STICK,
            '_',
            new ItemStack(Blocks.STONE_SLAB, 1, StoneSlabBlock.Variant.STONE.getId())
        );
        Collections.sort(this.recipes, new Comparator<Recipe>() {
            public int compare(Recipe recipe, Recipe recipe2) {
                if (recipe instanceof ShapelessRecipe && recipe2 instanceof ShapedRecipe) {
                    return 1;
                } else if (recipe2 instanceof ShapelessRecipe && recipe instanceof ShapedRecipe) {
                    return -1;
                } else if (recipe2.size() < recipe.size()) {
                    return -1;
                } else {
                    return recipe2.size() > recipe.size() ? 1 : 0;
                }
            }
        });
    }

    public ShapedRecipe registerShaped(ItemStack result, Object... args) {
        String s = "";
        int i = 0;
        int j = 0;
        int k = 0;
        if (args[i] instanceof String[]) {
            String[] astring = (String[])args[i++];

            for (int l = 0; l < astring.length; l++) {
                String s2 = astring[l];
                k++;
                j = s2.length();
                s = s + s2;
            }
        } else {
            while (args[i] instanceof String) {
                String s1 = (String)args[i++];
                k++;
                j = s1.length();
                s = s + s1;
            }
        }

        Map<Character, ItemStack> map = Maps.newHashMap();

        while (i < args.length) {
            Character character = (Character)args[i];
            ItemStack itemstack = null;
            if (args[i + 1] instanceof Item) {
                itemstack = new ItemStack((Item)args[i + 1]);
            } else if (args[i + 1] instanceof Block) {
                itemstack = new ItemStack((Block)args[i + 1], 1, 32767);
            } else if (args[i + 1] instanceof ItemStack) {
                itemstack = (ItemStack)args[i + 1];
            }

            map.put(character, itemstack);
            i += 2;
        }

        ItemStack[] aitemstack = new ItemStack[j * k];

        for (int i1 = 0; i1 < j * k; i1++) {
            char c0 = s.charAt(i1);
            if (map.containsKey(c0)) {
                aitemstack[i1] = map.get(c0).copy();
            } else {
                aitemstack[i1] = null;
            }
        }

        ShapedRecipe shapedrecipe = new ShapedRecipe(j, k, aitemstack, result);
        this.recipes.add(shapedrecipe);
        return shapedrecipe;
    }

    public void registerShapeless(ItemStack result, Object... args) {
        List<ItemStack> list = Lists.newArrayList();

        for (Object object : args) {
            if (object instanceof ItemStack) {
                list.add(((ItemStack)object).copy());
            } else if (object instanceof Item) {
                list.add(new ItemStack((Item)object));
            } else {
                if (!(object instanceof Block)) {
                    throw new IllegalArgumentException("Invalid shapeless recipe: unknown type " + object.getClass().getName() + "!");
                }

                list.add(new ItemStack((Block)object));
            }
        }

        this.recipes.add(new ShapelessRecipe(result, list));
    }

    public void register(Recipe recipe) {
        this.recipes.add(recipe);
    }

    public ItemStack getResult(CraftingInventory inventory, World world) {
        for (Recipe recipe : this.recipes) {
            if (recipe.matches(inventory, world)) {
                return recipe.getResult(inventory);
            }
        }

        return null;
    }

    public ItemStack[] getRemainder(CraftingInventory inventory, World world) {
        for (Recipe recipe : this.recipes) {
            if (recipe.matches(inventory, world)) {
                return recipe.getRemainder(inventory);
            }
        }

        ItemStack[] aitemstack = new ItemStack[inventory.getSize()];

        for (int i = 0; i < aitemstack.length; i++) {
            aitemstack[i] = inventory.getItem(i);
        }

        return aitemstack;
    }

    public List<Recipe> getRecipes() {
        return this.recipes;
    }
}
