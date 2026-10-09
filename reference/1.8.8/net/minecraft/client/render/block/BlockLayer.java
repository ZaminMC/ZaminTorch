package net.minecraft.client.render.block;

public enum BlockLayer {
    SOLID("Solid"),
    CUTOUT_MIPPED("Mipped Cutout"),
    CUTOUT("Cutout"),
    TRANSLUCENT("Translucent");

    private final String name;

    BlockLayer(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
