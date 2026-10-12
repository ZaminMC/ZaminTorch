package net.minecraft.world.biome;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Long2ObjectHashMap;
import net.minecraft.world.biome.source.BiomeSource;

public class BiomeCache {
    private final BiomeSource source;
    private long timeOfLastCleanUp;
    private Long2ObjectHashMap<BiomeCache.Entry> entriesByChunkPos = new Long2ObjectHashMap<>();
    private List<BiomeCache.Entry> entries = Lists.newArrayList();

    public BiomeCache(BiomeSource source) {
        this.source = source;
    }

    public BiomeCache.Entry getEntry(int x, int z) {
        x >>= 4;
        z >>= 4;
        long i = x & 4294967295L | (z & 4294967295L) << 32;
        BiomeCache.Entry biomecache$entry = this.entriesByChunkPos.get(i);
        if (biomecache$entry == null) {
            biomecache$entry = new BiomeCache.Entry(x, z);
            this.entriesByChunkPos.put(i, biomecache$entry);
            this.entries.add(biomecache$entry);
        }

        biomecache$entry.timeOfCreation = MinecraftServer.getTimeMillis();
        return biomecache$entry;
    }

    public Biome getBiome(int x, int z, Biome defaultValue) {
        Biome biome = this.getEntry(x, z).getBiome(x, z);
        return biome == null ? defaultValue : biome;
    }

    public void tick() {
        long i = MinecraftServer.getTimeMillis();
        long j = i - this.timeOfLastCleanUp;
        if (j > 7500L || j < 0L) {
            this.timeOfLastCleanUp = i;

            for (int k = 0; k < this.entries.size(); k++) {
                BiomeCache.Entry biomecache$entry = this.entries.get(k);
                long l = i - biomecache$entry.timeOfCreation;
                if (l > 30000L || l < 0L) {
                    this.entries.remove(k--);
                    long i1 = biomecache$entry.chunkX & 4294967295L | (biomecache$entry.chunkZ & 4294967295L) << 32;
                    this.entriesByChunkPos.remove(i1);
                }
            }
        }
    }

    public Biome[] getBiomes(int x, int z) {
        return this.getEntry(x, z).biomes;
    }

    public class Entry {
        public float[] downfalls = new float[256];
        public Biome[] biomes = new Biome[256];
        public int chunkX;
        public int chunkZ;
        public long timeOfCreation;

        public Entry(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            BiomeCache.this.source.getDownfalls(this.downfalls, chunkX << 4, chunkZ << 4, 16, 16);
            BiomeCache.this.source.getBiomes(this.biomes, chunkX << 4, chunkZ << 4, 16, 16, false);
        }

        public Biome getBiome(int x, int z) {
            return this.biomes[x & 15 | (z & 15) << 4];
        }
    }
}
