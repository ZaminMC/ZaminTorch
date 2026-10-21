package net.zaminmc.torch.server.world.layer;

import net.zaminmc.torch.server.world.Biome;

/**
 * The sunflower roll (AddSunflowerPlainsLayer 1:1): 1-in-57 plains
 * cells flip to the mutated SUNFLOWER_PLAINS (+128).
 */
public class AddSunflowerPlainsLayer extends Layer {

    public AddSunflowerPlainsLayer(long seed, Layer parent) {
        super(seed);
        this.parent = parent;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int[] parentArea = this.parent.getArea(x - 1, z - 1, sizeX + 2,
                sizeZ + 2);
        int[] area = IntCache.push(sizeX * sizeZ);
        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                this.setChunkSeed(j + x, i + z);
                int k = parentArea[j + 1 + (i + 1) * (sizeX + 2)];
                if (this.nextInt(57) == 0) {
                    if (k == Biome.PLAINS.legacyId()) {
                        area[j + i * sizeX] = Biome.SUNFLOWER_PLAINS.legacyId();
                    } else {
                        area[j + i * sizeX] = k;
                    }
                } else {
                    area[j + i * sizeX] = k;
                }
            }
        }
        return area;
    }
}
