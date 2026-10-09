package net.minecraft.world.biome.source;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

public class FixedBiomeSource extends BiomeSource {
    private Biome biome;
    private float downfall;

    public FixedBiomeSource(Biome biome, float downfall) {
        this.biome = biome;
        this.downfall = downfall;
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return this.biome;
    }

    @Override
    public Biome[] getNoiseBiomes(Biome[] noiseBiomes, int x, int z, int sizeX, int sizeZ) {
        if (noiseBiomes == null || noiseBiomes.length < sizeX * sizeZ) {
            noiseBiomes = new Biome[sizeX * sizeZ];
        }

        Arrays.fill(noiseBiomes, 0, sizeX * sizeZ, this.biome);
        return noiseBiomes;
    }

    @Override
    public float[] getDownfalls(float[] downfalls, int x, int z, int sizeX, int sizeZ) {
        if (downfalls == null || downfalls.length < sizeX * sizeZ) {
            downfalls = new float[sizeX * sizeZ];
        }

        Arrays.fill(downfalls, 0, sizeX * sizeZ, this.downfall);
        return downfalls;
    }

    @Override
    public Biome[] getBiomes(Biome[] biomes, int x, int z, int sizeX, int sizeZ) {
        if (biomes == null || biomes.length < sizeX * sizeZ) {
            biomes = new Biome[sizeX * sizeZ];
        }

        Arrays.fill(biomes, 0, sizeX * sizeZ, this.biome);
        return biomes;
    }

    @Override
    public Biome[] getBiomes(Biome[] biomes, int x, int z, int sizeX, int sizeZ, boolean useCache) {
        return this.getBiomes(biomes, x, z, sizeX, sizeZ);
    }

    @Override
    public BlockPos findBiome(int x, int z, int range, List<Biome> biomes, Random random) {
        return biomes.contains(this.biome) ? new BlockPos(x - range + random.nextInt(range * 2 + 1), 0, z - range + random.nextInt(range * 2 + 1)) : null;
    }

    @Override
    public boolean isBiomeWithin(int x, int z, int range, List<Biome> biomes) {
        return biomes.contains(this.biome);
    }
}
