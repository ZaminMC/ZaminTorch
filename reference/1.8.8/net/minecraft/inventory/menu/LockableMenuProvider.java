package net.minecraft.inventory.menu;

import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryLock;

public interface LockableMenuProvider extends Inventory, MenuProvider {
    boolean isLocked();

    void setLock(InventoryLock lock);

    InventoryLock getLock();
}
