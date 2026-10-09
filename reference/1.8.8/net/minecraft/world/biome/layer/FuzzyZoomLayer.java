package net.minecraft.world.biome.layer;

public class FuzzyZoomLayer extends NormalZoomLayer {
    public FuzzyZoomLayer(long l, Layer layer) {
        super(l, layer);
    }

    @Override
    protected int getModeOrRandom(int i1, int i2, int i3, int i4) {
        return this.pickInt(i1, i2, i3, i4);
    }
}
