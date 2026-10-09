package net.minecraft.inventory.menu;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class BeaconMenu extends InventoryMenu {
    private Inventory inventory;
    private final BeaconMenu.PaymentSlot paymentSlot;

    public BeaconMenu(Inventory player, Inventory inventory) {
        this.inventory = inventory;
        this.addSlot(this.paymentSlot = new BeaconMenu.PaymentSlot(inventory, 0, 136, 110));
        int i = 36;
        int j = 137;

        for (int k = 0; k < 3; k++) {
            for (int l = 0; l < 9; l++) {
                this.addSlot(new InventorySlot(player, l + k * 9 + 9, i + l * 18, j + k * 18));
            }
        }

        for (int i1 = 0; i1 < 9; i1++) {
            this.addSlot(new InventorySlot(player, i1, i + i1 * 18, 58 + j));
        }
    }

    @Override
    public void addListener(InventoryMenuListener listener) {
        super.addListener(listener);
        listener.updateData(this, this.inventory);
    }

    @Override
    public void setData(int id, int value) {
        this.inventory.setData(id, value);
    }

    public Inventory getBeacon() {
        return this.inventory;
    }

    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        if (player != null && !player.world.isClient) {
            ItemStack itemstack = this.paymentSlot.removeItem(this.paymentSlot.getMaxStackSize());
            if (itemstack != null) {
                player.dropItem(itemstack, false);
            }
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
            if (slot == 0) {
                if (!this.moveItem(itemstack1, 1, 37, true)) {
                    return null;
                }

                inventoryslot.onQuickMoved(itemstack1, itemstack);
            } else if (!this.paymentSlot.hasItem() && this.paymentSlot.isItemAllowed(itemstack1) && itemstack1.size == 1) {
                if (!this.moveItem(itemstack1, 0, 1, false)) {
                    return null;
                }
            } else if (slot >= 1 && slot < 28) {
                if (!this.moveItem(itemstack1, 28, 37, false)) {
                    return null;
                }
            } else if (slot >= 28 && slot < 37) {
                if (!this.moveItem(itemstack1, 1, 28, false)) {
                    return null;
                }
            } else if (!this.moveItem(itemstack1, 1, 37, false)) {
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

    class PaymentSlot extends InventorySlot {
        public PaymentSlot(Inventory inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
        }

        @Override
        public boolean isItemAllowed(ItemStack item) {
            return item != null
                && (
                    item.getItem() == Items.EMERALD
                        || item.getItem() == Items.DIAMOND
                        || item.getItem() == Items.GOLD_INGOT
                        || item.getItem() == Items.IRON_INGOT
                );
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
