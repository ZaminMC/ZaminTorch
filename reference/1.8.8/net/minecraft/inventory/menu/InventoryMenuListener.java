package net.minecraft.inventory.menu;

import java.util.List;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public interface InventoryMenuListener {
    void onMenuChanged(InventoryMenu menu, List<ItemStack> items);

    void onSlotChanged(InventoryMenu menu, int slot, ItemStack item);

    void onDataChanged(InventoryMenu menu, int id, int value);

    void updateData(InventoryMenu menu, Inventory inventory);
}
