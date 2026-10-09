package net.minecraft.client.gui.screen.inventory.menu;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.menu.InventoryMenuListener;
import net.minecraft.item.ItemStack;

public class CreativeInventoryListener implements InventoryMenuListener {
    private final Minecraft minecraft;

    public CreativeInventoryListener(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void onMenuChanged(InventoryMenu menu, List<ItemStack> items) {
    }

    @Override
    public void onSlotChanged(InventoryMenu menu, int slot, ItemStack item) {
        this.minecraft.interactionManager.addItemToCreativeMenu(item, slot);
    }

    @Override
    public void onDataChanged(InventoryMenu menu, int id, int value) {
    }

    @Override
    public void updateData(InventoryMenu menu, Inventory inventory) {
    }
}
