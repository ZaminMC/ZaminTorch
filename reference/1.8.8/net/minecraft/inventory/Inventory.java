package net.minecraft.inventory;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.Nameable;

public interface Inventory extends Nameable {
    int getSize();

    ItemStack getItem(int slot);

    ItemStack removeItem(int slot, int amount);

    ItemStack removeItemQuietly(int slot);

    void setItem(int slot, ItemStack item);

    int getMaxStackSize();

    void markDirty();

    boolean isValid(PlayerEntity player);

    void onOpen(PlayerEntity player);

    void onClose(PlayerEntity player);

    boolean isItemAllowed(int slot, ItemStack item);

    int getData(int id);

    void setData(int id, int value);

    int getDataSize();

    void clear();
}
