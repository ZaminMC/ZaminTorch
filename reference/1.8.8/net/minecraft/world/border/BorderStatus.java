package net.minecraft.world.border;

public enum BorderStatus {
    GROWING(4259712),
    SHRINKING(16724016),
    STATIONARY(2138367);

    private final int color;

    BorderStatus(int color) {
        this.color = color;
    }

    public int getColor() {
        return this.color;
    }
}
