package net.zaminmc.torch.server.world.layer;

import net.zaminmc.torch.server.world.Biome;

/**
 * The hills and the mutations (RegionHillsLayer 1:1): the river arm's
 * (marker - 2) % 29 curve bands pick the 1-in-29 mutation cells (+128
 * where a mutated row exists), the 1-in-3 hills roll walks the
 * biome-pair table (the desert/forest/birch/taiga/mega/cold hills, the
 * roofed forest's plains patch, the plains' forest pair, the ice
 * mountains, the jungle hills, the ocean's deep band, the extreme
 * hills' plus, the savanna's plateau, the mesa plateau's mesa), and the
 * changed cell lands only when at least three cross neighbors are the
 * same family (the j2 census).
 */
public class RegionHillsLayer extends Layer {

    private final Layer riverLayer;

    public RegionHillsLayer(long seed, Layer parent, Layer riverLayer) {
        super(seed);
        this.parent = parent;
        this.riverLayer = riverLayer;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int[] aint = this.parent.getArea(x - 1, z - 1, sizeX + 2, sizeZ + 2);
        int[] aint1 = this.riverLayer.getArea(x - 1, z - 1, sizeX + 2,
                sizeZ + 2);
        int[] aint2 = IntCache.push(sizeX * sizeZ);
        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                this.setChunkSeed(j + x, i + z);
                int k = aint[j + 1 + (i + 1) * (sizeX + 2)];
                int l = aint1[j + 1 + (i + 1) * (sizeX + 2)];
                boolean flag = (l - 2) % 29 == 0;
                if (k != 0 && l >= 2 && (l - 2) % 29 == 1 && k < 128) {
                    if (Biome.byLegacyId(k + 128) != null) {
                        aint2[j + i * sizeX] = k + 128;
                    } else {
                        aint2[j + i * sizeX] = k;
                    }
                } else if (this.nextInt(3) != 0 && !flag) {
                    aint2[j + i * sizeX] = k;
                } else {
                    int i1 = k;
                    if (k == Biome.DESERT.legacyId()) {
                        i1 = Biome.DESERT_HILLS.legacyId();
                    } else if (k == Biome.FOREST.legacyId()) {
                        i1 = Biome.FOREST_HILLS.legacyId();
                    } else if (k == Biome.BIRCH_FOREST.legacyId()) {
                        i1 = Biome.BIRCH_FOREST_HILLS.legacyId();
                    } else if (k == Biome.ROOFED_FOREST.legacyId()) {
                        i1 = Biome.PLAINS.legacyId();
                    } else if (k == Biome.TAIGA.legacyId()) {
                        i1 = Biome.TAIGA_HILLS.legacyId();
                    } else if (k == Biome.MEGA_TAIGA.legacyId()) {
                        i1 = Biome.MEGA_TAIGA_HILLS.legacyId();
                    } else if (k == Biome.COLD_TAIGA.legacyId()) {
                        i1 = Biome.COLD_TAIGA_HILLS.legacyId();
                    } else if (k == Biome.PLAINS.legacyId()) {
                        if (this.nextInt(3) == 0) {
                            i1 = Biome.FOREST_HILLS.legacyId();
                        } else {
                            i1 = Biome.FOREST.legacyId();
                        }
                    } else if (k == Biome.ICE_PLAINS.legacyId()) {
                        i1 = Biome.ICE_MOUNTAINS.legacyId();
                    } else if (k == Biome.JUNGLE.legacyId()) {
                        i1 = Biome.JUNGLE_HILLS.legacyId();
                    } else if (k == Biome.OCEAN.legacyId()) {
                        i1 = Biome.DEEP_OCEAN.legacyId();
                    } else if (k == Biome.EXTREME_HILLS.legacyId()) {
                        i1 = Biome.EXTREME_HILLS_PLUS.legacyId();
                    } else if (k == Biome.SAVANNA.legacyId()) {
                        i1 = Biome.SAVANNA_PLATEAU.legacyId();
                    } else if (isSame(k, Biome.MESA_PLATEAU_F.legacyId())) {
                        i1 = Biome.MESA.legacyId();
                    } else if (k == Biome.DEEP_OCEAN.legacyId()
                            && this.nextInt(3) == 0) {
                        int j1 = this.nextInt(2);
                        if (j1 == 0) {
                            i1 = Biome.PLAINS.legacyId();
                        } else {
                            i1 = Biome.FOREST.legacyId();
                        }
                    }

                    if (flag && i1 != k) {
                        if (Biome.byLegacyId(i1 + 128) != null) {
                            i1 += 128;
                        } else {
                            i1 = k;
                        }
                    }

                    if (i1 == k) {
                        aint2[j + i * sizeX] = k;
                    } else {
                        int k2 = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                        int k1 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                        int l1 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                        int i2 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                        int j2 = 0;
                        if (isSame(k2, k)) {
                            j2++;
                        }
                        if (isSame(k1, k)) {
                            j2++;
                        }
                        if (isSame(l1, k)) {
                            j2++;
                        }
                        if (isSame(i2, k)) {
                            j2++;
                        }
                        if (j2 >= 3) {
                            aint2[j + i * sizeX] = i1;
                        } else {
                            aint2[j + i * sizeX] = k;
                        }
                    }
                }
            }
        }
        return aint2;
    }
}
