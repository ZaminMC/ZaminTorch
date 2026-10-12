package net.minecraft.inventory.menu;

import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class HorseMenu extends InventoryMenu {
    private Inventory inventory;
    private HorseBaseEntity horse;

    public HorseMenu(Inventory playerInventory, Inventory inventory, HorseBaseEntity horse, PlayerEntity player) {
        this.inventory = inventory;
        this.horse = horse;
        int i = 3;
        inventory.onOpen(player);
        int j = (i - 4) * 18;
        this.addSlot(new InventorySlot(inventory, 0, 8, 18) {
            @Override
            public boolean isItemAllowed(ItemStack item) {
                return super.isItemAllowed(item) && item.getItem() == Items.SADDLE && !this.hasItem();
            }
        });
        this.addSlot(new InventorySlot(inventory, 1, 8, 36) {
            @Override
            public boolean isItemAllowed(ItemStack item) {
                return super.isItemAllowed(item) && horse.canHaveArmor() && HorseBaseEntity.isHorseArmor(item.getItem());
            }

            @Override
            public boolean isActive() {
                return horse.canHaveArmor();
            }
        });
        if (horse.hasChest()) {
            for (int k = 0; k < i; k++) {
                for (int l = 0; l < 5; l++) {
                    this.addSlot(new InventorySlot(inventory, 2 + l + k * 5, 80 + l * 18, 18 + k * 18));
                }
            }
        }

        for (int i1 = 0; i1 < 3; i1++) {
            for (int k1 = 0; k1 < 9; k1++) {
                this.addSlot(new InventorySlot(playerInventory, k1 + i1 * 9 + 9, 8 + k1 * 18, 102 + i1 * 18 + j));
            }
        }

        for (int j1 = 0; j1 < 9; j1++) {
            this.addSlot(new InventorySlot(playerInventory, j1, 8 + j1 * 18, 160 + j));
        }
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return this.inventory.isValid(player) && this.horse.isAlive() && this.horse.distanceTo(player) < 8.0F;
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
            } else if (this.getSlot(1).isItemAllowed(itemstack1) && !this.getSlot(1).hasItem()) {
                if (!this.moveItem(itemstack1, 1, 2, false)) {
                    return null;
                }
            } else if (this.getSlot(0).isItemAllowed(itemstack1)) {
                if (!this.moveItem(itemstack1, 0, 1, false)) {
                    return null;
                }
            } else if (this.inventory.getSize() <= 2 || !this.moveItem(itemstack1, 2, this.inventory.getSize(), false)) {
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
