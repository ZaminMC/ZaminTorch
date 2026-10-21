package net.zaminmc.torch.server.world.layer;

/**
 * The smoother (SmoothLayer 1:1): the cross-neighbor agreement pass —
 * opposing pairs agreeing take over the center (the 50/50 roll when
 * both pairs agree), the reference's own edge softener.
 */
public class SmoothLayer extends Layer {

    public SmoothLayer(long seed, Layer parent) {
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
                int k1 = parentArea[j1 + 0 + (i1 + 1) * k];
                int l1 = parentArea[j1 + 2 + (i1 + 1) * k];
                int i2 = parentArea[j1 + 1 + (i1 + 0) * k];
                int j2 = parentArea[j1 + 1 + (i1 + 2) * k];
                int k2 = parentArea[j1 + 1 + (i1 + 1) * k];
                if (k1 == l1 && i2 == j2) {
                    this.setChunkSeed(j1 + x, i1 + z);
                    if (this.nextInt(2) == 0) {
                        k2 = k1;
                    } else {
                        k2 = i2;
                    }
                } else {
                    if (k1 == l1) {
                        k2 = k1;
                    }
                    if (i2 == j2) {
                        k2 = i2;
                    }
                }
                area[j1 + i1 * sizeX] = k2;
            }
        }
        return area;
    }
}
