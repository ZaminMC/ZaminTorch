package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.IntCache;

public class AddSnowLayer extends Layer {
    public AddSnowLayer(long seed, Layer parent) {
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
                int k1 = aint[j1 + 1 + (i1 + 1) * k];
                this.setChunkSeed(j1 + x, i1 + z);
                if (k1 == 0) {
                    aint1[j1 + i1 * sizeX] = 0;
                } else {
                    int l1 = this.nextInt(6);
                    byte b0;
                    if (l1 == 0) {
                        b0 = 4;
                    } else if (l1 <= 1) {
                        b0 = 3;
                    } else {
                        b0 = 1;
                    }

                    aint1[j1 + i1 * sizeX] = b0;
                }
            }
        }

        return aint1;
    }
}
