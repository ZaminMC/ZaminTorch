package net.minecraft.world.saveddata;

import net.minecraft.nbt.NbtCompound;

public abstract class SavedData {
    public final String id;
    private boolean dirty;

    public SavedData(String id) {
        this.id = id;
    }

    public abstract void readNbt(NbtCompound nbt);

    public abstract void writeNbt(NbtCompound nbt);

    public void markDirty() {
        this.setDirty(true);
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }
}
