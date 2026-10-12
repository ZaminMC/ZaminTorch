package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.IntCache;
import net.minecraft.world.biome.JungleBiome;
import net.minecraft.world.biome.MesaBiome;

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
                Biome biome = Biome.byId(k);
                if (k == Biome.MUSHROOM_ISLAND.id) {
                    int l = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                    int i1 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                    int j1 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                    int k1 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                    if (l != Biome.OCEAN.id && i1 != Biome.OCEAN.id && j1 != Biome.OCEAN.id && k1 != Biome.OCEAN.id) {
                        aint1[j + i * sizeX] = k;
                    } else {
                        aint1[j + i * sizeX] = Biome.MUSHROOM_ISLAND_SHORE.id;
                    }
                } else if (biome != null && biome.getType() == JungleBiome.class) {
                    int j2 = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                    int i3 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                    int l3 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                    int k4 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                    if (!this.isJungleEdge(j2) || !this.isJungleEdge(i3) || !this.isJungleEdge(l3) || !this.isJungleEdge(k4)) {
                        aint1[j + i * sizeX] = Biome.JUNGLE_EDGE.id;
                    } else if (!isOcean(j2) && !isOcean(i3) && !isOcean(l3) && !isOcean(k4)) {
                        aint1[j + i * sizeX] = k;
                    } else {
                        aint1[j + i * sizeX] = Biome.BEACH.id;
                    }
                } else if (k == Biome.EXTREME_HILLS.id || k == Biome.EXTREME_HILLS_PLUS.id || k == Biome.EXTREME_HILLS_EDGE.id) {
                    this.createShore(aint, aint1, j, i, sizeX, k, Biome.STONE_BEACH.id);
                } else if (biome != null && biome.isCold()) {
                    this.createShore(aint, aint1, j, i, sizeX, k, Biome.COLD_BEACH.id);
                } else if (k == Biome.MESA.id || k == Biome.MESA_PLATEAU_F.id) {
                    int i2 = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                    int l2 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                    int k3 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                    int j4 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                    if (isOcean(i2) || isOcean(l2) || isOcean(k3) || isOcean(j4)) {
                        aint1[j + i * sizeX] = k;
                    } else if (this.isDesert(i2) && this.isDesert(l2) && this.isDesert(k3) && this.isDesert(j4)) {
                        aint1[j + i * sizeX] = k;
                    } else {
                        aint1[j + i * sizeX] = Biome.DESERT.id;
                    }
                } else if (k != Biome.OCEAN.id && k != Biome.DEEP_OCEAN.id && k != Biome.RIVER.id && k != Biome.SWAMPLAND.id) {
                    int l1 = aint[j + 1 + (i + 1 - 1) * (sizeX + 2)];
                    int k2 = aint[j + 1 + 1 + (i + 1) * (sizeX + 2)];
                    int j3 = aint[j + 1 - 1 + (i + 1) * (sizeX + 2)];
                    int i4 = aint[j + 1 + (i + 1 + 1) * (sizeX + 2)];
                    if (!isOcean(l1) && !isOcean(k2) && !isOcean(j3) && !isOcean(i4)) {
                        aint1[j + i * sizeX] = k;
                    } else {
                        aint1[j + i * sizeX] = Biome.BEACH.id;
                    }
                } else {
                    aint1[j + i * sizeX] = k;
                }
            }
        }

        return aint1;
    }

    private void createShore(int[] biome1, int[] biome2, int x, int z, int width, int length, int id) {
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
        return Biome.byId(id) != null && Biome.byId(id).getType() == JungleBiome.class
            || id == Biome.JUNGLE_EDGE.id
            || id == Biome.JUNGLE.id
            || id == Biome.JUNGLE_HILLS.id
            || id == Biome.FOREST.id
            || id == Biome.TAIGA.id
            || isOcean(id);
    }

    private boolean isDesert(int id) {
        return Biome.byId(id) instanceof MesaBiome;
    }
}
