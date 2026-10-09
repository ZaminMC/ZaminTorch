package net.minecraft.inventory;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public class ResultInventory implements Inventory {
    private ItemStack[] items = new ItemStack[1];

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items[0];
    }

    @Override
    public String getName() {
        return "Result";
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
    public ItemStack removeItem(int slot, int amount) {
        if (this.items[0] != null) {
            ItemStack itemstack = this.items[0];
            this.items[0] = null;
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public ItemStack removeItemQuietly(int slot) {
        if (this.items[0] != null) {
            ItemStack itemstack = this.items[0];
            this.items[0] = null;
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        this.items[0] = item;
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
}
