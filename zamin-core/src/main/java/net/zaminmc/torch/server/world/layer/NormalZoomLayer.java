package net.zaminmc.torch.server.world.layer;

/**
 * The 2x zoom (NormalZoomLayer 1:1): each parent cell becomes a 2x2
 * patch — the corner passes verbatim, the edges pick between the two
 * neighbors, the center takes the 4-corner mode (the subclass's
 * getModeOrRandom — the fuzzy form rolls instead).
 */
public class NormalZoomLayer extends Layer {

    public NormalZoomLayer(long seed, Layer parent) {
        super(seed);
        this.parent = parent;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int i = x >> 1;
        int j = z >> 1;
        int k = (sizeX >> 1) + 2;
        int l = (sizeZ >> 1) + 2;
        int[] parentArea = this.parent.getArea(i, j, k, l);
        int i1 = k - 1 << 1;
        int j1 = l - 1 << 1;
        int[] quilt = IntCache.push(i1 * j1);
        for (int k1 = 0; k1 < l - 1; k1++) {
            int l1 = (k1 << 1) * i1;
            int i2 = 0;
            int j2 = parentArea[i2 + 0 + (k1 + 0) * k];
            int k2 = parentArea[i2 + 0 + (k1 + 1) * k];
            while (i2 < k - 1) {
                this.setChunkSeed(i2 + i << 1, k1 + j << 1);
                int l2 = parentArea[i2 + 1 + (k1 + 0) * k];
                int i3 = parentArea[i2 + 1 + (k1 + 1) * k];
                quilt[l1] = j2;
                quilt[l1++ + i1] = this.pickInt(j2, k2);
                quilt[l1] = this.pickInt(j2, l2);
                quilt[l1++ + i1] = this.getModeOrRandom(j2, l2, k2, i3);
                j2 = l2;
                k2 = i3;
                i2++;
            }
        }
        int[] area = IntCache.push(sizeX * sizeZ);
        for (int j3 = 0; j3 < sizeZ; j3++) {
            System.arraycopy(quilt, (j3 + (z & 1)) * i1 + (x & 1),
                    area, j3 * sizeX, sizeX);
        }
        return area;
    }

    public static Layer zoom(long seed, Layer layer, int magnification) {
        Layer zoomed = layer;
        for (int i = 0; i < magnification; i++) {
            zoomed = new NormalZoomLayer(seed + i, zoomed);
        }
        return zoomed;
    }
}
