package net.zaminmc.torch.server.world.layer;

import net.zaminmc.torch.server.world.Biome;

/**
 * The biome-edge laws (BiomeEdgeLayer 1:1): the extreme hills keep
 * their core or take the EDGE band (the temperature-validity consult),
 * the mesa plateaus and the mega taiga take strict same-family edges,
 * the desert against an ice-plains neighbor becomes EXTREME_HILLS+, and
 * the swamp's desert/cold/ice neighbors flip it to plains while a
 * jungle neighbor draws the JUNGLE_EDGE.
 */
public class BiomeEdgeLayer extends Layer {

    public BiomeEdgeLayer(long seed, Layer parent) {
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
                if (!this.checkEdge(aint, aint1, j, i, sizeX, k,
                        Biome.EXTREME_HILLS.legacyId(),
                        Biome.EXTREME_HILLS_EDGE.legacyId())
                        && !this.checkEdgeStrict(aint, aint1, j, i, sizeX,
                                k, Biome.MESA_PLATEAU_F.legacyId(),
                                Biome.MESA.legacyId())
                        && !this.checkEdgeStrict(aint, aint1, j, i, sizeX,
                                k, Biome.MESA_PLATEAU.legacyId(),
                                Biome.MESA.legacyId())
                        && !this.checkEdgeStrict(aint, aint1, j, i, sizeX,
                                k, Biome.MEGA_TAIGA.legacyId(),
                                Biome.TAIGA.legacyId())) {
                    if (k == Biome.DESERT.legacyId()) {
                        int l = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                        int i1 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                        int j1 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                        int k1 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                        if (l != Biome.ICE_PLAINS.legacyId()
                                && i1 != Biome.ICE_PLAINS.legacyId()
                                && j1 != Biome.ICE_PLAINS.legacyId()
                                && k1 != Biome.ICE_PLAINS.legacyId()) {
                            aint1[j + i * sizeX] = k;
                        } else {
                            aint1[j + i * sizeX] =
                                    Biome.EXTREME_HILLS_PLUS.legacyId();
                        }
                    } else if (k == Biome.SWAMPLAND.legacyId()) {
                        int l1 = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                        int i2 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                        int j2 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                        int k2 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                        if (l1 == Biome.DESERT.legacyId()
                                || i2 == Biome.DESERT.legacyId()
                                || j2 == Biome.DESERT.legacyId()
                                || k2 == Biome.DESERT.legacyId()
                                || l1 == Biome.COLD_TAIGA.legacyId()
                                || i2 == Biome.COLD_TAIGA.legacyId()
                                || j2 == Biome.COLD_TAIGA.legacyId()
                                || k2 == Biome.COLD_TAIGA.legacyId()
                                || l1 == Biome.ICE_PLAINS.legacyId()
                                || i2 == Biome.ICE_PLAINS.legacyId()
                                || j2 == Biome.ICE_PLAINS.legacyId()
                                || k2 == Biome.ICE_PLAINS.legacyId()) {
                            aint1[j + i * sizeX] = Biome.PLAINS.legacyId();
                        } else if (l1 != Biome.JUNGLE.legacyId()
                                && k2 != Biome.JUNGLE.legacyId()
                                && i2 != Biome.JUNGLE.legacyId()
                                && j2 != Biome.JUNGLE.legacyId()) {
                            aint1[j + i * sizeX] = k;
                        } else {
                            aint1[j + i * sizeX] =
                                    Biome.JUNGLE_EDGE.legacyId();
                        }
                    } else {
                        aint1[j + i * sizeX] = k;
                    }
                }
            }
        }
        return aint1;
    }

    private boolean checkEdge(int[] area, int[] values, int x, int y,
            int length, int width, int biome, int edge) {
        if (!isSame(width, biome)) {
            return false;
        }
        int i = area[x + 1 + (y + 1 - 1) * (length + 2)];
        int j = area[x + 1 + 1 + (y + 1) * (length + 2)];
        int k = area[x + 1 - 1 + (y + 1) * (length + 2)];
        int l = area[x + 1 + (y + 1 + 1) * (length + 2)];
        if (this.isValidTemperatureEdge(i, biome)
                && this.isValidTemperatureEdge(j, biome)
                && this.isValidTemperatureEdge(k, biome)
                && this.isValidTemperatureEdge(l, biome)) {
            values[x + y * length] = width;
        } else {
            values[x + y * length] = edge;
        }
        return true;
    }

    private boolean checkEdgeStrict(int[] area, int[] values, int x, int y,
            int length, int width, int biome, int edge) {
        if (width != biome) {
            return false;
        }
        int i = area[x + 1 + (y + 1 - 1) * (length + 2)];
        int j = area[x + 1 + 1 + (y + 1) * (length + 2)];
        int k = area[x + 1 - 1 + (y + 1) * (length + 2)];
        int l = area[x + 1 + (y + 1 + 1) * (length + 2)];
        if (isSame(i, biome) && isSame(j, biome) && isSame(k, biome)
                && isSame(l, biome)) {
            values[x + y * length] = width;
        } else {
            values[x + y * length] = edge;
        }
        return true;
    }

    private boolean isValidTemperatureEdge(int biome, int edge) {
        if (isSame(biome, edge)) {
            return true;
        }
        Biome biomex = Biome.byLegacyId(biome);
        Biome biome1 = Biome.byLegacyId(edge);
        if (biomex != null && biome1 != null) {
            Biome.TemperatureCategory cat = biomex.temperatureCategory();
            Biome.TemperatureCategory cat1 = biome1.temperatureCategory();
            return cat == cat1
                    || cat == Biome.TemperatureCategory.MEDIUM
                    || cat1 == Biome.TemperatureCategory.MEDIUM;
        }
        return false;
    }
}
