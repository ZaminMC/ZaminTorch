package net.minecraft.world.biome.source;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeCache;
import net.minecraft.world.biome.IntCache;
import net.minecraft.world.biome.layer.Layer;
import net.minecraft.world.gen.WorldGeneratorType;

public class BiomeSource {
    private Layer noiseBiomes;
    private Layer biomes;
    private BiomeCache cache = new BiomeCache(this);
    private List<Biome> biomesForSpawnPoint;
    private String generatorOptions = "";

    protected BiomeSource() {
        this.biomesForSpawnPoint = Lists.newArrayList();
        this.biomesForSpawnPoint.add(Biome.FOREST);
        this.biomesForSpawnPoint.add(Biome.PLAINS);
        this.biomesForSpawnPoint.add(Biome.TAIGA);
        this.biomesForSpawnPoint.add(Biome.TAIGA_HILLS);
        this.biomesForSpawnPoint.add(Biome.FOREST_HILLS);
        this.biomesForSpawnPoint.add(Biome.JUNGLE);
        this.biomesForSpawnPoint.add(Biome.JUNGLE_HILLS);
    }

    public BiomeSource(long seed, WorldGeneratorType generatorType, String generatorOptions) {
        this();
        this.generatorOptions = generatorOptions;
        Layer[] alayer = Layer.init(seed, generatorType, generatorOptions);
        this.noiseBiomes = alayer[0];
        this.biomes = alayer[1];
    }

    public BiomeSource(World world) {
        this(world.getSeed(), world.getData().getGeneratorType(), world.getData().getGeneratorOptions());
    }

    public List<Biome> getBiomesForSpawnPoint() {
        return this.biomesForSpawnPoint;
    }

    public Biome getBiome(BlockPos pos) {
        return this.getBiome(pos, null);
    }

    public Biome getBiome(BlockPos pos, Biome defaultValue) {
        return this.cache.getBiome(pos.getX(), pos.getZ(), defaultValue);
    }

    public float[] getDownfalls(float[] downfalls, int x, int z, int sizeX, int sizeZ) {
        IntCache.pop();
        if (downfalls == null || downfalls.length < sizeX * sizeZ) {
            downfalls = new float[sizeX * sizeZ];
        }

        int[] aint = this.biomes.getArea(x, z, sizeX, sizeZ);

        for (int i = 0; i < sizeX * sizeZ; i++) {
            try {
                float f = Biome.byId(aint[i], Biome.DEFAULT).getScaledDownfall() / 65536.0F;
                if (f > 1.0F) {
                    f = 1.0F;
                }

                downfalls[i] = f;
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Invalid Biome id");
                CrashReportCategory crashreportcategory = crashreport.addCategory("DownfallBlock");
                crashreportcategory.add("biome id", i);
                crashreportcategory.add("downfalls[] size", downfalls.length);
                crashreportcategory.add("x", x);
                crashreportcategory.add("z", z);
                crashreportcategory.add("w", sizeX);
                crashreportcategory.add("h", sizeZ);
                throw new CrashException(crashreport);
            }
        }

        return downfalls;
    }

    public float adjustTemperatureForHeight(float temperature, int height) {
        return temperature;
    }

    public Biome[] getNoiseBiomes(Biome[] noiseBiomes, int x, int z, int sizeX, int sizeZ) {
        IntCache.pop();
        if (noiseBiomes == null || noiseBiomes.length < sizeX * sizeZ) {
            noiseBiomes = new Biome[sizeX * sizeZ];
        }

        int[] aint = this.noiseBiomes.getArea(x, z, sizeX, sizeZ);

        try {
            for (int i = 0; i < sizeX * sizeZ; i++) {
                noiseBiomes[i] = Biome.byId(aint[i], Biome.DEFAULT);
            }

            return noiseBiomes;
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Invalid Biome id");
            CrashReportCategory crashreportcategory = crashreport.addCategory("RawBiomeBlock");
            crashreportcategory.add("biomes[] size", noiseBiomes.length);
            crashreportcategory.add("x", x);
            crashreportcategory.add("z", z);
            crashreportcategory.add("w", sizeX);
            crashreportcategory.add("h", sizeZ);
            throw new CrashException(crashreport);
        }
    }

    public Biome[] getBiomes(Biome[] biomes, int x, int z, int sizeX, int sizeZ) {
        return this.getBiomes(biomes, x, z, sizeX, sizeZ, true);
    }

    public Biome[] getBiomes(Biome[] biomes, int x, int z, int sizeX, int sizeZ, boolean useCache) {
        IntCache.pop();
        if (biomes == null || biomes.length < sizeX * sizeZ) {
            biomes = new Biome[sizeX * sizeZ];
        }

        if (useCache && sizeX == 16 && sizeZ == 16 && (x & 15) == 0 && (z & 15) == 0) {
            Biome[] abiome = this.cache.getBiomes(x, z);
            System.arraycopy(abiome, 0, biomes, 0, sizeX * sizeZ);
            return biomes;
        }

        int[] aint = this.biomes.getArea(x, z, sizeX, sizeZ);

        for (int i = 0; i < sizeX * sizeZ; i++) {
            biomes[i] = Biome.byId(aint[i], Biome.DEFAULT);
        }

        return biomes;
    }

    public boolean isBiomeWithin(int x, int z, int range, List<Biome> biomes) {
        IntCache.pop();
        int i = x - range >> 2;
        int j = z - range >> 2;
        int k = x + range >> 2;
        int l = z + range >> 2;
        int i1 = k - i + 1;
        int j1 = l - j + 1;
        int[] aint = this.noiseBiomes.getArea(i, j, i1, j1);

        try {
            for (int k1 = 0; k1 < i1 * j1; k1++) {
                Biome biome = Biome.byId(aint[k1]);
                if (!biomes.contains(biome)) {
                    return false;
                }
            }

            return true;
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Invalid Biome id");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Layer");
            crashreportcategory.add("Layer", this.noiseBiomes.toString());
            crashreportcategory.add("x", x);
            crashreportcategory.add("z", z);
            crashreportcategory.add("radius", range);
            crashreportcategory.add("allowed", biomes);
            throw new CrashException(crashreport);
        }
    }

    public BlockPos findBiome(int x, int z, int range, List<Biome> biomes, Random random) {
        IntCache.pop();
        int i = x - range >> 2;
        int j = z - range >> 2;
        int k = x + range >> 2;
        int l = z + range >> 2;
        int i1 = k - i + 1;
        int j1 = l - j + 1;
        int[] aint = this.noiseBiomes.getArea(i, j, i1, j1);
        BlockPos blockpos = null;
        int k1 = 0;

        for (int l1 = 0; l1 < i1 * j1; l1++) {
            int i2 = i + l1 % i1 << 2;
            int j2 = j + l1 / i1 << 2;
            Biome biome = Biome.byId(aint[l1]);
            if (biomes.contains(biome) && (blockpos == null || random.nextInt(k1 + 1) == 0)) {
                blockpos = new BlockPos(i2, 0, j2);
                k1++;
            }
        }

        return blockpos;
    }

    public void tick() {
        this.cache.tick();
    }
}
