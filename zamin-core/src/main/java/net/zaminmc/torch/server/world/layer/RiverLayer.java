package net.zaminmc.torch.server.world.layer;

import net.zaminmc.torch.server.world.Biome;

/**
 * The river curve (RiverLayer 1:1): the marker's parity walk (the
 * makeCurves band 2 + (value & 1)) over the cross neighborhood — all
 * four agreeing leaves the no-river -1, any disagreement draws the
 * RIVER band.
 */
public class RiverLayer extends Layer {

    public RiverLayer(long seed, Layer parent) {
        super(seed);
        this.parent = parent;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int i = x - 1;
        int j = z - 1;
        int k = sizeX + 2;
        int l = sizeZ + 2;
        int[] parentArea = this.parent.getArea(i, j, k, l);
        int[] area = IntCache.push(sizeX * sizeZ);
        for (int i1 = 0; i1 < sizeZ; i1++) {
            for (int j1 = 0; j1 < sizeX; j1++) {
                int k1 = this.makeCurves(parentArea[j1 + 0 + (i1 + 1) * k]);
                int l1 = this.makeCurves(parentArea[j1 + 2 + (i1 + 1) * k]);
                int i2 = this.makeCurves(parentArea[j1 + 1 + (i1 + 0) * k]);
                int j2 = this.makeCurves(parentArea[j1 + 1 + (i1 + 2) * k]);
                int k2 = this.makeCurves(parentArea[j1 + 1 + (i1 + 1) * k]);
                if (k2 == k1 && k2 == i2 && k2 == l1 && k2 == j2) {
                    area[j1 + i1 * sizeX] = -1;
                } else {
                    area[j1 + i1 * sizeX] = Biome.RIVER.legacyId();
                }
            }
        }
        return area;
    }

    private int makeCurves(int value) {
        return value >= 2 ? 2 + (value & 1) : value;
    }
}
