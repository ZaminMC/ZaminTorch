package net.minecraft.inventory.menu;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.world.Nameable;

public interface MenuProvider extends Nameable {
    InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player);

    String getMenuType();
}
