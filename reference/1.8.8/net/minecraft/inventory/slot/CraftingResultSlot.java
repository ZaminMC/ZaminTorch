package net.minecraft.inventory.slot;

import net.minecraft.block.Blocks;
import net.minecraft.crafting.CraftingManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.stat.achievement.Achievements;

public class CraftingResultSlot extends InventorySlot {
    private final CraftingInventory craftingInventory;
    private final PlayerEntity player;
    private int removeAmount;

    public CraftingResultSlot(PlayerEntity player, CraftingInventory craftingInventory, Inventory resultInventory, int slot, int x, int y) {
        super(resultInventory, slot, x, y);
        this.player = player;
        this.craftingInventory = craftingInventory;
    }

    @Override
    public boolean isItemAllowed(ItemStack item) {
        return false;
    }

    @Override
    public ItemStack removeItem(int amount) {
        if (this.hasItem()) {
            this.removeAmount = this.removeAmount + Math.min(amount, this.getItem().size);
        }

        return super.removeItem(amount);
    }

    @Override
    protected void onQuickMoved(ItemStack item, int amount) {
        this.removeAmount += amount;
        this.checkAchievements(item);
    }

    @Override
    protected void checkAchievements(ItemStack item) {
        if (this.removeAmount > 0) {
            item.onResult(this.player.world, this.player, this.removeAmount);
        }

        this.removeAmount = 0;
        if (item.getItem() == Item.byBlock(Blocks.CRAFTING_TABLE)) {
            this.player.incrementStat(Achievements.CRAFT_CRAFTING_TABLE);
        }

        if (item.getItem() instanceof PickaxeItem) {
            this.player.incrementStat(Achievements.CRAFT_PICKAXE);
        }

        if (item.getItem() == Item.byBlock(Blocks.FURNACE)) {
            this.player.incrementStat(Achievements.CRAFT_FURNACE);
        }

        if (item.getItem() instanceof HoeItem) {
            this.player.incrementStat(Achievements.CRAFT_WOODEN_HOE);
        }

        if (item.getItem() == Items.BREAD) {
            this.player.incrementStat(Achievements.CRAFT_BREAD);
        }

        if (item.getItem() == Items.CAKE) {
            this.player.incrementStat(Achievements.CRAFT_CAKE);
        }

        if (item.getItem() instanceof PickaxeItem && ((PickaxeItem)item.getItem()).getTier() != Item.Tier.WOOD) {
            this.player.incrementStat(Achievements.CRAFT_BETTER_PICKAXE);
        }

        if (item.getItem() instanceof SwordItem) {
            this.player.incrementStat(Achievements.CRAFT_SWORD);
        }

        if (item.getItem() == Item.byBlock(Blocks.ENCHANTING_TABLE)) {
            this.player.incrementStat(Achievements.CRAFT_ENCHANTING_TABLE);
        }

        if (item.getItem() == Item.byBlock(Blocks.BOOKSHELF)) {
            this.player.incrementStat(Achievements.CRAFT_BOOKSHELF);
        }

        if (item.getItem() == Items.GOLDEN_APPLE && item.getMetadata() == 1) {
            this.player.incrementStat(Achievements.EAT_ENCHANTED_GOLDEN_APPLE);
        }
    }

    @Override
    public void onItemRemoved(PlayerEntity player, ItemStack item) {
        this.checkAchievements(item);
        ItemStack[] aitemstack = CraftingManager.getInstance().getRemainder(this.craftingInventory, player.world);

        for (int i = 0; i < aitemstack.length; i++) {
            ItemStack itemstack = this.craftingInventory.getItem(i);
            ItemStack itemstack1 = aitemstack[i];
            if (itemstack != null) {
                this.craftingInventory.removeItem(i, 1);
            }

            if (itemstack1 != null) {
                if (this.craftingInventory.getItem(i) == null) {
                    this.craftingInventory.setItem(i, itemstack1);
                } else if (!this.player.inventory.addItem(itemstack1)) {
                    this.player.dropItem(itemstack1, false);
                }
            }
        }
    }
}
