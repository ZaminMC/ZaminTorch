package net.minecraft.inventory;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.menu.LockableMenuProvider;
import net.minecraft.text.Text;

public class MenuInventory extends SimpleInventory implements LockableMenuProvider {
    private String type;
    private Map<Integer, Integer> data = Maps.newHashMap();

    public MenuInventory(String type, Text name, int size) {
        super(name, size);
        this.type = type;
    }

    @Override
    public int getData(int id) {
        return this.data.containsKey(id) ? this.data.get(id) : 0;
    }

    @Override
    public void setData(int id, int value) {
        this.data.put(id, value);
    }

    @Override
    public int getDataSize() {
        return this.data.size();
    }

    @Override
    public boolean isLocked() {
        return false;
    }

    @Override
    public void setLock(InventoryLock lock) {
    }

    @Override
    public InventoryLock getLock() {
        return InventoryLock.NONE;
    }

    @Override
    public String getMenuType() {
        return this.type;
    }

    @Override
    public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
        throw new UnsupportedOperationException();
    }
}
