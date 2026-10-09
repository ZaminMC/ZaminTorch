package net.minecraft.inventory.slot;

import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class FurnaceFuelSlot extends InventorySlot {
    public FurnaceFuelSlot(Inventory inventory, int i, int j, int k) {
        super(inventory, i, j, k);
    }

    @Override
    public boolean isItemAllowed(ItemStack item) {
        return FurnaceBlockEntity.isFuel(item) || isBucket(item);
    }

    @Override
    public int getMaxStackSize(ItemStack item) {
        return isBucket(item) ? 1 : super.getMaxStackSize(item);
    }

    public static boolean isBucket(ItemStack item) {
        return item != null && item.getItem() != null && item.getItem() == Items.BUCKET;
    }
}
