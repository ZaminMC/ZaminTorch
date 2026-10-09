package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.IntCache;

public class AddIslandLayer extends Layer {
    public AddIslandLayer(long seed, Layer parent) {
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
                int k1 = aint[j1 + 0 + (i1 + 0) * k];
                int l1 = aint[j1 + 2 + (i1 + 0) * k];
                int i2 = aint[j1 + 0 + (i1 + 2) * k];
                int j2 = aint[j1 + 2 + (i1 + 2) * k];
                int k2 = aint[j1 + 1 + (i1 + 1) * k];
                this.setChunkSeed(j1 + x, i1 + z);
                if (k2 != 0 || k1 == 0 && l1 == 0 && i2 == 0 && j2 == 0) {
                    if (k2 > 0 && (k1 == 0 || l1 == 0 || i2 == 0 || j2 == 0)) {
                        if (this.nextInt(5) == 0) {
                            if (k2 == 4) {
                                aint1[j1 + i1 * sizeX] = 4;
                            } else {
                                aint1[j1 + i1 * sizeX] = 0;
                            }
                        } else {
                            aint1[j1 + i1 * sizeX] = k2;
                        }
                    } else {
                        aint1[j1 + i1 * sizeX] = k2;
                    }
                } else {
                    int l2 = 1;
                    int i3 = 1;
                    if (k1 != 0 && this.nextInt(l2++) == 0) {
                        i3 = k1;
                    }

                    if (l1 != 0 && this.nextInt(l2++) == 0) {
                        i3 = l1;
                    }

                    if (i2 != 0 && this.nextInt(l2++) == 0) {
                        i3 = i2;
                    }

                    if (j2 != 0 && this.nextInt(l2++) == 0) {
                        i3 = j2;
                    }

                    if (this.nextInt(3) == 0) {
                        aint1[j1 + i1 * sizeX] = i3;
                    } else if (i3 == 4) {
                        aint1[j1 + i1 * sizeX] = 4;
                    } else {
                        aint1[j1 + i1 * sizeX] = 0;
                    }
                }
            }
        }

        return aint1;
    }
}
