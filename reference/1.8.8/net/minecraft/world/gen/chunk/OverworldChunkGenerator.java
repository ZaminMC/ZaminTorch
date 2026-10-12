package net.minecraft.world.gen.chunk;

import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.NaturalSpawner;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.Generator;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.gen.carver.CaveWorldCarver;
import net.minecraft.world.gen.carver.RavineWorldCarver;
import net.minecraft.world.gen.feature.DungeonFeature;
import net.minecraft.world.gen.feature.LakeFeature;
import net.minecraft.world.gen.noise.PerlinNoise;
import net.minecraft.world.gen.noise.PerlinSimplexNoise;
import net.minecraft.world.gen.structure.MineshaftStructure;
import net.minecraft.world.gen.structure.OceanMonumentStructure;
import net.minecraft.world.gen.structure.StrongholdStructure;
import net.minecraft.world.gen.structure.TempleStructure;
import net.minecraft.world.gen.structure.VillageStructure;

public class OverworldChunkGenerator implements ChunkSource {
    private Random random;
    private PerlinNoise minLimitPerlinNoise;
    private PerlinNoise maxLimitPerlinNoise;
    private PerlinNoise perlinNoise1;
    private PerlinSimplexNoise perlinNoise3;
    public PerlinNoise scaleNoise;
    public PerlinNoise depthNoise;
    public PerlinNoise forestNoise;
    private World world;
    private final boolean placeStructures;
    private WorldGeneratorType type;
    private final double[] heightMap;
    private final float[] biomeWeights;
    private OverworldGeneratorOptions options;
    private Block defaultLiquid = Blocks.WATER;
    private double[] depthBuffer = new double[256];
    private Generator cave = new CaveWorldCarver();
    private StrongholdStructure stronghold = new StrongholdStructure();
    private VillageStructure village = new VillageStructure();
    private MineshaftStructure mineshaft = new MineshaftStructure();
    private TempleStructure witchHut = new TempleStructure();
    private Generator ravine = new RavineWorldCarver();
    private OceanMonumentStructure oceanMonument = new OceanMonumentStructure();
    private Biome[] biomes;
    double[] perlinNoiseBuffer;
    double[] minLimitPerlinNoiseBuffer;
    double[] maxLimitPerlinNoiseBuffer;
    double[] depthNoiseBuffer;

    public OverworldChunkGenerator(World world, long seed, boolean placeStructures, String generatorOptions) {
        this.world = world;
        this.placeStructures = placeStructures;
        this.type = world.getData().getGeneratorType();
        this.random = new Random(seed);
        this.minLimitPerlinNoise = new PerlinNoise(this.random, 16);
        this.maxLimitPerlinNoise = new PerlinNoise(this.random, 16);
        this.perlinNoise1 = new PerlinNoise(this.random, 8);
        this.perlinNoise3 = new PerlinSimplexNoise(this.random, 4);
        this.scaleNoise = new PerlinNoise(this.random, 10);
        this.depthNoise = new PerlinNoise(this.random, 16);
        this.forestNoise = new PerlinNoise(this.random, 8);
        this.heightMap = new double[825];
        this.biomeWeights = new float[25];

        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                float f = 10.0F / MathHelper.sqrt(i * i + j * j + 0.2F);
                this.biomeWeights[i + 2 + (j + 2) * 5] = f;
            }
        }

        if (generatorOptions != null) {
            this.options = OverworldGeneratorOptions.Builder.fromJson(generatorOptions).build();
            this.defaultLiquid = this.options.useLavaOceans ? Blocks.LAVA : Blocks.WATER;
            world.setSeaLevel(this.options.seaLevel);
        }
    }

    /**
     * Generates the heightmap and places the basic terrain shape of continents and oceans.
     */
    public void buildTerrain(int chunkX, int chunkZ, BlockStateStorage blocks) {
        this.biomes = this.world.getBiomeSource().getNoiseBiomes(this.biomes, chunkX * 4 - 2, chunkZ * 4 - 2, 10, 10);
        this.generateHeightMap(chunkX * 4, 0, chunkZ * 4);

        for (int i = 0; i < 4; i++) {
            int j = i * 5;
            int k = (i + 1) * 5;

            for (int l = 0; l < 4; l++) {
                int i1 = (j + l) * 33;
                int j1 = (j + l + 1) * 33;
                int k1 = (k + l) * 33;
                int l1 = (k + l + 1) * 33;

                for (int i2 = 0; i2 < 32; i2++) {
                    double d0 = 0.125;
                    double d1 = this.heightMap[i1 + i2];
                    double d2 = this.heightMap[j1 + i2];
                    double d3 = this.heightMap[k1 + i2];
                    double d4 = this.heightMap[l1 + i2];
                    double d5 = (this.heightMap[i1 + i2 + 1] - d1) * d0;
                    double d6 = (this.heightMap[j1 + i2 + 1] - d2) * d0;
                    double d7 = (this.heightMap[k1 + i2 + 1] - d3) * d0;
                    double d8 = (this.heightMap[l1 + i2 + 1] - d4) * d0;

                    for (int j2 = 0; j2 < 8; j2++) {
                        double d9 = 0.25;
                        double d10 = d1;
                        double d11 = d2;
                        double d12 = (d3 - d1) * d9;
                        double d13 = (d4 - d2) * d9;

                        for (int k2 = 0; k2 < 4; k2++) {
                            double d14 = 0.25;
                            double d15 = d10;
                            double d16 = (d11 - d10) * d14;
                            d15 -= d16;

                            for (int l2 = 0; l2 < 4; l2++) {
                                if ((d15 += d16) > 0.0) {
                                    blocks.set(i * 4 + k2, i2 * 8 + j2, l * 4 + l2, Blocks.STONE.defaultState());
                                } else if (i2 * 8 + j2 < this.options.seaLevel) {
                                    blocks.set(i * 4 + k2, i2 * 8 + j2, l * 4 + l2, this.defaultLiquid.defaultState());
                                }
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
     * Places the bedrock layer, biome-dependent surface and subsurface blocks, and liquid bodies.
     */
    public void buildSurfaces(int chunkX, int chunkZ, BlockStateStorage blocks, Biome[] biomes) {
        double d0 = 0.03125;
        this.depthBuffer = this.perlinNoise3.getRegion(this.depthBuffer, chunkX * 16, chunkZ * 16, 16, 16, d0 * 2.0, d0 * 2.0, 1.0);

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                Biome biome = biomes[j + i * 16];
                biome.prepareAndBuildSurfaces(this.world, this.random, blocks, chunkX * 16 + i, chunkZ * 16 + j, this.depthBuffer[j + i * 16]);
            }
        }
    }

    @Override
    public WorldChunk getChunk(int chunkX, int chunkZ) {
        this.random.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L);
        BlockStateStorage blockstatestorage = new BlockStateStorage();
        this.buildTerrain(chunkX, chunkZ, blockstatestorage);
        this.biomes = this.world.getBiomeSource().getBiomes(this.biomes, chunkX * 16, chunkZ * 16, 16, 16);
        this.buildSurfaces(chunkX, chunkZ, blockstatestorage, this.biomes);
        if (this.options.useCaves) {
            this.cave.place(this, this.world, chunkX, chunkZ, blockstatestorage);
        }

        if (this.options.useRavines) {
            this.ravine.place(this, this.world, chunkX, chunkZ, blockstatestorage);
        }

        if (this.options.useMineshafts && this.placeStructures) {
            this.mineshaft.place(this, this.world, chunkX, chunkZ, blockstatestorage);
        }

        if (this.options.useVillages && this.placeStructures) {
            this.village.place(this, this.world, chunkX, chunkZ, blockstatestorage);
        }

        if (this.options.useStrongholds && this.placeStructures) {
            this.stronghold.place(this, this.world, chunkX, chunkZ, blockstatestorage);
        }

        if (this.options.useTemples && this.placeStructures) {
            this.witchHut.place(this, this.world, chunkX, chunkZ, blockstatestorage);
        }

        if (this.options.useMonuments && this.placeStructures) {
            this.oceanMonument.place(this, this.world, chunkX, chunkZ, blockstatestorage);
        }

        WorldChunk worldchunk = new WorldChunk(this.world, blockstatestorage, chunkX, chunkZ);
        byte[] abyte = worldchunk.getBiomes();

        for (int i = 0; i < abyte.length; i++) {
            abyte[i] = (byte)this.biomes[i].id;
        }

        worldchunk.populateHeightMap();
        return worldchunk;
    }

    private void generateHeightMap(int x, int y, int z) {
        this.depthNoiseBuffer = this.depthNoise
            .getRegion(this.depthNoiseBuffer, x, z, 5, 5, this.options.depthNoiseScaleX, this.options.depthNoiseScaleZ, this.options.depthNoiseScaleExponent);
        float f = this.options.coordinateScale;
        float f1 = this.options.heightScale;
        this.perlinNoiseBuffer = this.perlinNoise1
            .getRegion(
                this.perlinNoiseBuffer,
                x,
                y,
                z,
                5,
                33,
                5,
                f / this.options.mainNoiseScaleX,
                f1 / this.options.mainNoiseScaleY,
                f / this.options.mainNoiseScaleZ
            );
        this.minLimitPerlinNoiseBuffer = this.minLimitPerlinNoise.getRegion(this.minLimitPerlinNoiseBuffer, x, y, z, 5, 33, 5, f, f1, f);
        this.maxLimitPerlinNoiseBuffer = this.maxLimitPerlinNoise.getRegion(this.maxLimitPerlinNoiseBuffer, x, y, z, 5, 33, 5, f, f1, f);
        int flag1 = false;
        int flag = false;
        int i = 0;
        int j = 0;

        for (int k = 0; k < 5; k++) {
            for (int l = 0; l < 5; l++) {
                float f2 = 0.0F;
                float f3 = 0.0F;
                float f4 = 0.0F;
                int i1 = 2;
                Biome biome = this.biomes[k + 2 + (l + 2) * 10];

                for (int j1 = -i1; j1 <= i1; j1++) {
                    for (int k1 = -i1; k1 <= i1; k1++) {
                        Biome biome1 = this.biomes[k + j1 + 2 + (l + k1 + 2) * 10];
                        float f5 = this.options.biomeDepthOffset + biome1.baseHeight * this.options.biomeDepthWeight;
                        float f6 = this.options.biomeScaleOffset + biome1.heightVariation * this.options.biomeScaleWeight;
                        if (this.type == WorldGeneratorType.AMPLIFIED && f5 > 0.0F) {
                            f5 = 1.0F + f5 * 2.0F;
                            f6 = 1.0F + f6 * 4.0F;
                        }

                        float f7 = this.biomeWeights[j1 + 2 + (k1 + 2) * 5] / (f5 + 2.0F);
                        if (biome1.baseHeight > biome.baseHeight) {
                            f7 /= 2.0F;
                        }

                        f2 += f6 * f7;
                        f3 += f5 * f7;
                        f4 += f7;
                    }
                }

                f2 /= f4;
                f3 /= f4;
                f2 = f2 * 0.9F + 0.1F;
                f3 = (f3 * 4.0F - 1.0F) / 8.0F;
                double d7 = this.depthNoiseBuffer[j] / 8000.0;
                if (d7 < 0.0) {
                    d7 = -d7 * 0.3;
                }

                d7 = d7 * 3.0 - 2.0;
                if (d7 < 0.0) {
                    d7 /= 2.0;
                    if (d7 < -1.0) {
                        d7 = -1.0;
                    }

                    d7 /= 1.4;
                    d7 /= 2.0;
                } else {
                    if (d7 > 1.0) {
                        d7 = 1.0;
                    }

                    d7 /= 8.0;
                }

                j++;
                double d8 = f3;
                double d9 = f2;
                d8 += d7 * 0.2;
                d8 = d8 * this.options.baseSize / 8.0;
                double d0 = this.options.baseSize + d8 * 4.0;

                for (int l1 = 0; l1 < 33; l1++) {
                    double d1 = (l1 - d0) * this.options.stretchY * 128.0 / 256.0 / d9;
                    if (d1 < 0.0) {
                        d1 *= 4.0;
                    }

                    double d2 = this.minLimitPerlinNoiseBuffer[i] / this.options.lowerLimitScale;
                    double d3 = this.maxLimitPerlinNoiseBuffer[i] / this.options.upperLimitScale;
                    double d4 = (this.perlinNoiseBuffer[i] / 10.0 + 1.0) / 2.0;
                    double d5 = MathHelper.clampedLerp(d2, d3, d4) - d1;
                    if (l1 > 29) {
                        double d6 = (l1 - 29) / 3.0F;
                        d5 = d5 * (1.0 - d6) + -10.0 * d6;
                    }

                    this.heightMap[i] = d5;
                    i++;
                }
            }
        }
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public void populateChunk(ChunkSource source, int chunkX, int chunkZ) {
        FallingBlock.fallImmediately = true;
        int i = chunkX * 16;
        int j = chunkZ * 16;
        BlockPos blockpos = new BlockPos(i, 0, j);
        Biome biome = this.world.getBiome(blockpos.add(16, 0, 16));
        this.random.setSeed(this.world.getSeed());
        long k = this.random.nextLong() / 2L * 2L + 1L;
        long l = this.random.nextLong() / 2L * 2L + 1L;
        this.random.setSeed(chunkX * k + chunkZ * l ^ this.world.getSeed());
        boolean flag = false;
        ChunkPos chunkpos = new ChunkPos(chunkX, chunkZ);
        if (this.options.useMineshafts && this.placeStructures) {
            this.mineshaft.place(this.world, this.random, chunkpos);
        }

        if (this.options.useVillages && this.placeStructures) {
            flag = this.village.place(this.world, this.random, chunkpos);
        }

        if (this.options.useStrongholds && this.placeStructures) {
            this.stronghold.place(this.world, this.random, chunkpos);
        }

        if (this.options.useTemples && this.placeStructures) {
            this.witchHut.place(this.world, this.random, chunkpos);
        }

        if (this.options.useMonuments && this.placeStructures) {
            this.oceanMonument.place(this.world, this.random, chunkpos);
        }

        if (biome != Biome.DESERT
            && biome != Biome.DESERT_HILLS
            && this.options.useWaterLakes
            && !flag
            && this.random.nextInt(this.options.waterLakeChance) == 0) {
            int i1 = this.random.nextInt(16) + 8;
            int j1 = this.random.nextInt(256);
            int k1 = this.random.nextInt(16) + 8;
            new LakeFeature(Blocks.WATER).place(this.world, this.random, blockpos.add(i1, j1, k1));
        }

        if (!flag && this.random.nextInt(this.options.lavaLakeChance / 10) == 0 && this.options.useLavaLakes) {
            int i2 = this.random.nextInt(16) + 8;
            int l2 = this.random.nextInt(this.random.nextInt(248) + 8);
            int k3 = this.random.nextInt(16) + 8;
            if (l2 < this.world.getSeaLevel() || this.random.nextInt(this.options.lavaLakeChance / 8) == 0) {
                new LakeFeature(Blocks.LAVA).place(this.world, this.random, blockpos.add(i2, l2, k3));
            }
        }

        if (this.options.useDungeons) {
            for (int j2 = 0; j2 < this.options.dungeonChance; j2++) {
                int i3 = this.random.nextInt(16) + 8;
                int l3 = this.random.nextInt(256);
                int l1 = this.random.nextInt(16) + 8;
                new DungeonFeature().place(this.world, this.random, blockpos.add(i3, l3, l1));
            }
        }

        biome.decorate(this.world, this.random, new BlockPos(i, 0, j));
        NaturalSpawner.populateChunk(this.world, biome, i + 8, j + 8, 16, 16, this.random);
        blockpos = blockpos.add(8, 0, 8);

        for (int k2 = 0; k2 < 16; k2++) {
            for (int j3 = 0; j3 < 16; j3++) {
                BlockPos blockpos1 = this.world.getPrecipitationHeight(blockpos.add(k2, 0, j3));
                BlockPos blockpos2 = blockpos1.down();
                if (this.world.canFreeze(blockpos2)) {
                    this.world.setBlockState(blockpos2, Blocks.ICE.defaultState(), 2);
                }

                if (this.world.canSnowFall(blockpos1, true)) {
                    this.world.setBlockState(blockpos1, Blocks.SNOW_LAYER.defaultState(), 2);
                }
            }
        }

        FallingBlock.fallImmediately = false;
    }

    @Override
    public boolean populateChunkAfterTerrain(ChunkSource source, WorldChunk chunk, int chunkX, int chunkZ) {
        boolean flag = false;
        if (this.options.useMonuments && this.placeStructures && chunk.getInhabitedTime() < 3600L) {
            flag |= this.oceanMonument.place(this.world, this.random, new ChunkPos(chunkX, chunkZ));
        }

        return flag;
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
        Biome biome = this.world.getBiome(pos);
        if (this.placeStructures) {
            if (category == MobCategory.MONSTER && this.witchHut.isWitchHut(pos)) {
                return this.witchHut.getSpawnEntries();
            }

            if (category == MobCategory.MONSTER && this.options.useMonuments && this.oceanMonument.isInsideBounds(this.world, pos)) {
                return this.oceanMonument.getSpawnEntries();
            }
        }

        return biome.getSpawnEntries(category);
    }

    @Override
    public BlockPos findNearestStructure(World world, String type, BlockPos pos) {
        return "Stronghold".equals(type) && this.stronghold != null ? this.stronghold.findNearestPosition(world, pos) : null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void placeStructures(WorldChunk chunk, int chunkX, int chunkZ) {
        if (this.options.useMineshafts && this.placeStructures) {
            this.mineshaft.place(this, this.world, chunkX, chunkZ, null);
        }

        if (this.options.useVillages && this.placeStructures) {
            this.village.place(this, this.world, chunkX, chunkZ, null);
        }

        if (this.options.useStrongholds && this.placeStructures) {
            this.stronghold.place(this, this.world, chunkX, chunkZ, null);
        }

        if (this.options.useTemples && this.placeStructures) {
            this.witchHut.place(this, this.world, chunkX, chunkZ, null);
        }

        if (this.options.useMonuments && this.placeStructures) {
            this.oceanMonument.place(this, this.world, chunkX, chunkZ, null);
        }
    }

    @Override
    public WorldChunk getChunk(BlockPos pos) {
        return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
