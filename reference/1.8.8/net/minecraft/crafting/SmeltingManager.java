package net.minecraft.crafting;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.StonebrickBlock;
import net.minecraft.item.DyeColor;
import net.minecraft.item.FishItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class SmeltingManager {
    private static final SmeltingManager INSTANCE = new SmeltingManager();
    private Map<ItemStack, ItemStack> recipes = Maps.newHashMap();
    private Map<ItemStack, Float> xpResults = Maps.newHashMap();

    public static SmeltingManager getInstance() {
        return INSTANCE;
    }

    private SmeltingManager() {
        this.register(Blocks.IRON_ORE, new ItemStack(Items.IRON_INGOT), 0.7F);
        this.register(Blocks.GOLD_ORE, new ItemStack(Items.GOLD_INGOT), 1.0F);
        this.register(Blocks.DIAMOND_ORE, new ItemStack(Items.DIAMOND), 1.0F);
        this.register(Blocks.SAND, new ItemStack(Blocks.GLASS), 0.1F);
        this.register(Items.PORKCHOP, new ItemStack(Items.COOKED_PORKCHOP), 0.35F);
        this.register(Items.BEEF, new ItemStack(Items.COOKED_BEEF), 0.35F);
        this.register(Items.CHICKEN, new ItemStack(Items.COOKED_CHICKEN), 0.35F);
        this.register(Items.RABBIT, new ItemStack(Items.COOKED_RABBIT), 0.35F);
        this.register(Items.MUTTON, new ItemStack(Items.COOKED_MUTTON), 0.35F);
        this.register(Blocks.COBBLESTONE, new ItemStack(Blocks.STONE), 0.1F);
        this.register(
            new ItemStack(Blocks.STONE_BRICKS, 1, StonebrickBlock.DEFAULT_ID), new ItemStack(Blocks.STONE_BRICKS, 1, StonebrickBlock.CRACKED_ID), 0.1F
        );
        this.register(Items.CLAY_BALL, new ItemStack(Items.BRICK), 0.3F);
        this.register(Blocks.CLAY, new ItemStack(Blocks.HARDENED_CLAY), 0.35F);
        this.register(Blocks.CACTUS, new ItemStack(Items.DYE, 1, DyeColor.GREEN.getMetadata()), 0.2F);
        this.register(Blocks.LOG, new ItemStack(Items.COAL, 1, 1), 0.15F);
        this.register(Blocks.LOG2, new ItemStack(Items.COAL, 1, 1), 0.15F);
        this.register(Blocks.EMERALD_ORE, new ItemStack(Items.EMERALD), 1.0F);
        this.register(Items.POTATO, new ItemStack(Items.BAKED_POTATO), 0.35F);
        this.register(Blocks.NETHERRACK, new ItemStack(Items.NETHERBRICK), 0.1F);
        this.register(new ItemStack(Blocks.SPONGE, 1, 1), new ItemStack(Blocks.SPONGE, 1, 0), 0.15F);

        for (FishItem.Type fishitem$type : FishItem.Type.values()) {
            if (fishitem$type.canBeCooked()) {
                this.register(new ItemStack(Items.FISH, 1, fishitem$type.getId()), new ItemStack(Items.COOKED_FISH, 1, fishitem$type.getId()), 0.35F);
            }
        }

        this.register(Blocks.COAL_ORE, new ItemStack(Items.COAL), 0.1F);
        this.register(Blocks.REDSTONE_ORE, new ItemStack(Items.REDSTONE), 0.7F);
        this.register(Blocks.LAPIS_ORE, new ItemStack(Items.DYE, 1, DyeColor.BLUE.getMetadata()), 0.2F);
        this.register(Blocks.QUARTZ_ORE, new ItemStack(Items.QUARTZ), 0.2F);
    }

    public void register(Block input, ItemStack result, float xp) {
        this.register(Item.byBlock(input), result, xp);
    }

    public void register(Item input, ItemStack result, float xp) {
        this.register(new ItemStack(input, 1, 32767), result, xp);
    }

    public void register(ItemStack input, ItemStack result, float xp) {
        this.recipes.put(input, result);
        this.xpResults.put(result, xp);
    }

    public ItemStack getResult(ItemStack input) {
        for (Entry<ItemStack, ItemStack> entry : this.recipes.entrySet()) {
            if (this.matchesInput(input, entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Checks if the given item stack matches the given recipe input.
     */
    private boolean matchesInput(ItemStack item, ItemStack input) {
        return input.getItem() == item.getItem() && (input.getMetadata() == 32767 || input.getMetadata() == item.getMetadata());
    }

    public Map<ItemStack, ItemStack> getRecipes() {
        return this.recipes;
    }

    public float getXp(ItemStack input) {
        for (Entry<ItemStack, Float> entry : this.xpResults.entrySet()) {
            if (this.matchesInput(input, entry.getKey())) {
                return entry.getValue();
            }
        }

        return 0.0F;
    }
}
