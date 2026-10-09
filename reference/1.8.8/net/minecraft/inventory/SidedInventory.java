package net.minecraft.inventory;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

public interface SidedInventory extends Inventory {
    int[] getSlots(Direction side);

    boolean canPushItem(int slot, ItemStack item, Direction side);

    boolean canPullItem(int slot, ItemStack item, Direction side);
}
