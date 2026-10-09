package net.minecraft.inventory;

import net.minecraft.nbt.NbtCompound;

public class InventoryLock {
    public static final InventoryLock NONE = new InventoryLock("");
    private final String key;

    public InventoryLock(String key) {
        this.key = key;
    }

    public boolean isEmpty() {
        return this.key == null || this.key.isEmpty();
    }

    public String getKey() {
        return this.key;
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.putString("Lock", this.key);
    }

    public static InventoryLock fromNbt(NbtCompound nbt) {
        if (nbt.contains("Lock", 8)) {
            String s = nbt.getString("Lock");
            return new InventoryLock(s);
        } else {
            return NONE;
        }
    }
}
