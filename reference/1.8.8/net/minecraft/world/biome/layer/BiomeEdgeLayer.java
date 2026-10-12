package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.IntCache;

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
                if (!this.checkEdge(aint, aint1, j, i, sizeX, k, Biome.EXTREME_HILLS.id, Biome.EXTREME_HILLS_EDGE.id)
                    && !this.checkEdgeStrict(aint, aint1, j, i, sizeX, k, Biome.MESA_PLATEAU_F.id, Biome.MESA.id)
                    && !this.checkEdgeStrict(aint, aint1, j, i, sizeX, k, Biome.MESA_PLATEAU.id, Biome.MESA.id)
                    && !this.checkEdgeStrict(aint, aint1, j, i, sizeX, k, Biome.MEGA_TAIGA.id, Biome.TAIGA.id)) {
                    if (k == Biome.DESERT.id) {
                        int l = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                        int i1 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                        int j1 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                        int k1 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                        if (l != Biome.ICE_PLAINS.id && i1 != Biome.ICE_PLAINS.id && j1 != Biome.ICE_PLAINS.id && k1 != Biome.ICE_PLAINS.id) {
                            aint1[j + i * sizeX] = k;
                        } else {
                            aint1[j + i * sizeX] = Biome.EXTREME_HILLS_PLUS.id;
                        }
                    } else if (k == Biome.SWAMPLAND.id) {
                        int l1 = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                        int i2 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                        int j2 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                        int k2 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                        if (l1 == Biome.DESERT.id
                            || i2 == Biome.DESERT.id
                            || j2 == Biome.DESERT.id
                            || k2 == Biome.DESERT.id
                            || l1 == Biome.COLD_TAIGA.id
                            || i2 == Biome.COLD_TAIGA.id
                            || j2 == Biome.COLD_TAIGA.id
                            || k2 == Biome.COLD_TAIGA.id
                            || l1 == Biome.ICE_PLAINS.id
                            || i2 == Biome.ICE_PLAINS.id
                            || j2 == Biome.ICE_PLAINS.id
                            || k2 == Biome.ICE_PLAINS.id) {
                            aint1[j + i * sizeX] = Biome.PLAINS.id;
                        } else if (l1 != Biome.JUNGLE.id && k2 != Biome.JUNGLE.id && i2 != Biome.JUNGLE.id && j2 != Biome.JUNGLE.id) {
                            aint1[j + i * sizeX] = k;
                        } else {
                            aint1[j + i * sizeX] = Biome.JUNGLE_EDGE.id;
                        }
                    } else {
                        aint1[j + i * sizeX] = k;
                    }
                }
            }
        }

        return aint1;
    }

    private boolean checkEdge(int[] area, int[] values, int x, int y, int length, int width, int biome, int edge) {
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

    private boolean checkEdgeStrict(int[] area, int[] values, int x, int y, int length, int width, int biome, int edge) {
        if (width != biome) {
            return false;
        }

        int i = area[x + 1 + (y + 1 - 1) * (length + 2)];
        int j = area[x + 1 + 1 + (y + 1) * (length + 2)];
        int k = area[x + 1 - 1 + (y + 1) * (length + 2)];
        int l = area[x + 1 + (y + 1 + 1) * (length + 2)];
        if (isSame(i, biome) && isSame(j, biome) && isSame(k, biome) && isSame(l, biome)) {
            values[x + y * length] = width;
        } else {
            values[x + y * length] = edge;
        }

        return true;
    }

    private boolean isValidTemperatureEdge(int biome, int edge) {
        if (isSame(biome, edge)) {
            return true;
        } else {
            Biome biomex = Biome.byId(biome);
            Biome biome1 = Biome.byId(edge);
            if (biomex != null && biome1 != null) {
                Biome.TemperatureCategory biome$temperaturecategory = biomex.getTemperatureCategory();
                Biome.TemperatureCategory biome$temperaturecategory1 = biome1.getTemperatureCategory();
                return biome$temperaturecategory == biome$temperaturecategory1
                    || biome$temperaturecategory == Biome.TemperatureCategory.MEDIUM
                    || biome$temperaturecategory1 == Biome.TemperatureCategory.MEDIUM;
            } else {
                return false;
            }
        }
    }
}
