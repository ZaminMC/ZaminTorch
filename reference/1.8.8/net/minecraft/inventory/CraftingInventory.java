package net.minecraft.inventory;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public class CraftingInventory implements Inventory {
    private final ItemStack[] items;
    private final int size;
    private final int height;
    private final InventoryMenu menu;

    public CraftingInventory(InventoryMenu menu, int width, int height) {
        int i = width * height;
        this.items = new ItemStack[i];
        this.menu = menu;
        this.size = width;
        this.height = height;
    }

    @Override
    public int getSize() {
        return this.items.length;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= this.getSize() ? null : this.items[slot];
    }

    public ItemStack getItem(int column, int row) {
        return column >= 0 && column < this.size && row >= 0 && row <= this.height ? this.getItem(column + row * this.size) : null;
    }

    @Override
    public String getName() {
        return "container.crafting";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public Text getDisplayName() {
        return this.hasCustomName() ? new LiteralText(this.getName()) : new TranslatableText(this.getName());
    }

    @Override
    public ItemStack removeItemQuietly(int slot) {
        if (this.items[slot] != null) {
            ItemStack itemstack = this.items[slot];
            this.items[slot] = null;
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (this.items[slot] != null) {
            if (this.items[slot].size <= amount) {
                ItemStack itemstack1 = this.items[slot];
                this.items[slot] = null;
                this.menu.onContentsChanged(this);
                return itemstack1;
            }

            ItemStack itemstack = this.items[slot].split(amount);
            if (this.items[slot].size == 0) {
                this.items[slot] = null;
            }

            this.menu.onContentsChanged(this);
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        this.items[slot] = item;
        this.menu.onContentsChanged(this);
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void markDirty() {
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return true;
    }

    @Override
    public void onOpen(PlayerEntity player) {
    }

    @Override
    public void onClose(PlayerEntity player) {
    }

    @Override
    public boolean isItemAllowed(int slot, ItemStack item) {
        return true;
    }

    @Override
    public int getData(int id) {
        return 0;
    }

    @Override
    public void setData(int id, int value) {
    }

    @Override
    public int getDataSize() {
        return 0;
    }

    @Override
    public void clear() {
        for (int i = 0; i < this.items.length; i++) {
            this.items[i] = null;
        }
    }

    public int getHeight() {
        return this.height;
    }

    public int getWidth() {
        return this.size;
    }
}
