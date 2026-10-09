package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.IntCache;

public class VoronoiZoomLayer extends Layer {
    public VoronoiZoomLayer(long seed, Layer parent) {
        super(seed);
        super.parent = parent;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        x -= 2;
        z -= 2;
        int i = x >> 2;
        int j = z >> 2;
        int k = (sizeX >> 2) + 2;
        int l = (sizeZ >> 2) + 2;
        int[] aint = this.parent.getArea(i, j, k, l);
        int i1 = k - 1 << 2;
        int j1 = l - 1 << 2;
        int[] aint1 = IntCache.push(i1 * j1);

        for (int k1 = 0; k1 < l - 1; k1++) {
            int l1 = 0;
            int i2 = aint[l1 + 0 + (k1 + 0) * k];
            int j2 = aint[l1 + 0 + (k1 + 1) * k];

            while (l1 < k - 1) {
                double d0 = 3.6;
                this.setChunkSeed(l1 + i << 2, k1 + j << 2);
                double d1 = (this.nextInt(1024) / 1024.0 - 0.5) * 3.6;
                double d2 = (this.nextInt(1024) / 1024.0 - 0.5) * 3.6;
                this.setChunkSeed(l1 + i + 1 << 2, k1 + j << 2);
                double d3 = (this.nextInt(1024) / 1024.0 - 0.5) * 3.6 + 4.0;
                double d4 = (this.nextInt(1024) / 1024.0 - 0.5) * 3.6;
                this.setChunkSeed(l1 + i << 2, k1 + j + 1 << 2);
                double d5 = (this.nextInt(1024) / 1024.0 - 0.5) * 3.6;
                double d6 = (this.nextInt(1024) / 1024.0 - 0.5) * 3.6 + 4.0;
                this.setChunkSeed(l1 + i + 1 << 2, k1 + j + 1 << 2);
                double d7 = (this.nextInt(1024) / 1024.0 - 0.5) * 3.6 + 4.0;
                double d8 = (this.nextInt(1024) / 1024.0 - 0.5) * 3.6 + 4.0;
                int k2 = aint[l1 + 1 + (k1 + 0) * k] & 0xFF;
                int l2 = aint[l1 + 1 + (k1 + 1) * k] & 0xFF;

                for (int i3 = 0; i3 < 4; i3++) {
                    int j3 = ((k1 << 2) + i3) * i1 + (l1 << 2);

                    for (int k3 = 0; k3 < 4; k3++) {
                        double d9 = (i3 - d2) * (i3 - d2) + (k3 - d1) * (k3 - d1);
                        double d10 = (i3 - d4) * (i3 - d4) + (k3 - d3) * (k3 - d3);
                        double d11 = (i3 - d6) * (i3 - d6) + (k3 - d5) * (k3 - d5);
                        double d12 = (i3 - d8) * (i3 - d8) + (k3 - d7) * (k3 - d7);
                        if (d9 < d10 && d9 < d11 && d9 < d12) {
                            aint1[j3++] = i2;
                        } else if (d10 < d9 && d10 < d11 && d10 < d12) {
                            aint1[j3++] = k2;
                        } else if (d11 < d9 && d11 < d10 && d11 < d12) {
                            aint1[j3++] = j2;
                        } else {
                            aint1[j3++] = l2;
                        }
                    }
                }

                i2 = k2;
                j2 = l2;
                l1++;
            }
        }

        int[] aint2 = IntCache.push(sizeX * sizeZ);

        for (int l3 = 0; l3 < sizeZ; l3++) {
            System.arraycopy(aint1, (l3 + (z & 3)) * i1 + (x & 3), aint2, l3 * sizeX, sizeX);
        }

        return aint2;
    }
}
