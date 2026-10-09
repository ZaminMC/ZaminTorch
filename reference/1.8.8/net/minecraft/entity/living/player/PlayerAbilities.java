package net.minecraft.entity.living.player;

import net.minecraft.nbt.NbtCompound;

public class PlayerAbilities {
    public boolean invulnerable;
    public boolean flying;
    public boolean canFly;
    public boolean creativeMode;
    public boolean canModifyWorld = true;
    private float flySpeed = 0.05F;
    private float walkSpeed = 0.1F;

    public void writeNbt(NbtCompound nbt) {
        NbtCompound nbtcompound = new NbtCompound();
        nbtcompound.putBoolean("invulnerable", this.invulnerable);
        nbtcompound.putBoolean("flying", this.flying);
        nbtcompound.putBoolean("mayfly", this.canFly);
        nbtcompound.putBoolean("instabuild", this.creativeMode);
        nbtcompound.putBoolean("mayBuild", this.canModifyWorld);
        nbtcompound.putFloat("flySpeed", this.flySpeed);
        nbtcompound.putFloat("walkSpeed", this.walkSpeed);
        nbt.put("abilities", nbtcompound);
    }

    public void readNbt(NbtCompound nbt) {
        if (nbt.contains("abilities", 10)) {
            NbtCompound nbtcompound = nbt.getCompound("abilities");
            this.invulnerable = nbtcompound.getBoolean("invulnerable");
            this.flying = nbtcompound.getBoolean("flying");
            this.canFly = nbtcompound.getBoolean("mayfly");
            this.creativeMode = nbtcompound.getBoolean("instabuild");
            if (nbtcompound.contains("flySpeed", 99)) {
                this.flySpeed = nbtcompound.getFloat("flySpeed");
                this.walkSpeed = nbtcompound.getFloat("walkSpeed");
            }

            if (nbtcompound.contains("mayBuild", 1)) {
                this.canModifyWorld = nbtcompound.getBoolean("mayBuild");
            }
        }
    }

    public float getFlySpeed() {
        return this.flySpeed;
    }

    public void setFlySpeed(float flySpeed) {
        this.flySpeed = flySpeed;
    }

    public float getWalkSpeed() {
        return this.walkSpeed;
    }

    public void setWalkSpeed(float walkSpeed) {
        this.walkSpeed = walkSpeed;
    }
}
