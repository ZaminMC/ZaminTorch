package net.minecraft.world.gen.chunk;

import java.util.List;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.predicate.BlockPredicate;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.Generator;
import net.minecraft.world.gen.carver.NetherCaveCarver;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FirePatchFeature;
import net.minecraft.world.gen.feature.GlowstoneClusterFeature;
import net.minecraft.world.gen.feature.LiquidPocketFeature;
import net.minecraft.world.gen.feature.PlantFeature;
import net.minecraft.world.gen.feature.UpsideDownGlowstoneClusterFeature;
import net.minecraft.world.gen.feature.VeinFeature;
import net.minecraft.world.gen.noise.PerlinNoise;
import net.minecraft.world.gen.structure.FortressStructure;

public class NetherChunkGenerator implements ChunkSource {
    private final World world;
    private final boolean placeStructures;
    private final Random random;
    private double[] sandBuffer = new double[256];
    private double[] gravelBuffer = new double[256];
    private double[] depthBuffer = new double[256];
    private double[] heightMap;
    private final PerlinNoise minLimitPerlinNoise;
    private final PerlinNoise maxLimitPerlinNoise;
    private final PerlinNoise perlinNoise1;
    private final PerlinNoise perlinNoise2;
    private final PerlinNoise perlinNoise3;
    public final PerlinNoise scaleNoise;
    public final PerlinNoise depthNoise;
    private final FirePatchFeature firePatch = new FirePatchFeature();
    private final UpsideDownGlowstoneClusterFeature upsideDownGlowstoneCluster = new UpsideDownGlowstoneClusterFeature();
    private final GlowstoneClusterFeature glowstoneCluster = new GlowstoneClusterFeature();
    private final Feature quartzOreVein = new VeinFeature(Blocks.QUARTZ_ORE.defaultState(), 14, BlockPredicate.of(Blocks.NETHERRACK));
    private final LiquidPocketFeature exposedLavaPocket = new LiquidPocketFeature(Blocks.FLOWING_LAVA, true);
    private final LiquidPocketFeature lavaPocket = new LiquidPocketFeature(Blocks.FLOWING_LAVA, false);
    private final PlantFeature brownMushroom = new PlantFeature(Blocks.BROWN_MUSHROOM);
    private final PlantFeature redMushroom = new PlantFeature(Blocks.RED_MUSHROOM);
    private final FortressStructure fortress = new FortressStructure();
    private final Generator cave = new NetherCaveCarver();
    double[] perlinNoiseBuffer;
    double[] minLimitPerlinNoiseBuffer;
    double[] maxLimitPerlinNoiseBuffer;
    double[] scaleNoiseBuffer;
    double[] depthNoiseBuffer;

    public NetherChunkGenerator(World world, boolean placeStructures, long seed) {
        this.world = world;
        this.placeStructures = placeStructures;
        this.random = new Random(seed);
        this.minLimitPerlinNoise = new PerlinNoise(this.random, 16);
        this.maxLimitPerlinNoise = new PerlinNoise(this.random, 16);
        this.perlinNoise1 = new PerlinNoise(this.random, 8);
        this.perlinNoise2 = new PerlinNoise(this.random, 4);
        this.perlinNoise3 = new PerlinNoise(this.random, 4);
        this.scaleNoise = new PerlinNoise(this.random, 10);
        this.depthNoise = new PerlinNoise(this.random, 16);
        world.setSeaLevel(63);
    }

    /**
     * Generates the heightmap and places the basic terrain shape of continents and oceans.
     */
    public void buildTerrain(int chunkX, int chunkZ, BlockStateStorage blocks) {
        int i = 4;
        int j = this.world.getSeaLevel() / 2 + 1;
        int k = i + 1;
        int l = 17;
        int i1 = i + 1;
        this.heightMap = this.generateHeightMap(this.heightMap, chunkX * i, 0, chunkZ * i, k, l, i1);

        for (int j1 = 0; j1 < i; j1++) {
            for (int k1 = 0; k1 < i; k1++) {
                for (int l1 = 0; l1 < 16; l1++) {
                    double d0 = 0.125;
                    double d1 = this.heightMap[((j1 + 0) * i1 + k1 + 0) * l + l1 + 0];
                    double d2 = this.heightMap[((j1 + 0) * i1 + k1 + 1) * l + l1 + 0];
                    double d3 = this.heightMap[((j1 + 1) * i1 + k1 + 0) * l + l1 + 0];
                    double d4 = this.heightMap[((j1 + 1) * i1 + k1 + 1) * l + l1 + 0];
                    double d5 = (this.heightMap[((j1 + 0) * i1 + k1 + 0) * l + l1 + 1] - d1) * d0;
                    double d6 = (this.heightMap[((j1 + 0) * i1 + k1 + 1) * l + l1 + 1] - d2) * d0;
                    double d7 = (this.heightMap[((j1 + 1) * i1 + k1 + 0) * l + l1 + 1] - d3) * d0;
                    double d8 = (this.heightMap[((j1 + 1) * i1 + k1 + 1) * l + l1 + 1] - d4) * d0;

                    for (int i2 = 0; i2 < 8; i2++) {
                        double d9 = 0.25;
                        double d10 = d1;
                        double d11 = d2;
                        double d12 = (d3 - d1) * d9;
                        double d13 = (d4 - d2) * d9;

                        for (int j2 = 0; j2 < 4; j2++) {
                            double d14 = 0.25;
                            double d15 = d10;
                            double d16 = (d11 - d10) * d14;

                            for (int k2 = 0; k2 < 4; k2++) {
                                BlockState blockstate = null;
                                if (l1 * 8 + i2 < j) {
                                    blockstate = Blocks.LAVA.defaultState();
                                }

                                if (d15 > 0.0) {
                                    blockstate = Blocks.NETHERRACK.defaultState();
                                }

                                int l2 = j2 + j1 * 4;
                                int i3 = i2 + l1 * 8;
                                int j3 = k2 + k1 * 4;
                                blocks.set(l2, i3, j3, blockstate);
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
     * Places the bedrock layers, biome-dependent surface and subsurface blocks, and liquid bodies.
     */
    public void buildSurfaces(int chunkX, int chunkZ, BlockStateStorage blocks) {
        int i = this.world.getSeaLevel() + 1;
        double d0 = 0.03125;
        this.sandBuffer = this.perlinNoise2.getRegion(this.sandBuffer, chunkX * 16, chunkZ * 16, 0, 16, 16, 1, d0, d0, 1.0);
        this.gravelBuffer = this.perlinNoise2.getRegion(this.gravelBuffer, chunkX * 16, 109, chunkZ * 16, 16, 1, 16, d0, 1.0, d0);
        this.depthBuffer = this.perlinNoise3.getRegion(this.depthBuffer, chunkX * 16, chunkZ * 16, 0, 16, 16, 1, d0 * 2.0, d0 * 2.0, d0 * 2.0);

        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                boolean flag = this.sandBuffer[j + k * 16] + this.random.nextDouble() * 0.2 > 0.0;
                boolean flag1 = this.gravelBuffer[j + k * 16] + this.random.nextDouble() * 0.2 > 0.0;
                int l = (int)(this.depthBuffer[j + k * 16] / 3.0 + 3.0 + this.random.nextDouble() * 0.25);
                int i1 = -1;
                BlockState blockstate = Blocks.NETHERRACK.defaultState();
                BlockState blockstate1 = Blocks.NETHERRACK.defaultState();

                for (int j1 = 127; j1 >= 0; j1--) {
                    if (j1 < 127 - this.random.nextInt(5) && j1 > this.random.nextInt(5)) {
                        BlockState blockstate2 = blocks.get(k, j1, j);
                        if (blockstate2.getBlock() == null || blockstate2.getBlock().getMaterial() == Material.AIR) {
                            i1 = -1;
                        } else if (blockstate2.getBlock() == Blocks.NETHERRACK) {
                            if (i1 == -1) {
                                if (l <= 0) {
                                    blockstate = null;
                                    blockstate1 = Blocks.NETHERRACK.defaultState();
                                } else if (j1 >= i - 4 && j1 <= i + 1) {
                                    blockstate = Blocks.NETHERRACK.defaultState();
                                    blockstate1 = Blocks.NETHERRACK.defaultState();
                                    if (flag1) {
                                        blockstate = Blocks.GRAVEL.defaultState();
                                        blockstate1 = Blocks.NETHERRACK.defaultState();
                                    }

                                    if (flag) {
                                        blockstate = Blocks.SOUL_SAND.defaultState();
                                        blockstate1 = Blocks.SOUL_SAND.defaultState();
                                    }
                                }

                                if (j1 < i && (blockstate == null || blockstate.getBlock().getMaterial() == Material.AIR)) {
                                    blockstate = Blocks.LAVA.defaultState();
                                }

                                i1 = l;
                                if (j1 >= i - 1) {
                                    blocks.set(k, j1, j, blockstate);
                                } else {
                                    blocks.set(k, j1, j, blockstate1);
                                }
                            } else if (i1 > 0) {
                                i1--;
                                blocks.set(k, j1, j, blockstate1);
                            }
                        }
                    } else {
                        blocks.set(k, j1, j, Blocks.BEDROCK.defaultState());
                    }
                }
            }
        }
    }

    @Override
    public WorldChunk getChunk(int chunkX, int chunkZ) {
        this.random.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L);
        BlockStateStorage blockstatestorage = new BlockStateStorage();
        this.buildTerrain(chunkX, chunkZ, blockstatestorage);
        this.buildSurfaces(chunkX, chunkZ, blockstatestorage);
        this.cave.place(this, this.world, chunkX, chunkZ, blockstatestorage);
        if (this.placeStructures) {
            this.fortress.place(this, this.world, chunkX, chunkZ, blockstatestorage);
        }

        WorldChunk worldchunk = new WorldChunk(this.world, blockstatestorage, chunkX, chunkZ);
        Biome[] abiome = this.world.getBiomeSource().getBiomes(null, chunkX * 16, chunkZ * 16, 16, 16);
        byte[] abyte = worldchunk.getBiomes();

        for (int i = 0; i < abyte.length; i++) {
            abyte[i] = (byte)abiome[i].id;
        }

        worldchunk.resetBorderLightChecks();
        return worldchunk;
    }

    private double[] generateHeightMap(double[] heightMap, int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        if (heightMap == null) {
            heightMap = new double[sizeX * sizeY * sizeZ];
        }

        double d0 = 684.412;
        double d1 = 2053.236;
        this.scaleNoiseBuffer = this.scaleNoise.getRegion(this.scaleNoiseBuffer, x, y, z, sizeX, 1, sizeZ, 1.0, 0.0, 1.0);
        this.depthNoiseBuffer = this.depthNoise.getRegion(this.depthNoiseBuffer, x, y, z, sizeX, 1, sizeZ, 100.0, 0.0, 100.0);
        this.perlinNoiseBuffer = this.perlinNoise1.getRegion(this.perlinNoiseBuffer, x, y, z, sizeX, sizeY, sizeZ, d0 / 80.0, d1 / 60.0, d0 / 80.0);
        this.minLimitPerlinNoiseBuffer = this.minLimitPerlinNoise.getRegion(this.minLimitPerlinNoiseBuffer, x, y, z, sizeX, sizeY, sizeZ, d0, d1, d0);
        this.maxLimitPerlinNoiseBuffer = this.maxLimitPerlinNoise.getRegion(this.maxLimitPerlinNoiseBuffer, x, y, z, sizeX, sizeY, sizeZ, d0, d1, d0);
        int i = 0;
        double[] adouble = new double[sizeY];

        for (int j = 0; j < sizeY; j++) {
            adouble[j] = Math.cos(j * Math.PI * 6.0 / sizeY) * 2.0;
            double d2 = j;
            if (j > sizeY / 2) {
                d2 = sizeY - 1 - j;
            }

            if (d2 < 4.0) {
                d2 = 4.0 - d2;
                adouble[j] -= d2 * d2 * d2 * 10.0;
            }
        }

        for (int l = 0; l < sizeX; l++) {
            for (int i1 = 0; i1 < sizeZ; i1++) {
                double d3 = 0.0;

                for (int k = 0; k < sizeY; k++) {
                    double d4 = 0.0;
                    double d5 = adouble[k];
                    double d6 = this.minLimitPerlinNoiseBuffer[i] / 512.0;
                    double d7 = this.maxLimitPerlinNoiseBuffer[i] / 512.0;
                    double d8 = (this.perlinNoiseBuffer[i] / 10.0 + 1.0) / 2.0;
                    if (d8 < 0.0) {
                        d4 = d6;
                    } else if (d8 > 1.0) {
                        d4 = d7;
                    } else {
                        d4 = d6 + (d7 - d6) * d8;
                    }

                    d4 -= d5;
                    if (k > sizeY - 4) {
                        double d9 = (k - (sizeY - 4)) / 3.0F;
                        d4 = d4 * (1.0 - d9) + -10.0 * d9;
                    }

                    if (k < d3) {
                        double d10 = (d3 - k) / 4.0;
                        d10 = MathHelper.clamp(d10, 0.0, 1.0);
                        d4 = d4 * (1.0 - d10) + -10.0 * d10;
                    }

                    heightMap[i] = d4;
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
        ChunkPos chunkpos = new ChunkPos(chunkX, chunkZ);
        this.fortress.place(this.world, this.random, chunkpos);

        for (int i = 0; i < 8; i++) {
            this.lavaPocket
                .place(this.world, this.random, blockpos.add(this.random.nextInt(16) + 8, this.random.nextInt(120) + 4, this.random.nextInt(16) + 8));
        }

        for (int j = 0; j < this.random.nextInt(this.random.nextInt(10) + 1) + 1; j++) {
            this.firePatch.place(this.world, this.random, blockpos.add(this.random.nextInt(16) + 8, this.random.nextInt(120) + 4, this.random.nextInt(16) + 8));
        }

        for (int k = 0; k < this.random.nextInt(this.random.nextInt(10) + 1); k++) {
            this.upsideDownGlowstoneCluster
                .place(this.world, this.random, blockpos.add(this.random.nextInt(16) + 8, this.random.nextInt(120) + 4, this.random.nextInt(16) + 8));
        }

        for (int l = 0; l < 10; l++) {
            this.glowstoneCluster
                .place(this.world, this.random, blockpos.add(this.random.nextInt(16) + 8, this.random.nextInt(128), this.random.nextInt(16) + 8));
        }

        if (this.random.nextBoolean()) {
            this.brownMushroom.place(this.world, this.random, blockpos.add(this.random.nextInt(16) + 8, this.random.nextInt(128), this.random.nextInt(16) + 8));
        }

        if (this.random.nextBoolean()) {
            this.redMushroom.place(this.world, this.random, blockpos.add(this.random.nextInt(16) + 8, this.random.nextInt(128), this.random.nextInt(16) + 8));
        }

        for (int i1 = 0; i1 < 16; i1++) {
            this.quartzOreVein.place(this.world, this.random, blockpos.add(this.random.nextInt(16), this.random.nextInt(108) + 10, this.random.nextInt(16)));
        }

        for (int j1 = 0; j1 < 16; j1++) {
            this.exposedLavaPocket
                .place(this.world, this.random, blockpos.add(this.random.nextInt(16), this.random.nextInt(108) + 10, this.random.nextInt(16)));
        }

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
        return "HellRandomLevelSource";
    }

    @Override
    public List<Biome.SpawnEntry> getSpawnEntries(MobCategory category, BlockPos pos) {
        if (category == MobCategory.MONSTER) {
            if (this.fortress.isInside(pos)) {
                return this.fortress.getMonsterSpawnEntries();
            }

            if (this.fortress.isInsideBounds(this.world, pos) && this.world.getBlockState(pos.down()).getBlock() == Blocks.NETHER_BRICKS) {
                return this.fortress.getMonsterSpawnEntries();
            }
        }

        Biome biome = this.world.getBiome(pos);
        return biome.getSpawnEntries(category);
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
        this.fortress.place(this, this.world, chunkX, chunkZ, null);
    }

    @Override
    public WorldChunk getChunk(BlockPos pos) {
        return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
