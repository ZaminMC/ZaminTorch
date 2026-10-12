package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.IntCache;

public class OceanRiverMixerLayer extends Layer {
    private Layer biomeLayer;
    private Layer riverLayer;

    public OceanRiverMixerLayer(long seed, Layer biomeLayer, Layer riverLayer) {
        super(seed);
        this.biomeLayer = biomeLayer;
        this.riverLayer = riverLayer;
    }

    @Override
    public void setLocalWorldSeed(long worldSeed) {
        this.biomeLayer.setLocalWorldSeed(worldSeed);
        this.riverLayer.setLocalWorldSeed(worldSeed);
        super.setLocalWorldSeed(worldSeed);
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int[] aint = this.biomeLayer.getArea(x, z, sizeX, sizeZ);
        int[] aint1 = this.riverLayer.getArea(x, z, sizeX, sizeZ);
        int[] aint2 = IntCache.push(sizeX * sizeZ);

        for (int i = 0; i < sizeX * sizeZ; i++) {
            if (aint[i] == Biome.OCEAN.id || aint[i] == Biome.DEEP_OCEAN.id) {
                aint2[i] = aint[i];
            } else if (aint1[i] == Biome.RIVER.id) {
                if (aint[i] == Biome.ICE_PLAINS.id) {
                    aint2[i] = Biome.FROZEN_RIVER.id;
                } else if (aint[i] != Biome.MUSHROOM_ISLAND.id && aint[i] != Biome.MUSHROOM_ISLAND_SHORE.id) {
                    aint2[i] = aint1[i] & 0xFF;
                } else {
                    aint2[i] = Biome.MUSHROOM_ISLAND_SHORE.id;
                }
            } else {
                aint2[i] = aint[i];
            }
        }

        return aint2;
    }
}
