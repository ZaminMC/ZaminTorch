package net.minecraft.inventory.menu;

import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.crafting.SmeltingManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.slot.FurnaceFuelSlot;
import net.minecraft.inventory.slot.FurnaceResultSlot;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;

public class FurnaceMenu extends InventoryMenu {
    private final Inventory inventory;
    private int cookTime;
    private int totalCookTime;
    private int fuelTime;
    private int totalFuelTime;

    public FurnaceMenu(PlayerInventory playerInventory, Inventory inventory) {
        this.inventory = inventory;
        this.addSlot(new InventorySlot(inventory, 0, 56, 17));
        this.addSlot(new FurnaceFuelSlot(inventory, 1, 56, 53));
        this.addSlot(new FurnaceResultSlot(playerInventory.player, inventory, 2, 116, 35));

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
            if (this.cookTime != this.inventory.getData(2)) {
                inventorymenulistener.onDataChanged(this, 2, this.inventory.getData(2));
            }

            if (this.fuelTime != this.inventory.getData(0)) {
                inventorymenulistener.onDataChanged(this, 0, this.inventory.getData(0));
            }

            if (this.totalFuelTime != this.inventory.getData(1)) {
                inventorymenulistener.onDataChanged(this, 1, this.inventory.getData(1));
            }

            if (this.totalCookTime != this.inventory.getData(3)) {
                inventorymenulistener.onDataChanged(this, 3, this.inventory.getData(3));
            }
        }

        this.cookTime = this.inventory.getData(2);
        this.fuelTime = this.inventory.getData(0);
        this.totalFuelTime = this.inventory.getData(1);
        this.totalCookTime = this.inventory.getData(3);
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
            if (slot == 2) {
                if (!this.moveItem(itemstack1, 3, 39, true)) {
                    return null;
                }

                inventoryslot.onQuickMoved(itemstack1, itemstack);
            } else if (slot != 1 && slot != 0) {
                if (SmeltingManager.getInstance().getResult(itemstack1) != null) {
                    if (!this.moveItem(itemstack1, 0, 1, false)) {
                        return null;
                    }
                } else if (FurnaceBlockEntity.isFuel(itemstack1)) {
                    if (!this.moveItem(itemstack1, 1, 2, false)) {
                        return null;
                    }
                } else if (slot >= 3 && slot < 30) {
                    if (!this.moveItem(itemstack1, 30, 39, false)) {
                        return null;
                    }
                } else if (slot >= 30 && slot < 39 && !this.moveItem(itemstack1, 3, 30, false)) {
                    return null;
                }
            } else if (!this.moveItem(itemstack1, 3, 39, false)) {
                return null;
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
}
