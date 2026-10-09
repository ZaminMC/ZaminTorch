package net.minecraft.block.material;

public class LiquidMaterial extends Material {
    public LiquidMaterial(MapColor mapColor) {
        super(mapColor);
        this.setReplaceable();
        this.setDestroyOnPistonMove();
    }

    @Override
    public boolean isLiquid() {
        return true;
    }

    @Override
    public boolean blocksMovement() {
        return false;
    }

    @Override
    public boolean isSolid() {
        return false;
    }
}
