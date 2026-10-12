package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.IntCache;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.gen.chunk.OverworldGeneratorOptions;

public class BiomeInitLayer extends Layer {
    private Biome[] warmBiomes = new Biome[]{Biome.DESERT, Biome.DESERT, Biome.DESERT, Biome.SAVANNA, Biome.SAVANNA, Biome.PLAINS};
    private Biome[] temperateBiomes = new Biome[]{Biome.FOREST, Biome.ROOFED_FOREST, Biome.EXTREME_HILLS, Biome.PLAINS, Biome.BIRCH_FOREST, Biome.SWAMPLAND};
    private Biome[] coolBiomes = new Biome[]{Biome.FOREST, Biome.EXTREME_HILLS, Biome.TAIGA, Biome.PLAINS};
    private Biome[] snowyBiomes = new Biome[]{Biome.ICE_PLAINS, Biome.ICE_PLAINS, Biome.ICE_PLAINS, Biome.COLD_TAIGA};
    private final OverworldGeneratorOptions options;

    public BiomeInitLayer(long seed, Layer parent, WorldGeneratorType generatorType, String generatorOptions) {
        super(seed);
        this.parent = parent;
        if (generatorType == WorldGeneratorType.DEFAULT_1_1) {
            this.warmBiomes = new Biome[]{Biome.DESERT, Biome.FOREST, Biome.EXTREME_HILLS, Biome.SWAMPLAND, Biome.PLAINS, Biome.TAIGA};
            this.options = null;
        } else if (generatorType == WorldGeneratorType.CUSTOMIZED) {
            this.options = OverworldGeneratorOptions.Builder.fromJson(generatorOptions).build();
        } else {
            this.options = null;
        }
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int[] aint = this.parent.getArea(x, z, sizeX, sizeZ);
        int[] aint1 = IntCache.push(sizeX * sizeZ);

        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                this.setChunkSeed(j + x, i + z);
                int k = aint[j + i * sizeX];
                int l = (k & 3840) >> 8;
                k &= -3841;
                if (this.options != null && this.options.fixedBiome >= 0) {
                    aint1[j + i * sizeX] = this.options.fixedBiome;
                } else if (isOcean(k)) {
                    aint1[j + i * sizeX] = k;
                } else if (k == Biome.MUSHROOM_ISLAND.id) {
                    aint1[j + i * sizeX] = k;
                } else if (k == 1) {
                    if (l > 0) {
                        if (this.nextInt(3) == 0) {
                            aint1[j + i * sizeX] = Biome.MESA_PLATEAU.id;
                        } else {
                            aint1[j + i * sizeX] = Biome.MESA_PLATEAU_F.id;
                        }
                    } else {
                        aint1[j + i * sizeX] = this.warmBiomes[this.nextInt(this.warmBiomes.length)].id;
                    }
                } else if (k == 2) {
                    if (l > 0) {
                        aint1[j + i * sizeX] = Biome.JUNGLE.id;
                    } else {
                        aint1[j + i * sizeX] = this.temperateBiomes[this.nextInt(this.temperateBiomes.length)].id;
                    }
                } else if (k == 3) {
                    if (l > 0) {
                        aint1[j + i * sizeX] = Biome.MEGA_TAIGA.id;
                    } else {
                        aint1[j + i * sizeX] = this.coolBiomes[this.nextInt(this.coolBiomes.length)].id;
                    }
                } else if (k == 4) {
                    aint1[j + i * sizeX] = this.snowyBiomes[this.nextInt(this.snowyBiomes.length)].id;
                } else {
                    aint1[j + i * sizeX] = Biome.MUSHROOM_ISLAND.id;
                }
            }
        }

        return aint1;
    }
}
