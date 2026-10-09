package net.minecraft.world.biome.layer;

import java.util.concurrent.Callable;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.gen.chunk.OverworldGeneratorOptions;

public abstract class Layer {
    private long localWorldSeed;
    protected Layer parent;
    private long chunkSeed;
    protected long seed;

    public static Layer[] init(long seed, WorldGeneratorType generatorType, String generatorOptions) {
        Layer layer = new IslandLayer(1L);
        layer = new FuzzyZoomLayer(2000L, layer);
        layer = new AddIslandLayer(1L, layer);
        layer = new NormalZoomLayer(2001L, layer);
        layer = new AddIslandLayer(2L, layer);
        layer = new AddIslandLayer(50L, layer);
        layer = new AddIslandLayer(70L, layer);
        layer = new RemoveTooMuchOceanLayer(2L, layer);
        layer = new AddSnowLayer(2L, layer);
        layer = new AddIslandLayer(3L, layer);
        layer = new AddEdgeLayer(2L, layer, AddEdgeLayer.Mode.COOL_WARM);
        layer = new AddEdgeLayer(2L, layer, AddEdgeLayer.Mode.HEAT_ICE);
        layer = new AddEdgeLayer(3L, layer, AddEdgeLayer.Mode.SPECIAL);
        layer = new NormalZoomLayer(2002L, layer);
        layer = new NormalZoomLayer(2003L, layer);
        layer = new AddIslandLayer(4L, layer);
        layer = new AddMushroomIslandLayer(5L, layer);
        layer = new AddDeepOceanLayer(4L, layer);
        layer = NormalZoomLayer.zoom(1000L, layer, 0);
        OverworldGeneratorOptions overworldgeneratoroptions = null;
        int i = 4;
        int j = i;
        if (generatorType == WorldGeneratorType.CUSTOMIZED && generatorOptions.length() > 0) {
            overworldgeneratoroptions = OverworldGeneratorOptions.Builder.fromJson(generatorOptions).build();
            i = overworldgeneratoroptions.biomeSize;
            j = overworldgeneratoroptions.riverSize;
        }

        if (generatorType == WorldGeneratorType.LARGE_BIOMES) {
            i = 6;
        }

        Layer layer1 = layer;
        layer1 = NormalZoomLayer.zoom(1000L, layer1, 0);
        layer1 = new RiverInitLayer(100L, layer1);
        Layer layer2 = layer;
        layer2 = new BiomeInitLayer(200L, layer2, generatorType, generatorOptions);
        layer2 = NormalZoomLayer.zoom(1000L, layer2, 2);
        layer2 = new BiomeEdgeLayer(1000L, layer2);
        Layer layer3 = layer1;
        layer3 = NormalZoomLayer.zoom(1000L, layer3, 2);
        layer2 = new RegionHillsLayer(1000L, layer2, layer3);
        layer1 = NormalZoomLayer.zoom(1000L, layer1, 2);
        layer1 = NormalZoomLayer.zoom(1000L, layer1, j);
        layer1 = new RiverLayer(1L, layer1);
        layer1 = new SmoothLayer(1000L, layer1);
        layer2 = new AddSunflowerPlainsLayer(1001L, layer2);

        for (int k = 0; k < i; k++) {
            layer2 = new NormalZoomLayer(1000 + k, layer2);
            if (k == 0) {
                layer2 = new AddIslandLayer(3L, layer2);
            }

            if (k == 1 || i == 1) {
                layer2 = new ShoreLayer(1000L, layer2);
            }
        }

        layer2 = new SmoothLayer(1000L, layer2);
        layer2 = new OceanRiverMixerLayer(100L, layer2, layer1);
        Layer layer5 = layer2;
        Layer layer4 = new VoronoiZoomLayer(10L, layer2);
        layer2.setLocalWorldSeed(seed);
        layer4.setLocalWorldSeed(seed);
        return new Layer[]{layer2, layer4, layer5};
    }

    public Layer(long seed) {
        this.seed = seed;
        this.seed = this.seed * (this.seed * 6364136223846793005L + 1442695040888963407L);
        this.seed += seed;
        this.seed = this.seed * (this.seed * 6364136223846793005L + 1442695040888963407L);
        this.seed += seed;
        this.seed = this.seed * (this.seed * 6364136223846793005L + 1442695040888963407L);
        this.seed += seed;
    }

    public void setLocalWorldSeed(long worldSeed) {
        this.localWorldSeed = worldSeed;
        if (this.parent != null) {
            this.parent.setLocalWorldSeed(worldSeed);
        }

        this.localWorldSeed = this.localWorldSeed * (this.localWorldSeed * 6364136223846793005L + 1442695040888963407L);
        this.localWorldSeed = this.localWorldSeed + this.seed;
        this.localWorldSeed = this.localWorldSeed * (this.localWorldSeed * 6364136223846793005L + 1442695040888963407L);
        this.localWorldSeed = this.localWorldSeed + this.seed;
        this.localWorldSeed = this.localWorldSeed * (this.localWorldSeed * 6364136223846793005L + 1442695040888963407L);
        this.localWorldSeed = this.localWorldSeed + this.seed;
    }

    public void setChunkSeed(long chunkX, long chunkZ) {
        this.chunkSeed = this.localWorldSeed;
        this.chunkSeed = this.chunkSeed * (this.chunkSeed * 6364136223846793005L + 1442695040888963407L);
        this.chunkSeed += chunkX;
        this.chunkSeed = this.chunkSeed * (this.chunkSeed * 6364136223846793005L + 1442695040888963407L);
        this.chunkSeed += chunkZ;
        this.chunkSeed = this.chunkSeed * (this.chunkSeed * 6364136223846793005L + 1442695040888963407L);
        this.chunkSeed += chunkX;
        this.chunkSeed = this.chunkSeed * (this.chunkSeed * 6364136223846793005L + 1442695040888963407L);
        this.chunkSeed += chunkZ;
    }

    protected int nextInt(int bound) {
        int i = (int)((this.chunkSeed >> 24) % bound);
        if (i < 0) {
            i += bound;
        }

        this.chunkSeed = this.chunkSeed * (this.chunkSeed * 6364136223846793005L + 1442695040888963407L);
        this.chunkSeed = this.chunkSeed + this.localWorldSeed;
        return i;
    }

    public abstract int[] getArea(int x, int z, int sizeX, int sizeZ);

    protected static boolean isSame(int biome1, int biome2) {
        if (biome1 == biome2) {
            return true;
        }

        if (biome1 != Biome.MESA_PLATEAU_F.id && biome1 != Biome.MESA_PLATEAU.id) {
            final Biome biome = Biome.byId(biome1);
            final Biome biome1x = Biome.byId(biome2);

            try {
                return biome != null && biome1x != null && biome.is(biome1x);
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Comparing biomes");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Biomes being compared");
                crashreportcategory.add("Biome A ID", biome1);
                crashreportcategory.add("Biome B ID", biome2);
                crashreportcategory.add("Biome A", new Callable<String>() {
                    public String call() throws Exception {
                        return String.valueOf(biome);
                    }
                });
                crashreportcategory.add("Biome B", new Callable<String>() {
                    public String call() throws Exception {
                        return String.valueOf(biome1);
                    }
                });
                throw new CrashException(crashreport);
            }
        } else {
            return biome2 == Biome.MESA_PLATEAU_F.id || biome2 == Biome.MESA_PLATEAU.id;
        }
    }

    protected static boolean isOcean(int biome) {
        return biome == Biome.OCEAN.id || biome == Biome.DEEP_OCEAN.id || biome == Biome.FROZEN_OCEAN.id;
    }

    protected int pickInt(int... ints) {
        return ints[this.nextInt(ints.length)];
    }

    /**
     * Returns the mode of these numbers (the most frequently occuring value) or a random
     * number from this set.
     */
    protected int getModeOrRandom(int i1, int i2, int i3, int i4) {
        if (i2 == i3 && i3 == i4) {
            return i2;
        } else if (i1 == i2 && i1 == i3) {
            return i1;
        } else if (i1 == i2 && i1 == i4) {
            return i1;
        } else if (i1 == i3 && i1 == i4) {
            return i1;
        } else if (i1 == i2 && i3 != i4) {
            return i1;
        } else if (i1 == i3 && i2 != i4) {
            return i1;
        } else if (i1 == i4 && i2 != i3) {
            return i1;
        } else if (i2 == i3 && i1 != i4) {
            return i2;
        } else if (i2 == i4 && i1 != i3) {
            return i2;
        } else {
            return i3 == i4 && i1 != i2 ? i3 : this.pickInt(i1, i2, i3, i4);
        }
    }
}
