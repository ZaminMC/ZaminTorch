package net.minecraft.block.entity;

import java.util.Random;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.DispenserMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

public class DispenserBlockEntity extends InventoryBlockEntity implements Inventory {
    private static final Random RANDOM = new Random();
    private ItemStack[] inventory = new ItemStack[9];
    protected String customName;

    @Override
    public int getSize() {
        return 9;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.inventory[slot];
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (this.inventory[slot] != null) {
            if (this.inventory[slot].size <= amount) {
                ItemStack itemstack1 = this.inventory[slot];
                this.inventory[slot] = null;
                this.markDirty();
                return itemstack1;
            }

            ItemStack itemstack = this.inventory[slot].split(amount);
            if (this.inventory[slot].size == 0) {
                this.inventory[slot] = null;
            }

            this.markDirty();
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public ItemStack removeItemQuietly(int slot) {
        if (this.inventory[slot] != null) {
            ItemStack itemstack = this.inventory[slot];
            this.inventory[slot] = null;
            return itemstack;
        } else {
            return null;
        }
    }

    public int pickNonEmptySlot() {
        int i = -1;
        int j = 1;

        for (int k = 0; k < this.inventory.length; k++) {
            if (this.inventory[k] != null && RANDOM.nextInt(j++) == 0) {
                i = k;
            }
        }

        return i;
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        this.inventory[slot] = item;
        if (item != null && item.size > this.getMaxStackSize()) {
            item.size = this.getMaxStackSize();
        }

        this.markDirty();
    }

    public int insertItem(ItemStack item) {
        for (int i = 0; i < this.inventory.length; i++) {
            if (this.inventory[i] == null || this.inventory[i].getItem() == null) {
                this.setItem(i, item);
                return i;
            }
        }

        return -1;
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.customName : "container.dispenser";
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    @Override
    public boolean hasCustomName() {
        return this.customName != null;
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        NbtList nbtlist = nbt.getList("Items", 10);
        this.inventory = new ItemStack[this.getSize()];

        for (int i = 0; i < nbtlist.size(); i++) {
            NbtCompound nbtcompound = nbtlist.getCompound(i);
            int j = nbtcompound.getByte("Slot") & 255;
            if (j >= 0 && j < this.inventory.length) {
                this.inventory[j] = ItemStack.fromNbt(nbtcompound);
            }
        }

        if (nbt.contains("CustomName", 8)) {
            this.customName = nbt.getString("CustomName");
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        NbtList nbtlist = new NbtList();

        for (int i = 0; i < this.inventory.length; i++) {
            if (this.inventory[i] != null) {
                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putByte("Slot", (byte)i);
                this.inventory[i].writeNbt(nbtcompound);
                nbtlist.addElement(nbtcompound);
            }
        }

        nbt.put("Items", nbtlist);
        if (this.hasCustomName()) {
            nbt.putString("CustomName", this.customName);
        }
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return this.world.getBlockEntity(this.pos) == this
            && !(player.squaredDistanceTo(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 64.0);
    }

    @Override
    public void onOpen(PlayerEntity player) {
    }

    @Override
    public void onClose(PlayerEntity player) {
    }

    @Override
    public boolean isItemAllowed(int slot, ItemStack item) {
        return true;
    }

    @Override
    public String getMenuType() {
        return "minecraft:dispenser";
    }

    @Override
    public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
        return new DispenserMenu(playerInventory, this);
    }

    @Override
    public int getData(int id) {
        return 0;
    }

    @Override
    public void setData(int id, int value) {
    }

    @Override
    public int getDataSize() {
        return 0;
    }

    @Override
    public void clear() {
        for (int i = 0; i < this.inventory.length; i++) {
            this.inventory[i] = null;
        }
    }
}
