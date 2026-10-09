package net.minecraft.inventory.menu;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;

public class ChestMenu extends InventoryMenu {
    private Inventory inventory;
    private int rows;

    public ChestMenu(Inventory playerInventory, Inventory inventory, PlayerEntity player) {
        this.inventory = inventory;
        this.rows = inventory.getSize() / 9;
        inventory.onOpen(player);
        int i = (this.rows - 4) * 18;

        for (int j = 0; j < this.rows; j++) {
            for (int k = 0; k < 9; k++) {
                this.addSlot(new InventorySlot(inventory, k + j * 9, 8 + k * 18, 18 + j * 18));
            }
        }

        for (int l = 0; l < 3; l++) {
            for (int j1 = 0; j1 < 9; j1++) {
                this.addSlot(new InventorySlot(playerInventory, j1 + l * 9 + 9, 8 + j1 * 18, 103 + l * 18 + i));
            }
        }

        for (int i1 = 0; i1 < 9; i1++) {
            this.addSlot(new InventorySlot(playerInventory, i1, 8 + i1 * 18, 161 + i));
        }
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
            if (slot < this.rows * 9) {
                if (!this.moveItem(itemstack1, this.rows * 9, this.slots.size(), true)) {
                    return null;
                }
            } else if (!this.moveItem(itemstack1, 0, this.rows * 9, false)) {
                return null;
            }

            if (itemstack1.size == 0) {
                inventoryslot.setItem(null);
            } else {
                inventoryslot.markDirty();
            }
        }

        return itemstack;
    }

    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        this.inventory.onClose(player);
    }

    public Inventory getChest() {
        return this.inventory;
    }
}
