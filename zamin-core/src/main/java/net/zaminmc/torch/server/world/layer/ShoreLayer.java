package net.zaminmc.torch.server.world.layer;

import net.zaminmc.torch.server.world.Biome;

/**
 * The shore laws (ShoreLayer 1:1): the mushroom isle takes its SHORE
 * against ocean neighbors; the jungle family takes JUNGLE_EDGE or
 * BEACH; the extreme hills family takes STONE_BEACH; any snowy biome
 * takes COLD_BEACH; the mesa pair takes DESERT against non-mesa
 * neighbors; everything non-ocean/river/swamp takes BEACH against
 * ocean; ocean, deep ocean, river and swamp pass whole.
 */
public class ShoreLayer extends Layer {

    public ShoreLayer(long seed, Layer parent) {
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
                Biome biome = Biome.byLegacyId(k);
                if (k == Biome.MUSHROOM_ISLAND.legacyId()) {
                    int l = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                    int i1 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                    int j1 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                    int k1 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                    if (l != Biome.OCEAN.legacyId()
                            && i1 != Biome.OCEAN.legacyId()
                            && j1 != Biome.OCEAN.legacyId()
                            && k1 != Biome.OCEAN.legacyId()) {
                        aint1[j + i * sizeX] = k;
                    } else {
                        aint1[j + i * sizeX] =
                                Biome.MUSHROOM_ISLAND_SHORE.legacyId();
                    }
                } else if (biome != null
                        && biome.family() == Biome.Family.JUNGLE) {
                    int j2 = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                    int i3 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                    int l3 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                    int k4 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                    if (!this.isJungleEdge(j2) || !this.isJungleEdge(i3)
                            || !this.isJungleEdge(l3)
                            || !this.isJungleEdge(k4)) {
                        aint1[j + i * sizeX] = Biome.JUNGLE_EDGE.legacyId();
                    } else if (!isOcean(j2) && !isOcean(i3) && !isOcean(l3)
                            && !isOcean(k4)) {
                        aint1[j + i * sizeX] = k;
                    } else {
                        aint1[j + i * sizeX] = Biome.BEACH.legacyId();
                    }
                } else if (k == Biome.EXTREME_HILLS.legacyId()
                        || k == Biome.EXTREME_HILLS_PLUS.legacyId()
                        || k == Biome.EXTREME_HILLS_EDGE.legacyId()) {
                    this.createShore(aint, aint1, j, i, sizeX, k,
                            Biome.STONE_BEACH.legacyId());
                } else if (biome != null && biome.snowy()) {
                    this.createShore(aint, aint1, j, i, sizeX, k,
                            Biome.COLD_BEACH.legacyId());
                } else if (k == Biome.MESA.legacyId()
                        || k == Biome.MESA_PLATEAU_F.legacyId()) {
                    int i2 = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                    int l2 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                    int k3 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                    int j4 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                    if (isOcean(i2) || isOcean(l2) || isOcean(k3)
                            || isOcean(j4)) {
                        aint1[j + i * sizeX] = k;
                    } else if (this.isDesert(i2) && this.isDesert(l2)
                            && this.isDesert(k3) && this.isDesert(j4)) {
                        aint1[j + i * sizeX] = k;
                    } else {
                        aint1[j + i * sizeX] = Biome.DESERT.legacyId();
                    }
                } else if (k != Biome.OCEAN.legacyId()
                        && k != Biome.DEEP_OCEAN.legacyId()
                        && k != Biome.RIVER.legacyId()
                        && k != Biome.SWAMPLAND.legacyId()) {
                    int l1 = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                    int k2 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                    int j3 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                    int i4 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                    if (!isOcean(l1) && !isOcean(k2) && !isOcean(j3)
                            && !isOcean(i4)) {
                        aint1[j + i * sizeX] = k;
                    } else {
                        aint1[j + i * sizeX] = Biome.BEACH.legacyId();
                    }
                } else {
                    aint1[j + i * sizeX] = k;
                }
            }
        }
        return aint1;
    }

    private void createShore(int[] biome1, int[] biome2, int x, int z,
            int width, int length, int id) {
        if (isOcean(length)) {
            biome2[x + z * width] = length;
        } else {
            int i = biome1[x + 1 + (z + 1 - 1) * (width + 2)];
            int j = biome1[x + 1 + 1 + (z + 1) * (width + 2)];
            int k = biome1[x + 1 - 1 + (z + 1) * (width + 2)];
            int l = biome1[x + 1 + (z + 1 + 1) * (width + 2)];
            if (!isOcean(i) && !isOcean(j) && !isOcean(k) && !isOcean(l)) {
                biome2[x + z * width] = length;
            } else {
                biome2[x + z * width] = id;
            }
        }
    }

    private boolean isJungleEdge(int id) {
        Biome biome = Biome.byLegacyId(id);
        return (biome != null && biome.family() == Biome.Family.JUNGLE)
                || id == Biome.JUNGLE_EDGE.legacyId()
                || id == Biome.JUNGLE.legacyId()
                || id == Biome.JUNGLE_HILLS.legacyId()
                || id == Biome.FOREST.legacyId()
                || id == Biome.TAIGA.legacyId()
                || isOcean(id);
    }

    private boolean isDesert(int id) {
        Biome biome = Biome.byLegacyId(id);
        return biome != null && biome.family() == Biome.Family.MESA;
    }
}
