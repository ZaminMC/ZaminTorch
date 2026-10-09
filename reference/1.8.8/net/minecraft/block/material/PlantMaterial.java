package net.minecraft.block.material;

public class PlantMaterial extends Material {
    public PlantMaterial(MapColor mapColor) {
        super(mapColor);
        this.setCanBeBrokenInAdventureMode();
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
