package net.minecraft.block.entity;

import net.minecraft.nbt.NbtCompound;

public class ComparatorBlockEntity extends BlockEntity {
    private int outputSignal;

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("OutputSignal", this.outputSignal);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.outputSignal = nbt.getInt("OutputSignal");
    }

    public int getOutputSignal() {
        return this.outputSignal;
    }

    public void setOutputSignal(int signal) {
        this.outputSignal = signal;
    }
}
