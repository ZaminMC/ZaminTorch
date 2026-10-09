package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.IntCache;

public class IslandLayer extends Layer {
    public IslandLayer(long l) {
        super(l);
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int[] aint = IntCache.push(sizeX * sizeZ);

        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                this.setChunkSeed(x + j, z + i);
                aint[j + i * sizeX] = this.nextInt(10) == 0 ? 1 : 0;
            }
        }

        if (x > -sizeX && x <= 0 && z > -sizeZ && z <= 0) {
            aint[-x + -z * sizeX] = 1;
        }

        return aint;
    }
}
