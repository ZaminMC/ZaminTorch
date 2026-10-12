package net.minecraft.inventory.menu;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.inventory.slot.TradeResultSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.village.trade.Trader;
import net.minecraft.world.village.trade.TraderInventory;

public class TraderMenu extends InventoryMenu {
    private Trader trader;
    private TraderInventory inventory;
    private final World world;

    public TraderMenu(PlayerInventory playerInventory, Trader trader, World world) {
        this.trader = trader;
        this.world = world;
        this.inventory = new TraderInventory(playerInventory.player, trader);
        this.addSlot(new InventorySlot(this.inventory, 0, 36, 53));
        this.addSlot(new InventorySlot(this.inventory, 1, 62, 53));
        this.addSlot(new TradeResultSlot(playerInventory.player, trader, this.inventory, 2, 120, 53));

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new InventorySlot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (int k = 0; k < 9; k++) {
            this.addSlot(new InventorySlot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    public TraderInventory getTraderInventory() {
        return this.inventory;
    }

    @Override
    public void addListener(InventoryMenuListener listener) {
        super.addListener(listener);
    }

    @Override
    public void updateListeners() {
        super.updateListeners();
    }

    @Override
    public void onContentsChanged(Inventory inventory) {
        this.inventory.updateOffer();
        super.onContentsChanged(inventory);
    }

    public void setRecipeIndex(int index) {
        this.inventory.setOffer(index);
    }

    @Override
    public void setData(int id, int value) {
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return this.trader.getCustomer() == player;
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
            } else if (slot != 0 && slot != 1) {
                if (slot >= 3 && slot < 30) {
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

    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        this.trader.setCustomer(null);
        super.close(player);
        if (!this.world.isClient) {
            ItemStack itemstack = this.inventory.removeItemQuietly(0);
            if (itemstack != null) {
                player.dropItem(itemstack, false);
            }

            itemstack = this.inventory.removeItemQuietly(1);
            if (itemstack != null) {
                player.dropItem(itemstack, false);
            }
        }
    }
}
