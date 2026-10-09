package net.minecraft.block.material;

public class PortalMaterial extends Material {
    public PortalMaterial(MapColor mapColor) {
        super(mapColor);
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
