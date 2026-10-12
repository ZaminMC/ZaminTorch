package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.IntCache;

public class RiverInitLayer extends Layer {
    public RiverInitLayer(long seed, Layer parent) {
        super(seed);
        this.parent = parent;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int[] aint = this.parent.getArea(x, z, sizeX, sizeZ);
        int[] aint1 = IntCache.push(sizeX * sizeZ);

        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                this.setChunkSeed(j + x, i + z);
                aint1[j + i * sizeX] = aint[j + i * sizeX] > 0 ? this.nextInt(299999) + 2 : 0;
            }
        }

        return aint1;
    }
}
