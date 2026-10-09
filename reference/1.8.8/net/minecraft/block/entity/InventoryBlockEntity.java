package net.minecraft.block.entity;

import net.minecraft.inventory.InventoryLock;
import net.minecraft.inventory.menu.LockableMenuProvider;
import net.minecraft.inventory.menu.MenuProvider;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public abstract class InventoryBlockEntity extends BlockEntity implements MenuProvider, LockableMenuProvider {
    private InventoryLock lock = InventoryLock.NONE;

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.lock = InventoryLock.fromNbt(nbt);
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (this.lock != null) {
            this.lock.writeNbt(nbt);
        }
    }

    @Override
    public boolean isLocked() {
        return this.lock != null && !this.lock.isEmpty();
    }

    @Override
    public InventoryLock getLock() {
        return this.lock;
    }

    @Override
    public void setLock(InventoryLock lock) {
        this.lock = lock;
    }

    @Override
    public Text getDisplayName() {
        return this.hasCustomName() ? new LiteralText(this.getName()) : new TranslatableText(this.getName());
    }
}
