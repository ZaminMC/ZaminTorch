package net.minecraft.world.gen.chunk;

import java.util.List;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.noise.PerlinNoise;

public class TheEndChunkGenerator implements ChunkSource {
    private Random random;
    private PerlinNoise minLimitPerlinNoise;
    private PerlinNoise maxLimitPerlinNoise;
    private PerlinNoise perlinNoise1;
    public PerlinNoise scaleNoise;
    public PerlinNoise depthNoise;
    private World world;
    private double[] heightMap;
    private Biome[] biomes;
    double[] perlinNoiseBuffer;
    double[] minLimitPerlinNoiseBuffer;
    double[] maxLimitPerlinNoiseBuffer;
    double[] scaleNoiseBuffer;
    double[] depthNoiseBuffer;

    public TheEndChunkGenerator(World world, long seed) {
        this.world = world;
        this.random = new Random(seed);
        this.minLimitPerlinNoise = new PerlinNoise(this.random, 16);
        this.maxLimitPerlinNoise = new PerlinNoise(this.random, 16);
        this.perlinNoise1 = new PerlinNoise(this.random, 8);
        this.scaleNoise = new PerlinNoise(this.random, 10);
        this.depthNoise = new PerlinNoise(this.random, 16);
    }

    /**
     * Generates the heightmap and places the basic terrain shape.
     */
    public void buildTerrain(int chunkX, int chunkZ, BlockStateStorage blocks) {
        int i = 2;
        int j = i + 1;
        int k = 33;
        int l = i + 1;
        this.heightMap = this.generateHeightMap(this.heightMap, chunkX * i, 0, chunkZ * i, j, k, l);

        for (int i1 = 0; i1 < i; i1++) {
            for (int j1 = 0; j1 < i; j1++) {
                for (int k1 = 0; k1 < 32; k1++) {
                    double d0 = 0.25;
                    double d1 = this.heightMap[((i1 + 0) * l + j1 + 0) * k + k1 + 0];
                    double d2 = this.heightMap[((i1 + 0) * l + j1 + 1) * k + k1 + 0];
                    double d3 = this.heightMap[((i1 + 1) * l + j1 + 0) * k + k1 + 0];
                    double d4 = this.heightMap[((i1 + 1) * l + j1 + 1) * k + k1 + 0];
                    double d5 = (this.heightMap[((i1 + 0) * l + j1 + 0) * k + k1 + 1] - d1) * d0;
                    double d6 = (this.heightMap[((i1 + 0) * l + j1 + 1) * k + k1 + 1] - d2) * d0;
                    double d7 = (this.heightMap[((i1 + 1) * l + j1 + 0) * k + k1 + 1] - d3) * d0;
                    double d8 = (this.heightMap[((i1 + 1) * l + j1 + 1) * k + k1 + 1] - d4) * d0;

                    for (int l1 = 0; l1 < 4; l1++) {
                        double d9 = 0.125;
                        double d10 = d1;
                        double d11 = d2;
                        double d12 = (d3 - d1) * d9;
                        double d13 = (d4 - d2) * d9;

                        for (int i2 = 0; i2 < 8; i2++) {
                            double d14 = 0.125;
                            double d15 = d10;
                            double d16 = (d11 - d10) * d14;

                            for (int j2 = 0; j2 < 8; j2++) {
                                BlockState blockstate = null;
                                if (d15 > 0.0) {
                                    blockstate = Blocks.END_STONE.defaultState();
                                }

                                int k2 = i2 + i1 * 8;
                                int l2 = l1 + k1 * 4;
                                int i3 = j2 + j1 * 8;
                                blocks.set(k2, l2, i3, blockstate);
                                d15 += d16;
                            }

                            d10 += d12;
                            d11 += d13;
                        }

                        d1 += d5;
                        d2 += d6;
                        d3 += d7;
                        d4 += d8;
                    }
                }
            }
        }
    }

    /**
     * Places the biome-dependent surface and subsurface blocks.
     */
    public void buildSurfaces(BlockStateStorage blocks) {
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int k = 1;
                int l = -1;
                BlockState blockstate = Blocks.END_STONE.defaultState();
                BlockState blockstate1 = Blocks.END_STONE.defaultState();

                for (int i1 = 127; i1 >= 0; i1--) {
                    BlockState blockstate2 = blocks.get(i, i1, j);
                    if (blockstate2.getBlock().getMaterial() == Material.AIR) {
                        l = -1;
                    } else if (blockstate2.getBlock() == Blocks.STONE) {
                        if (l == -1) {
                            if (k <= 0) {
                                blockstate = Blocks.AIR.defaultState();
                                blockstate1 = Blocks.END_STONE.defaultState();
                            }

                            l = k;
                            if (i1 >= 0) {
                                blocks.set(i, i1, j, blockstate);
                            } else {
                                blocks.set(i, i1, j, blockstate1);
                            }
                        } else if (l > 0) {
                            l--;
                            blocks.set(i, i1, j, blockstate1);
                        }
                    }
                }
            }
        }
    }

    @Override
    public WorldChunk getChunk(int chunkX, int chunkZ) {
        this.random.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L);
        BlockStateStorage blockstatestorage = new BlockStateStorage();
        this.biomes = this.world.getBiomeSource().getBiomes(this.biomes, chunkX * 16, chunkZ * 16, 16, 16);
        this.buildTerrain(chunkX, chunkZ, blockstatestorage);
        this.buildSurfaces(blockstatestorage);
        WorldChunk worldchunk = new WorldChunk(this.world, blockstatestorage, chunkX, chunkZ);
        byte[] abyte = worldchunk.getBiomes();

        for (int i = 0; i < abyte.length; i++) {
            abyte[i] = (byte)this.biomes[i].id;
        }

        worldchunk.populateHeightMap();
        return worldchunk;
    }

    private double[] generateHeightMap(double[] heightMap, int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        if (heightMap == null) {
            heightMap = new double[sizeX * sizeY * sizeZ];
        }

        double d0 = 684.412;
        double d1 = 684.412;
        this.scaleNoiseBuffer = this.scaleNoise.getRegion(this.scaleNoiseBuffer, x, z, sizeX, sizeZ, 1.121, 1.121, 0.5);
        this.depthNoiseBuffer = this.depthNoise.getRegion(this.depthNoiseBuffer, x, z, sizeX, sizeZ, 200.0, 200.0, 0.5);
        d0 *= 2.0;
        this.perlinNoiseBuffer = this.perlinNoise1.getRegion(this.perlinNoiseBuffer, x, y, z, sizeX, sizeY, sizeZ, d0 / 80.0, d1 / 160.0, d0 / 80.0);
        this.minLimitPerlinNoiseBuffer = this.minLimitPerlinNoise.getRegion(this.minLimitPerlinNoiseBuffer, x, y, z, sizeX, sizeY, sizeZ, d0, d1, d0);
        this.maxLimitPerlinNoiseBuffer = this.maxLimitPerlinNoise.getRegion(this.maxLimitPerlinNoiseBuffer, x, y, z, sizeX, sizeY, sizeZ, d0, d1, d0);
        int i = 0;

        for (int j = 0; j < sizeX; j++) {
            for (int k = 0; k < sizeZ; k++) {
                float f = (j + x) / 1.0F;
                float f1 = (k + z) / 1.0F;
                float f2 = 100.0F - MathHelper.sqrt(f * f + f1 * f1) * 8.0F;
                if (f2 > 80.0F) {
                    f2 = 80.0F;
                }

                if (f2 < -100.0F) {
                    f2 = -100.0F;
                }

                for (int l = 0; l < sizeY; l++) {
                    double d2 = 0.0;
                    double d3 = this.minLimitPerlinNoiseBuffer[i] / 512.0;
                    double d4 = this.maxLimitPerlinNoiseBuffer[i] / 512.0;
                    double d5 = (this.perlinNoiseBuffer[i] / 10.0 + 1.0) / 2.0;
                    if (d5 < 0.0) {
                        d2 = d3;
                    } else if (d5 > 1.0) {
                        d2 = d4;
                    } else {
                        d2 = d3 + (d4 - d3) * d5;
                    }

                    d2 -= 8.0;
                    d2 += f2;
                    int i1 = 2;
                    if (l > sizeY / 2 - i1) {
                        double d6 = (l - (sizeY / 2 - i1)) / 64.0F;
                        d6 = MathHelper.clamp(d6, 0.0, 1.0);
                        d2 = d2 * (1.0 - d6) + -3000.0 * d6;
                    }

                    int b0 = 8;
                    if (l < b0) {
                        double d7 = (b0 - l) / (b0 - 1.0F);
                        d2 = d2 * (1.0 - d7) + -30.0 * d7;
                    }

                    heightMap[i] = d2;
                    i++;
                }
            }
        }

        return heightMap;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public void populateChunk(ChunkSource source, int chunkX, int chunkZ) {
        FallingBlock.fallImmediately = true;
        BlockPos blockpos = new BlockPos(chunkX * 16, 0, chunkZ * 16);
        this.world.getBiome(blockpos.add(16, 0, 16)).decorate(this.world, this.world.random, blockpos);
        FallingBlock.fallImmediately = false;
    }

    @Override
    public boolean populateChunkAfterTerrain(ChunkSource source, WorldChunk chunk, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean save(boolean saveEntities, ProgressListener listener) {
        return true;
    }

    @Override
    public void flush() {
    }

    @Override
    public boolean tick() {
        return false;
    }

    @Override
    public boolean shouldSave() {
        return true;
    }

    @Override
    public String getDebugInfo() {
        return "RandomLevelSource";
    }

    @Override
    public List<Biome.SpawnEntry> getSpawnEntries(MobCategory category, BlockPos pos) {
        return this.world.getBiome(pos).getSpawnEntries(category);
    }

    @Override
    public BlockPos findNearestStructure(World world, String type, BlockPos pos) {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void placeStructures(WorldChunk chunk, int chunkX, int chunkZ) {
    }

    @Override
    public WorldChunk getChunk(BlockPos pos) {
        return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
