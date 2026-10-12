package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.IntCache;

public class AddSunflowerPlainsLayer extends Layer {
    public AddSunflowerPlainsLayer(long seed, Layer parent) {
        super(seed);
        this.parent = parent;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int[] aint = this.parent.getArea(x - 1, z - 1, sizeX + 2, sizeZ + 2);
        int[] aint1 = IntCache.push(sizeX * sizeZ);

        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                this.setChunkSeed(j + x, i + z);
                int k = aint[j + 1 + (i + 1) * (sizeX + 2)];
                if (this.nextInt(57) == 0) {
                    if (k == Biome.PLAINS.id) {
                        aint1[j + i * sizeX] = Biome.PLAINS.id + 128;
                    } else {
                        aint1[j + i * sizeX] = k;
                    }
                } else {
                    aint1[j + i * sizeX] = k;
                }
            }
        }

        return aint1;
    }
}
