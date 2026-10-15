package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.world.noise.PerlinNoise;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;

import java.util.Objects;
import java.util.Random;

/**
 * Ported from reference/1.8.8 net/minecraft/world/gen/chunk/
 * TheEndChunkGenerator.java (lines 1-294) + the End's decorate walk
 * (world/gen/feature/decorator/EndIslandDecorator.java + feature/
 * EndPillarFeature.java, TheEndBiome's own decorator).
 *
 * <h2>The island shape (buildTerrain lines 49-106 + generateHeightMap
 * lines 164-227)</h2>
 *
 * The five-noise stack (minLimit 16 octaves, maxLimit 16, perlinNoise1 8,
 * scale 10, depth 16 — the reference's own construction order off one
 * seed-shared Random). The 2x2x33 heightmap sampled at x/z stride 1 (the
 * reference's {@code i = 2, j = 3, k = 33, l = 3} band), the trilinear
 * 0.25/0.125 fill into the chunk's 16x128x16 grid: END_STONE wherever the
 * interpolated value is above zero. The island envelope
 * {@code 100 - sqrt(x*x + z*z) * 8} clamped to [-100, 80] — the central
 * island falls off to void by roughly radius 100 — with the vertical
 * fades: the -3000 shelf above heightmap midpoint (the {@code l > 16}
 * walk) and the -30 floor below sample 8.
 *
 * <h2>The surfaces pass (buildSurfaces lines 111-144)</h2>
 *
 * Ported verbatim as the zero-write walk it is: the reference scans the
 * column for STONE, and the End's terrain carries only END_STONE — no
 * cell ever takes the write (the walk is the overworld's own surface
 * builder reused). The loop stays so a future stone source keeps the
 * reference's exact shape.
 *
 * <h2>The decorate (EndIslandDecorator.decorate lines 17-30)</h2>
 *
 * <ul>
 *   <li><b>The veins</b>: the reference's {@code placeVeins()} walk —
 *       every vein feature probes for STONE to replace, and the End
 *       carries none, so the walk places nothing. Skipped whole (the
 *       stream it would consume gates only its own no-op writes); the
 *       observable terrain is identical.</li>
 *   <li><b>The pillar</b> (1-in-5 per chunk): the EndPillarFeature at a
 *       rolled +8..+23 offset — the base ring gate (the cell below every
 *       ring position must be END_STONE), the height
 *       {@code nextInt(32) + 6} and radius {@code nextInt(4) + 1}, the
 *       obsidian cylinder {@code dx*dx + dz*dz <= r*r + 1} from the
 *       surface to the top, and the BEDROCK cap one above the top. The
 *       crystal that rides the pillar top in the reference
 *       (EndPillarFeature lines 47-51) lands with Slice 62 — the
 *       populate record the engine drains at publish.</li>
 *   <li><b>The dragon</b>: the reference spawns the ender dragon when
 *       chunk (0,0) decorates — deferred to the dragon slice.</li>
 * </ul>
 *
 * <h2>Determinism adaptations (the nether port's own contract)</h2>
 *
 * <ul>
 *   <li>The reference's getChunk re-seeds per chunk
 *       ({@code chunkX * 341873128712L + chunkZ * 132897987541L}) and the
 *       decorate continues the same stream — the reference's decorate
 *       rolls then depend on generation ORDER. This port re-seeds the
 *       decorate pass from the same per-chunk formula so decoration is
 *       order-independent (rebuild-identical).</li>
 *   <li>The pillar writes clip to their own chunk's 16x16x128 grid (the
 *       feature starts at +8..+23 and the radius reaches 4 — a spill
 *       writes nothing out-of-chunk). The roll SHAPES are verbatim; only
 *       the landed writes clip.</li>
 *   <li>{@code FallingBlock.fallImmediately = true} (the reference's
 *       populate flag) has no engine counterpart — gravity blocks settle
 *       through the live scheduled-update system instead.</li>
 * </ul>
 *
 * <h2>The End's own constants</h2>
 *
 * The biome is THE_END (wire id 9, the reference's FixedBiomeSource), the
 * world height 128 (the 32-slice heightmap at quarter resolution), the
 * spawn anchor (100, 50, 0) is the dimension's forced spawn — the
 * arrival platform, not a generator product (TheEndDimension
 * getForcedSpawnPoint line 74).
 */
public final class TheEndWorldGenerator implements WorldGenerator {

    /** The End's world height (the heightmap's 33 slices at quarter resolution). */
    public static final int WORLD_HEIGHT = 128;
    /** The End biome's wire id (Biome.THE_END, the FixedBiomeSource's constant). */
    public static final int THE_END_BIOME_ID = 9;

    private final BlockRegistry registry;
    private final long seed;
    private final Random random;
    private final BlockType air;
    private final BlockType endStone;
    private final BlockType obsidian;
    private final BlockType bedrock;

    private double[] heightMap;
    /** The noise stack, in the reference's construction order (one shared Random). */
    private final PerlinNoise minLimitPerlinNoise;
    private final PerlinNoise maxLimitPerlinNoise;
    private final PerlinNoise perlinNoise1;
    private final PerlinNoise scaleNoise;
    private final PerlinNoise depthNoise;

    double[] perlinNoiseBuffer;
    double[] minLimitPerlinNoiseBuffer;
    double[] maxLimitPerlinNoiseBuffer;
    double[] scaleNoiseBuffer;
    double[] depthNoiseBuffer;

    public TheEndWorldGenerator(BlockRegistry registry, long seed) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.seed = seed;
        this.random = new Random(seed);
        this.minLimitPerlinNoise = new PerlinNoise(this.random, 16);
        this.maxLimitPerlinNoise = new PerlinNoise(this.random, 16);
        this.perlinNoise1 = new PerlinNoise(this.random, 8);
        this.scaleNoise = new PerlinNoise(this.random, 10);
        this.depthNoise = new PerlinNoise(this.random, 16);
        this.air = registry.require(Identifier.parse("minecraft:air"));
        this.endStone = registry.require(Identifier.parse("minecraft:end_stone"));
        this.obsidian = registry.require(Identifier.parse("minecraft:obsidian"));
        this.bedrock = registry.require(Identifier.parse("minecraft:bedrock"));
    }

    @Override
    public long seed() {
        return seed;
    }

    /**
     * The End is never a spawn dimension (the reference's
     * hasSpawnPoint returns false and prepareWorlds prepares only
     * worlds[0]); the engine contract wants a standable cell — the
     * origin chunk's island surface, which the heightmap guarantees
     * at the origin (the envelope's 100-point center).
     */
    @Override
    public int groundLevel() {
        EngineChunk origin = originChunk();
        for (int y = WORLD_HEIGHT - 1; y > 0; y--) {
            if (!origin.getBlock(0, y, 0).equals(air)) {
                return y;
            }
        }
        return 0;
    }

    @Override
    public Position spawnPosition() {
        return new Position(0.5, groundLevel() + 1.0, 0.5);
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
        buildSurfaces(chunk);
        // The decorate re-seed (the order-independent adaptation): the
        // reference's populateChunk continues the terrain stream; this
        // port re-seeds so decoration is rebuild-identical.
        this.random.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L);
        decorate(chunkX, chunkZ, chunk);
        // The reference's FixedBiomeSource(Biome.THE_END): every cell 9.
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                chunk.setBiome(lx, lz, (byte) THE_END_BIOME_ID);
            }
        }
    }

    // ------------------------------------------------------------------
    // buildTerrain (TheEndChunkGenerator lines 49-106)
    // ------------------------------------------------------------------

    private void buildTerrain(int chunkX, int chunkZ, EngineChunk chunk) {
        int i = 2;
        int j = i + 1;
        int k = 33;
        int l = i + 1;
        this.heightMap = generateHeightMap(this.heightMap, chunkX * i, 0, chunkZ * i, j, k, l);

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
                                BlockType blockstate = null;
                                if (d15 > 0.0) {
                                    blockstate = endStone;
                                }

                                int k2 = i2 + i1 * 8;
                                int l2 = l1 + k1 * 4;
                                int i3 = j2 + j1 * 8;
                                if (blockstate != null) {
                                    chunk.setBlock(k2, l2, i3, blockstate);
                                } else {
                                    chunk.setBlock(k2, l2, i3, air);
                                }
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

    // ------------------------------------------------------------------
    // generateHeightMap (TheEndChunkGenerator lines 164-227)
    // ------------------------------------------------------------------

    private double[] generateHeightMap(double[] heightMap, int x, int y, int z,
                                       int sizeX, int sizeY, int sizeZ) {
        if (heightMap == null) {
            heightMap = new double[sizeX * sizeY * sizeZ];
        }

        double d0 = 684.412;
        double d1 = 684.412;
        this.scaleNoiseBuffer = this.scaleNoise.getRegion(
                this.scaleNoiseBuffer, x, z, sizeX, sizeZ, 1.121, 1.121, 0.5);
        this.depthNoiseBuffer = this.depthNoise.getRegion(
                this.depthNoiseBuffer, x, z, sizeX, sizeZ, 200.0, 200.0, 0.5);
        d0 *= 2.0;
        this.perlinNoiseBuffer = this.perlinNoise1.getRegion(
                this.perlinNoiseBuffer, x, y, z, sizeX, sizeY, sizeZ,
                d0 / 80.0, d1 / 160.0, d0 / 80.0);
        this.minLimitPerlinNoiseBuffer = this.minLimitPerlinNoise.getRegion(
                this.minLimitPerlinNoiseBuffer, x, y, z, sizeX, sizeY, sizeZ, d0, d1, d0);
        this.maxLimitPerlinNoiseBuffer = this.maxLimitPerlinNoise.getRegion(
                this.maxLimitPerlinNoiseBuffer, x, y, z, sizeX, sizeY, sizeZ, d0, d1, d0);
        int i = 0;

        for (int j = 0; j < sizeX; j++) {
            for (int k = 0; k < sizeZ; k++) {
                float f = (j + x) / 1.0F;
                float f1 = (k + z) / 1.0F;
                float f2 = 100.0F - (float) Math.sqrt(f * f + f1 * f1) * 8.0F;
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
                        d6 = Math.max(0.0, Math.min(1.0, d6));
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

    // ------------------------------------------------------------------
    // buildSurfaces (TheEndChunkGenerator lines 111-144) — the zero-write
    // walk (the End carries no STONE; the loop keeps the reference's
    // exact shape for any future stone source).
    // ------------------------------------------------------------------

    private void buildSurfaces(EngineChunk chunk) {
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int k = 1;
                int l = -1;
                BlockType blockstate = endStone;
                BlockType blockstate1 = endStone;

                for (int i1 = 127; i1 >= 0; i1--) {
                    BlockType blockstate2 = chunk.getBlock(i, i1, j);
                    if (blockstate2.equals(air)) {
                        l = -1;
                    } else if (isStone(blockstate2)) {
                        if (l == -1) {
                            if (k <= 0) {
                                blockstate = air;
                                blockstate1 = endStone;
                            }

                            l = k;
                            if (i1 >= 0) {
                                chunk.setBlock(i, i1, j, blockstate);
                            } else {
                                chunk.setBlock(i, i1, j, blockstate1);
                            }
                        } else if (l > 0) {
                            l--;
                            chunk.setBlock(i, i1, j, blockstate1);
                        }
                    }
                }
            }
        }
    }

    /** The reference's material gate: the surfaces walk only rewrites STONE. */
    private boolean isStone(BlockType type) {
        return type.identifier().namespace().equals("minecraft")
                && type.identifier().value().equals("stone");
    }

    // ------------------------------------------------------------------
    // The decorate (EndIslandDecorator.decorate + EndPillarFeature)
    // ------------------------------------------------------------------

    private void decorate(int chunkX, int chunkZ, EngineChunk chunk) {
        // EndIslandDecorator line 21: the 1-in-5 pillar roll. The veins
        // walk before it places nothing (no STONE in the End) and is
        // skipped whole — the ledgered adaptation.
        if (this.random.nextInt(5) == 0) {
            int i = this.random.nextInt(16) + 8;
            int j = this.random.nextInt(16) + 8;
            placePillar(chunk, chunkX * 16 + i, chunkZ * 16 + j);
        }
        // The dragon's chunk-(0,0) spawn rides the dragon slice (the
        // reference's EndIslandDecorator lines 25-29).
    }

    /**
     * The pillar (EndPillarFeature.place lines 24-56): the surface-height
     * gate (air at the top, END_STONE below), the base ring gate (every
     * ring cell at {@code y - 1} must be END_STONE), the height
     * {@code nextInt(32) + 6}, the radius {@code nextInt(4) + 1}, the
     * obsidian cylinder {@code dx*dx + dz*dz <= r*r + 1}, the crystal
     * record at the top (lines 47-49), and the BEDROCK cap at the top.
     * Writes clip to the owning chunk (the nether port's own contract).
     */
    private void placePillar(EngineChunk chunk, int worldX, int worldZ) {
        // world.getSurfaceHeight: the top non-air cell of the column.
        int surfaceY = -1;
        for (int y = WORLD_HEIGHT - 1; y >= 0; y--) {
            int lx = worldX & 0xF;
            int lz = worldZ & 0xF;
            if (!chunk.getBlock(lx, y, lz).equals(air)) {
                surfaceY = y + 1; // the first air above the surface
                break;
            }
        }
        if (surfaceY < 0) {
            return; // no surface: the feature's own refusal
        }
        // The gate (place lines 24-25): air at the pos, END_STONE below.
        if (!chunk.getBlock(worldX & 0xF, surfaceY, worldZ & 0xF).equals(air)
                || !chunk.getBlock(worldX & 0xF, surfaceY - 1, worldZ & 0xF).equals(endStone)) {
            return;
        }

        int height = this.random.nextInt(32) + 6;
        int radius = this.random.nextInt(4) + 1;

        // The base ring gate (place lines 27-37): every ring cell at
        // y - 1 must be END_STONE.
        for (int k = worldX - radius; k <= worldX + radius; k++) {
            for (int l = worldZ - radius; l <= worldZ + radius; l++) {
                int i1 = k - worldX;
                int j1 = l - worldZ;
                if (i1 * i1 + j1 * j1 <= radius * radius + 1
                        && !blockAt(chunk, k, surfaceY - 1, l).equals(endStone)) {
                    return;
                }
            }
        }

        // The cylinder fill (place lines 39-50).
        for (int l1 = surfaceY; l1 < surfaceY + height && l1 < 256; l1++) {
            for (int i2 = worldX - radius; i2 <= worldX + radius; i2++) {
                for (int j2 = worldZ - radius; j2 <= worldZ + radius; j2++) {
                    int k2 = i2 - worldX;
                    int k1 = j2 - worldZ;
                    if (k2 * k2 + k1 * k1 <= radius * radius + 1) {
                        setBlockInChunk(chunk, i2, l1, j2, obsidian);
                    }
                }
            }
        }

        // The crystal on the pillar top (EndPillarFeature lines 47-49): the
        // entity at the +0.5 centers, the pillar-top y, and the reference's
        // own {@code nextFloat() * 360} yaw roll — recorded as a populate
        // attachment (the engine drains it at the chunk publish; the
        // crystal system is live-state, the generator stays offline). The
        // BEDROCK cap at the same cell lands after it, the reference's own
        // write order (line 50) — the first tick's fire then eats the cap
        // (EnderCrystalEntity.tick's feet-cell write).
        chunk.addPendingEnderCrystal(worldX, surfaceY + height, worldZ,
                this.random.nextFloat() * 360.0f);
        // The bedrock cap (place line 50, one above the pillar top).
        setBlockInChunk(chunk, worldX, surfaceY + height, worldZ, bedrock);
    }

    /** The in-chunk read (out-of-chunk reads answer air — the clip contract). */
    private BlockType blockAt(EngineChunk chunk, int worldX, int y, int worldZ) {
        int lx = worldX & 0xF;
        int lz = worldZ & 0xF;
        int baseX = chunk.position().x() << 4;
        int baseZ = chunk.position().z() << 4;
        if (worldX < baseX || worldX >= baseX + 16
                || worldZ < baseZ || worldZ >= baseZ + 16
                || y < 0 || y > 255) {
            return air;
        }
        return chunk.getBlock(lx, y, lz);
    }

    /** The in-chunk write (out-of-chunk writes clip — the nether contract). */
    private void setBlockInChunk(EngineChunk chunk, int worldX, int y, int worldZ, BlockType type) {
        int baseX = chunk.position().x() << 4;
        int baseZ = chunk.position().z() << 4;
        if (worldX < baseX || worldX >= baseX + 16
                || worldZ < baseZ || worldZ >= baseZ + 16
                || y < 0 || y > 255) {
            return;
        }
        chunk.setBlock(worldX & 0xF, y, worldZ & 0xF, type);
    }
}
