package net.minecraft.inventory.menu;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.achievement.Achievements;

public class BrewingStandMenu extends InventoryMenu {
    private Inventory inventory;
    private final InventorySlot ingredientSlot;
    private int timer;

    public BrewingStandMenu(PlayerInventory playerInventory, Inventory inventory) {
        this.inventory = inventory;
        this.addSlot(new BrewingStandMenu.PotionSlot(playerInventory.player, inventory, 0, 56, 46));
        this.addSlot(new BrewingStandMenu.PotionSlot(playerInventory.player, inventory, 1, 79, 53));
        this.addSlot(new BrewingStandMenu.PotionSlot(playerInventory.player, inventory, 2, 102, 46));
        this.ingredientSlot = this.addSlot(new BrewingStandMenu.IngredientSlot(inventory, 3, 79, 17));

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new InventorySlot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (int k = 0; k < 9; k++) {
            this.addSlot(new InventorySlot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    @Override
    public void addListener(InventoryMenuListener listener) {
        super.addListener(listener);
        listener.updateData(this, this.inventory);
    }

    @Override
    public void updateListeners() {
        super.updateListeners();

        for (int i = 0; i < this.listeners.size(); i++) {
            InventoryMenuListener inventorymenulistener = this.listeners.get(i);
            if (this.timer != this.inventory.getData(0)) {
                inventorymenulistener.onDataChanged(this, 0, this.inventory.getData(0));
            }
        }

        this.timer = this.inventory.getData(0);
    }

    @Override
    public void setData(int id, int value) {
        this.inventory.setData(id, value);
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return this.inventory.isValid(player);
    }

    @Override
    public ItemStack quickMoveItem(PlayerEntity player, int slot) {
        ItemStack itemstack = null;
        InventorySlot inventoryslot = this.slots.get(slot);
        if (inventoryslot != null && inventoryslot.hasItem()) {
            ItemStack itemstack1 = inventoryslot.getItem();
            itemstack = itemstack1.copy();
            if ((slot < 0 || slot > 2) && slot != 3) {
                if (!this.ingredientSlot.hasItem() && this.ingredientSlot.isItemAllowed(itemstack1)) {
                    if (!this.moveItem(itemstack1, 3, 4, false)) {
                        return null;
                    }
                } else if (BrewingStandMenu.PotionSlot.matches(itemstack)) {
                    if (!this.moveItem(itemstack1, 0, 3, false)) {
                        return null;
                    }
                } else if (slot >= 4 && slot < 31) {
                    if (!this.moveItem(itemstack1, 31, 40, false)) {
                        return null;
                    }
                } else if (slot >= 31 && slot < 40) {
                    if (!this.moveItem(itemstack1, 4, 31, false)) {
                        return null;
                    }
                } else if (!this.moveItem(itemstack1, 4, 40, false)) {
                    return null;
                }
            } else {
                if (!this.moveItem(itemstack1, 4, 40, true)) {
                    return null;
                }

                inventoryslot.onQuickMoved(itemstack1, itemstack);
            }

            if (itemstack1.size == 0) {
                inventoryslot.setItem(null);
            } else {
                inventoryslot.markDirty();
            }

            if (itemstack1.size == itemstack.size) {
                return null;
            }

            inventoryslot.onItemRemoved(player, itemstack1);
        }

        return itemstack;
    }

    class IngredientSlot extends InventorySlot {
        public IngredientSlot(Inventory inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
        }

        @Override
        public boolean isItemAllowed(ItemStack item) {
            return item != null && item.getItem().isPotionIngredient(item);
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }

    static class PotionSlot extends InventorySlot {
        private PlayerEntity player;

        public PotionSlot(PlayerEntity player, Inventory inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
            this.player = player;
        }

        @Override
        public boolean isItemAllowed(ItemStack item) {
            return matches(item);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void onItemRemoved(PlayerEntity player, ItemStack item) {
            if (item.getItem() == Items.POTION && item.getMetadata() > 0) {
                this.player.incrementStat(Achievements.BREW_POTION);
            }

            super.onItemRemoved(player, item);
        }

        public static boolean matches(ItemStack item) {
            return item != null && (item.getItem() == Items.POTION || item.getItem() == Items.GLASS_BOTTLE);
        }
    }
}
