package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.IntCache;

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
        int[] aint = this.parent.getArea(i, j, k, l);
        int[] aint1 = IntCache.push(sizeX * sizeZ);

        for (int i1 = 0; i1 < sizeZ; i1++) {
            for (int j1 = 0; j1 < sizeX; j1++) {
                int k1 = aint[j1 + 1 + (i1 + 1 - 1) * (sizeX + 2)];
                int l1 = aint[j1 + 1 + 1 + (i1 + 1) * (sizeX + 2)];
                int i2 = aint[j1 + 1 - 1 + (i1 + 1) * (sizeX + 2)];
                int j2 = aint[j1 + 1 + (i1 + 1 + 1) * (sizeX + 2)];
                int k2 = aint[j1 + 1 + (i1 + 1) * k];
                int l2 = 0;
                if (k1 == 0) {
                    l2++;
                }

                if (l1 == 0) {
                    l2++;
                }

                if (i2 == 0) {
                    l2++;
                }

                if (j2 == 0) {
                    l2++;
                }

                if (k2 == 0 && l2 > 3) {
                    aint1[j1 + i1 * sizeX] = Biome.DEEP_OCEAN.id;
                } else {
                    aint1[j1 + i1 * sizeX] = k2;
                }
            }
        }

        return aint1;
    }
}
