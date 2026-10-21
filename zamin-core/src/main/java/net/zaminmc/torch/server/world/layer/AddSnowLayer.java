package net.zaminmc.torch.server.world.layer;

/**
 * The temperature root (AddSnowLayer 1:1): every land cell rolls its
 * band — 1-in-6 snow (4), 2-in-6 cool (3), else temperate (1); the
 * ocean (0) passes through. The 1/3/4 codes are the BiomeInit's
 * dispatch keys (2 is the cool/warm edge blend).
 */
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
        int[] parentArea = this.parent.getArea(i, j, k, l);
        int[] area = IntCache.push(sizeX * sizeZ);
        for (int i1 = 0; i1 < sizeZ; i1++) {
            for (int j1 = 0; j1 < sizeX; j1++) {
                int k1 = parentArea[j1 + 1 + (i1 + 1) * k];
                this.setChunkSeed(j1 + x, i1 + z);
                if (k1 == 0) {
                    area[j1 + i1 * sizeX] = 0;
                } else {
                    int l1 = this.nextInt(6);
                    byte band;
                    if (l1 == 0) {
                        band = 4;
                    } else if (l1 <= 1) {
                        band = 3;
                    } else {
                        band = 1;
                    }
                    area[j1 + i1 * sizeX] = band;
                }
            }
        }
        return area;
    }
}
