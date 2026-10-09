package net.minecraft.inventory.menu;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.text.Text;

public class EmptyMenuProvider implements MenuProvider {
    private String type;
    private Text name;

    public EmptyMenuProvider(String type, Text name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return this.name.getString();
    }

    @Override
    public boolean hasCustomName() {
        return true;
    }

    @Override
    public String getMenuType() {
        return this.type;
    }

    @Override
    public Text getDisplayName() {
        return this.name;
    }
}
