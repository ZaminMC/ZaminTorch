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
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CraftingTableMenu extends InventoryMenu {
    public CraftingInventory inventory = new CraftingInventory(this, 3, 3);
    public Inventory resultInventory = new ResultInventory();
    private World world;
    private BlockPos pos;

    public CraftingTableMenu(PlayerInventory playerInventory, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
        this.addSlot(new CraftingResultSlot(playerInventory.player, this.inventory, this.resultInventory, 0, 124, 35));

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                this.addSlot(new InventorySlot(this.inventory, j + i * 3, 30 + j * 18, 17 + i * 18));
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

        this.onContentsChanged(this.inventory);
    }

    @Override
    public void onContentsChanged(Inventory inventory) {
        this.resultInventory.setItem(0, CraftingManager.getInstance().getResult(this.inventory, this.world));
    }

    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        if (!this.world.isClient) {
            for (int i = 0; i < 9; i++) {
                ItemStack itemstack = this.inventory.removeItemQuietly(i);
                if (itemstack != null) {
                    player.dropItem(itemstack, false);
                }
            }
        }
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return this.world.getBlockState(this.pos).getBlock() == Blocks.CRAFTING_TABLE
            && !(player.squaredDistanceTo(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 64.0);
    }

    @Override
    public ItemStack quickMoveItem(PlayerEntity player, int slot) {
        ItemStack itemstack = null;
        InventorySlot inventoryslot = this.slots.get(slot);
        if (inventoryslot != null && inventoryslot.hasItem()) {
            ItemStack itemstack1 = inventoryslot.getItem();
            itemstack = itemstack1.copy();
            if (slot == 0) {
                if (!this.moveItem(itemstack1, 10, 46, true)) {
                    return null;
                }

                inventoryslot.onQuickMoved(itemstack1, itemstack);
            } else if (slot >= 10 && slot < 37) {
                if (!this.moveItem(itemstack1, 37, 46, false)) {
                    return null;
                }
            } else if (slot >= 37 && slot < 46) {
                if (!this.moveItem(itemstack1, 10, 37, false)) {
                    return null;
                }
            } else if (!this.moveItem(itemstack1, 10, 46, false)) {
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
