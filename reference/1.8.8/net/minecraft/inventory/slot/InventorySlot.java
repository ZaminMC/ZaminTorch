package net.minecraft.inventory.slot;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public class InventorySlot {
    /**
     * The slot's index in its inventory
     */
    private final int id;
    public final Inventory inventory;
    /**
     * The slot's index in its {@link net.minecraft.inventory.menu.InventoryMenu InventoryMenu}
     */
    public int index;
    public int x;
    public int y;

    public InventorySlot(Inventory inventory, int id, int x, int y) {
        this.inventory = inventory;
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public void onQuickMoved(ItemStack oldItem, ItemStack newItem) {
        if (oldItem != null && newItem != null) {
            if (oldItem.getItem() == newItem.getItem()) {
                int i = newItem.size - oldItem.size;
                if (i > 0) {
                    this.onQuickMoved(oldItem, i);
                }
            }
        }
    }

    protected void onQuickMoved(ItemStack item, int amount) {
    }

    protected void checkAchievements(ItemStack item) {
    }

    public void onItemRemoved(PlayerEntity player, ItemStack item) {
        this.markDirty();
    }

    public boolean isItemAllowed(ItemStack item) {
        return true;
    }

    public ItemStack getItem() {
        return this.inventory.getItem(this.id);
    }

    public boolean hasItem() {
        return this.getItem() != null;
    }

    public void setItem(ItemStack item) {
        this.inventory.setItem(this.id, item);
        this.markDirty();
    }

    public void markDirty() {
        this.inventory.markDirty();
    }

    public int getMaxStackSize() {
        return this.inventory.getMaxStackSize();
    }

    public int getMaxStackSize(ItemStack item) {
        return this.getMaxStackSize();
    }

    public String getTexture() {
        return null;
    }

    public ItemStack removeItem(int amount) {
        return this.inventory.removeItem(this.id, amount);
    }

    public boolean equals(Inventory inventory, int slot) {
        return inventory == this.inventory && slot == this.id;
    }

    public boolean canPickUp(PlayerEntity player) {
        return true;
    }

    public boolean isActive() {
        return true;
    }
}
