package net.zaminmc.torch.server.world.layer;

/**
 * The island ladder (AddIslandLayer 1:1): the land/ocean erosion pass —
 * a land cell surrounded by ocean keeps or erodes (the 1-in-5, the
 * snow-band pin at 4), an ocean cell inside land picks a neighbor or
 * stays (the growing-odds ladder and the 1-in-3 fill).
 */
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
        int[] parentArea = this.parent.getArea(i, j, k, l);
        int[] area = IntCache.push(sizeX * sizeZ);
        for (int i1 = 0; i1 < sizeZ; i1++) {
            for (int j1 = 0; j1 < sizeX; j1++) {
                int k1 = parentArea[j1 + 0 + (i1 + 0) * k];
                int l1 = parentArea[j1 + 2 + (i1 + 0) * k];
                int i2 = parentArea[j1 + 0 + (i1 + 2) * k];
                int j2 = parentArea[j1 + 2 + (i1 + 2) * k];
                int k2 = parentArea[j1 + 1 + (i1 + 1) * k];
                this.setChunkSeed(j1 + x, i1 + z);
                if (k2 != 0 || k1 == 0 && l1 == 0 && i2 == 0 && j2 == 0) {
                    if (k2 > 0 && (k1 == 0 || l1 == 0 || i2 == 0 || j2 == 0)) {
                        if (this.nextInt(5) == 0) {
                            if (k2 == 4) {
                                area[j1 + i1 * sizeX] = 4;
                            } else {
                                area[j1 + i1 * sizeX] = 0;
                            }
                        } else {
                            area[j1 + i1 * sizeX] = k2;
                        }
                    } else {
                        area[j1 + i1 * sizeX] = k2;
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
                        area[j1 + i1 * sizeX] = i3;
                    } else if (i3 == 4) {
                        area[j1 + i1 * sizeX] = 4;
                    } else {
                        area[j1 + i1 * sizeX] = 0;
                    }
                }
            }
        }
        return area;
    }
}
