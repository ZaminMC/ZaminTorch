package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.IntCache;

public class NormalZoomLayer extends Layer {
    public NormalZoomLayer(long seed, Layer parent) {
        super(seed);
        super.parent = parent;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int i = x >> 1;
        int j = z >> 1;
        int k = (sizeX >> 1) + 2;
        int l = (sizeZ >> 1) + 2;
        int[] aint = this.parent.getArea(i, j, k, l);
        int i1 = k - 1 << 1;
        int j1 = l - 1 << 1;
        int[] aint1 = IntCache.push(i1 * j1);

        for (int k1 = 0; k1 < l - 1; k1++) {
            int l1 = (k1 << 1) * i1;
            int i2 = 0;
            int j2 = aint[i2 + 0 + (k1 + 0) * k];
            int k2 = aint[i2 + 0 + (k1 + 1) * k];

            while (i2 < k - 1) {
                this.setChunkSeed(i2 + i << 1, k1 + j << 1);
                int l2 = aint[i2 + 1 + (k1 + 0) * k];
                int i3 = aint[i2 + 1 + (k1 + 1) * k];
                aint1[l1] = j2;
                aint1[l1++ + i1] = this.pickInt(j2, k2);
                aint1[l1] = this.pickInt(j2, l2);
                aint1[l1++ + i1] = this.getModeOrRandom(j2, l2, k2, i3);
                j2 = l2;
                k2 = i3;
                i2++;
            }
        }

        int[] aint2 = IntCache.push(sizeX * sizeZ);

        for (int j3 = 0; j3 < sizeZ; j3++) {
            System.arraycopy(aint1, (j3 + (z & 1)) * i1 + (x & 1), aint2, j3 * sizeX, sizeX);
        }

        return aint2;
    }

    public static Layer zoom(long seed, Layer layer, int magnification) {
        Layer layerx = layer;

        for (int i = 0; i < magnification; i++) {
            layerx = new NormalZoomLayer(seed + i, layerx);
        }

        return layerx;
    }
}
