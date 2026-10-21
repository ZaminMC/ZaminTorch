package net.zaminmc.torch.server.world.layer;

import net.zaminmc.torch.server.world.Biome;

/**
 * The deep-ocean band (AddDeepOceanLayer 1:1): an ocean cell with at
 * least four ocean neighbors (the cross census > 3) deepens to
 * DEEP_OCEAN.
 */
public class AddDeepOceanLayer extends Layer {

    public AddDeepOceanLayer(long seed, Layer parent) {
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
                int oceanNeighbors = 0;
                if (k1 == 0) {
                    oceanNeighbors++;
                }
                if (l1 == 0) {
                    oceanNeighbors++;
                }
                if (i2 == 0) {
                    oceanNeighbors++;
                }
                if (j2 == 0) {
                    oceanNeighbors++;
                }
                if (k2 == 0 && oceanNeighbors > 3) {
                    area[j1 + i1 * sizeX] = Biome.DEEP_OCEAN.legacyId();
                } else {
                    area[j1 + i1 * sizeX] = k2;
                }
            }
        }
        return area;
    }
}
