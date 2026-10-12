package net.minecraft.inventory;

import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

public class EnderChestInventory extends SimpleInventory {
    private EnderChestBlockEntity enderChest;

    public EnderChestInventory() {
        super("container.enderchest", false, 27);
    }

    public void setCurrentBlockEntity(EnderChestBlockEntity enderChest) {
        this.enderChest = enderChest;
    }

    public void readNbt(NbtList list) {
        for (int i = 0; i < this.getSize(); i++) {
            this.setItem(i, null);
        }

        for (int k = 0; k < list.size(); k++) {
            NbtCompound nbtcompound = list.getCompound(k);
            int j = nbtcompound.getByte("Slot") & 255;
            if (j >= 0 && j < this.getSize()) {
                this.setItem(j, ItemStack.fromNbt(nbtcompound));
            }
        }
    }

    public NbtList toNbt() {
        NbtList nbtlist = new NbtList();

        for (int i = 0; i < this.getSize(); i++) {
            ItemStack itemstack = this.getItem(i);
            if (itemstack != null) {
                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putByte("Slot", (byte)i);
                itemstack.writeNbt(nbtcompound);
                nbtlist.addElement(nbtcompound);
            }
        }

        return nbtlist;
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return (this.enderChest == null || this.enderChest.isValid(player)) && super.isValid(player);
    }

    @Override
    public void onOpen(PlayerEntity player) {
        if (this.enderChest != null) {
            this.enderChest.onOpen();
        }

        super.onOpen(player);
    }

    @Override
    public void onClose(PlayerEntity player) {
        if (this.enderChest != null) {
            this.enderChest.onClose();
        }

        super.onClose(player);
        this.enderChest = null;
    }
}
