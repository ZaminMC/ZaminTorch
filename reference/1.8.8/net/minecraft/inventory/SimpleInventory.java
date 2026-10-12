package net.minecraft.inventory;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public class SimpleInventory implements Inventory {
    private String name;
    private int size;
    private ItemStack[] items;
    private List<InventoryListener> listeners;
    private boolean hasCustomName;

    public SimpleInventory(String name, boolean hasCustomName, int size) {
        this.name = name;
        this.hasCustomName = hasCustomName;
        this.size = size;
        this.items = new ItemStack[size];
    }

    public SimpleInventory(Text name, int size) {
        this(name.getString(), true, size);
    }

    public void addListener(InventoryListener listener) {
        if (this.listeners == null) {
            this.listeners = Lists.newArrayList();
        }

        this.listeners.add(listener);
    }

    public void removeListener(InventoryListener listener) {
        this.listeners.remove(listener);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.items.length ? this.items[slot] : null;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (this.items[slot] != null) {
            if (this.items[slot].size <= amount) {
                ItemStack itemstack1 = this.items[slot];
                this.items[slot] = null;
                this.markDirty();
                return itemstack1;
            }

            ItemStack itemstack = this.items[slot].split(amount);
            if (this.items[slot].size == 0) {
                this.items[slot] = null;
            }

            this.markDirty();
            return itemstack;
        } else {
            return null;
        }
    }

    public ItemStack addItem(ItemStack item) {
        ItemStack itemstack = item.copy();

        for (int i = 0; i < this.size; i++) {
            ItemStack itemstack1 = this.getItem(i);
            if (itemstack1 == null) {
                this.setItem(i, itemstack);
                this.markDirty();
                return null;
            }

            if (ItemStack.matchesItem(itemstack1, itemstack)) {
                int j = Math.min(this.getMaxStackSize(), itemstack1.getMaxSize());
                int k = Math.min(itemstack.size, j - itemstack1.size);
                if (k > 0) {
                    itemstack1.size += k;
                    itemstack.size -= k;
                    if (itemstack.size <= 0) {
                        this.markDirty();
                        return null;
                    }
                }
            }
        }

        if (itemstack.size != item.size) {
            this.markDirty();
        }

        return itemstack;
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
    public void setItem(int slot, ItemStack item) {
        this.items[slot] = item;
        if (item != null && item.size > this.getMaxStackSize()) {
            item.size = this.getMaxStackSize();
        }

        this.markDirty();
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public boolean hasCustomName() {
        return this.hasCustomName;
    }

    public void setCustomName(String name) {
        this.hasCustomName = true;
        this.name = name;
    }

    @Override
    public Text getDisplayName() {
        return this.hasCustomName() ? new LiteralText(this.getName()) : new TranslatableText(this.getName());
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void markDirty() {
        if (this.listeners != null) {
            for (int i = 0; i < this.listeners.size(); i++) {
                this.listeners.get(i).onInventoryChanged(this);
            }
        }
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
