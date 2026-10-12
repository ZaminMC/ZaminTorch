package net.minecraft.block.dispenser;

import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockSource;

public interface DispenseBehavior {
    DispenseBehavior NONE = new DispenseBehavior() {
        @Override
        public ItemStack dispense(IBlockSource source, ItemStack item) {
            return item;
        }
    };

    /**
     * Dispense the given item at the given location.
     * 
     * @return the remainder after the item has been dispensed.
     */
    ItemStack dispense(IBlockSource source, ItemStack item);
}
