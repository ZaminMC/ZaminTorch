package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.IntCache;

public class RiverLayer extends Layer {
    public RiverLayer(long seed, Layer parent) {
        super(seed);
        super.parent = parent;
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
                int k1 = this.makeCurves(aint[j1 + 0 + (i1 + 1) * k]);
                int l1 = this.makeCurves(aint[j1 + 2 + (i1 + 1) * k]);
                int i2 = this.makeCurves(aint[j1 + 1 + (i1 + 0) * k]);
                int j2 = this.makeCurves(aint[j1 + 1 + (i1 + 2) * k]);
                int k2 = this.makeCurves(aint[j1 + 1 + (i1 + 1) * k]);
                if (k2 == k1 && k2 == i2 && k2 == l1 && k2 == j2) {
                    aint1[j1 + i1 * sizeX] = -1;
                } else {
                    aint1[j1 + i1 * sizeX] = Biome.RIVER.id;
                }
            }
        }

        return aint1;
    }

    private int makeCurves(int value) {
        return value >= 2 ? 2 + (value & 1) : value;
    }
}
