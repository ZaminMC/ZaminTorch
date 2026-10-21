package net.zaminmc.torch.server.world.layer;

import net.zaminmc.torch.server.world.Biome;

/**
 * The final mix (OceanRiverMixerLayer 1:1): the river band draws over
 * the biome map — the ocean pair keeps whole, the ice plains take the
 * FROZEN_RIVER, the mushroom pair takes its SHORE, everything else
 * takes the river (the & 0xFF masking the river's own -1 no-band).
 */
public class OceanRiverMixerLayer extends Layer {

    private final Layer biomeLayer;
    private final Layer riverLayer;

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
            if (aint[i] == Biome.OCEAN.legacyId()
                    || aint[i] == Biome.DEEP_OCEAN.legacyId()) {
                aint2[i] = aint[i];
            } else if (aint1[i] == Biome.RIVER.legacyId()) {
                if (aint[i] == Biome.ICE_PLAINS.legacyId()) {
                    aint2[i] = Biome.FROZEN_RIVER.legacyId();
                } else if (aint[i] != Biome.MUSHROOM_ISLAND.legacyId()
                        && aint[i] != Biome.MUSHROOM_ISLAND_SHORE.legacyId()) {
                    aint2[i] = aint1[i] & 0xFF;
                } else {
                    aint2[i] = Biome.MUSHROOM_ISLAND_SHORE.legacyId();
                }
            } else {
                aint2[i] = aint[i];
            }
        }
        return aint2;
    }
}
