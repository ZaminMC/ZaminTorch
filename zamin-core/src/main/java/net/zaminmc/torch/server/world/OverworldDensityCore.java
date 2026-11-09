package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.noise.PerlinNoise;
import net.zaminmc.torch.server.world.noise.PerlinSimplexNoise;

/**
 * THE VANILLA TERRAIN NOISE CORE (Wave TN — the owner's report "terrain is
 * not vanilla like... biomes... all sucks" closed at its root): the
 * reference's OverworldChunkGenerator density stack, ported verbatim from
 * reference/1.8.8/net/minecraft/world/gen/chunk/OverworldChunkGenerator.java.
 *
 * <p>WHAT THIS REPLACES: the engine's own value-noise heightmap — fbm scales
 * 0.0085/0.0035 over biome coefficients *17, clamped 8..120 — a column model
 * that could never place an overhang, a cave roof, or a vanilla band
 * (the retired law stood in for the 4-noise density stack the generator's
 * own header confessed). The reference's model is a 4-noise DENSITY field:
 * two limit noises (16 octaves each) lerped by a main noise (8 octaves),
 * stretched over a per-column base-size line set by the 5x5 biome kernel's
 * depth/varying averages and the depth noise's folded walk — sampled on the
 * 5x33x5 grid (4 blocks in x/z, 8 in y) and trilinear-filled into the chunk
 * at 0.25/0.125 cell fractions. Stone goes where the density is positive —
 * ANYWHERE, including floating over air: the overhang-bearing world the
 * column model could never answer.</p>
 *
 * <p>THE CONSTRUCTION ORDER IS THE DETERMINISM: the seven noises build from
 * ONE {@code new Random(seed)} stream in the reference's own order (lines
 * 67-74) — minLimit 16, maxLimit 16, perlinNoise1 8, perlinNoise3 4 (the
 * simplex depth consult — the SURFACE pass's own {@code depthAt} reads this
 * very instance, the fourth of the stream), scaleNoise 10, depthNoise 16,
 * forestNoise 8. scaleNoise and forestNoise are CONSTRUCTED but never
 * consulted by the default walk (the reference's own leftovers — their
 * construction still consumes the stream, so the order is part of the
 * world's seed law and the port keeps them).</p>
 *
 * <p>THE LAYOUTS (each derived from the tree's own loops — three different
 * conventions ride in one generator): the noise buffers from the Perlin
 * octaves lay out {@code index = (x * sizeZ + z) * sizeY + y} (z-fastest —
 * ImprovedNoise's 3D walk); the heightMap therefore reads
 * {@code index = x * 165 + z * 33 + y} over the 5x5x33 grid; the fill's own
 * corner indexing (lines 104-107) is the same layout; the depth buffer from
 * the simplex octaves lays out x-fastest (the surface consult's own
 * transposition law rides on it — see NormalWorldGenerator's fill).</p>
 */
final class OverworldDensityCore {

    // ------------------------------------------------------------ the options
    // (OverworldGeneratorOptions.Builder's own default table, lines 181-197 —
    // the DEFAULT world type's constants, verbatim)

    static final float COORDINATE_SCALE = 684.412F;
    static final float HEIGHT_SCALE = 684.412F;
    static final float UPPER_LIMIT_SCALE = 512.0F;
    static final float LOWER_LIMIT_SCALE = 512.0F;
    static final float DEPTH_NOISE_SCALE_X = 200.0F;
    static final float DEPTH_NOISE_SCALE_Z = 200.0F;
    static final float DEPTH_NOISE_SCALE_EXPONENT = 0.5F;
    static final float MAIN_NOISE_SCALE_X = 80.0F;
    static final float MAIN_NOISE_SCALE_Y = 160.0F;
    static final float MAIN_NOISE_SCALE_Z = 80.0F;
    static final float BASE_SIZE = 8.5F;
    static final float STRETCH_Y = 12.0F;
    static final float BIOME_DEPTH_WEIGHT = 1.0F;
    static final float BIOME_DEPTH_OFFSET = 0.0F;
    static final float BIOME_SCALE_WEIGHT = 1.0F;
    static final float BIOME_SCALE_OFFSET = 0.0F;
    /** The reference's sea level (World.java line 62: 63 — the water fills y < 63). */
    static final int SEA_LEVEL = 63;

    /**
     * The world-type gate (the reference's {@code this.type ==
     * WorldGeneratorType.AMPLIFIED} consult, line 254): the engine's only
     * world type is the DEFAULT — the amplified swell never fires, the
     * branch carried as the reference's own shape.
     */
    private static final boolean AMPLIFIED = false;

    // ------------------------------------------------------------ the noises

    private final PerlinNoise minLimitPerlinNoise;
    private final PerlinNoise maxLimitPerlinNoise;
    private final PerlinNoise perlinNoise1;
    private final PerlinSimplexNoise perlinNoise3;
    private final PerlinNoise scaleNoise;
    private final PerlinNoise depthNoise;
    private final PerlinNoise forestNoise;
    /** The 5x5 kernel weights (lines 78-83): 10 / sqrt(i*i + j*j + 0.2). */
    private final float[] biomeWeights = new float[25];

    OverworldDensityCore(long seed) {
        java.util.Random random = new java.util.Random(seed);
        this.minLimitPerlinNoise = new PerlinNoise(random, 16);
        this.maxLimitPerlinNoise = new PerlinNoise(random, 16);
        this.perlinNoise1 = new PerlinNoise(random, 8);
        this.perlinNoise3 = new PerlinSimplexNoise(random, 4);
        this.scaleNoise = new PerlinNoise(random, 10);
        this.depthNoise = new PerlinNoise(random, 16);
        this.forestNoise = new PerlinNoise(random, 8);
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                float f = 10.0F / (float) Math.sqrt(i * i + j * j + 0.2F);
                this.biomeWeights[i + 2 + (j + 2) * 5] = f;
            }
        }
    }

    /**
     * The surface depth consult (the reference's own {@code perlinNoise3} —
     * the shared ctor-stream instance the surface pass reads through
     * {@code getRegion(chunkX*16, chunkZ*16, 16, 16, 0.0625, 0.0625, 1.0)}).
     */
    PerlinSimplexNoise depthConsult() {
        return this.perlinNoise3;
    }

    // -------------------------------------------------------- the height map

    /**
     * generateHeightMap (the reference's lines 216-323) — the biome kernel
     * walk and the 33-step density walk, verbatim. The biome sample is the
     * 10x10 noise-grid area at {@code (chunkX*4 - 2, chunkZ*4 - 2)} (the
     * reference's getNoiseBiomes consult, line 96 — the 1:4 grid layer, the
     * +2/-2 centering so the 5x5 kernel reads a 2-cell margin).
     *
     * <p>Fresh buffers per call (the reference's reused arrays are its own
     * single-thread discipline; the engine generates chunks on compute
     * workers — the noise instances are immutable, so per-call buffers keep
     * the walk parallel with the same arithmetic).</p>
     *
     * @param x the area x in noise-grid cells (chunkX * 4)
     * @param y the area y (0 — the walk's own base)
     * @param z the area z in noise-grid cells (chunkZ * 4)
     * @param biomes the 10x10 noise-grid biome sample
     * @return the fresh 825-entry height map ({@code index = x*165 + z*33 + y})
     */
    double[] generateHeightMap(int x, int y, int z, Biome[] biomes) {
        double[] depthNoiseBuffer = this.depthNoise.getRegion(
                null, x, z, 5, 5, DEPTH_NOISE_SCALE_X, DEPTH_NOISE_SCALE_Z, DEPTH_NOISE_SCALE_EXPONENT);
        float f = COORDINATE_SCALE;
        float f1 = HEIGHT_SCALE;
        double[] perlinNoiseBuffer = this.perlinNoise1.getRegion(
                null,
                x,
                y,
                z,
                5,
                33,
                5,
                f / MAIN_NOISE_SCALE_X,
                f1 / MAIN_NOISE_SCALE_Y,
                f / MAIN_NOISE_SCALE_Z);
        double[] minLimitPerlinNoiseBuffer = this.minLimitPerlinNoise.getRegion(null, x, y, z, 5, 33, 5, f, f1, f);
        double[] maxLimitPerlinNoiseBuffer = this.maxLimitPerlinNoise.getRegion(null, x, y, z, 5, 33, 5, f, f1, f);
        double[] heightMap = new double[825];
        int i = 0;
        int j = 0;

        for (int k = 0; k < 5; k++) {
            for (int l = 0; l < 5; l++) {
                float f2 = 0.0F;
                float f3 = 0.0F;
                float f4 = 0.0F;
                int i1 = 2;
                Biome biome = biomes[k + 2 + (l + 2) * 10];

                for (int j1 = -i1; j1 <= i1; j1++) {
                    for (int k1 = -i1; k1 <= i1; k1++) {
                        Biome biome1 = biomes[k + j1 + 2 + (l + k1 + 2) * 10];
                        float f5 = BIOME_DEPTH_OFFSET + biome1.baseHeight() * BIOME_DEPTH_WEIGHT;
                        float f6 = BIOME_SCALE_OFFSET + biome1.heightVariation() * BIOME_SCALE_WEIGHT;
                        // The AMPLIFIED world type's swell (lines 254-257) — the DEFAULT
                        // type never takes it (the engine's only type; the branch kept
                        // as the reference's own shape).
                        if (AMPLIFIED && f5 > 0.0F) {
                            f5 = 1.0F + f5 * 2.0F;
                            f6 = 1.0F + f6 * 4.0F;
                        }

                        float f7 = this.biomeWeights[j1 + 2 + (k1 + 2) * 5] / (f5 + 2.0F);
                        if (biome1.baseHeight() > biome.baseHeight()) {
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
                double d7 = depthNoiseBuffer[j] / 8000.0;
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
                d8 = d8 * BASE_SIZE / 8.0;
                double d0 = BASE_SIZE + d8 * 4.0;

                for (int l1 = 0; l1 < 33; l1++) {
                    double d1 = (l1 - d0) * STRETCH_Y * 128.0 / 256.0 / d9;
                    if (d1 < 0.0) {
                        d1 *= 4.0;
                    }

                    double d2 = minLimitPerlinNoiseBuffer[i] / LOWER_LIMIT_SCALE;
                    double d3 = maxLimitPerlinNoiseBuffer[i] / UPPER_LIMIT_SCALE;
                    double d4 = (perlinNoiseBuffer[i] / 10.0 + 1.0) / 2.0;
                    double d5 = clampedLerp(d2, d3, d4) - d1;
                    if (l1 > 29) {
                        double d6 = (l1 - 29) / 3.0F;
                        d5 = d5 * (1.0 - d6) + -10.0 * d6;
                    }

                    heightMap[i] = d5;
                    i++;
                }
            }
        }

        return heightMap;
    }

    /** The reference MathHelper.clampedLerp (lines 101-107). */
    private static double clampedLerp(double min, double max, double delta) {
        if (delta < 0.0) {
            return min;
        } else {
            return delta > 1.0 ? max : min + (max - min) * delta;
        }
    }

    // ------------------------------------------------------------- the fill

    /** The fill's write seam (the reference's BlockStateStorage.set). */
    interface FillTarget {
        void set(int localX, int y, int localZ, BlockType type);
    }

    /**
     * buildTerrain (the reference's lines 95-153) — the 4x4x8-cell trilinear
     * fill: each cell's 4 corner densities (the x0/x1, z0/z1 columns of the
     * 5x5 grid) walk 8 y sub-cells at the 0.125 y-gradient, each y sub-cell
     * walks 4 x sub-cells at the 0.25 x-gradient and 4 z sub-cells at the
     * 0.25 z-gradient — stone where the walked density is positive, the
     * liquid where the cell sits below the sea level. The accumulation is
     * the reference's own repeated-add ladder, kept literally (the probe
     * below reproduces it bit-for-bit).
     */
    void buildTerrain(int chunkX, int chunkZ, double[] heightMap, BlockType stone, BlockType liquid, FillTarget target) {
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
                    double d1 = heightMap[i1 + i2];
                    double d2 = heightMap[j1 + i2];
                    double d3 = heightMap[k1 + i2];
                    double d4 = heightMap[l1 + i2];
                    double d5 = (heightMap[i1 + i2 + 1] - d1) * d0;
                    double d6 = (heightMap[j1 + i2 + 1] - d2) * d0;
                    double d7 = (heightMap[k1 + i2 + 1] - d3) * d0;
                    double d8 = (heightMap[l1 + i2 + 1] - d4) * d0;

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
                                    target.set(i * 4 + k2, i2 * 8 + j2, l * 4 + l2, stone);
                                } else if (i2 * 8 + j2 < SEA_LEVEL) {
                                    target.set(i * 4 + k2, i2 * 8 + j2, l * 4 + l2, liquid);
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

    // ------------------------------------------------------------ the probe

    /**
     * The density at one block — the fill's own accumulation ladder walked
     * to a single cell (the literal sub-cell loops, so the probe's answer
     * is bit-identical to what the fill placed: y sub-walk to {@code y & 7},
     * x sub-walk to {@code lx & 3}, z sub-walk to {@code lz & 3}).
     */
    static double densityAt(double[] heightMap, int lx, int y, int lz) {
        int xCell = lx >> 2;
        int zCell = lz >> 2;
        int yStep = y >> 3;
        int xSub = lx & 3;
        int zSub = lz & 3;
        int ySub = y & 7;
        int base00 = xCell * 165 + zCell * 33;
        int base01 = xCell * 165 + (zCell + 1) * 33;
        int base10 = (xCell + 1) * 165 + zCell * 33;
        int base11 = (xCell + 1) * 165 + (zCell + 1) * 33;
        double d1 = heightMap[base00 + yStep];
        double d2 = heightMap[base01 + yStep];
        double d3 = heightMap[base10 + yStep];
        double d4 = heightMap[base11 + yStep];
        double d5 = (heightMap[base00 + yStep + 1] - d1) * 0.125;
        double d6 = (heightMap[base01 + yStep + 1] - d2) * 0.125;
        double d7 = (heightMap[base10 + yStep + 1] - d3) * 0.125;
        double d8 = (heightMap[base11 + yStep + 1] - d4) * 0.125;
        for (int j2 = 0; j2 < ySub; j2++) {
            d1 += d5;
            d2 += d6;
            d3 += d7;
            d4 += d8;
        }
        double d12 = (d3 - d1) * 0.25;
        double d13 = (d4 - d2) * 0.25;
        double d10 = d1;
        double d11 = d2;
        for (int k2 = 0; k2 < xSub; k2++) {
            d10 += d12;
            d11 += d13;
        }
        double d16 = (d11 - d10) * 0.25;
        double d15 = d10 - d16;
        for (int l2 = 0; l2 <= zSub; l2++) {
            d15 += d16;
        }
        return d15;
    }

    /**
     * The column's terrain top (the probe the consults read — the spawn
     * scan, the structures' surface height, the decorations' ground): the
     * topmost block the fill placed stone in, walked with the fill's own
     * arithmetic. Overhang columns answer their TOP band (the same cell a
     * top-down heightmap scan would find).
     *
     * <p>THE WALK: the 33 y-steps first (the xz-interpolated corner
     * densities — the fill's own sub-cell ladder at the step's base),
     * then the 8 sub-cells of the topmost positive step refined — 41
     * evaluations instead of 256, the same answer (a step with all eight
     * sub-cells negative cannot be the top: the sub-cell densities ride
     * the step's corners... with ONE exception — the sub-cell y-walk can
     * dip negative mid-step when the corners straddle zero, so the
     * refine walks the sub-cells top-down and falls through to the next
     * step below when the whole band is negative).</p>
     */
    static int columnTop(double[] heightMap, int lx, int lz) {
        int xCell = lx >> 2;
        int zCell = lz >> 2;
        int xSub = lx & 3;
        int zSub = lz & 3;
        int base00 = xCell * 165 + zCell * 33;
        int base01 = xCell * 165 + (zCell + 1) * 33;
        int base10 = (xCell + 1) * 165 + zCell * 33;
        int base11 = (xCell + 1) * 165 + (zCell + 1) * 33;
        for (int yStep = 31; yStep >= 0; yStep--) {
            double c0 = heightMap[base00 + yStep];
            double c1 = heightMap[base01 + yStep];
            double c2 = heightMap[base10 + yStep];
            double c3 = heightMap[base11 + yStep];
            double c4 = heightMap[base00 + yStep + 1];
            double c5 = heightMap[base01 + yStep + 1];
            double c6 = heightMap[base10 + yStep + 1];
            double c7 = heightMap[base11 + yStep + 1];
            // The step's band: any positive corner pair at either end
            // means the band may cross — walk the sub-cells (the fill's
            // own ladder).
            if (c0 <= 0.0 && c1 <= 0.0 && c2 <= 0.0 && c3 <= 0.0
                    && c4 <= 0.0 && c5 <= 0.0 && c6 <= 0.0 && c7 <= 0.0) {
                continue; // the whole band negative: no stone in this step
            }
            for (int y = Math.min(255, yStep * 8 + 7); y >= yStep * 8; y--) {
                if (densityAt(heightMap, lx, y, lz) > 0.0) {
                    return y;
                }
            }
        }
        return 0;
    }
}
