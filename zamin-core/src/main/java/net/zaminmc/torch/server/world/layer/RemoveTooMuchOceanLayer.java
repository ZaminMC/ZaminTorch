package net.zaminmc.torch.server.world.layer;

/**
 * The ocean diluter (RemoveTooMuchOceanLayer 1:1): an ocean cell whose
 * whole cross neighborhood is ocean flips to land half the time — the
 * continental thinning pass.
 */
public class RemoveTooMuchOceanLayer extends Layer {

    public RemoveTooMuchOceanLayer(long seed, Layer parent) {
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
                int k1 = parentArea[j1 + 1 + (i1 + 1 - 1) * (sizeX + 2)];
                int l1 = parentArea[j1 + 1 + 1 + (i1 + 1) * (sizeX + 2)];
                int i2 = parentArea[j1 + 1 - 1 + (i1 + 1) * (sizeX + 2)];
                int j2 = parentArea[j1 + 1 + (i1 + 1 + 1) * (sizeX + 2)];
                int k2 = parentArea[j1 + 1 + (i1 + 1) * k];
                area[j1 + i1 * sizeX] = k2;
                this.setChunkSeed(j1 + x, i1 + z);
                if (k2 == 0 && k1 == 0 && l1 == 0 && i2 == 0 && j2 == 0
                        && this.nextInt(2) == 0) {
                    area[j1 + i1 * sizeX] = 1;
                }
            }
        }
        return area;
    }
}
