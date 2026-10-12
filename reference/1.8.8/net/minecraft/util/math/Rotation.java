package net.minecraft.util.math;

import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;

public class Rotation {
    protected final float pitch;
    protected final float yaw;
    protected final float roll;

    public Rotation(float pitch, float yaw, float roll) {
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
    }

    public Rotation(NbtList nbt) {
        this.pitch = nbt.getFloat(0);
        this.yaw = nbt.getFloat(1);
        this.roll = nbt.getFloat(2);
    }

    public NbtList toNbt() {
        NbtList nbtlist = new NbtList();
        nbtlist.addElement(new NbtFloat(this.pitch));
        nbtlist.addElement(new NbtFloat(this.yaw));
        nbtlist.addElement(new NbtFloat(this.roll));
        return nbtlist;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Rotation)) {
            return false;
        }

        Rotation rotation = (Rotation)object;
        return this.pitch == rotation.pitch && this.yaw == rotation.yaw && this.roll == rotation.roll;
    }

    public float getPitch() {
        return this.pitch;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getRoll() {
        return this.roll;
    }
}
