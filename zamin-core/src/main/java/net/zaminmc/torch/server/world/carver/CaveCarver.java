package net.zaminmc.torch.server.world.carver;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.noise.ReferenceMath;
import net.zaminmc.torch.util.Identifier;

/**
 * The cave carver (Wave CV — the reference's CaveWorldCarver.java lines
 * 1-267, ported verbatim): the worm-and-room cave family the retired fbm
 * stand-in could never answer. THE LAWS KEPT WHOLE:
 *
 * <ul>
 * <li>THE REGION WALK (the Generator base, lines 10-33): every generated
 * chunk consults the 17x17 chunk neighborhood ({@code range = 8}), each
 * region chunk seeded {@code l*j ^ i1*k ^ worldSeed} where {@code j, k}
 * are the two longs off the world-seed stream — the caves cross chunk
 * borders because every chunk re-rolls its neighbors' starts and clips
 * the carve to itself.
 * <li>THE COUNT LADDER (line 240): {@code nextInt(nextInt(nextInt(15) +
 * 1) + 1)} caves per region chunk, kept only 1-in-7 (the rest zeroed).
 * <li>THE START (lines 246-248): the region chunk's own block span, the
 * y {@code nextInt(nextInt(120) + 8)} — the caves live in the low 128.
 * <li>THE ROOM FORM (lines 16-18, 250-253): 1-in-4 caves open with a room
 * (the width {@code 1 + nextFloat*6}, the halfway-start one-shot), then
 * {@code 1 + nextInt(4)} tunnels.
 * <li>THE TUNNEL WALK (lines 20-223): the sin-scaled width
 * {@code 1.5 + sin(tunnel*PI/count)*baseWidth} per step, the position
 * advance over the yaw/pitch pair (the pitch decay 0.92 when the 1-in-6
 * flat roll hits, else 0.7), the two jitter accumulators with their own
 * decays, THE SPLIT at the 1-in-2-ish branch step (the recursive pair at
 * ±PI/2, the halved width, the thirded pitch), the early-outs (the
 * spiral distance test, the 16-block span bounds), THE WATER
 * SUPPRESSION (any water in the carve box's shell cancels the whole
 * step — the caves never open into the sea), and the carve itself: the
 * ellipsoid test with the {@code d9 > -0.7} floor guard, the
 * canReplace family gate, THE LAVA FLOOR below {@code j2 - 1 < 10},
 * THE SAND CEILING FIX (the sand-family cell above a carved cell
 * re-set to its matching sandstone — the falling-sand guard), and THE
 * GRASS/MYCELIUM FLOOR FIX (the dirt under a carved surface cell
 * re-set to the biome's registered surface).
 * </ul>
 *
 * <p>THE MATH: the yaw/pitch walks read {@link ReferenceMath}'s
 * table-quantized sin/cos (the reference MathHelper's own 65536-entry
 * table — java.lang.Math would drift the tunnel shapes; the table's
 * quantization IS the vanilla cave shape). The per-tunnel
 * {@code new Random(seed)} and the recursive splits' {@code nextLong}
 * seeds are the determinism.</p>
 */
public final class CaveCarver {

    /** The Generator base's own range (line 6): the 17x17 region. */
    private final int range = 8;
    /** The Generator base's own random (the region-walk stream). */
    private final java.util.Random random = new java.util.Random();

    private final BlockType air;
    private final BlockType lava;
    private final BlockType sandstone;
    private final BlockType redSandstone;

    public CaveCarver(BlockType air, BlockType lava, BlockType sandstone, BlockType redSandstone) {
        this.air = air;
        this.lava = lava;
        this.sandstone = sandstone;
        this.redSandstone = redSandstone;
    }

    /**
     * The Generator base's region walk (lines 10-33): the world-seed
     * stream's two longs, the per-region-chunk seed
     * {@code l*j ^ i1*k ^ worldSeed}, the placement into the TARGET chunk.
     */
    public void place(long worldSeed, int chunkX, int chunkZ, CarverChunk chunk) {
        int i = this.range;
        this.random.setSeed(worldSeed);
        long j = this.random.nextLong();
        long k = this.random.nextLong();

        for (int l = chunkX - i; l <= chunkX + i; l++) {
            for (int i1 = chunkZ - i; i1 <= chunkZ + i; i1++) {
                long j1 = l * j;
                long k1 = i1 * k;
                this.random.setSeed(j1 ^ k1 ^ worldSeed);
                this.placeInto(l, i1, chunkX, chunkZ, chunk);
            }
        }
    }

    /** The reference's own place() body (lines 238-266). */
    private void placeInto(int startChunkX, int startChunkZ, int chunkX, int chunkZ, CarverChunk chunk) {
        int i = this.random.nextInt(this.random.nextInt(this.random.nextInt(15) + 1) + 1);
        if (this.random.nextInt(7) != 0) {
            i = 0;
        }

        for (int j = 0; j < i; j++) {
            double d0 = startChunkX * 16 + this.random.nextInt(16);
            double d1 = this.random.nextInt(this.random.nextInt(120) + 8);
            double d2 = startChunkZ * 16 + this.random.nextInt(16);
            int k = 1;
            if (this.random.nextInt(4) == 0) {
                this.carveRoom(this.random.nextLong(), chunkX, chunkZ, chunk, d0, d1, d2);
                k += this.random.nextInt(4);
            }

            for (int l = 0; l < k; l++) {
                float f = this.random.nextFloat() * (float) Math.PI * 2.0F;
                float f1 = (this.random.nextFloat() - 0.5F) * 2.0F / 8.0F;
                float f2 = this.random.nextFloat() * 2.0F + this.random.nextFloat();
                if (this.random.nextInt(10) == 0) {
                    f2 *= this.random.nextFloat() * this.random.nextFloat() * 3.0F + 1.0F;
                }

                this.carveTunnel(this.random.nextLong(), chunkX, chunkZ, chunk, d0, d1, d2, f2, f, f1, 0, 0, 1.0);
            }
        }
    }

    /** The room form (lines 16-18). */
    private void carveRoom(long seed, int chunkX, int chunkZ, CarverChunk chunk, double x, double y, double z) {
        this.carveTunnel(seed, chunkX, chunkZ, chunk, x, y, z, 1.0F + this.random.nextFloat() * 6.0F, 0.0F, 0.0F, -1, -1, 0.5);
    }

    /** The tunnel walk (lines 20-223, verbatim). */
    private void carveTunnel(
        long seed,
        int chunkX,
        int chunkZ,
        CarverChunk chunk,
        double x,
        double y,
        double z,
        float baseWidth,
        float yaw,
        float pitch,
        int tunnel,
        int tunnelCount,
        double widthHeightRatio
    ) {
        double d0 = chunkX * 16 + 8;
        double d1 = chunkZ * 16 + 8;
        float f = 0.0F;
        float f1 = 0.0F;
        java.util.Random random = new java.util.Random(seed);
        if (tunnelCount <= 0) {
            int i = this.range * 16 - 16;
            tunnelCount = i - random.nextInt(i / 4);
        }

        boolean flag2 = false;
        if (tunnel == -1) {
            tunnel = tunnelCount / 2;
            flag2 = true;
        }

        int j = random.nextInt(tunnelCount / 2) + tunnelCount / 4;
        boolean flag = random.nextInt(6) == 0;

        while (tunnel < tunnelCount) {
            double d2 = 1.5 + ReferenceMath.sin(tunnel * (float) Math.PI / tunnelCount) * baseWidth * 1.0F;
            double d3 = d2 * widthHeightRatio;
            float f2 = ReferenceMath.cos(pitch);
            float f3 = ReferenceMath.sin(pitch);
            x += ReferenceMath.cos(yaw) * f2;
            y += f3;
            z += ReferenceMath.sin(yaw) * f2;
            if (flag) {
                pitch *= 0.92F;
            } else {
                pitch *= 0.7F;
            }

            pitch += f1 * 0.1F;
            yaw += f * 0.1F;
            f1 *= 0.9F;
            f *= 0.75F;
            f1 += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            f += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (!flag2 && tunnel == j && baseWidth > 1.0F && tunnelCount > 0) {
                this.carveTunnel(
                    random.nextLong(),
                    chunkX,
                    chunkZ,
                    chunk,
                    x,
                    y,
                    z,
                    random.nextFloat() * 0.5F + 0.5F,
                    yaw - (float) (Math.PI / 2),
                    pitch / 3.0F,
                    tunnel,
                    tunnelCount,
                    1.0
                );
                this.carveTunnel(
                    random.nextLong(),
                    chunkX,
                    chunkZ,
                    chunk,
                    x,
                    y,
                    z,
                    random.nextFloat() * 0.5F + 0.5F,
                    yaw + (float) (Math.PI / 2),
                    pitch / 3.0F,
                    tunnel,
                    tunnelCount,
                    1.0
                );
                return;
            }

            if (flag2 || random.nextInt(4) != 0) {
                double d4 = x - d0;
                double d5 = z - d1;
                double d6 = tunnelCount - tunnel;
                double d7 = baseWidth + 2.0F + 16.0F;
                if (d4 * d4 + d5 * d5 - d6 * d6 > d7 * d7) {
                    return;
                }

                if (!(x < d0 - 16.0 - d2 * 2.0) && !(z < d1 - 16.0 - d2 * 2.0) && !(x > d0 + 16.0 + d2 * 2.0) && !(z > d1 + 16.0 + d2 * 2.0)) {
                    int k2 = floor(x - d2) - chunkX * 16 - 1;
                    int k = floor(x + d2) - chunkX * 16 + 1;
                    int l2 = floor(y - d3) - 1;
                    int l = floor(y + d3) + 1;
                    int i3 = floor(z - d2) - chunkZ * 16 - 1;
                    int i1 = floor(z + d2) - chunkZ * 16 + 1;
                    if (k2 < 0) {
                        k2 = 0;
                    }

                    if (k > 16) {
                        k = 16;
                    }

                    if (l2 < 1) {
                        l2 = 1;
                    }

                    if (l > 248) {
                        l = 248;
                    }

                    if (i3 < 0) {
                        i3 = 0;
                    }

                    if (i1 > 16) {
                        i1 = 16;
                    }

                    boolean flag3 = false;

                    for (int j1 = k2; !flag3 && j1 < k; j1++) {
                        for (int k1 = i3; !flag3 && k1 < i1; k1++) {
                            for (int l1 = l + 1; !flag3 && l1 >= l2 - 1; l1--) {
                                if (l1 >= 0 && l1 < 256) {
                                    BlockType blockstate = chunk.get(j1, l1, k1);
                                    if (isWaterFamily(blockstate)) {
                                        flag3 = true;
                                    }

                                    if (l1 != l2 - 1 && j1 != k2 && j1 != k - 1 && k1 != i3 && k1 != i1 - 1) {
                                        l1 = l2;
                                    }
                                }
                            }
                        }
                    }

                    if (!flag3) {
                        for (int j3 = k2; j3 < k; j3++) {
                            double d10 = (j3 + chunkX * 16 + 0.5 - x) / d2;

                            for (int i2 = i3; i2 < i1; i2++) {
                                double d8 = (i2 + chunkZ * 16 + 0.5 - z) / d2;
                                boolean flag1 = false;
                                if (d10 * d10 + d8 * d8 < 1.0) {
                                    for (int j2 = l; j2 > l2; j2--) {
                                        double d9 = (j2 - 1 + 0.5 - y) / d3;
                                        if (d9 > -0.7 && d10 * d10 + d9 * d9 + d8 * d8 < 1.0) {
                                            BlockType blockstate1 = chunk.get(j3, j2, i2);
                                            BlockType blockstate2 = chunk.get(j3, j2 + 1, i2);
                                            if (isGrass(blockstate1) || isMycelium(blockstate1)) {
                                                flag1 = true;
                                            }

                                            if (this.canReplace(blockstate1, blockstate2)) {
                                                if (j2 - 1 < 10) {
                                                    chunk.set(j3, j2, i2, lava);
                                                } else {
                                                    chunk.set(j3, j2, i2, air);
                                                    if (isSand(blockstate2)) {
                                                        chunk.set(
                                                            j3,
                                                            j2 + 1,
                                                            i2,
                                                            isRedSand(blockstate2) ? redSandstone : sandstone
                                                        );
                                                    }

                                                    if (flag1 && isDirt(chunk.get(j3, j2 - 1, i2))) {
                                                        chunk.set(j3, j2 - 1, i2,
                                                                chunk.registeredSurfaceOf(
                                                                        chunk.biomeAt(j3 + chunkX * 16, i2 + chunkZ * 16)));
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (flag2) {
                            break;
                        }
                    }
                }
            }

            tunnel++;
        }
    }

    /** The canReplace family (lines 225-236): the carve's own material gate. */
    private boolean canReplace(BlockType state, BlockType with) {
        return isStone(state)
            || isDirt(state)
            || isGrass(state)
            || isHardenedClayFamily(state)
            || isSandstone(state)
            || isRedSandstone(state)
            || isMycelium(state)
            || isSnowLayer(state)
            || (isSand(state) || isGravel(state)) && !isWaterFamily(with);
    }

    // ------------------------------------------------------------ the material consults
    // (the reference's block identity checks over the engine's identifier
    // family — the stained clay and the fluid families carry the engine's
    // own suffixed forms)

    private static String value(BlockType type) {
        Identifier id = type.identifier();
        return id.namespace().equals("minecraft") ? id.value() : id.toString();
    }

    static boolean isWaterFamily(BlockType type) {
        String v = value(type);
        return v.equals("water") || v.equals("flowing_water") || v.equals("falling_water")
                || v.startsWith("still_water");
    }

    private static boolean isStone(BlockType type) {
        return value(type).equals("stone");
    }

    private static boolean isDirt(BlockType type) {
        return value(type).equals("dirt");
    }

    private static boolean isGrass(BlockType type) {
        return value(type).equals("grass_block");
    }

    private static boolean isMycelium(BlockType type) {
        return value(type).equals("mycelium");
    }

    private static boolean isHardenedClayFamily(BlockType type) {
        String v = value(type);
        return v.equals("hardened_clay") || v.contains("stained_clay");
    }

    private static boolean isSandstone(BlockType type) {
        return value(type).equals("sandstone");
    }

    private static boolean isRedSandstone(BlockType type) {
        return value(type).equals("red_sandstone");
    }

    private static boolean isSnowLayer(BlockType type) {
        return value(type).equals("snow");
    }

    static boolean isSand(BlockType type) {
        String v = value(type);
        return v.equals("sand") || v.equals("red_sand");
    }

    private static boolean isRedSand(BlockType type) {
        return value(type).equals("red_sand");
    }

    private static boolean isGravel(BlockType type) {
        return value(type).equals("gravel");
    }

    /** The reference MathHelper.floor (the truncation-then-check shape). */
    private static int floor(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }
}
