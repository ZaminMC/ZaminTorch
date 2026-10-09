package net.minecraft.item;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DirtBlock;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.PrismarineBlock;
import net.minecraft.block.RedSandstoneBlock;
import net.minecraft.block.SandBlock;
import net.minecraft.block.SandstoneBlock;
import net.minecraft.block.StoneBlock;
import net.minecraft.block.StonebrickBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.PaintingEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.effect.PotionHelper;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.IdRegistry;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class Item {
    public static final IdRegistry<Identifier, Item> REGISTRY = new IdRegistry<>();
    private static final Map<Block, Item> BLOCK_ITEMS = Maps.newHashMap();
    protected static final UUID ATTACK_DAMAGE_MODIFIER_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private CreativeModeTab creativeModeTab;
    protected static Random random = new Random();
    protected int maxStackSize = 64;
    private int maxDamage;
    protected boolean handheld;
    protected boolean hasCustomData;
    /**
     * The item that remains after this item is used up during crafting, smelting or brewing.
     */
    private Item recipeRemainder;
    private String potionIngredient;
    private String key;

    public static int getId(Item item) {
        return item == null ? 0 : REGISTRY.getId(item);
    }

    public static Item byId(int id) {
        return REGISTRY.get(id);
    }

    public static Item byBlock(Block block) {
        return BLOCK_ITEMS.get(block);
    }

    public static Item byKey(String key) {
        Item item = REGISTRY.get(new Identifier(key));
        if (item == null) {
            try {
                return byId(Integer.parseInt(key));
            } catch (NumberFormatException numberformatexception) {
            }
        }

        return item;
    }

    public boolean validateNbt(NbtCompound nbt) {
        return false;
    }

    public Item setMaxStackSize(int size) {
        this.maxStackSize = size;
        return this;
    }

    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        return false;
    }

    public float getMiningSpeed(ItemStack stack, Block block) {
        return 1.0F;
    }

    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        return stack;
    }

    public ItemStack finishUsing(ItemStack stack, World world, PlayerEntity player) {
        return stack;
    }

    public int getMaxStackSize() {
        return this.maxStackSize;
    }

    public int getBlockMetadata(int metadata) {
        return 0;
    }

    public boolean hasCustomData() {
        return this.hasCustomData;
    }

    protected Item setHasCustomData(boolean hasCustomData) {
        this.hasCustomData = hasCustomData;
        return this;
    }

    public int getMaxDamage() {
        return this.maxDamage;
    }

    protected Item setMaxDamage(int damage) {
        this.maxDamage = damage;
        return this;
    }

    public boolean isDamageable() {
        return this.maxDamage > 0 && !this.hasCustomData;
    }

    public boolean attack(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }

    public boolean mineBlock(ItemStack stack, World world, Block block, BlockPos pos, LivingEntity entity) {
        return false;
    }

    public boolean canMineBlock(Block block) {
        return false;
    }

    public boolean interact(ItemStack stack, PlayerEntity player, LivingEntity entity) {
        return false;
    }

    public Item setHandheld() {
        this.handheld = true;
        return this;
    }

    public boolean isHandheld() {
        return this.handheld;
    }

    public boolean shouldRotate() {
        return false;
    }

    public Item setKey(String key) {
        this.key = key;
        return this;
    }

    public String getDescription(ItemStack stack) {
        String s = this.getTranslationKey(stack);
        return s == null ? "" : I18n.translate(s);
    }

    public String getTranslationKey() {
        return "item." + this.key;
    }

    public String getTranslationKey(ItemStack stack) {
        return "item." + this.key;
    }

    public Item setRecipeRemainder(Item item) {
        this.recipeRemainder = item;
        return this;
    }

    public boolean shouldSyncNbt() {
        return true;
    }

    public Item getRecipeRemainder() {
        return this.recipeRemainder;
    }

    public boolean hasRecipeRemainder() {
        return this.recipeRemainder != null;
    }

    public int getDisplayColor(ItemStack stack, int stage) {
        return 16777215;
    }

    public void tick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
    }

    public void onResult(ItemStack stack, World world, PlayerEntity player) {
    }

    public boolean isNetworkSynced() {
        return false;
    }

    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    public int getUseDuration(ItemStack stack) {
        return 0;
    }

    public void stopUsing(ItemStack stack, World world, PlayerEntity player, int remainingUseTime) {
    }

    protected Item setPotionIngredient(String ingredient) {
        this.potionIngredient = ingredient;
        return this;
    }

    public String asPotionIngredient(ItemStack stack) {
        return this.potionIngredient;
    }

    public boolean isPotionIngredient(ItemStack stack) {
        return this.asPotionIngredient(stack) != null;
    }

    public void addHoverText(ItemStack stack, PlayerEntity player, List<String> tooltip, boolean advanced) {
    }

    public String getName(ItemStack stack) {
        return ("" + I18n.translate(this.getDescription(stack) + ".name")).trim();
    }

    public boolean hasEnchantmentGlint(ItemStack stack) {
        return stack.hasEnchantments();
    }

    public Rarity getRarity(ItemStack stack) {
        return stack.hasEnchantments() ? Rarity.RARE : Rarity.COMMON;
    }

    public boolean isEnchantable(ItemStack stack) {
        return this.getMaxStackSize() == 1 && this.isDamageable();
    }

    protected HitResult getUseTarget(World world, PlayerEntity player, boolean allowLiquids) {
        float f = player.pitch;
        float f1 = player.yaw;
        double d0 = player.x;
        double d1 = player.y + player.getEyeHeight();
        double d2 = player.z;
        Vec3d vec3d = new Vec3d(d0, d1, d2);
        float f2 = MathHelper.cos(-f1 * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f3 = MathHelper.sin(-f1 * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f4 = -MathHelper.cos(-f * (float) (Math.PI / 180.0));
        float f5 = MathHelper.sin(-f * (float) (Math.PI / 180.0));
        float f6 = f3 * f4;
        float f7 = f5;
        float f8 = f2 * f4;
        double d3 = 5.0;
        Vec3d vec3d1 = vec3d.add(f6 * d3, f7 * d3, f8 * d3);
        return world.rayTrace(vec3d, vec3d1, allowLiquids, !allowLiquids, false);
    }

    public int getEnchantability() {
        return 0;
    }

    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        inventory.add(new ItemStack(item, 1, 0));
    }

    public CreativeModeTab getCreativeModeTab() {
        return this.creativeModeTab;
    }

    public Item setCreativeModeTab(CreativeModeTab tab) {
        this.creativeModeTab = tab;
        return this;
    }

    public boolean canUseOnBlockInAdventureMode() {
        return false;
    }

    public boolean isRepairable(ItemStack stack, ItemStack ingredient) {
        return false;
    }

    public Multimap<String, AttributeModifier> getDefaultAttributeModifiers() {
        return HashMultimap.create();
    }

    public static void init() {
        register(Blocks.STONE, new VariantBlockItem(Blocks.STONE, Blocks.STONE, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return StoneBlock.Variant.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("stone"));
        register(Blocks.GRASS, new PlantBlockItem(Blocks.GRASS, false));
        register(Blocks.DIRT, new VariantBlockItem(Blocks.DIRT, Blocks.DIRT, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return DirtBlock.Variant.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("dirt"));
        register(Blocks.COBBLESTONE);
        register(Blocks.PLANKS, new VariantBlockItem(Blocks.PLANKS, Blocks.PLANKS, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return PlanksBlock.Variant.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("wood"));
        register(Blocks.SAPLING, new VariantBlockItem(Blocks.SAPLING, Blocks.SAPLING, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return PlanksBlock.Variant.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("sapling"));
        register(Blocks.BEDROCK);
        register(Blocks.SAND, new VariantBlockItem(Blocks.SAND, Blocks.SAND, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return SandBlock.Variant.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("sand"));
        register(Blocks.GRAVEL);
        register(Blocks.GOLD_ORE);
        register(Blocks.IRON_ORE);
        register(Blocks.COAL_ORE);
        register(Blocks.LOG, new VariantBlockItem(Blocks.LOG, Blocks.LOG, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return PlanksBlock.Variant.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("log"));
        register(Blocks.LOG2, new VariantBlockItem(Blocks.LOG2, Blocks.LOG2, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return PlanksBlock.Variant.byId(itemStack.getMetadata() + 4).getName();
            }
        }).setKey("log"));
        register(Blocks.LEAVES, new LeavesItem(Blocks.LEAVES).setKey("leaves"));
        register(Blocks.LEAVES2, new LeavesItem(Blocks.LEAVES2).setKey("leaves"));
        register(Blocks.SPONGE, new VariantBlockItem(Blocks.SPONGE, Blocks.SPONGE, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return (itemStack.getMetadata() & 1) == 1 ? "wet" : "dry";
            }
        }).setKey("sponge"));
        register(Blocks.GLASS);
        register(Blocks.LAPIS_ORE);
        register(Blocks.LAPIS_BLOCK);
        register(Blocks.DISPENSER);
        register(Blocks.SANDSTONE, new VariantBlockItem(Blocks.SANDSTONE, Blocks.SANDSTONE, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return SandstoneBlock.Type.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("sandStone"));
        register(Blocks.NOTEBLOCK);
        register(Blocks.POWERED_RAIL);
        register(Blocks.DETECTOR_RAIL);
        register(Blocks.STICKY_PISTON, new PistonBlockItem(Blocks.STICKY_PISTON));
        register(Blocks.WEB);
        register(Blocks.TALLGRASS, new PlantBlockItem(Blocks.TALLGRASS, true).setNames(new String[]{"shrub", "grass", "fern"}));
        register(Blocks.DEADBUSH);
        register(Blocks.PISTON, new PistonBlockItem(Blocks.PISTON));
        register(Blocks.WOOL, new ColoredBlockItem(Blocks.WOOL).setKey("cloth"));
        register(Blocks.YELLOW_FLOWER, new VariantBlockItem(Blocks.YELLOW_FLOWER, Blocks.YELLOW_FLOWER, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return FlowerBlock.Type.byId(FlowerBlock.Group.YELLOW, itemStack.getMetadata()).getName();
            }
        }).setKey("flower"));
        register(Blocks.RED_FLOWER, new VariantBlockItem(Blocks.RED_FLOWER, Blocks.RED_FLOWER, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return FlowerBlock.Type.byId(FlowerBlock.Group.RED, itemStack.getMetadata()).getName();
            }
        }).setKey("rose"));
        register(Blocks.BROWN_MUSHROOM);
        register(Blocks.RED_MUSHROOM);
        register(Blocks.GOLD_BLOCK);
        register(Blocks.IRON_BLOCK);
        register(Blocks.STONE_SLAB, new SlabItem(Blocks.STONE_SLAB, Blocks.STONE_SLAB, Blocks.DOUBLE_STONE_SLAB).setKey("stoneSlab"));
        register(Blocks.BRICKS);
        register(Blocks.TNT);
        register(Blocks.BOOKSHELF);
        register(Blocks.MOSSY_COBBLESTONE);
        register(Blocks.OBSIDIAN);
        register(Blocks.TORCH);
        register(Blocks.MOB_SPAWNER);
        register(Blocks.OAK_STAIRS);
        register(Blocks.CHEST);
        register(Blocks.DIAMOND_ORE);
        register(Blocks.DIAMOND_BLOCK);
        register(Blocks.CRAFTING_TABLE);
        register(Blocks.FARMLAND);
        register(Blocks.FURNACE);
        register(Blocks.LIT_FURNACE);
        register(Blocks.LADDER);
        register(Blocks.RAIL);
        register(Blocks.STONE_STAIRS);
        register(Blocks.LEVER);
        register(Blocks.STONE_PRESSURE_PLATE);
        register(Blocks.WOODEN_PRESSURE_PLATE);
        register(Blocks.REDSTONE_ORE);
        register(Blocks.REDSTONE_TORCH);
        register(Blocks.STONE_BUTTON);
        register(Blocks.SNOW_LAYER, new SnowLayerItem(Blocks.SNOW_LAYER));
        register(Blocks.ICE);
        register(Blocks.SNOW);
        register(Blocks.CACTUS);
        register(Blocks.CLAY);
        register(Blocks.JUKEBOX);
        register(Blocks.FENCE);
        register(Blocks.SPRUCE_FENCE);
        register(Blocks.BIRCH_FENCE);
        register(Blocks.JUNGLE_FENCE);
        register(Blocks.DARK_OAK_FENCE);
        register(Blocks.ACACIA_FENCE);
        register(Blocks.PUMPKIN);
        register(Blocks.NETHERRACK);
        register(Blocks.SOUL_SAND);
        register(Blocks.GLOWSTONE);
        register(Blocks.LIT_PUMPKIN);
        register(Blocks.TRAPDOOR);
        register(Blocks.MONSTER_EGG, new VariantBlockItem(Blocks.MONSTER_EGG, Blocks.MONSTER_EGG, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return InfestedBlock.Variant.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("monsterStoneEgg"));
        register(Blocks.STONE_BRICKS, new VariantBlockItem(Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return StonebrickBlock.Variant.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("stonebricksmooth"));
        register(Blocks.BROWN_MUSHROOM_BLOCK);
        register(Blocks.RED_MUSHROOM_BLOCK);
        register(Blocks.IRON_BARS);
        register(Blocks.GLASS_PANE);
        register(Blocks.MELON_BLOCK);
        register(Blocks.VINE, new PlantBlockItem(Blocks.VINE, false));
        register(Blocks.FENCE_GATE);
        register(Blocks.SPRUCE_FENCE_GATE);
        register(Blocks.BIRCH_FENCE_GATE);
        register(Blocks.JUNGLE_FENCE_GATE);
        register(Blocks.DARK_OAK_FENCE_GATE);
        register(Blocks.ACACIA_FENCE_GATE);
        register(Blocks.BRICK_STAIRS);
        register(Blocks.STONE_BRICK_STAIRS);
        register(Blocks.MYCELIUM);
        register(Blocks.LILY_PAD, new LilyPadItem(Blocks.LILY_PAD));
        register(Blocks.NETHER_BRICKS);
        register(Blocks.NETHER_BRICK_FENCE);
        register(Blocks.NETHER_BRICK_STAIRS);
        register(Blocks.ENCHANTING_TABLE);
        register(Blocks.END_PORTAL_FRAME);
        register(Blocks.END_STONE);
        register(Blocks.DRAGON_EGG);
        register(Blocks.REDSTONE_LAMP);
        register(Blocks.WOODEN_SLAB, new SlabItem(Blocks.WOODEN_SLAB, Blocks.WOODEN_SLAB, Blocks.DOUBLE_WOODEN_SLAB).setKey("woodSlab"));
        register(Blocks.SANDSTONE_STAIRS);
        register(Blocks.EMERALD_ORE);
        register(Blocks.ENDER_CHEST);
        register(Blocks.TRIPWIRE_HOOK);
        register(Blocks.EMERALD_BLOCK);
        register(Blocks.SPRUCE_STAIRS);
        register(Blocks.BIRCH_STAIRS);
        register(Blocks.JUNGLE_STAIRS);
        register(Blocks.COMMAND_BLOCK);
        register(Blocks.BEACON);
        register(Blocks.COBBLESTONE_WALL, new VariantBlockItem(Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return WallBlock.Variant.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("cobbleWall"));
        register(Blocks.WOODEN_BUTTON);
        register(Blocks.ANVIL, new AnvilItem(Blocks.ANVIL).setKey("anvil"));
        register(Blocks.TRAPPED_CHEST);
        register(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE);
        register(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE);
        register(Blocks.DAYLIGHT_DETECTOR);
        register(Blocks.REDSTONE_BLOCK);
        register(Blocks.QUARTZ_ORE);
        register(Blocks.HOPPER);
        register(
            Blocks.QUARTZ_BLOCK,
            new VariantBlockItem(Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BLOCK, new String[]{"default", "chiseled", "lines"}).setKey("quartzBlock")
        );
        register(Blocks.QUARTZ_STAIRS);
        register(Blocks.ACTIVATOR_RAIL);
        register(Blocks.DROPPER);
        register(Blocks.STAINED_HARDENED_CLAY, new ColoredBlockItem(Blocks.STAINED_HARDENED_CLAY).setKey("clayHardenedStained"));
        register(Blocks.BARRIER);
        register(Blocks.IRON_TRAPDOOR);
        register(Blocks.HAY);
        register(Blocks.CARPET, new ColoredBlockItem(Blocks.CARPET).setKey("woolCarpet"));
        register(Blocks.HARDENED_CLAY);
        register(Blocks.COAL_BLOCK);
        register(Blocks.PACKED_ICE);
        register(Blocks.ACACIA_STAIRS);
        register(Blocks.DARK_OAK_STAIRS);
        register(Blocks.SLIME);
        register(Blocks.DOUBLE_PLANT, new BushItem(Blocks.DOUBLE_PLANT, Blocks.DOUBLE_PLANT, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return DoublePlantBlock.Variant.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("doublePlant"));
        register(Blocks.STAINED_GLASS, new ColoredBlockItem(Blocks.STAINED_GLASS).setKey("stainedGlass"));
        register(Blocks.STAINED_GLASS_PANE, new ColoredBlockItem(Blocks.STAINED_GLASS_PANE).setKey("stainedGlassPane"));
        register(Blocks.PRISMARINE, new VariantBlockItem(Blocks.PRISMARINE, Blocks.PRISMARINE, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return PrismarineBlock.Variant.byId(itemStack.getMetadata()).getName();
            }
        }).setKey("prismarine"));
        register(Blocks.SEA_LANTERN);
        register(Blocks.RED_SANDSTONE, new VariantBlockItem(Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                return RedSandstoneBlock.Type.byId(itemStack.getMetadata()).getKey();
            }
        }).setKey("redSandStone"));
        register(Blocks.RED_SANDSTONE_STAIRS);
        register(
            Blocks.RED_SANDSTONE_SLAB,
            new SlabItem(Blocks.RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB, Blocks.DOUBLE_RED_SANDSTONE_SLAB).setKey("stoneSlab2")
        );
        register(256, "iron_shovel", new ShovelItem(Item.Tier.IRON).setKey("shovelIron"));
        register(257, "iron_pickaxe", new PickaxeItem(Item.Tier.IRON).setKey("pickaxeIron"));
        register(258, "iron_axe", new AxeItem(Item.Tier.IRON).setKey("hatchetIron"));
        register(259, "flint_and_steel", new FlintAndSteelItem().setKey("flintAndSteel"));
        register(260, "apple", new FoodItem(4, 0.3F, false).setKey("apple"));
        register(261, "bow", new BowItem().setKey("bow"));
        register(262, "arrow", new Item().setKey("arrow").setCreativeModeTab(CreativeModeTab.COMBAT));
        register(263, "coal", new CoalItem().setKey("coal"));
        register(264, "diamond", new Item().setKey("diamond").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(265, "iron_ingot", new Item().setKey("ingotIron").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(266, "gold_ingot", new Item().setKey("ingotGold").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(267, "iron_sword", new SwordItem(Item.Tier.IRON).setKey("swordIron"));
        register(268, "wooden_sword", new SwordItem(Item.Tier.WOOD).setKey("swordWood"));
        register(269, "wooden_shovel", new ShovelItem(Item.Tier.WOOD).setKey("shovelWood"));
        register(270, "wooden_pickaxe", new PickaxeItem(Item.Tier.WOOD).setKey("pickaxeWood"));
        register(271, "wooden_axe", new AxeItem(Item.Tier.WOOD).setKey("hatchetWood"));
        register(272, "stone_sword", new SwordItem(Item.Tier.STONE).setKey("swordStone"));
        register(273, "stone_shovel", new ShovelItem(Item.Tier.STONE).setKey("shovelStone"));
        register(274, "stone_pickaxe", new PickaxeItem(Item.Tier.STONE).setKey("pickaxeStone"));
        register(275, "stone_axe", new AxeItem(Item.Tier.STONE).setKey("hatchetStone"));
        register(276, "diamond_sword", new SwordItem(Item.Tier.DIAMOND).setKey("swordDiamond"));
        register(277, "diamond_shovel", new ShovelItem(Item.Tier.DIAMOND).setKey("shovelDiamond"));
        register(278, "diamond_pickaxe", new PickaxeItem(Item.Tier.DIAMOND).setKey("pickaxeDiamond"));
        register(279, "diamond_axe", new AxeItem(Item.Tier.DIAMOND).setKey("hatchetDiamond"));
        register(280, "stick", new Item().setHandheld().setKey("stick").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(281, "bowl", new Item().setKey("bowl").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(282, "mushroom_stew", new StewItem(6).setKey("mushroomStew"));
        register(283, "golden_sword", new SwordItem(Item.Tier.GOLD).setKey("swordGold"));
        register(284, "golden_shovel", new ShovelItem(Item.Tier.GOLD).setKey("shovelGold"));
        register(285, "golden_pickaxe", new PickaxeItem(Item.Tier.GOLD).setKey("pickaxeGold"));
        register(286, "golden_axe", new AxeItem(Item.Tier.GOLD).setKey("hatchetGold"));
        register(287, "string", new PlaceableItem(Blocks.TRIPWIRE).setKey("string").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(288, "feather", new Item().setKey("feather").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(289, "gunpowder", new Item().setKey("sulphur").setPotionIngredient(PotionHelper.GUNPOWDER).setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(290, "wooden_hoe", new HoeItem(Item.Tier.WOOD).setKey("hoeWood"));
        register(291, "stone_hoe", new HoeItem(Item.Tier.STONE).setKey("hoeStone"));
        register(292, "iron_hoe", new HoeItem(Item.Tier.IRON).setKey("hoeIron"));
        register(293, "diamond_hoe", new HoeItem(Item.Tier.DIAMOND).setKey("hoeDiamond"));
        register(294, "golden_hoe", new HoeItem(Item.Tier.GOLD).setKey("hoeGold"));
        register(295, "wheat_seeds", new WheatSeedsItem(Blocks.WHEAT, Blocks.FARMLAND).setKey("seeds"));
        register(296, "wheat", new Item().setKey("wheat").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(297, "bread", new FoodItem(5, 0.6F, false).setKey("bread"));
        register(298, "leather_helmet", new ArmorItem(ArmorItem.Tier.CLOTH, 0, 0).setKey("helmetCloth"));
        register(299, "leather_chestplate", new ArmorItem(ArmorItem.Tier.CLOTH, 0, 1).setKey("chestplateCloth"));
        register(300, "leather_leggings", new ArmorItem(ArmorItem.Tier.CLOTH, 0, 2).setKey("leggingsCloth"));
        register(301, "leather_boots", new ArmorItem(ArmorItem.Tier.CLOTH, 0, 3).setKey("bootsCloth"));
        register(302, "chainmail_helmet", new ArmorItem(ArmorItem.Tier.CHAIN, 1, 0).setKey("helmetChain"));
        register(303, "chainmail_chestplate", new ArmorItem(ArmorItem.Tier.CHAIN, 1, 1).setKey("chestplateChain"));
        register(304, "chainmail_leggings", new ArmorItem(ArmorItem.Tier.CHAIN, 1, 2).setKey("leggingsChain"));
        register(305, "chainmail_boots", new ArmorItem(ArmorItem.Tier.CHAIN, 1, 3).setKey("bootsChain"));
        register(306, "iron_helmet", new ArmorItem(ArmorItem.Tier.IRON, 2, 0).setKey("helmetIron"));
        register(307, "iron_chestplate", new ArmorItem(ArmorItem.Tier.IRON, 2, 1).setKey("chestplateIron"));
        register(308, "iron_leggings", new ArmorItem(ArmorItem.Tier.IRON, 2, 2).setKey("leggingsIron"));
        register(309, "iron_boots", new ArmorItem(ArmorItem.Tier.IRON, 2, 3).setKey("bootsIron"));
        register(310, "diamond_helmet", new ArmorItem(ArmorItem.Tier.DIAMOND, 3, 0).setKey("helmetDiamond"));
        register(311, "diamond_chestplate", new ArmorItem(ArmorItem.Tier.DIAMOND, 3, 1).setKey("chestplateDiamond"));
        register(312, "diamond_leggings", new ArmorItem(ArmorItem.Tier.DIAMOND, 3, 2).setKey("leggingsDiamond"));
        register(313, "diamond_boots", new ArmorItem(ArmorItem.Tier.DIAMOND, 3, 3).setKey("bootsDiamond"));
        register(314, "golden_helmet", new ArmorItem(ArmorItem.Tier.GOLD, 4, 0).setKey("helmetGold"));
        register(315, "golden_chestplate", new ArmorItem(ArmorItem.Tier.GOLD, 4, 1).setKey("chestplateGold"));
        register(316, "golden_leggings", new ArmorItem(ArmorItem.Tier.GOLD, 4, 2).setKey("leggingsGold"));
        register(317, "golden_boots", new ArmorItem(ArmorItem.Tier.GOLD, 4, 3).setKey("bootsGold"));
        register(318, "flint", new Item().setKey("flint").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(319, "porkchop", new FoodItem(3, 0.3F, true).setKey("porkchopRaw"));
        register(320, "cooked_porkchop", new FoodItem(8, 0.8F, true).setKey("porkchopCooked"));
        register(321, "painting", new DecorationItem(PaintingEntity.class).setKey("painting"));
        register(
            322,
            "golden_apple",
            new GoldenAppleItem(4, 1.2F, false).setAlwaysEdible().setStatusEffect(StatusEffect.REGENERATION.id, 5, 1, 1.0F).setKey("appleGold")
        );
        register(323, "sign", new SignItem().setKey("sign"));
        register(324, "wooden_door", new DoorItem(Blocks.WOODEN_DOOR).setKey("doorOak"));
        Item item = new BucketItem(Blocks.AIR).setKey("bucket").setMaxStackSize(16);
        register(325, "bucket", item);
        register(326, "water_bucket", new BucketItem(Blocks.FLOWING_WATER).setKey("bucketWater").setRecipeRemainder(item));
        register(327, "lava_bucket", new BucketItem(Blocks.FLOWING_LAVA).setKey("bucketLava").setRecipeRemainder(item));
        register(328, "minecart", new MinecartItem(MinecartEntity.Type.RIDEABLE).setKey("minecart"));
        register(329, "saddle", new SaddleItem().setKey("saddle"));
        register(330, "iron_door", new DoorItem(Blocks.IRON_DOOR).setKey("doorIron"));
        register(331, "redstone", new RedstoneItem().setKey("redstone").setPotionIngredient(PotionHelper.REDSTONE));
        register(332, "snowball", new SnowballItem().setKey("snowball"));
        register(333, "boat", new BoatItem().setKey("boat"));
        register(334, "leather", new Item().setKey("leather").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(335, "milk_bucket", new MilkBucketItem().setKey("milk").setRecipeRemainder(item));
        register(336, "brick", new Item().setKey("brick").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(337, "clay_ball", new Item().setKey("clay").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(338, "reeds", new PlaceableItem(Blocks.REEDS).setKey("reeds").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(339, "paper", new Item().setKey("paper").setCreativeModeTab(CreativeModeTab.MISC));
        register(340, "book", new BookItem().setKey("book").setCreativeModeTab(CreativeModeTab.MISC));
        register(341, "slime_ball", new Item().setKey("slimeball").setCreativeModeTab(CreativeModeTab.MISC));
        register(342, "chest_minecart", new MinecartItem(MinecartEntity.Type.CHEST).setKey("minecartChest"));
        register(343, "furnace_minecart", new MinecartItem(MinecartEntity.Type.FURNACE).setKey("minecartFurnace"));
        register(344, "egg", new EggItem().setKey("egg"));
        register(345, "compass", new Item().setKey("compass").setCreativeModeTab(CreativeModeTab.TOOLS));
        register(346, "fishing_rod", new FishingRodItem().setKey("fishingRod"));
        register(347, "clock", new Item().setKey("clock").setCreativeModeTab(CreativeModeTab.TOOLS));
        register(
            348, "glowstone_dust", new Item().setKey("yellowDust").setPotionIngredient(PotionHelper.GLOWSTONE).setCreativeModeTab(CreativeModeTab.MATERIALS)
        );
        register(349, "fish", new FishItem(false).setKey("fish").setHasCustomData(true));
        register(350, "cooked_fish", new FishItem(true).setKey("fish").setHasCustomData(true));
        register(351, "dye", new DyeItem().setKey("dyePowder"));
        register(352, "bone", new Item().setKey("bone").setHandheld().setCreativeModeTab(CreativeModeTab.MISC));
        register(353, "sugar", new Item().setKey("sugar").setPotionIngredient(PotionHelper.SUGAR).setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(354, "cake", new PlaceableItem(Blocks.CAKE).setMaxStackSize(1).setKey("cake").setCreativeModeTab(CreativeModeTab.FOOD));
        register(355, "bed", new BedItem().setMaxStackSize(1).setKey("bed"));
        register(356, "repeater", new PlaceableItem(Blocks.REPEATER).setKey("diode").setCreativeModeTab(CreativeModeTab.REDSTONE));
        register(357, "cookie", new FoodItem(2, 0.1F, false).setKey("cookie"));
        register(358, "filled_map", new FilledMapItem().setKey("map"));
        register(359, "shears", new ShearsItem().setKey("shears"));
        register(360, "melon", new FoodItem(2, 0.3F, false).setKey("melon"));
        register(361, "pumpkin_seeds", new WheatSeedsItem(Blocks.PUMPKIN_STEM, Blocks.FARMLAND).setKey("seeds_pumpkin"));
        register(362, "melon_seeds", new WheatSeedsItem(Blocks.MELON_STEM, Blocks.FARMLAND).setKey("seeds_melon"));
        register(363, "beef", new FoodItem(3, 0.3F, true).setKey("beefRaw"));
        register(364, "cooked_beef", new FoodItem(8, 0.8F, true).setKey("beefCooked"));
        register(365, "chicken", new FoodItem(2, 0.3F, true).setStatusEffect(StatusEffect.HUNGER.id, 30, 0, 0.3F).setKey("chickenRaw"));
        register(366, "cooked_chicken", new FoodItem(6, 0.6F, true).setKey("chickenCooked"));
        register(367, "rotten_flesh", new FoodItem(4, 0.1F, true).setStatusEffect(StatusEffect.HUNGER.id, 30, 0, 0.8F).setKey("rottenFlesh"));
        register(368, "ender_pearl", new EnderPearlItem().setKey("enderPearl"));
        register(369, "blaze_rod", new Item().setKey("blazeRod").setCreativeModeTab(CreativeModeTab.MATERIALS).setHandheld());
        register(370, "ghast_tear", new Item().setKey("ghastTear").setPotionIngredient(PotionHelper.GHAST_TEAR).setCreativeModeTab(CreativeModeTab.BREWING));
        register(371, "gold_nugget", new Item().setKey("goldNugget").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(372, "nether_wart", new WheatSeedsItem(Blocks.NETHER_WART, Blocks.SOUL_SAND).setKey("netherStalkSeeds").setPotionIngredient("+4"));
        register(373, "potion", new PotionItem().setKey("potion"));
        register(374, "glass_bottle", new GlassBottleItem().setKey("glassBottle"));
        register(
            375,
            "spider_eye",
            new FoodItem(2, 0.8F, false).setStatusEffect(StatusEffect.POISON.id, 5, 0, 1.0F).setKey("spiderEye").setPotionIngredient(PotionHelper.SPIDER_EYE)
        );
        register(
            376,
            "fermented_spider_eye",
            new Item().setKey("fermentedSpiderEye").setPotionIngredient(PotionHelper.FERMENTED_SPIDER_EYE).setCreativeModeTab(CreativeModeTab.BREWING)
        );
        register(
            377, "blaze_powder", new Item().setKey("blazePowder").setPotionIngredient(PotionHelper.BLAZE_POWDER).setCreativeModeTab(CreativeModeTab.BREWING)
        );
        register(378, "magma_cream", new Item().setKey("magmaCream").setPotionIngredient(PotionHelper.MAGMA_CREAM).setCreativeModeTab(CreativeModeTab.BREWING));
        register(379, "brewing_stand", new PlaceableItem(Blocks.BREWING_STAND).setKey("brewingStand").setCreativeModeTab(CreativeModeTab.BREWING));
        register(380, "cauldron", new PlaceableItem(Blocks.CAULDRON).setKey("cauldron").setCreativeModeTab(CreativeModeTab.BREWING));
        register(381, "ender_eye", new EnderEyeItem().setKey("eyeOfEnder"));
        register(
            382,
            "speckled_melon",
            new Item().setKey("speckledMelon").setPotionIngredient(PotionHelper.GLISTERING_MELON).setCreativeModeTab(CreativeModeTab.BREWING)
        );
        register(383, "spawn_egg", new SpawnEggItem().setKey("monsterPlacer"));
        register(384, "experience_bottle", new ExperienceBottleItem().setKey("expBottle"));
        register(385, "fire_charge", new FireChargeItem().setKey("fireball"));
        register(386, "writable_book", new BookAndQuillItem().setKey("writingBook").setCreativeModeTab(CreativeModeTab.MISC));
        register(387, "written_book", new WrittenBookItem().setKey("writtenBook").setMaxStackSize(16));
        register(388, "emerald", new Item().setKey("emerald").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(389, "item_frame", new DecorationItem(ItemFrameEntity.class).setKey("frame"));
        register(390, "flower_pot", new PlaceableItem(Blocks.FLOWER_POT).setKey("flowerPot").setCreativeModeTab(CreativeModeTab.DECORATIONS));
        register(391, "carrot", new CropItem(3, 0.6F, Blocks.CARROTS, Blocks.FARMLAND).setKey("carrots"));
        register(392, "potato", new CropItem(1, 0.3F, Blocks.POTATOES, Blocks.FARMLAND).setKey("potato"));
        register(393, "baked_potato", new FoodItem(5, 0.6F, false).setKey("potatoBaked"));
        register(394, "poisonous_potato", new FoodItem(2, 0.3F, false).setStatusEffect(StatusEffect.POISON.id, 5, 0, 0.6F).setKey("potatoPoisonous"));
        register(395, "map", new EmptyMapItem().setKey("emptyMap"));
        register(
            396,
            "golden_carrot",
            new FoodItem(6, 1.2F, false).setKey("carrotGolden").setPotionIngredient(PotionHelper.GOLDEN_CARROT).setCreativeModeTab(CreativeModeTab.BREWING)
        );
        register(397, "skull", new SkullItem().setKey("skull"));
        register(398, "carrot_on_a_stick", new CarrotOnAStickItem().setKey("carrotOnAStick"));
        register(399, "nether_star", new NetherStarItem().setKey("netherStar").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(400, "pumpkin_pie", new FoodItem(8, 0.3F, false).setKey("pumpkinPie").setCreativeModeTab(CreativeModeTab.FOOD));
        register(401, "fireworks", new FireworksItem().setKey("fireworks"));
        register(402, "firework_charge", new FireworksChargeItem().setKey("fireworksCharge").setCreativeModeTab(CreativeModeTab.MISC));
        register(403, "enchanted_book", new EnchantedBookItem().setMaxStackSize(1).setKey("enchantedBook"));
        register(404, "comparator", new PlaceableItem(Blocks.COMPARATOR).setKey("comparator").setCreativeModeTab(CreativeModeTab.REDSTONE));
        register(405, "netherbrick", new Item().setKey("netherbrick").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(406, "quartz", new Item().setKey("netherquartz").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(407, "tnt_minecart", new MinecartItem(MinecartEntity.Type.TNT).setKey("minecartTnt"));
        register(408, "hopper_minecart", new MinecartItem(MinecartEntity.Type.HOPPER).setKey("minecartHopper"));
        register(409, "prismarine_shard", new Item().setKey("prismarineShard").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(410, "prismarine_crystals", new Item().setKey("prismarineCrystals").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(411, "rabbit", new FoodItem(3, 0.3F, true).setKey("rabbitRaw"));
        register(412, "cooked_rabbit", new FoodItem(5, 0.6F, true).setKey("rabbitCooked"));
        register(413, "rabbit_stew", new StewItem(10).setKey("rabbitStew"));
        register(414, "rabbit_foot", new Item().setKey("rabbitFoot").setPotionIngredient(PotionHelper.JUMP_BOOST).setCreativeModeTab(CreativeModeTab.BREWING));
        register(415, "rabbit_hide", new Item().setKey("rabbitHide").setCreativeModeTab(CreativeModeTab.MATERIALS));
        register(416, "armor_stand", new ArmorStandItem().setKey("armorStand").setMaxStackSize(16));
        register(417, "iron_horse_armor", new Item().setKey("horsearmormetal").setMaxStackSize(1).setCreativeModeTab(CreativeModeTab.MISC));
        register(418, "golden_horse_armor", new Item().setKey("horsearmorgold").setMaxStackSize(1).setCreativeModeTab(CreativeModeTab.MISC));
        register(419, "diamond_horse_armor", new Item().setKey("horsearmordiamond").setMaxStackSize(1).setCreativeModeTab(CreativeModeTab.MISC));
        register(420, "lead", new LeadItem().setKey("leash"));
        register(421, "name_tag", new NameTagItem().setKey("nameTag"));
        register(422, "command_block_minecart", new MinecartItem(MinecartEntity.Type.COMMAND_BLOCK).setKey("minecartCommandBlock").setCreativeModeTab(null));
        register(423, "mutton", new FoodItem(2, 0.3F, true).setKey("muttonRaw"));
        register(424, "cooked_mutton", new FoodItem(6, 0.8F, true).setKey("muttonCooked"));
        register(425, "banner", new BannerItem().setKey("banner"));
        register(427, "spruce_door", new DoorItem(Blocks.SPRUCE_DOOR).setKey("doorSpruce"));
        register(428, "birch_door", new DoorItem(Blocks.BIRCH_DOOR).setKey("doorBirch"));
        register(429, "jungle_door", new DoorItem(Blocks.JUNGLE_DOOR).setKey("doorJungle"));
        register(430, "acacia_door", new DoorItem(Blocks.ACACIA_DOOR).setKey("doorAcacia"));
        register(431, "dark_oak_door", new DoorItem(Blocks.DARK_OAK_DOOR).setKey("doorDarkOak"));
        register(2256, "record_13", new MusicDiscItem("13").setKey("record"));
        register(2257, "record_cat", new MusicDiscItem("cat").setKey("record"));
        register(2258, "record_blocks", new MusicDiscItem("blocks").setKey("record"));
        register(2259, "record_chirp", new MusicDiscItem("chirp").setKey("record"));
        register(2260, "record_far", new MusicDiscItem("far").setKey("record"));
        register(2261, "record_mall", new MusicDiscItem("mall").setKey("record"));
        register(2262, "record_mellohi", new MusicDiscItem("mellohi").setKey("record"));
        register(2263, "record_stal", new MusicDiscItem("stal").setKey("record"));
        register(2264, "record_strad", new MusicDiscItem("strad").setKey("record"));
        register(2265, "record_ward", new MusicDiscItem("ward").setKey("record"));
        register(2266, "record_11", new MusicDiscItem("11").setKey("record"));
        register(2267, "record_wait", new MusicDiscItem("wait").setKey("record"));
    }

    private static void register(Block block) {
        register(block, new BlockItem(block));
    }

    protected static void register(Block block, Item item) {
        register(Block.getId(block), Block.REGISTRY.getKey(block), item);
        BLOCK_ITEMS.put(block, item);
    }

    private static void register(int id, String key, Item item) {
        register(id, new Identifier(key), item);
    }

    private static void register(int id, Identifier key, Item item) {
        REGISTRY.register(id, key, item);
    }

    public enum Tier {
        WOOD(0, 59, 2.0F, 0.0F, 15),
        STONE(1, 131, 4.0F, 1.0F, 5),
        IRON(2, 250, 6.0F, 2.0F, 14),
        DIAMOND(3, 1561, 8.0F, 3.0F, 10),
        GOLD(0, 32, 12.0F, 0.0F, 22);

        private final int level;
        private final int durability;
        private final float miningSpeed;
        private final float attackDamage;
        private final int enchantability;

        Tier(int level, int durability, float miningSpeed, float attackDamage, int enchantability) {
            this.level = level;
            this.durability = durability;
            this.miningSpeed = miningSpeed;
            this.attackDamage = attackDamage;
            this.enchantability = enchantability;
        }

        public int getDurability() {
            return this.durability;
        }

        public float getMiningSpeed() {
            return this.miningSpeed;
        }

        public float getAttackDamage() {
            return this.attackDamage;
        }

        public int getLevel() {
            return this.level;
        }

        public int getEnchantability() {
            return this.enchantability;
        }

        public Item getRepairIngredient() {
            if (this == WOOD) {
                return Item.byBlock(Blocks.PLANKS);
            } else if (this == STONE) {
                return Item.byBlock(Blocks.COBBLESTONE);
            } else if (this == GOLD) {
                return Items.GOLD_INGOT;
            } else if (this == IRON) {
                return Items.IRON_INGOT;
            } else {
                return this == DIAMOND ? Items.DIAMOND : null;
            }
        }
    }
}
