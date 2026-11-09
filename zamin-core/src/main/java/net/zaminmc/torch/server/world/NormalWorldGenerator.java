package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * The full-world terrain generator (the historical "default" level-type):
 * THE VANILLA TERRAIN NOISE CORE (Wave TN) — the reference's 4-noise
 * density stack ({@link OverworldDensityCore}, the OverworldChunkGenerator
 * port whole: the 16/16/8 Perlin octaves, the simplex depth consult, the
 * 5x5 biome kernel, the 33-step density walk, the 0.125/0.25 trilinear
 * fill), the reference's own surface column walk (the chunk random's k
 * roll, the bedrock band, the sandstone extension — the mirrored
 * biome/depth consults the reference's [j + i*16] reads carry), seas at
 * the reference's own 63, the documented cave-worm stand-in (the
 * reference's carver pair rides its own lane), and the populate walk's
 * features, structures and animals over the generated cells.
 *
 * <p>Determinism contract (the delta-overlay model's own law): the seed
 * arrives from the caller — the engine's boot resolves it through
 * {@link WorldSeedStore} (the reference's level-seed/random derivation,
 * persisted per world so every boot of the same world rebuilds the same
 * terrain without storing generated chunks). The name constructor below
 * keeps the HISTORICAL name-derived law for the deterministic test
 * fixtures. The noise core itself is the reference's own — one
 * {@code new Random(seed)} stream, the seven noises in the reference's
 * construction order, so the same seed answers the reference's terrain.</p>
 */
public final class NormalWorldGenerator implements WorldGenerator {

    /**
     * The reference's sea level (World.java line 62: 63) — the fill's water
     * band {@code y < 63} (the top water cell 62, the same cells the
     * retired constant 62 named).
     */
    public static final int SEA_LEVEL = 63;

    private final BlockRegistry registry;
    private final long seed;
    /** The mineshaft placement layer (Slice 12a): deterministic per chunk. */
    private final net.zaminmc.torch.server.world.structure.MineshaftStructure mineshafts;
    /**
     * The depth-dependent surface builders (VIII14's surface slice — the
     * reference's prepareAndBuildSurfaces whole: the core walk + the four
     * family overrides, over the engine's per-cell column model).
     */
    private final BiomeSurfaceBuilder surfaces;
    /**
     * THE VANILLA TERRAIN NOISE CORE (Wave TN): the reference's density
     * stack — the height maps, the fill, and the column probe the
     * consults read (the retired value-noise column model's replacement).
     */
    private final OverworldDensityCore density;
    /**
     * The height-map cache: one 825-entry density snapshot per chunk (a
     * pure function of the seed — the probe's out-of-chunk consults and
     * the fill read the same snapshots; eviction transparent). Bounded,
     * eldest-out — wide 4096-block sweeps hold ~4k chunks, so the bound
     * rides above them (the engine's own working set is the chunk's
     * neighbors, a few dozen).
     */
    private final java.util.LinkedHashMap<Long, double[]> heightMapCache =
            new java.util.LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        java.util.Map.Entry<Long, double[]> eldest) {
                    return size() > 8192;
                }
            };
    /**
     * The feature decorator (VIII14's decorators slice — the reference's
     * populate walk whole: the lakes, the dungeons, the biome's decorate,
     * over the populate-time view).
     */
    private final net.zaminmc.torch.server.world.feature.FeatureDecorator featureDecorator;
    /** The village placement layer (Slice 12b): deterministic per chunk. */
    private final net.zaminmc.torch.server.world.structure.VillageStructure villages;
    /** The stronghold placement layer (Slice 12c): deterministic per chunk. */
    private final net.zaminmc.torch.server.world.structure.StrongholdStructure strongholds;
    /** The ocean monument placement layer (Slice 12e): deterministic per chunk. */
    private final net.zaminmc.torch.server.world.structure.OceanMonumentStructure monuments;
    /** The temple placement layer (Slice 12f): deterministic per chunk. */
    private final net.zaminmc.torch.server.world.structure.TempleStructure temples;
    private final net.zaminmc.torch.server.world.structure.StructurePalette structurePalette;

    @Override
    public long seed() {
        return seed;
    }

    private final BlockType air;
    private final BlockType bedrock;
    private final BlockType stone;
    private final BlockType dirt;
    private final BlockType grass;
    private final BlockType sand;
    private final BlockType gravel;
    private final BlockType water;
    private final BlockType coalOre;
    private final BlockType ironOre;
    private final BlockType goldOre;
    private final BlockType redstoneOre;
    private final BlockType diamondOre;
    private final BlockType oakLog;
    private final BlockType oakLeaves;
    private final BlockType tallGrass;
    private final BlockType deadBush;
    private final BlockType dandelion;
    private final BlockType poppy;
    private final BlockType sandstone;
    private final BlockType sugarCane;
    private final BlockType cactus;
    /** THE CAVE CARVER (Wave CV): the reference's worm-and-room family. */
    private final net.zaminmc.torch.server.world.carver.CaveCarver caves;
    /** THE RAVINE CARVER (Wave CV): the reference's 1-in-50 wide trench. */
    private final net.zaminmc.torch.server.world.carver.RavineCarver ravines;

    /** The 1:4 biome grid (Layer.init's first answer — the terrain consult). */
    private final net.zaminmc.torch.server.world.layer.Layer gridLayer;
    /** The block-level biome map (Layer.init's second answer — the wire array). */
    private final net.zaminmc.torch.server.world.layer.Layer voronoiLayer;
    /**
     * The voronoi sample cache: 16x16-block chunks of the block-level
     * map (the reference's BiomeCache shape — the values pure in the
     * seed, the eviction transparent). Bounded, eldest-out.
     */
    private final java.util.LinkedHashMap<Long, int[]> blockBiomeCache =
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        java.util.Map.Entry<Long, int[]> eldest) {
                    return size() > 1024;
                }
            };

    /**
     * The HISTORICAL name law (the pre-T-SEED engine's only derivation):
     * kept as the deterministic fixture's constructor — the engine's boot
     * feeds the persisted seed through the long constructor (the
     * {@link WorldSeedStore} resolution), never this one.
     */
    public NormalWorldGenerator(BlockRegistry registry, String worldName) {
        this(registry, WorldSeedStore.historicalNameSeed(worldName));
    }

    public NormalWorldGenerator(BlockRegistry registry, long seed) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.seed = seed;
        // THE NOISE CORE FIRST (Wave TN): the reference's construction stream
        // — one Random(seed), the seven noises in order (minLimit 16, maxLimit
        // 16, perlinNoise1 8, perlinNoise3 4, scaleNoise 10, depthNoise 16,
        // forestNoise 8) — the surface builder's depth consult rides the
        // core's own perlinNoise3 (the fourth of the stream).
        this.density = new OverworldDensityCore(seed);
        // The biome layout (VIII14's second slice — Layer.init 1:1, the
        // DEFAULT type's chain): grid[0] answers the 1:4 biome grid the
        // density kernel consults, voronoi[1] answers the block-level
        // biome map the chunk's wire array carries. The caches hold the
        // last regions sampled (bounded LRU — the values are pure
        // functions of the seed, eviction transparent).
        this.surfaces = new BiomeSurfaceBuilder(this.seed, registry, density.depthConsult());
        this.featureDecorator = new net.zaminmc.torch.server.world.feature.FeatureDecorator(
                registry, this.seed);
        net.zaminmc.torch.server.world.layer.Layer[] layers =
                net.zaminmc.torch.server.world.layer.Layer.init(this.seed);
        this.gridLayer = layers[0];
        this.voronoiLayer = layers[1];
        this.mineshafts = new net.zaminmc.torch.server.world.structure.MineshaftStructure(this.seed);
        this.villages = new net.zaminmc.torch.server.world.structure.VillageStructure(this.seed);
        this.structurePalette = net.zaminmc.torch.server.world.structure.StructurePalette.of(registry);
        // The strongholds need the biome probe at construction time (the
        // positions walk's findBiome snap).
        net.zaminmc.torch.server.world.structure.StructureWorld positionView =
                new net.zaminmc.torch.server.world.structure.StructureWorld() {
                    @Override
                    public net.zaminmc.torch.block.BlockType blockAt(int x, int y, int z) {
                        return null;
                    }

                    @Override
                    public boolean isLiquid(net.zaminmc.torch.block.BlockType type) {
                        return false;
                    }

                    @Override
                    public void setBlock(int x, int y, int z, net.zaminmc.torch.block.BlockType type) {
                    }

                    @Override
                    public int seaLevel() {
                        return SEA_LEVEL;
                    }

                    @Override
                    public net.zaminmc.torch.server.world.structure.StructurePalette palette() {
                        return structurePalette;
                    }

                    @Override
                    public boolean isOpaque(net.zaminmc.torch.block.BlockType type) {
                        return false;
                    }

                    @Override
                    public Biome biomeAt(int x, int z) {
                        return NormalWorldGenerator.this.biomeAt(x, z);
                    }
                };
        this.strongholds = new net.zaminmc.torch.server.world.structure.StrongholdStructure(
                this.seed, positionView);
        this.monuments = new net.zaminmc.torch.server.world.structure.OceanMonumentStructure(this.seed);
        this.temples = new net.zaminmc.torch.server.world.structure.TempleStructure(this.seed);
        this.air = registry.require(Identifier.parse("minecraft:air"));
        this.bedrock = registry.require(Identifier.parse("minecraft:bedrock"));
        this.stone = registry.require(Identifier.parse("minecraft:stone"));
        // THE CARVER PAIR (Wave CV — the reference's own carvers, the retired
        // fbm cave stand-in's replacement): the caves and the ravines over
        // the filled and skinned chunk, the region walk's 17x17
        // neighborhood.
        this.caves = new net.zaminmc.torch.server.world.carver.CaveCarver(
                this.air,
                registry.require(Identifier.parse("minecraft:lava")),
                registry.require(Identifier.parse("minecraft:sandstone")),
                registry.require(Identifier.parse("minecraft:red_sandstone")));
        this.ravines = new net.zaminmc.torch.server.world.carver.RavineCarver(
                this.air,
                registry.require(Identifier.parse("minecraft:flowing_lava")));
        this.dirt = registry.require(Identifier.parse("minecraft:dirt"));
        this.grass = registry.require(Identifier.parse("minecraft:grass_block"));
        this.sand = registry.require(Identifier.parse("minecraft:sand"));
        this.gravel = registry.require(Identifier.parse("minecraft:gravel"));
        this.water = registry.require(Identifier.parse("minecraft:water"));
        this.coalOre = registry.require(Identifier.parse("minecraft:coal_ore"));
        this.ironOre = registry.require(Identifier.parse("minecraft:iron_ore"));
        this.goldOre = registry.require(Identifier.parse("minecraft:gold_ore"));
        this.redstoneOre = registry.require(Identifier.parse("minecraft:redstone_ore"));
        this.diamondOre = registry.require(Identifier.parse("minecraft:diamond_ore"));
        this.oakLog = registry.require(Identifier.parse("minecraft:oak_log"));
        this.oakLeaves = registry.require(Identifier.parse("minecraft:oak_leaves"));
        this.tallGrass = registry.require(Identifier.parse("minecraft:tall_grass"));
        this.deadBush = registry.require(Identifier.parse("minecraft:dead_bush"));
        this.dandelion = registry.require(Identifier.parse("minecraft:dandelion"));
        this.poppy = registry.require(Identifier.parse("minecraft:poppy"));
        this.sandstone = registry.require(Identifier.parse("minecraft:sandstone"));
        this.sugarCane = registry.require(Identifier.parse("minecraft:sugar_cane"));
        this.cactus = registry.require(Identifier.parse("minecraft:cactus"));
        // The spawn scan LAST (the layers and the structures are up; the
        // scan consults biomeAt and heightAt over the layout).
        this.spawnColumnHeight = scanSpawnColumn();
    }

    /**
     * The column's biome (VIII14's second slice — the vanilla layout):
     * the block-level voronoi map over the ~30-layer chain, the chunk
     * cache answering (the reference's BiomeCache shape —
     * BiomeSource.getBiome over alayer[1]). The old climate-noise
     * shading is retired: every biome the reference's DEFAULT chain
     * produces can now generate, the mutated forms included (the
     * RegionHills +128 band, the sunflower roll).
     */
    public Biome biomeAt(int x, int z) {
        // THE MASKED PACKING (Wave SA's negative-coordinate audit — the
        // sign-collapse retired): the two 32-bit halves carry the chunk
        // coords' own bit patterns, the pair injective over the whole int
        // domain. The retired bare-cast packing smeared the sign extension
        // — every chunk with a negative z collapsed onto ONE key per
        // x-band (the first-cached chunk's 16x16 array answering the whole
        // band — the biome map south of the origin a consult-order smear,
        // the whole negative half-world reading wrong biomes: the owner's
        // own all-negative-coordinates world). The VR wave's fixedPoint
        // lane-collapse was the same class.
        long key = ((long) (x >> 4) & 0xFFFFFFFFL)
                | (((long) (z >> 4) & 0xFFFFFFFFL) << 32);
        int[] cell;
        synchronized (blockBiomeCache) {
            cell = blockBiomeCache.get(key);
            if (cell == null) {
                cell = sampleArea(voronoiLayer, (x >> 4) << 4,
                        (z >> 4) << 4, 16, 16);
                blockBiomeCache.put(key, cell);
            }
        }
        int id = cell[(x & 15) + (z & 15) * 16] & 0xFF;
        Biome biome = Biome.byLegacyId(id);
        return biome == null ? Biome.PLAINS : biome;
    }

    /**
     * The shared sampling lock: the grid and voronoi chains share their
     * layers (the voronoi's parent IS the grid layer), and every layer
     * carries mutable seed state — the reference's own single-threaded
     * discipline, the engine's lock.
     */
    private final Object layerLock = new Object();

    /**
     * One top-level area query: the reference's IntCache discipline
     * (pop the previous query's arrays back to the reuse pool, push the
     * new one) with the result CLONED out — the cached array must never
     * ride the reuse pool (a later query would overwrite it).
     */
    private int[] sampleArea(
            net.zaminmc.torch.server.world.layer.Layer layer,
            int x, int z, int sizeX, int sizeZ) {
        synchronized (layerLock) {
            net.zaminmc.torch.server.world.layer.IntCache.pop();
            return layer.getArea(x, z, sizeX, sizeZ).clone();
        }
    }

    @Override
    public int groundLevel() {
        return spawnColumnHeight;
    }

    @Override
    public net.zaminmc.torch.util.Position spawnPosition() {
        return new net.zaminmc.torch.util.Position(spawnX + 0.5, spawnColumnHeight + 1.0, spawnZ + 0.5);
    }

    /** The first dry column on the +x diagonal (deterministic spawn anchor). */
    private int spawnX;
    private int spawnZ;
    private int spawnColumnHeight;

    /**
     * The spawn column scan (the reference's own allowlist —
     * BiomeSource.biomesForSpawnPoint: FOREST, PLAINS, TAIGA,
     * TAIGA_HILLS, FOREST_HILLS, JUNGLE, JUNGLE_HILLS): the first dry
     * column on the +x diagonal inside one of the seven, the (0,0)
     * land pin guaranteeing the search starts on a continent.
     */
    private int scanSpawnColumn() {
        for (int r = 0; r < 256; r += 4) {
            int x = r;
            int z = r;
            Biome biome = biomeAt(x, z);
            boolean allowed = biome == Biome.FOREST || biome == Biome.PLAINS
                    || biome == Biome.TAIGA || biome == Biome.TAIGA_HILLS
                    || biome == Biome.FOREST_HILLS || biome == Biome.JUNGLE
                    || biome == Biome.JUNGLE_HILLS;
            int h = heightAt(x, z);
            if (allowed && h > SEA_LEVEL && h < 100) {
                spawnX = x;
                spawnZ = z;
                return h;
            }
        }
        spawnX = 0;
        spawnZ = 0;
        return Math.max(heightAt(0, 0), SEA_LEVEL - 1) + 1;
    }

    @Override
    public void generate(EngineChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        net.zaminmc.torch.block.ChunkPosition at = chunk.position();
        int chunkX = at.x();
        int chunkZ = at.z();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        // THE BIOME ARRAY (the reference's getChunk line 175 — the block-level
        // voronoi map, the chunk's wire array).
        Biome[][] biomes = new Biome[16][16];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                biomes[lx][lz] = biomeAt(baseX + lx, baseZ + lz);
                chunk.setBiome(lx, lz, (byte) biomes[lx][lz].legacyId());
            }
        }

        // THE DENSITY FILL (buildTerrain, the reference's lines 95-153):
        // stone where the density walks positive, water below the sea —
        // the overhang-bearing world the retired column model could never
        // place. The height map rides the cache (the probe below reads the
        // same snapshot — the fill and the consults can never disagree).
        double[] heightMap = heightMapFor(chunkX, chunkZ);
        density.buildTerrain(chunkX, chunkZ, heightMap, stone, water,
                (localX, y, localZ, type) -> chunk.setBlock(localX, y, localZ, type));

        // THE SURFACE PASS (buildSurfaces, the reference's lines 158-168):
        // one stateful column walk over the placed cells with the CHUNK
        // RANDOM — the k roll, the bedrock band, the sandstone extension,
        // the family pair switches. THE CONSULTS ARE THE REFERENCE'S OWN
        // TRANSPOSED READS: the column (i, j) carries the biome
        // {@code biomes[j + i*16]} and the depth {@code depthBuffer[j +
        // i*16]} — the MIRRORED cells of the x-fastest arrays (the
        // reference's own quirk, kept verbatim), while the mesa's own
        // noise consults read the TRUE column coordinates.
        java.util.Random surfaceRandom = new java.util.Random(
                chunkX * 341873128712L + chunkZ * 132897987541L);
        double[] depthBuffer = density.depthConsult().getRegion(
                null, baseX, baseZ, 16, 16, 0.03125 * 2.0, 0.03125 * 2.0, 1.0);
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                Biome surfaceBiome = biomes[j][i]; // the [j + i*16] mirrored consult
                double depth = depthBuffer[j + i * 16]; // the mirrored depth cell
                final int lx = i;
                final int lz = j;
                surfaces.buildColumn(baseX + i, baseZ + j, surfaceBiome, depth,
                        surfaceRandom, new BiomeSurfaceBuilder.ColumnAccess() {
                            @Override
                            public net.zaminmc.torch.block.BlockType get(int y) {
                                return chunk.getBlock(lx, y, lz);
                            }

                            @Override
                            public void set(int y, net.zaminmc.torch.block.BlockType type) {
                                chunk.setBlock(lx, y, lz, type);
                            }
                        });
            }
        }

        // THE CARVER PAIR (the reference's getChunk lines 177-183 — the
        // caves then the ravines, AFTER the surface pass): the region walk
        // over the 17x17 neighborhood, every region chunk re-rolling its
        // own starts with the carve clipped to THIS chunk — the caves
        // cross chunk borders exactly like the reference's. The retired
        // fbm worm stand-in (isCave) answered scattered blobs in the
        // (4, h-3) band; the reference's worms walk their own yaw/pitch
        // ladders with the water suppression, the lava floor below 10,
        // the sandstone ceiling fix and the grass floor fix whole.
        net.zaminmc.torch.server.world.carver.CarverChunk carverChunk =
                new net.zaminmc.torch.server.world.carver.CarverChunk() {
                    @Override
                    public BlockType get(int localX, int y, int localZ) {
                        return chunk.getBlock(localX, y, localZ);
                    }

                    @Override
                    public void set(int localX, int y, int localZ, BlockType type) {
                        chunk.setBlock(localX, y, localZ, type);
                    }

                    @Override
                    public Biome biomeAt(int worldX, int worldZ) {
                        return NormalWorldGenerator.this.biomeAt(worldX, worldZ);
                    }

                    @Override
                    public BlockType registeredSurfaceOf(Biome biome) {
                        return surfaces.registeredSurface(biome);
                    }
                };
        caves.place(seed, chunkX, chunkZ, carverChunk);
        ravines.place(seed, chunkX, chunkZ, carverChunk);

        // THE HEIGHTS after the carvers (the reference's populate reads
        // the CARVED world — the cave mouths open the surface): the
        // topmost movement-blocking cell per column, the water passed
        // through and the leaves excluded (the reference's
        // getSurfaceHeight — World.java lines 1132-1141).
        int[][] heights = new int[16][16];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int top = 0;
                for (int y = 255; y >= 0; y--) {
                    BlockType cell = chunk.getBlock(lx, y, lz);
                    if (net.zaminmc.torch.server.block.WorldSolidity.isSolid(cell)
                            && !net.zaminmc.torch.server.block.LeafDecayBlocks
                                    .isLeafState(cell.identifier())) {
                        top = y;
                        break;
                    }
                }
                heights[lx][lz] = top;
            }
        }

        plantDecorations(chunk, baseX, baseZ);
        // The structure pass (Slice 12a — the populate hook): the region
        // scan + the clipped piece writes, after the terrain and the
        // flora exactly like the reference's populate step.
        populateStructures(chunk, baseX, baseZ);
        // The animal seeding (the reference's populate step's LAST pass —
        // OverworldChunkGenerator line 393, after biome.decorate).
        seedAnimals(chunk, baseX, baseZ, heights);
    }

    /**
     * The chunk's density height map (the cached snapshot — the fill's own
     * 825 doubles, pure in the seed). The 10x10 noise-grid biome sample
     * rides the grid layer (the reference's getNoiseBiomes at
     * {@code (chunkX*4 - 2, chunkZ*4 - 2)}).
     */
    private double[] heightMapFor(int chunkX, int chunkZ) {
        // THE MASKED PACKING (the biome cache's own fix — the sign-collapse
        // retired here too: the retired bare-cast key collapsed every
        // negative-z chunk onto one entry, the terrain heights south of
        // the origin answering the first-cached chunk's snapshot).
        long key = ((long) chunkX & 0xFFFFFFFFL)
                | (((long) chunkZ & 0xFFFFFFFFL) << 32);
        synchronized (heightMapCache) {
            double[] cached = heightMapCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        // The compute runs OUTSIDE the cache lock — the generation is pure
        // (fresh buffers, the immutable noises), so parallel chunk workers
        // computing the same chunk waste work but never corrupt.
        int[] ids = sampleArea(gridLayer, chunkX * 4 - 2, chunkZ * 4 - 2, 10, 10);
        Biome[] kernel = new Biome[100];
        for (int cell = 0; cell < 100; cell++) {
            Biome biome = Biome.byLegacyId(ids[cell] & 0xFF);
            kernel[cell] = biome == null ? Biome.PLAINS : biome;
        }
        double[] heightMap = density.generateHeightMap(chunkX * 4, 0, chunkZ * 4, kernel);
        synchronized (heightMapCache) {
            heightMapCache.put(key, heightMap);
        }
        return heightMap;
    }

    /**
     * The feature decorator's populate (VIII14's decorators slice — the
     * reference's populateChunk's own populate side, lines 341-393): the
     * populate-time world view over this chunk (the structures' own
     * clipped-write + pure-read shape), the region re-derivation window
     * (the walks of the {self, -x} x {self, -z} quadrant — every foreign
     * cell's writer exactly one pass), the shared per-chunk random over
     * the lakes, the dungeons, and the biome's decorate. The old
     * oak-only tree stand-in and the simplified flora rolls RETIRE (the
     * plantTrees/plantFlora pair) — the walk's own stages answer now.
     */
    private void plantDecorations(EngineChunk chunk, int baseX, int baseZ) {
        net.zaminmc.torch.server.world.feature.FeatureWorld view =
                new net.zaminmc.torch.server.world.feature.FeatureWorld() {
                    @Override
                    public net.zaminmc.torch.block.BlockType blockAt(int x, int y, int z) {
                        if (y < 0 || y > 255) {
                            return air;
                        }
                        int localX = x - baseX;
                        int localZ = z - baseZ;
                        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                            return chunk.getBlock(localX, y, localZ);
                        }
                        return surfaceBlockAt(x, y, z);
                    }

                    @Override
                    public void setBlockAt(int x, int y, int z,
                            net.zaminmc.torch.block.BlockType type) {
                        if (y < 0 || y > 255) {
                            return;
                        }
                        int localX = x - baseX;
                        int localZ = z - baseZ;
                        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                            chunk.setBlock(localX, y, localZ, type);
                        }
                    }

 @Override
                    public boolean isAirAt(int x, int y, int z) {
                        return blockAt(x, y, z).equals(air);
                    }

                    @Override
                    public boolean isLiquidAt(int x, int y, int z) {
                        net.zaminmc.torch.util.Identifier id = blockAt(x, y, z).identifier();
                        return id.namespace().equals("minecraft")
                                && (id.value().equals("water") || id.value().equals("lava"));
                    }

                    @Override
                    public boolean isSolidAt(int x, int y, int z) {
                        return net.zaminmc.torch.server.block.WorldSolidity.isSolid(blockAt(x, y, z));
                    }

                    @Override
                    public boolean isOpaqueAt(int x, int y, int z) {
                        return net.zaminmc.torch.server.block.BlockLightTable.filter(
                                blockAt(x, y, z)) >= 15;
                    }

                    @Override
                    public int heightAt(int x, int z) {
                        int localX = x - baseX;
                        int localZ = z - baseZ;
                        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                            for (int y = 255; y >= 0; y--) {
                                if (net.zaminmc.torch.server.block.BlockLightTable
                                        .filter(chunk.getBlock(localX, y, localZ)) > 0) {
                                    return y + 1;
                                }
                            }
                            return 0;
                        }
                        int h = NormalWorldGenerator.this.heightAt(x, z);
                        return Math.max(h, SEA_LEVEL - 1) + 1;
                    }

                    @Override
                    public int surfaceHeightAt(int x, int z) {
                        int localX = x - baseX;
                        int localZ = z - baseZ;
                        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                            for (int y = 255; y >= 0; y--) {
                                net.zaminmc.torch.block.BlockType at =
                                        chunk.getBlock(localX, y, localZ);
                                if (net.zaminmc.torch.server.block.WorldSolidity.isSolid(at)
                                        && !net.zaminmc.torch.server.block.LeafDecayBlocks
                                                .isLeafState(at.identifier())) {
                                    return y + 1;
                                }
                            }
                            return 0;
                        }
                        return NormalWorldGenerator.this.heightAt(x, z) + 1;
                    }

                    @Override
                    public int seaLevel() {
                        return SEA_LEVEL;
                    }

                    @Override
                    public Biome biomeAt(int x, int z) {
                        return NormalWorldGenerator.this.biomeAt(x, z);
                    }

                    @Override
                    public void placeMobSpawner(int x, int y, int z,
                            net.zaminmc.torch.server.entity.MobType mobType) {
                        chunk.addPendingMobSpawner(x, y, z, mobType);
                    }

                    @Override
                    public void placeGenChest(int x, int y, int z,
                            java.util.List<net.zaminmc.torch.server.world.structure.StructureWorld.GenLootSlot> slots) {
                        chunk.addPendingGenChest(x, y, z, slots);
                    }

                    // The TreeWorld seam's own trio (the tree features'
                    // whole surface — the live world's triple, over the
                    // populate view).
                    @Override
                    public net.zaminmc.torch.block.BlockType getBlock(
                            net.zaminmc.torch.block.BlockPosition position) {
                        return blockAt(position.x(), position.y(), position.z());
                    }

                    @Override
                    public boolean setBlock(net.zaminmc.torch.block.BlockPosition position,
                            net.zaminmc.torch.block.BlockType type) {
                        int y = position.y();
                        if (y < 0 || y > 255) {
                            return false;
                        }
                        int localX = position.x() - baseX;
                        int localZ = position.z() - baseZ;
                        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                            return chunk.setBlock(localX, y, localZ, type);
                        }
                        return false;
                    }

                    @Override
                    public net.zaminmc.torch.block.BlockType airType() {
                        return air;
                    }
                };
        featureDecorator.populate(chunk.position().x(), chunk.position().z(), view);
    }

    /**
     * The structure pass: the mineshaft region walk over this chunk's
     * own view (in-chunk reads from the chunk, out-of-chunk reads from
     * the pure terrain — the deterministic equivalent of the reference's
     * populate-time world reads).
     */
    private void populateStructures(EngineChunk chunk, int baseX, int baseZ) {
        net.zaminmc.torch.server.world.structure.StructureWorld view =
                new net.zaminmc.torch.server.world.structure.StructureWorld() {
                    @Override
                    public net.zaminmc.torch.block.BlockType blockAt(int x, int y, int z) {
                        if (y < 0 || y > 255) {
                            return null;
                        }
                        int localX = x - baseX;
                        int localZ = z - baseZ;
                        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                            return chunk.getBlock(localX, y, localZ);
                        }
                        return surfaceBlockAt(x, y, z);
                    }

                    @Override
                    public boolean isLiquid(net.zaminmc.torch.block.BlockType type) {
                        if (type == null) {
                            return false;
                        }
                        net.zaminmc.torch.util.Identifier id = type.identifier();
                        return id.namespace().equals("minecraft")
                                && (id.value().equals("water") || id.value().equals("lava"));
                    }

                    @Override
                    public void setBlock(int x, int y, int z, net.zaminmc.torch.block.BlockType type) {
                        if (y < 0 || y > 255) {
                            return;
                        }
                        int localX = x - baseX;
                        int localZ = z - baseZ;
                        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                            chunk.setBlock(localX, y, localZ, type);
                        }
                    }

                    @Override
                    public int seaLevel() {
                        return SEA_LEVEL;
                    }

                    @Override
                    public net.zaminmc.torch.server.world.structure.StructurePalette palette() {
                        return structurePalette;
                    }

                    @Override
                    public boolean isOpaque(net.zaminmc.torch.block.BlockType type) {
                        return net.zaminmc.torch.server.block.WorldSolidity.isSolid(type);
                    }

                    @Override
                    public int surfaceHeight(int x, int z) {
                        // The pure probe — never the chunk's array — so a
                        // piece spanning chunks re-bases identically in
                        // every chunk that touches it (Slice 12b's
                        // deterministic-contract note).
                        return heightAt(x, z);
                    }

                    @Override
                    public Biome biomeAt(int x, int z) {
                        return NormalWorldGenerator.this.biomeAt(x, z);
                    }

                    @Override
                    public void spawnVillager(int x, int y, int z, int profession) {
                        chunk.addPendingVillager(x, y, z, profession);
                    }

                    @Override
                    public void spawnWitch(int x, int y, int z) {
                        chunk.addPendingWitch(x, y, z);
                    }

                    @Override
                    public void spawnElderGuardian(int x, int y, int z) {
                        chunk.addPendingElderGuardian(x, y, z);
                    }

                    @Override
                    public void placeGenChest(int x, int y, int z,
                            java.util.List<net.zaminmc.torch.server.world.structure.StructureWorld.GenLootSlot> slots) {
                        chunk.addPendingGenChest(x, y, z, slots);
                    }

                    @Override
                    public void placeGenDispenser(int x, int y, int z, int facing,
                            java.util.List<net.zaminmc.torch.server.world.structure.StructureWorld.GenLootSlot> slots) {
                        chunk.addPendingGenDispenser(x, y, z, facing, slots);
                    }

                    @Override
                    public void placeGenChestMinecart(int x, int y, int z,
                            java.util.List<net.zaminmc.torch.server.world.structure.StructureWorld.GenLootSlot> slots) {
                        chunk.addPendingChestMinecart(x, y, z, slots);
                    }

                    @Override
                    public void placeMobSpawner(int x, int y, int z,
                            net.zaminmc.torch.server.entity.MobType mobType) {
                        chunk.addPendingMobSpawner(x, y, z, mobType);
                    }
                };
        mineshafts.populate(chunk.position().x(), chunk.position().z(), view);
        villages.populate(chunk.position().x(), chunk.position().z(), view);
        strongholds.populate(chunk.position().x(), chunk.position().z(), view);
        monuments.populate(chunk.position().x(), chunk.position().z(), view);
        temples.populate(chunk.position().x(), chunk.position().z(), view);
    }

    /**
     * The nearest stronghold's start-piece position to a point (the eye
     * of ender's target consult — the reference's
     * {@code world.findNearestStructure("Stronghold", pos)}, EnderEyeItem
     * lines 121-133): the positions table's nearest entry answered as the
     * START PIECE'S own placement — ((chunkX &lt;&lt; 4) + 2, 64, (chunkZ
     * &lt;&lt; 4) + 2), the pieces' own start-room convention (the 1.8.8
     * StrongholdStart at y=64, the +2 start corner) — the derivation
     * named per §27.9. The engine's table always answers (the three
     * positions exist by construction); the reference's not-found no-op
     * never occurs on the engine's gen.
     */
    public double[] nearestStrongholdTo(double x, double z) {
        int[][] table = strongholds.positionTable();
        int[] best = table[0];
        double bestDistance = Double.MAX_VALUE;
        for (int[] position : table) {
            double px = (position[0] << 4) + 2.0;
            double pz = (position[1] << 4) + 2.0;
            double dx = px - x;
            double dz = pz - z;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = position;
            }
        }
        return new double[]{(best[0] << 4) + 2.0, 64.0, (best[1] << 4) + 2.0};
    }

    /** The block for one column cell: core, skin, water, cave or ore (the
     * out-of-chunk probe's own form — the mirrored biome + depth the
     * caller carries). */
    private BlockType blockAt(int x, int y, int z, int h, boolean underwater, Biome biome,
            double depth) {
        if (y == 0) {
            return bedrock;
        }
        if (y > h) {
            // The Bryce pillars first (VIII14's surface slice — the
            // reference's air-fill arm: stone rises to (int) d4 over the
            // terrain; every other column answers the caller's own).
            BlockType pillar = surfaces.blockAt(x, y, z, h, biome, depth);
            if (pillar != null) {
                return pillar;
            }
            return y < SEA_LEVEL ? water : air;
        }
        // THE CARVERS DO NOT RIDE THE PROBE (Wave CV): the reference's
        // cave/ravine carves are region-walk dependent (the 17x17
        // neighborhood's own seeds) — the out-of-chunk probe answers the
        // un-carved terrain, the documented stand-in (the in-chunk reads
        // see the real carves; the structures' neighbor consults are the
        // populate-time contract's own approximation).
        // THE SURFACE BUILDERS (VIII14's surface slice — the reference's
        // buildSurfaces walk + the family overrides over the engine's
        // per-cell probe): null answers the core stone the ore ladder owns
        // (and the air/water above the terrain, the caller's own).
        BlockType skin = surfaces.blockAt(x, y, z, h, biome, depth);
        return skin != null ? skin : oreOrStone(x, y, z);
    }


    /**
     * The pure stone core (the generation-side ore ladder RETIRED — the
     * owner's report 'ore veins generation etc all are very wrong'): the
     * retired stateless per-cell hash could only ever answer scattered
     * single ore cells in wrong depth bands (coal to 60, iron to 54, no
     * stone family, no lapis), never a vein. The vanilla ore law rides
     * the populate walk's own decorate head now — FeatureDecorator's
     * placeVeins over VeinFeature's ellipsoid chains with the default
     * table's counts/sizes/heights, stone-replacing, chunk-seeded — and
     * the pure terrain function answers stone alone.
     */
    private BlockType oreOrStone(int x, int y, int z) {
        return stone;
    }

    /**
     * The column's terrain top (Wave TN — the DENSITY PROBE: the fill's own
     * accumulation ladder walked to one column, answering the topmost cell
     * the fill placed stone in — bit-identical to the terrain by
     * construction). The consults (the spawn scan, the structures'
     * surface height, the out-of-chunk probes) read the same law the
     * terrain itself was built from — the retired value-noise column model
     * (fbm 0.0085/0.0035 over biome bands *17, clamped 8..120) never
     * could.
     */
    public int heightAt(int x, int z) {
        double[] heightMap = heightMapFor(x >> 4, z >> 4);
        return OverworldDensityCore.columnTop(heightMap, x & 15, z & 15);
    }

    /**
     * The world block of one column cell (the diagnostics view of the
     * generation surface: biome-aware, no chunk allocation). The consult
     * carries the fill's own MIRRORED biome (the reference's [j + i*16]
     * law — the surface pass reads the mirrored cell of the chunk's
     * x-fastest biome array; the probe answers what the fill placed, so
     * the mirror rides here too).
     */
    public BlockType surfaceBlockAt(int x, int y, int z) {
        int h = heightAt(x, z);
        int baseX = (x >> 4) << 4;
        int baseZ = (z >> 4) << 4;
        // The fill's own MIRRORED consults (the reference's [j + i*16]
        // law — the surface pass reads the mirrored biome and depth cells
        // of the chunk's x-fastest arrays; the probe answers what the
        // fill placed, so the mirror rides here too).
        Biome mirrored = biomeAt(baseX + (z & 15), baseZ + (x & 15));
        double mirroredDepth = surfaces.depthAt(baseX + (z & 15), baseZ + (x & 15));
        return blockAt(x, y, z, h, h < SEA_LEVEL, mirrored, mirroredDepth);
    }

    // ---------------------------------------------------------- worldgen animal seeding
    // (NaturalSpawner.populateChunk 1:1 — the reference's populate step's
    // LAST pass: OverworldChunkGenerator.populateChunk line 393)

    /** Biome.getAnimalSpawnChance (line 401): the flat 10% seeding chance. */
    private static final float ANIMAL_SPAWN_CHANCE = 0.1F;

    /**
     * The animal seeding walk (NaturalSpawner.populateChunk lines 164-215,
     * 1:1): the {@code while (nextFloat() < 0.1)} group roll, the weighted
     * pick, the group size, the 4-attempt placement per member with the
     * +8/+16 region and the nextInt(5) drift walk with the leash re-anchor,
     * the horse group's shared dice (the reference's initialize: the first
     * member's type + color, the re-rolled marking per member, the 1-in-5
     * baby), and the yaw roll per placed member.
     *
     * <p>The animals join the world through the chunk's pending records —
     * the drain materializes them exactly once (the villagers' own
     * pattern). The region is the reference's own {@code [chunk+8,
     * chunk+24)} span: the records ride THIS chunk even when a member
     * walks into the neighbor's strip (the reference's addEntity is
     * world-global; the engine's mob manager is too).</p>
     */
    private void seedAnimals(EngineChunk chunk, int baseX, int baseZ, int[][] heights) {
        // The reference reads the biome at the chunk's +16 corner
        // (OverworldChunkGenerator line 336: blockpos.add(16, 0, 16)).
        Biome biome = biomeAt(baseX + 16, baseZ + 16);
        // The registration table's own creature list (the spawn-tables
        // slice: the reference's populateChunk reads
        // biome.getSpawnEntries(CREATURE) — the desert/ocean/river/
        // beach/ice/mesa/stone families' cleared tables refuse the
        // seeding here now, the jungle's second chicken carries its
        // weight twice, and the ocelot NEVER seeds at populate (its row
        // rides the monster table — the natural cycle's jungle consult,
        // the engine's own earlier stand-in retired).
        List<Biome.SpawnEntry> rows = biome.creatureEntries();
        if (rows.isEmpty()) {
            return;
        }
        // The deterministic per-chunk random (the reference's
        // setRandomSeed(chunkX * k + chunkZ * l ^ seed) equivalent — the
        // engine's hash-seed family).
        long chunkSeed = seed ^ (chunk.position().x() * 0x68E31DA4L)
                ^ (chunk.position().z() * 0xB5297A4DL);
        java.util.Random random = new java.util.Random(chunkSeed);
        int totalWeight = 0;
        for (Biome.SpawnEntry row : rows) {
            totalWeight += row.weight();
        }
        int centerX = baseX + 8;
        int centerZ = baseZ + 8;

        while (random.nextFloat() < ANIMAL_SPAWN_CHANCE) {
            // WeightedPicker.pick: the cumulative walk over the table.
            int roll = random.nextInt(totalWeight);
            Biome.SpawnEntry picked = rows.get(rows.size() - 1);
            int cumulative = 0;
            for (Biome.SpawnEntry row : rows) {
                cumulative += row.weight();
                if (roll < cumulative) {
                    picked = row;
                    break;
                }
            }
            int groupSize = picked.minGroup()
                    + random.nextInt(1 + picked.maxGroup() - picked.minGroup());
            int j = centerX + random.nextInt(16);
            int k = centerZ + random.nextInt(16);
            int anchorJ = j;
            int anchorK = k;
            // The horse group's shared entitydata (the reference's
            // EntityHorseGroupData: null until the first horse initializes).
            Integer horseSubtype = null;
            int horseColor = 0;

            for (int member = 0; member < groupSize; member++) {
                boolean placed = false;
                for (int attempt = 0; !placed && attempt < 4; attempt++) {
                    int surfaceY = surfaceYFor(chunk, heights, baseX, baseZ, j, k);
                    if (validAnimalGround(chunk, baseX, baseZ, j, k, surfaceY)) {
                        float yaw = random.nextFloat() * 360.0F;
                        int subtype = 0;
                        int variant = 0;
                        if (picked.type() == net.zaminmc.torch.server.entity.MobType.HORSE) {
                            // HorseBaseEntity.initialize (lines 1262-1280): the
                            // first member rolls the type + the coat, the pack
                            // shares the type + the color and re-rolls markings.
                            if (horseSubtype == null) {
                                if (random.nextInt(10) == 0) {
                                    horseSubtype = net.zaminmc.torch.server.entity.MobEntity
                                            .HORSE_SUBTYPE_DONKEY;
                                    subtype = horseSubtype;
                                } else {
                                    horseSubtype = net.zaminmc.torch.server.entity.MobEntity
                                            .HORSE_SUBTYPE_HORSE;
                                    subtype = horseSubtype;
                                    variant = random.nextInt(7) | random.nextInt(5) << 8;
                                    horseColor = variant & 0xFF;
                                }
                            } else {
                                subtype = horseSubtype;
                                variant = horseColor | random.nextInt(5) << 8;
                            }
                        }
                        boolean baby = picked.type() == net.zaminmc.torch.server.entity.MobType.HORSE
                                && random.nextInt(5) == 0;
                        chunk.addPendingAnimal(new net.zaminmc.torch.server.world.EngineChunk
                                .PendingAnimal(picked.type(), j + 0.5, surfaceY, k + 0.5,
                                yaw, baby, subtype, variant));
                        placed = true;
                    }
                    // The drift + the leash (the reference's own loop shape,
                    // lines 205-215 — the k bound checks rangeX, the
                    // reference's own quirk; identical here at 16 == 16).
                    j += random.nextInt(5) - random.nextInt(5);
                    for (k += random.nextInt(5) - random.nextInt(5);
                            j < centerX || j >= centerX + 16
                                    || k < centerZ || k >= centerZ + 16;
                            k = anchorK + random.nextInt(5) - random.nextInt(5)) {
                        j = anchorJ + random.nextInt(5) - random.nextInt(5);
                    }
                }
            }
        }
    }

    /**
     * The surface height for the seeding (the reference's
     * getSurfaceHeight): the first free cell above the ground — in-chunk
     * the generated terrain's own height, out-of-chunk the pure heightAt
     * (the deterministic neighbor answer, Slice 12b's own contract).
     */
    private int surfaceYFor(EngineChunk chunk, int[][] heights, int baseX, int baseZ,
                            int x, int z) {
        int localX = x - baseX;
        int localZ = z - baseZ;
        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
            return heights[localX][localZ] + 1;
        }
        return heightAt(x, z) + 1;
    }

    /**
     * The placement gate (isValidSpawnPos' ON_GROUND arm, lines 137-157):
     * the ground below is a solid block (not bedrock), the cell itself is
     * not solid, and the cell above is not solid either. In-chunk reads
     * from the chunk (the trees' trunks refuse), out-of-chunk from the
     * pure terrain (the Slice 12b contract).
     */
    private boolean validAnimalGround(EngineChunk chunk, int baseX, int baseZ,
                                      int x, int z, int surfaceY) {
        if (surfaceY <= 1 || surfaceY > 255) {
            return false;
        }
        BlockType ground = blockFor(chunk, baseX, baseZ, x, surfaceY - 1, z);
        if (!net.zaminmc.torch.server.block.WorldSolidity.isSolid(ground)
                || ground.equals(bedrock)) {
            return false;
        }
        BlockType cell = blockFor(chunk, baseX, baseZ, x, surfaceY, z);
        BlockType above = blockFor(chunk, baseX, baseZ, x, surfaceY + 1, z);
        return !net.zaminmc.torch.server.block.WorldSolidity.isSolid(cell)
                && !net.zaminmc.torch.server.block.WorldSolidity.isSolid(above);
    }

    /** One cell read: the chunk inside, the pure terrain outside. */
    private BlockType blockFor(EngineChunk chunk, int baseX, int baseZ,
                               int x, int y, int z) {
        int localX = x - baseX;
        int localZ = z - baseZ;
        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
            return chunk.getBlock(localX, y, localZ);
        }
        return surfaceBlockAt(x, y, z);
    }

}
