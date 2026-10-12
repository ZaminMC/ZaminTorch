package net.minecraft.entity.vehicle;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.InventoryLock;
import net.minecraft.inventory.InventoryUtils;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.menu.LockableMenuProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.World;

public abstract class InventoryMinecartEntity extends MinecartEntity implements LockableMenuProvider {
    private ItemStack[] items = new ItemStack[36];
    private boolean dropContents = true;

    public InventoryMinecartEntity(World world) {
        super(world);
    }

    public InventoryMinecartEntity(World world, double d, double e, double f) {
        super(world, d, e, f);
    }

    @Override
    public void dropItems(DamageSource damageSource) {
        super.dropItems(damageSource);
        if (this.world.getGameRules().getBoolean("doEntityDrops")) {
            InventoryUtils.dropItems(this.world, this, this);
        }
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items[slot];
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (this.items[slot] != null) {
            if (this.items[slot].size <= amount) {
                ItemStack itemstack1 = this.items[slot];
                this.items[slot] = null;
                return itemstack1;
            }

            ItemStack itemstack = this.items[slot].split(amount);
            if (this.items[slot].size == 0) {
                this.items[slot] = null;
            }

            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public ItemStack removeItemQuietly(int slot) {
        if (this.items[slot] != null) {
            ItemStack itemstack = this.items[slot];
            this.items[slot] = null;
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        this.items[slot] = item;
        if (item != null && item.size > this.getMaxStackSize()) {
            item.size = this.getMaxStackSize();
        }
    }

    @Override
    public void markDirty() {
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return !this.removed && !(player.squaredDistanceTo(this) > 64.0);
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
    public String getName() {
        return this.hasCustomName() ? this.getCustomName() : "container.minecart";
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void changeDimension(int dimension) {
        this.dropContents = false;
        super.changeDimension(dimension);
    }

    @Override
    public void remove() {
        if (this.dropContents) {
            InventoryUtils.dropItems(this.world, this, this);
        }

        super.remove();
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        NbtList nbtlist = new NbtList();

        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] != null) {
                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putByte("Slot", (byte)i);
                this.items[i].writeNbt(nbtcompound);
                nbtlist.addElement(nbtcompound);
            }
        }

        nbt.put("Items", nbtlist);
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        NbtList nbtlist = nbt.getList("Items", 10);
        this.items = new ItemStack[this.getSize()];

        for (int i = 0; i < nbtlist.size(); i++) {
            NbtCompound nbtcompound = nbtlist.getCompound(i);
            int j = nbtcompound.getByte("Slot") & 255;
            if (j >= 0 && j < this.items.length) {
                this.items[j] = ItemStack.fromNbt(nbtcompound);
            }
        }
    }

    @Override
    public boolean interact(PlayerEntity player) {
        if (!this.world.isClient) {
            player.openChestMenu(this);
        }

        return true;
    }

    @Override
    protected void applySlowdown() {
        int i = 15 - InventoryMenu.getAnalogSignal(this);
        float f = 0.98F + i * 0.001F;
        this.velocityX *= f;
        this.velocityY *= 0.0;
        this.velocityZ *= f;
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
    public void clear() {
        for (int i = 0; i < this.items.length; i++) {
            this.items[i] = null;
        }
    }
}
