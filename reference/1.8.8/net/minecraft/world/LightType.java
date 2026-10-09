package net.minecraft.world;

public enum LightType {
    SKY(15),
    BLOCK(0);

    public final int defaultValue;

    LightType(int defaultValue) {
        this.defaultValue = defaultValue;
    }
}
