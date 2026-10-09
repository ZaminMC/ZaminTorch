package net.minecraft.inventory.menu;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;

public class HopperMenu extends InventoryMenu {
    private final Inventory inventory;

    public HopperMenu(PlayerInventory playerInventory, Inventory inventory, PlayerEntity player) {
        this.inventory = inventory;
        inventory.onOpen(player);
        int i = 51;

        for (int j = 0; j < inventory.getSize(); j++) {
            this.addSlot(new InventorySlot(inventory, j, 44 + j * 18, 20));
        }

        for (int l = 0; l < 3; l++) {
            for (int k = 0; k < 9; k++) {
                this.addSlot(new InventorySlot(playerInventory, k + l * 9 + 9, 8 + k * 18, l * 18 + i));
            }
        }

        for (int i1 = 0; i1 < 9; i1++) {
            this.addSlot(new InventorySlot(playerInventory, i1, 8 + i1 * 18, 58 + i));
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
            if (slot < this.inventory.getSize()) {
                if (!this.moveItem(itemstack1, this.inventory.getSize(), this.slots.size(), true)) {
                    return null;
                }
            } else if (!this.moveItem(itemstack1, 0, this.inventory.getSize(), false)) {
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
}
