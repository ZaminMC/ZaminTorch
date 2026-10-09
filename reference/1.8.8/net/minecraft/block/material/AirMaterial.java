package net.minecraft.block.material;

public class AirMaterial extends Material {
    public AirMaterial(MapColor mapColor) {
        super(mapColor);
        this.setReplaceable();
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    public boolean blocksMovement() {
        return false;
    }
}
