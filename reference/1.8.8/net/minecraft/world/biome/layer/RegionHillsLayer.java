package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.IntCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RegionHillsLayer extends Layer {
    private static final Logger LOGGER = LogManager.getLogger();
    private Layer riverLayer;

    public RegionHillsLayer(long seed, Layer parent, Layer riverLayer) {
        super(seed);
        this.parent = parent;
        this.riverLayer = riverLayer;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int[] aint = this.parent.getArea(x - 1, z - 1, sizeX + 2, sizeZ + 2);
        int[] aint1 = this.riverLayer.getArea(x - 1, z - 1, sizeX + 2, sizeZ + 2);
        int[] aint2 = IntCache.push(sizeX * sizeZ);

        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                this.setChunkSeed(j + x, i + z);
                int k = aint[j + 1 + (i + 1) * (sizeX + 2)];
                int l = aint1[j + 1 + (i + 1) * (sizeX + 2)];
                boolean flag = (l - 2) % 29 == 0;
                if (k > 255) {
                    LOGGER.debug("old! " + k);
                }

                if (k != 0 && l >= 2 && (l - 2) % 29 == 1 && k < 128) {
                    if (Biome.byId(k + 128) != null) {
                        aint2[j + i * sizeX] = k + 128;
                    } else {
                        aint2[j + i * sizeX] = k;
                    }
                } else if (this.nextInt(3) != 0 && !flag) {
                    aint2[j + i * sizeX] = k;
                } else {
                    int i1 = k;
                    if (k == Biome.DESERT.id) {
                        i1 = Biome.DESERT_HILLS.id;
                    } else if (k == Biome.FOREST.id) {
                        i1 = Biome.FOREST_HILLS.id;
                    } else if (k == Biome.BIRCH_FOREST.id) {
                        i1 = Biome.BIRCH_FOREST_HILLS.id;
                    } else if (k == Biome.ROOFED_FOREST.id) {
                        i1 = Biome.PLAINS.id;
                    } else if (k == Biome.TAIGA.id) {
                        i1 = Biome.TAIGA_HILLS.id;
                    } else if (k == Biome.MEGA_TAIGA.id) {
                        i1 = Biome.MEGA_TAIGA_HILLS.id;
                    } else if (k == Biome.COLD_TAIGA.id) {
                        i1 = Biome.COLD_TAIGA_HILLS.id;
                    } else if (k == Biome.PLAINS.id) {
                        if (this.nextInt(3) == 0) {
                            i1 = Biome.FOREST_HILLS.id;
                        } else {
                            i1 = Biome.FOREST.id;
                        }
                    } else if (k == Biome.ICE_PLAINS.id) {
                        i1 = Biome.ICE_MOUNTAINS.id;
                    } else if (k == Biome.JUNGLE.id) {
                        i1 = Biome.JUNGLE_HILLS.id;
                    } else if (k == Biome.OCEAN.id) {
                        i1 = Biome.DEEP_OCEAN.id;
                    } else if (k == Biome.EXTREME_HILLS.id) {
                        i1 = Biome.EXTREME_HILLS_PLUS.id;
                    } else if (k == Biome.SAVANNA.id) {
                        i1 = Biome.SAVANNA_PLATEAU.id;
                    } else if (isSame(k, Biome.MESA_PLATEAU_F.id)) {
                        i1 = Biome.MESA.id;
                    } else if (k == Biome.DEEP_OCEAN.id && this.nextInt(3) == 0) {
                        int j1 = this.nextInt(2);
                        if (j1 == 0) {
                            i1 = Biome.PLAINS.id;
                        } else {
                            i1 = Biome.FOREST.id;
                        }
                    }

                    if (flag && i1 != k) {
                        if (Biome.byId(i1 + 128) != null) {
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
