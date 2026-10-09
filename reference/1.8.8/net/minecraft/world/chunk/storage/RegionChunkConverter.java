package net.minecraft.world.chunk.storage;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.ChunkNibbleStorage;

public class RegionChunkConverter {
    public static RegionChunkConverter.RegionChunk loadChunk(NbtCompound nbt) {
        int i = nbt.getInt("xPos");
        int j = nbt.getInt("zPos");
        RegionChunkConverter.RegionChunk regionchunkconverter$regionchunk = new RegionChunkConverter.RegionChunk(i, j);
        regionchunkconverter$regionchunk.blocks = nbt.getByteArray("Blocks");
        regionchunkconverter$regionchunk.data = new AlphaChunkDataArray(nbt.getByteArray("Data"), 7);
        regionchunkconverter$regionchunk.skyLight = new AlphaChunkDataArray(nbt.getByteArray("SkyLight"), 7);
        regionchunkconverter$regionchunk.blockLight = new AlphaChunkDataArray(nbt.getByteArray("BlockLight"), 7);
        regionchunkconverter$regionchunk.heightMap = nbt.getByteArray("HeightMap");
        regionchunkconverter$regionchunk.terrainPopulated = nbt.getBoolean("TerrainPopulated");
        regionchunkconverter$regionchunk.entities = nbt.getList("Entities", 10);
        regionchunkconverter$regionchunk.blockEntities = nbt.getList("TileEntities", 10);
        regionchunkconverter$regionchunk.scheduledTicks = nbt.getList("TileTicks", 10);

        try {
            regionchunkconverter$regionchunk.lastUpdate = nbt.getLong("LastUpdate");
        } catch (ClassCastException classcastexception) {
            regionchunkconverter$regionchunk.lastUpdate = nbt.getInt("LastUpdate");
        }

        return regionchunkconverter$regionchunk;
    }

    public static void convertChunkToAnvil(RegionChunkConverter.RegionChunk chunk, NbtCompound nbt, BiomeSource biomeSource) {
        nbt.putInt("xPos", chunk.chunkX);
        nbt.putInt("zPos", chunk.chunkZ);
        nbt.putLong("LastUpdate", chunk.lastUpdate);
        int[] aint = new int[chunk.heightMap.length];

        for (int i = 0; i < chunk.heightMap.length; i++) {
            aint[i] = chunk.heightMap[i];
        }

        nbt.putIntArray("HeightMap", aint);
        nbt.putBoolean("TerrainPopulated", chunk.terrainPopulated);
        NbtList nbtlist = new NbtList();

        for (int j = 0; j < 8; j++) {
            boolean flag = true;

            for (int k = 0; k < 16 && flag; k++) {
                for (int l = 0; l < 16 && flag; l++) {
                    for (int i1 = 0; i1 < 16; i1++) {
                        int j1 = k << 11 | i1 << 7 | l + (j << 4);
                        int k1 = chunk.blocks[j1];
                        if (k1 != 0) {
                            flag = false;
                            break;
                        }
                    }
                }
            }

            if (!flag) {
                byte[] abyte1 = new byte[4096];
                ChunkNibbleStorage chunknibblestorage = new ChunkNibbleStorage();
                ChunkNibbleStorage chunknibblestorage1 = new ChunkNibbleStorage();
                ChunkNibbleStorage chunknibblestorage2 = new ChunkNibbleStorage();

                for (int j3 = 0; j3 < 16; j3++) {
                    for (int l1 = 0; l1 < 16; l1++) {
                        for (int i2 = 0; i2 < 16; i2++) {
                            int j2 = j3 << 11 | i2 << 7 | l1 + (j << 4);
                            int k2 = chunk.blocks[j2];
                            abyte1[l1 << 8 | i2 << 4 | j3] = (byte)(k2 & 0xFF);
                            chunknibblestorage.set(j3, l1, i2, chunk.data.get(j3, l1 + (j << 4), i2));
                            chunknibblestorage1.set(j3, l1, i2, chunk.skyLight.get(j3, l1 + (j << 4), i2));
                            chunknibblestorage2.set(j3, l1, i2, chunk.blockLight.get(j3, l1 + (j << 4), i2));
                        }
                    }
                }

                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putByte("Y", (byte)(j & 0xFF));
                nbtcompound.putByteArray("Blocks", abyte1);
                nbtcompound.putByteArray("Data", chunknibblestorage.getData());
                nbtcompound.putByteArray("SkyLight", chunknibblestorage1.getData());
                nbtcompound.putByteArray("BlockLight", chunknibblestorage2.getData());
                nbtlist.addElement(nbtcompound);
            }
        }

        nbt.put("Sections", nbtlist);
        byte[] abyte = new byte[256];
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int l2 = 0; l2 < 16; l2++) {
            for (int i3 = 0; i3 < 16; i3++) {
                blockpos$mutable.set(chunk.chunkX << 4 | l2, 0, chunk.chunkZ << 4 | i3);
                abyte[i3 << 4 | l2] = (byte)(biomeSource.getBiome(blockpos$mutable, Biome.DEFAULT).id & 0xFF);
            }
        }

        nbt.putByteArray("Biomes", abyte);
        nbt.put("Entities", chunk.entities);
        nbt.put("TileEntities", chunk.blockEntities);
        if (chunk.scheduledTicks != null) {
            nbt.put("TileTicks", chunk.scheduledTicks);
        }
    }

    public static class RegionChunk {
        public long lastUpdate;
        public boolean terrainPopulated;
        public byte[] heightMap;
        public AlphaChunkDataArray blockLight;
        public AlphaChunkDataArray skyLight;
        public AlphaChunkDataArray data;
        public byte[] blocks;
        public NbtList entities;
        public NbtList blockEntities;
        public NbtList scheduledTicks;
        public final int chunkX;
        public final int chunkZ;

        public RegionChunk(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }
}
