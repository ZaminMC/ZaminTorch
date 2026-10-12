package net.minecraft.inventory.menu;

import net.minecraft.block.Blocks;
import net.minecraft.crafting.CraftingManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.ResultInventory;
import net.minecraft.inventory.slot.CraftingResultSlot;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class PlayerMenu extends InventoryMenu {
    public CraftingInventory craftingInventory = new CraftingInventory(this, 2, 2);
    public Inventory resultInventory = new ResultInventory();
    public boolean active;
    private final PlayerEntity player;

    public PlayerMenu(PlayerInventory playerInventory, boolean active, PlayerEntity player) {
        this.active = active;
        this.player = player;
        this.addSlot(new CraftingResultSlot(playerInventory.player, this.craftingInventory, this.resultInventory, 0, 144, 36));

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                this.addSlot(new InventorySlot(this.craftingInventory, j + i * 2, 88 + j * 18, 26 + i * 18));
            }
        }

        for (int k = 0; k < 4; k++) {
            final int j1 = k;
            this.addSlot(
                new InventorySlot(playerInventory, playerInventory.getSize() - 1 - k, 8, 8 + k * 18) {
                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }

                    @Override
                    public boolean isItemAllowed(ItemStack item) {
                        if (item == null) {
                            return false;
                        } else {
                            return item.getItem() instanceof ArmorItem
                                ? ((ArmorItem)item.getItem()).slot == j1
                                : (item.getItem() == Item.byBlock(Blocks.PUMPKIN) || item.getItem() == Items.SKULL) && j1 == 0;
                        }
                    }

                    @Override
                    public String getTexture() {
                        return ArmorItem.EMPTY_SLOTS[j1];
                    }
                }
            );
        }

        for (int l = 0; l < 3; l++) {
            for (int k1 = 0; k1 < 9; k1++) {
                this.addSlot(new InventorySlot(playerInventory, k1 + (l + 1) * 9, 8 + k1 * 18, 84 + l * 18));
            }
        }

        for (int i1 = 0; i1 < 9; i1++) {
            this.addSlot(new InventorySlot(playerInventory, i1, 8 + i1 * 18, 142));
        }

        this.onContentsChanged(this.craftingInventory);
    }

    @Override
    public void onContentsChanged(Inventory inventory) {
        this.resultInventory.setItem(0, CraftingManager.getInstance().getResult(this.craftingInventory, this.player.world));
    }

    @Override
    public void close(PlayerEntity player) {
        super.close(player);

        for (int i = 0; i < 4; i++) {
            ItemStack itemstack = this.craftingInventory.removeItemQuietly(i);
            if (itemstack != null) {
                player.dropItem(itemstack, false);
            }
        }

        this.resultInventory.setItem(0, null);
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMoveItem(PlayerEntity player, int slot) {
        ItemStack itemstack = null;
        InventorySlot inventoryslot = this.slots.get(slot);
        if (inventoryslot != null && inventoryslot.hasItem()) {
            ItemStack itemstack1 = inventoryslot.getItem();
            itemstack = itemstack1.copy();
            if (slot == 0) {
                if (!this.moveItem(itemstack1, 9, 45, true)) {
                    return null;
                }

                inventoryslot.onQuickMoved(itemstack1, itemstack);
            } else if (slot >= 1 && slot < 5) {
                if (!this.moveItem(itemstack1, 9, 45, false)) {
                    return null;
                }
            } else if (slot >= 5 && slot < 9) {
                if (!this.moveItem(itemstack1, 9, 45, false)) {
                    return null;
                }
            } else if (itemstack.getItem() instanceof ArmorItem && !this.slots.get(5 + ((ArmorItem)itemstack.getItem()).slot).hasItem()) {
                int i = 5 + ((ArmorItem)itemstack.getItem()).slot;
                if (!this.moveItem(itemstack1, i, i + 1, false)) {
                    return null;
                }
            } else if (slot >= 9 && slot < 36) {
                if (!this.moveItem(itemstack1, 36, 45, false)) {
                    return null;
                }
            } else if (slot >= 36 && slot < 45) {
                if (!this.moveItem(itemstack1, 9, 36, false)) {
                    return null;
                }
            } else if (!this.moveItem(itemstack1, 9, 45, false)) {
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
    public boolean canRemoveForPickupAll(ItemStack item, InventorySlot slot) {
        return slot.inventory != this.resultInventory && super.canRemoveForPickupAll(item, slot);
    }
}
