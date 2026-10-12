package net.minecraft.inventory.menu;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;

public class DispenserMenu extends InventoryMenu {
    private Inventory inventory;

    public DispenserMenu(Inventory playerInventory, Inventory inventory) {
        this.inventory = inventory;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                this.addSlot(new InventorySlot(inventory, j + i * 3, 62 + j * 18, 17 + i * 18));
            }
        }

        for (int k = 0; k < 3; k++) {
            for (int i1 = 0; i1 < 9; i1++) {
                this.addSlot(new InventorySlot(playerInventory, i1 + k * 9 + 9, 8 + i1 * 18, 84 + k * 18));
            }
        }

        for (int l = 0; l < 9; l++) {
            this.addSlot(new InventorySlot(playerInventory, l, 8 + l * 18, 142));
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
            if (slot < 9) {
                if (!this.moveItem(itemstack1, 9, 45, true)) {
                    return null;
                }
            } else if (!this.moveItem(itemstack1, 0, 9, false)) {
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
