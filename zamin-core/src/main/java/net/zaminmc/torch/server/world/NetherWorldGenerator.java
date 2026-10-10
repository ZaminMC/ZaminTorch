package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.world.noise.PerlinNoise;
import net.zaminmc.torch.server.world.noise.ReferenceMath;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;

import java.util.Objects;
import java.util.Random;

/**
 * Ported from reference/1.8.8 net/minecraft/world/gen/chunk/NetherChunkGenerator.java
 * (lines 1-415): the nether's terrain — the seven-PerlinNoise stack, the
 * 5x17x5 trilinear terrain shape with its lava sea below y=32, the
 * soul-sand/gravel skin pass over the bedrock-banded netherrack column, the
 * eight-neighbor cave carve, and the decorate pass (lava pockets, fire
 * patches, the two glowstone cluster forms, quartz veins, the mushrooms).
 *
 * <p>Determinism adaptations (documented, both keep the engine's
 * rebuild-identical contract):</p>
 * <ul>
 *   <li>The reference's {@code getChunk} re-seeds {@code this.random} per
 *   chunk ({@code chunkX * 341873128712L + chunkZ * 132897987541L}) and its
 *   {@code populateChunk} continues the same stream — the decorate rolls then
 *   depend on chunk generation ORDER. This port re-seeds the decorate pass
 *   from the same per-chunk formula, so decoration is order-independent.</li>
 *   <li>The reference's population writes spill into neighbor chunks (the
 *   feature positions start at +8, the vein walk reaches across borders, the
 *   lava-pocket neighbor counts read the world). This port clips every
 *   feature to its own chunk's 16x16x128 grid: out-of-chunk cells are
 *   unwritable and read as air. The roll SHAPES are verbatim — the stream
 *   consumes exactly what the reference consumes; only the landed writes
 *   clip.</li>
 *   <li>The fortress placement is skipped (structures ride their own slice);
 *   the reference's populateChunk draws it before the features, so the
 *   decorate stream differs from vanilla by that consumption.</li>
 *   <li>{@code FallingBlock.fallImmediately = true} (the reference's
 *   populate flag) has no engine counterpart — gravity blocks settle through
 *   the live scheduled-update system instead.</li>
 * </ul>
 *
 * <p>The nether's own constants: sea level 63 (the constructor's
 * {@code world.setSeaLevel(63)}), the lava fill below {@code 63/2+1 = 32},
 * the skin band at {@code 63+1 = 64}, biomes all HELL (the reference's
 * FixedBiomeSource(Biome.HELL), the wire id 8), world height 128.</p>
 */
public final class NetherWorldGenerator implements WorldGenerator {

    /** The nether's sea level (the reference constructor's setSeaLevel(63)). */
    public static final int SEA_LEVEL = 63;
    /** The lava sea's top (the buildTerrain {@code j = seaLevel / 2 + 1}). */
    public static final int LAVA_LEVEL = SEA_LEVEL / 2 + 1;
    /** The nether's world height (every carve/fill loop caps at 128). */
    public static final int WORLD_HEIGHT = 128;

    private final BlockRegistry registry;
    private final long seed;
    private final Random random;
    private final BlockType air;
    private final BlockType bedrock;
    private final BlockType netherrack;
    private final BlockType soulSand;
    private final BlockType gravel;
    private final BlockType lava;
    private final BlockType flowingLava;
    private final BlockType fire;
    private final BlockType glowstone;
    private final BlockType quartzOre;
    private final BlockType brownMushroom;
    private final BlockType redMushroom;

    private double[] sandBuffer = new double[256];
    private double[] gravelBuffer = new double[256];
    private double[] depthBuffer = new double[256];
    private double[] heightMap;
    /** The noise stack, in the reference's construction order (the stream shares one Random). */
    private final PerlinNoise minLimitPerlinNoise;
    private final PerlinNoise maxLimitPerlinNoise;
    private final PerlinNoise perlinNoise1;
    private final PerlinNoise perlinNoise2;
    private final PerlinNoise perlinNoise3;
    private final PerlinNoise scaleNoise;
    private final PerlinNoise depthNoise;
    private final NetherCaveCarver cave;

    double[] perlinNoiseBuffer;
    double[] minLimitPerlinNoiseBuffer;
    double[] maxLimitPerlinNoiseBuffer;
    double[] scaleNoiseBuffer;
    double[] depthNoiseBuffer;

    public NetherWorldGenerator(BlockRegistry registry, long seed) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.seed = seed;
        this.random = new Random(seed);
        this.minLimitPerlinNoise = new PerlinNoise(this.random, 16);
        this.maxLimitPerlinNoise = new PerlinNoise(this.random, 16);
        this.perlinNoise1 = new PerlinNoise(this.random, 8);
        this.perlinNoise2 = new PerlinNoise(this.random, 4);
        this.perlinNoise3 = new PerlinNoise(this.random, 4);
        this.scaleNoise = new PerlinNoise(this.random, 10);
        this.depthNoise = new PerlinNoise(this.random, 16);
        this.cave = new NetherCaveCarver(registry);
        this.air = registry.require(Identifier.parse("minecraft:air"));
        this.bedrock = registry.require(Identifier.parse("minecraft:bedrock"));
        this.netherrack = registry.require(Identifier.parse("minecraft:netherrack"));
        this.soulSand = registry.require(Identifier.parse("minecraft:soul_sand"));
        this.gravel = registry.require(Identifier.parse("minecraft:gravel"));
        this.lava = registry.require(Identifier.parse("minecraft:lava"));
        this.flowingLava = FluidBlocks.typeOf(FluidBlocks.Kind.LAVA, 1);
        this.fire = registry.require(Identifier.parse("minecraft:fire"));
        this.glowstone = registry.require(Identifier.parse("minecraft:glowstone"));
        this.quartzOre = registry.require(Identifier.parse("minecraft:quartz_ore"));
        this.brownMushroom = registry.require(Identifier.parse("minecraft:brown_mushroom"));
        this.redMushroom = registry.require(Identifier.parse("minecraft:red_mushroom"));
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public int groundLevel() {
        return spawnScan()[1];
    }

    @Override
    public Position spawnPosition() {
        int[] at = spawnScan();
        return new Position(at[0] + 0.5, at[1] + 1.0, at[2] + 0.5);
    }

    /**
     * The spawn anchor scan (the nether is never a spawn dimension — the
     * reference's isValidSpawnPoint returns false — but the engine contract
     * wants a standable cell): the first column on the +x diagonal whose
     * INTERIOR floor (a solid cell with air above, below the top wall band)
     * sits above the lava sea, falling back to the origin column's floor.
     * Deterministic via the memoized origin-chunk generation.
     */
    private int[] spawnScan() {
        if (spawnAnchor != null) {
            return spawnAnchor;
        }
        EngineChunk origin = originChunk();
        int[] best = {0, interiorFloorY(origin, 0, 0), 0};
        for (int r = 4; r < 64; r += 4) {
            int lx = r & 0xF;
            int lz = r & 0xF;
            int top = interiorFloorY(origin, lx, lz);
            if (top > LAVA_LEVEL) {
                best = new int[]{r, top, r};
                break;
            }
        }
        spawnAnchor = best;
        return best;
    }

    private int[] spawnAnchor;

    /** The highest solid cell with air above (a standable floor), scanning
     * under the top wall band; 32 (the lava level) when none exists. */
    private int interiorFloorY(EngineChunk chunk, int lx, int lz) {
        for (int y = 120; y > LAVA_LEVEL; y--) {
            if (!chunk.getBlock(lx, y, lz).equals(air)
                    && chunk.getBlock(lx, y + 1, lz).equals(air)) {
                return y;
            }
        }
        return LAVA_LEVEL;
    }

    private EngineChunk originChunk;

    /** The (0,0) chunk, generated once for the spawn scan. */
    private EngineChunk originChunk() {
        if (originChunk == null) {
            originChunk = new EngineChunk(new ChunkPosition(0, 0), air);
            generate(originChunk);
        }
        return originChunk;
    }

    @Override
    public void generate(EngineChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        ChunkPosition at = chunk.position();
        int chunkX = at.x();
        int chunkZ = at.z();
        // The reference getChunk's per-chunk re-seed (the terrain stream).
        this.random.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L);
        buildTerrain(chunkX, chunkZ, chunk);
        buildSurfaces(chunkX, chunkZ, chunk);
        this.cave.carve(seed, chunkX, chunkZ, chunk);
        decorate(chunkX, chunkZ, chunk);
        // The reference's FixedBiomeSource(Biome.HELL): every cell is HELL (wire id 8).
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                chunk.setBiome(lx, lz, (byte) 8);
            }
        }
    }

    /**
     * The reference buildTerrain (lines 80-142): the 4x4 lattice of noise
     * cells trilinearly interpolated into the 128-tall netherrack/lava shape.
     */
    private void buildTerrain(int chunkX, int chunkZ, EngineChunk chunk) {
        int i = 4;
        int j = LAVA_LEVEL;
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
                                BlockType blockstate = null;
                                if (l1 * 8 + i2 < j) {
                                    blockstate = this.lava;
                                }

                                if (d15 > 0.0) {
                                    blockstate = this.netherrack;
                                }

                                int l2 = j2 + j1 * 4;
                                int i3 = i2 + l1 * 8;
                                int j3 = k2 + k1 * 4;
                                set(chunk, l2, i3, j3, blockstate);
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
     * The reference generateHeightMap (lines 233-298): the five-noise walk —
     * scale (1.0, 0.0, 1.0) and depth (100.0, 0.0, 100.0) single-row reads,
     * the three 3D stacks at 684.412/2053.236 scales — folded by the cosine
     * profile ({@code cos(j * PI * 6 / 17) * 2} with the near-wall dents) and
     * the min/max limit blend through perlin1's normalized gate, plus the two
     * edge tapers (the top's slide to -10 over the last four rows, the
     * reference's dead {@code k < d3} bottom branch preserved).
     */
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
                        d10 = Math.max(0.0, Math.min(1.0, d10));
                        d4 = d4 * (1.0 - d10) + -10.0 * d10;
                    }

                    heightMap[i] = d4;
                    i++;
                }
            }
        }

        return heightMap;
    }

    /**
     * The reference buildSurfaces (lines 147-208): the three surface-noise
     * reads (soul sand, gravel, depth — note the reference reads the buffers
     * TRANSPOSED: index {@code [j + k * 16]} against getRegion's
     * {@code [x + z * 16]} layout, the vanilla quirk preserved), then the
     * 127-to-0 column walk — the bedrock bands (the {@code j1 < 127 -
     * nextInt(5) && j1 > nextInt(5)} shape with its short-circuiting stream
     * consumption), the skin choice at the band around {@code seaLevel + 1},
     * the lava fill under the skin, and the depth-counted netherrack
     * subsurface.
     */
    private void buildSurfaces(int chunkX, int chunkZ, EngineChunk chunk) {
        int i = SEA_LEVEL + 1;
        double d0 = 0.03125;
        this.sandBuffer = this.perlinNoise2.getRegion(this.sandBuffer, chunkX * 16, chunkZ * 16, 0, 16, 16, 1, d0, d0, 1.0);
        this.gravelBuffer = this.perlinNoise2.getRegion(this.gravelBuffer, chunkX * 16, 109, chunkZ * 16, 16, 1, 16, d0, 1.0, d0);
        this.depthBuffer = this.perlinNoise3.getRegion(this.depthBuffer, chunkX * 16, chunkZ * 16, 0, 16, 16, 1, d0 * 2.0, d0 * 2.0, d0 * 2.0);

        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                boolean flag = this.sandBuffer[j + k * 16] + this.random.nextDouble() * 0.2 > 0.0;
                boolean flag1 = this.gravelBuffer[j + k * 16] + this.random.nextDouble() * 0.2 > 0.0;
                int l = (int) (this.depthBuffer[j + k * 16] / 3.0 + 3.0 + this.random.nextDouble() * 0.25);
                int i1 = -1;
                BlockType blockstate = this.netherrack;
                BlockType blockstate1 = this.netherrack;

                for (int j1 = 127; j1 >= 0; j1--) {
                    if (j1 < 127 - this.random.nextInt(5) && j1 > this.random.nextInt(5)) {
                        BlockType blockstate2 = chunk.getBlock(k, j1, j);
                        if (blockstate2.equals(this.air)) {
                            i1 = -1;
                        } else if (blockstate2.equals(this.netherrack)) {
                            if (i1 == -1) {
                                if (l <= 0) {
                                    blockstate = null;
                                    blockstate1 = this.netherrack;
                                } else if (j1 >= i - 4 && j1 <= i + 1) {
                                    blockstate = this.netherrack;
                                    blockstate1 = this.netherrack;
                                    if (flag1) {
                                        blockstate = this.gravel;
                                        blockstate1 = this.netherrack;
                                    }

                                    if (flag) {
                                        blockstate = this.soulSand;
                                        blockstate1 = this.soulSand;
                                    }
                                }

                                if (j1 < i && (blockstate == null || blockstate.equals(this.air))) {
                                    blockstate = this.lava;
                                }

                                i1 = l;
                                if (j1 >= i - 1) {
                                    set(chunk, k, j1, j, blockstate);
                                } else {
                                    set(chunk, k, j1, j, blockstate1);
                                }
                            } else if (i1 > 0) {
                                i1--;
                                set(chunk, k, j1, j, blockstate1);
                            }
                        }
                    } else {
                        chunk.setBlock(k, j1, j, this.bedrock);
                    }
                }
            }
        }
    }

    /**
     * The reference populateChunk's decorate pass (lines 306-348), fortress
     * skipped (structures ride their own slice). Order and roll shapes
     * verbatim: eight lava pockets (the enclosed form), the fire-patch count
     * roll, the upside-down cluster count roll, ten glowstone clusters, the
     * two mushroom booleans, sixteen quartz veins, sixteen exposed lava
     * pockets. Re-seeded per chunk (the determinism adaptation above).
     */
    private void decorate(int chunkX, int chunkZ, EngineChunk chunk) {
        this.random.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L);

        for (int i = 0; i < 8; i++) {
            this.liquidPocket(chunk, this.random,
                    this.random.nextInt(16) + 8, this.random.nextInt(120) + 4, this.random.nextInt(16) + 8, false);
        }

        for (int j = 0; j < this.random.nextInt(this.random.nextInt(10) + 1) + 1; j++) {
            this.firePatch(chunk, this.random,
                    this.random.nextInt(16) + 8, this.random.nextInt(120) + 4, this.random.nextInt(16) + 8);
        }

        for (int k = 0; k < this.random.nextInt(this.random.nextInt(10) + 1); k++) {
            this.glowstoneCluster(chunk, this.random,
                    this.random.nextInt(16) + 8, this.random.nextInt(120) + 4, this.random.nextInt(16) + 8);
        }

        for (int l = 0; l < 10; l++) {
            this.glowstoneCluster(chunk, this.random,
                    this.random.nextInt(16) + 8, this.random.nextInt(128), this.random.nextInt(16) + 8);
        }

        if (this.random.nextBoolean()) {
            this.plantPatch(chunk, this.random,
                    this.random.nextInt(16) + 8, this.random.nextInt(128), this.random.nextInt(16) + 8, this.brownMushroom);
        }

        if (this.random.nextBoolean()) {
            this.plantPatch(chunk, this.random,
                    this.random.nextInt(16) + 8, this.random.nextInt(128), this.random.nextInt(16) + 8, this.redMushroom);
        }

        for (int i1 = 0; i1 < 16; i1++) {
            this.quartzVein(chunk, this.random,
                    this.random.nextInt(16), this.random.nextInt(108) + 10, this.random.nextInt(16));
        }

        for (int j1 = 0; j1 < 16; j1++) {
            this.liquidPocket(chunk, this.random,
                    this.random.nextInt(16), this.random.nextInt(108) + 10, this.random.nextInt(16), true);
        }
    }

    /**
     * The reference FirePatchFeature.place: 64 attempts, an air cell on
     * netherrack becomes fire (the offset pair {@code nextInt(8) -
     * nextInt(8)} per axis, the y band +/- 4).
     */
    private void firePatch(EngineChunk chunk, Random random, int px, int py, int pz) {
        for (int i = 0; i < 64; i++) {
            int x = px + random.nextInt(8) - random.nextInt(8);
            int y = py + random.nextInt(4) - random.nextInt(4);
            int z = pz + random.nextInt(8) - random.nextInt(8);
            if (inBounds(x, y, z) && chunk.getBlock(x, y, z).equals(this.air)
                    && chunk.getBlock(x, y - 1, z).equals(this.netherrack)) {
                chunk.setBlock(x, y, z, this.fire);
            }
        }
    }

    /**
     * The reference GlowstoneClusterFeature.place (and its byte-identical
     * upside-down twin — the reference ships the same walk twice): an air
     * cell under netherrack grows a glowstone cluster downward — the anchor
     * cell, then 1500 attempts placing glowstone into air cells that touch
     * EXACTLY ONE existing glowstone neighbor (the {@code j == 1} rule, the
     * count breaking early past one).
     */
    private boolean glowstoneCluster(EngineChunk chunk, Random random, int px, int py, int pz) {
        if (!inBounds(px, py, pz) || !chunk.getBlock(px, py, pz).equals(this.air)) {
            return false;
        }

        int aboveY = py + 1;
        if (!inBounds(px, aboveY, pz) || !chunk.getBlock(px, aboveY, pz).equals(this.netherrack)) {
            return false;
        }

        chunk.setBlock(px, py, pz, this.glowstone);

        for (int i = 0; i < 1500; i++) {
            int x = px + random.nextInt(8) - random.nextInt(8);
            int y = py - random.nextInt(12);
            int z = pz + random.nextInt(8) - random.nextInt(8);
            if (inBounds(x, y, z) && chunk.getBlock(x, y, z).equals(this.air)) {
                int j = 0;

                for (int[] direction : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                    int nx = x + direction[0];
                    int ny = y + direction[1];
                    int nz = z + direction[2];
                    if (inBounds(nx, ny, nz) && chunk.getBlock(nx, ny, nz).equals(this.glowstone)) {
                        j++;
                    }

                    if (j > 1) {
                        break;
                    }
                }

                if (j == 1) {
                    chunk.setBlock(x, y, z, this.glowstone);
                }
            }
        }

        return true;
    }

    /**
     * The reference PlantFeature.place with the mushroom's canSurvive (the
     * PlantBlock rule: the block below is an opaque cube): 64 attempts, an
     * air cell over solid ground takes the plant.
     */
    private void plantPatch(EngineChunk chunk, Random random, int px, int py, int pz, BlockType plant) {
        for (int i = 0; i < 64; i++) {
            int x = px + random.nextInt(8) - random.nextInt(8);
            int y = py + random.nextInt(4) - random.nextInt(4);
            int z = pz + random.nextInt(8) - random.nextInt(8);
            if (inBounds(x, y, z) && chunk.getBlock(x, y, z).equals(this.air)
                    && canSurviveOn(chunk, x, y - 1, z)) {
                chunk.setBlock(x, y, z, plant);
            }
        }
    }

    /** The mushroom's soil rule: the block below is a full opaque cube. */
    private boolean canSurviveOn(EngineChunk chunk, int x, int y, int z) {
        if (!inBounds(x, y, z)) {
            return false;
        }
        BlockType below = chunk.getBlock(x, y, z);
        if (below.equals(this.air) || below.equals(this.fire)) {
            return false;
        }
        Identifier id = below.identifier();
        String value = id.value();
        // The walk-through forms (flora, portal, fluids) never host a mushroom.
        return !value.equals("nether_portal") && !value.equals("nether_portal_z")
                && !FluidBlocks.isFluid(id);
    }

    /**
     * The reference VeinFeature.place (size 14, replaceable = NETHERRACK):
     * the sin/cos-shaped two-endpoint walk whose per-step radius balloons by
     * the {@code (sin(PI * t) + 1) * nextDouble() * size / 16 + 1} band, the
     * per-cell ellipsoid test, netherrack-only replacement.
     */
    private void quartzVein(EngineChunk chunk, Random random, int px, int py, int pz) {
        int size = 14;
        float f = random.nextFloat() * (float) Math.PI;
        double d0 = px + 8 + ReferenceMath.sin(f) * size / 8.0F;
        double d1 = px + 8 - ReferenceMath.sin(f) * size / 8.0F;
        double d2 = pz + 8 + ReferenceMath.cos(f) * size / 8.0F;
        double d3 = pz + 8 - ReferenceMath.cos(f) * size / 8.0F;
        double d4 = py + random.nextInt(3) - 2;
        double d5 = py + random.nextInt(3) - 2;

        for (int i = 0; i < size; i++) {
            float f1 = (float) i / size;
            double d6 = d0 + (d1 - d0) * f1;
            double d7 = d4 + (d5 - d4) * f1;
            double d8 = d2 + (d3 - d2) * f1;
            double d9 = random.nextDouble() * size / 16.0;
            double d10 = (ReferenceMath.sin((float) Math.PI * f1) + 1.0F) * d9 + 1.0;
            double d11 = (ReferenceMath.sin((float) Math.PI * f1) + 1.0F) * d9 + 1.0;
            int j = floorInt(d6 - d10 / 2.0);
            int k = floorInt(d7 - d11 / 2.0);
            int l = floorInt(d8 - d10 / 2.0);
            int i1 = floorInt(d6 + d10 / 2.0);
            int j1 = floorInt(d7 + d11 / 2.0);
            int k1 = floorInt(d8 + d10 / 2.0);

            for (int l1 = j; l1 <= i1; l1++) {
                double d12 = (l1 + 0.5 - d6) / (d10 / 2.0);
                if (d12 * d12 < 1.0) {
                    for (int i2 = k; i2 <= j1; i2++) {
                        double d13 = (i2 + 0.5 - d7) / (d11 / 2.0);
                        if (d12 * d12 + d13 * d13 < 1.0) {
                            for (int j2 = l; j2 <= k1; j2++) {
                                double d14 = (j2 + 0.5 - d8) / (d10 / 2.0);
                                if (d12 * d12 + d13 * d13 + d14 * d14 < 1.0) {
                                    if (inBounds(l1, i2, j2) && chunk.getBlock(l1, i2, j2).equals(this.netherrack)) {
                                        chunk.setBlock(l1, i2, j2, this.quartzOre);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The reference LiquidPocketFeature.place: the cell under netherrack, air
     * or netherrack itself, with the neighbor ledger — i counts netherrack
     * among W/E/N/S/down, j counts air among the same; the enclosed form
     * places at i == 5, the exposed form at (i == 4 && j == 1) (the
     * {@code canBeExposedToAir} gate). The placed lava is the reference's
     * FLOWING_LAVA default state (flowing level 1 on the engine's fluid
     * ladder).
     */
    private void liquidPocket(EngineChunk chunk, Random random, int px, int py, int pz, boolean canBeExposedToAir) {
        if (!inBounds(px, py, pz) || !inBounds(px, py + 1, pz)) {
            return;
        }

        if (!chunk.getBlock(px, py + 1, pz).equals(this.netherrack)) {
            return;
        }

        BlockType self = chunk.getBlock(px, py, pz);
        if (!self.equals(this.air) && !self.equals(this.netherrack)) {
            return;
        }

        int i = 0;
        int j = 0;
        for (int[] direction : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int nx = px + direction[0];
            int nz = pz + direction[1];
            if (inBounds(nx, py, nz)) {
                BlockType side = chunk.getBlock(nx, py, nz);
                if (side.equals(this.netherrack)) {
                    i++;
                }
                if (side.equals(this.air)) {
                    j++;
                }
            }
        }
        int dy = py - 1;
        if (inBounds(px, dy, pz)) {
            BlockType down = chunk.getBlock(px, dy, pz);
            if (down.equals(this.netherrack)) {
                i++;
            }
            if (down.equals(this.air)) {
                j++;
            }
        }

        if (!canBeExposedToAir && i == 4 && j == 1 || i == 5) {
            chunk.setBlock(px, py, pz, this.flowingLava);
        }
    }

    /** The write helper: {@code null} states are the reference's air. */
    private void set(EngineChunk chunk, int x, int y, int z, BlockType type) {
        if (y < 0 || y >= 256) {
            return;
        }
        chunk.setBlock(x, y, z, type == null ? this.air : type);
    }

    private static boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < 16 && y >= 0 && y < WORLD_HEIGHT && z >= 0 && z < 16;
    }

    /** The reference MathHelper.floor: largest int not above the value. */
    private static int floorInt(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
