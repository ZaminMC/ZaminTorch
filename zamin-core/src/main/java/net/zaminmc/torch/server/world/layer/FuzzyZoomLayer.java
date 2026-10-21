package net.zaminmc.torch.server.world.layer;

/**
 * The fuzzy zoom (FuzzyZoomLayer 1:1): the zoom's center rolls one of
 * the four corners instead of taking the mode — the chain's first
 * roughening pass.
 */
public class FuzzyZoomLayer extends NormalZoomLayer {

    public FuzzyZoomLayer(long seed, Layer parent) {
        super(seed, parent);
    }

    @Override
    protected int getModeOrRandom(int i1, int i2, int i3, int i4) {
        return this.pickInt(i1, i2, i3, i4);
    }
}
