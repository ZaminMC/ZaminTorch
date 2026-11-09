package net.zaminmc.torch.server.world.carver;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.noise.ReferenceMath;

/**
 * The ravine carver (Wave CV — the reference's RavineWorldCarver.java
 * lines 1-194, ported verbatim): the 1-in-50 wide trench family. THE LAWS
 * KEPT WHOLE:
 *
 * <ul>
 * <li>THE REGION WALK: the same Generator base shape as the caves (the
 * 17x17 neighborhood, the {@code l*j ^ i1*k ^ worldSeed} seeds).
 * <li>THE CHANCE (line 156): 1-in-50 per region chunk; the start's y
 * {@code nextInt(nextInt(40) + 8) + 20} — the ravines live in the 20-67
 * band, opening the surface.
 * <li>THE WIDTH (lines 160-162): {@code (nextFloat*2 + nextFloat) * 2}
 * — twice the cave scale; the ratio 3.0 (the tall cross-section).
 * <li>THE PROFILE BUFFER (lines 37-49): the 1024-entry width-squared
 * walk, re-rolled 1-in-3 per entry ({@code 1 + f*f} up to 4.0) — the
 * ravine's own pinched-waist vertical profile. The buffer is allocated
 * PER CALL (the reference's instance field is its own single-threaded
 * discipline; the engine generates chunks on compute workers — the
 * fresh buffer keeps the walk parallel with the same arithmetic).
 * <li>THE PER-STEP WIDTH ROLLS (lines 52-55): BOTH axes re-rolled at
 * 0.75-1.0 per step ({@code nextFloat*0.25 + 0.75}).
 * <li>THE CARVE TEST (line 122): {@code (d10*d10 + d7*d7) * buffer[j2-1]
 * + d8*d8/6 < 1} — the horizontal square scaled by the profile, the
 * vertical sixth — the wide flat trench shape.
 * <li>THE FAMILY GATE (lines 124-126): stone, dirt and grass alone (the
 * ravines cut the surface band; the clay/sand families stay), the lava
 * below {@code j2 - 1 < 10} in the FLOWING form, the grass floor fix.
 * </ul>
 */
public final class RavineCarver {

    /** The Generator base's own range (line 6): the 17x17 region. */
    private final int range = 8;
    /** The Generator base's own random (the region-walk stream). */
    private final java.util.Random random = new java.util.Random();

    private final BlockType air;
    private final BlockType flowingLava;

    public RavineCarver(BlockType air, BlockType flowingLava) {
        this.air = air;
        this.flowingLava = flowingLava;
    }

    /** The Generator base's region walk (lines 10-33). */
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

    /** The reference's own place() body (lines 155-170). */
    private void placeInto(int startChunkX, int startChunkZ, int chunkX, int chunkZ, CarverChunk chunk) {
        if (this.random.nextInt(50) == 0) {
            double d0 = startChunkX * 16 + this.random.nextInt(16);
            double d1 = this.random.nextInt(this.random.nextInt(40) + 8) + 20;
            double d2 = startChunkZ * 16 + this.random.nextInt(16);
            int i = 1;

            for (int j = 0; j < i; j++) {
                float f = this.random.nextFloat() * (float) Math.PI * 2.0F;
                float f1 = (this.random.nextFloat() - 0.5F) * 2.0F / 8.0F;
                float f2 = (this.random.nextFloat() * 2.0F + this.random.nextFloat()) * 2.0F;
                this.carveRavine(this.random.nextLong(), chunkX, chunkZ, chunk, d0, d1, d2, f2, f, f1, 0, 0, 3.0);
            }
        }
    }

    /** The ravine walk (lines 19-154, verbatim — the fresh buffer per call). */
    private void carveRavine(
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
        java.util.Random random = new java.util.Random(seed);
        double d0 = chunkX * 16 + 8;
        double d1 = chunkZ * 16 + 8;
        float f = 0.0F;
        float f1 = 0.0F;
        if (tunnelCount <= 0) {
            int i = this.range * 16 - 16;
            tunnelCount = i - random.nextInt(i / 4);
        }

        boolean flag1 = false;
        if (tunnel == -1) {
            tunnel = tunnelCount / 2;
            flag1 = true;
        }

        float f2 = 1.0F;
        float[] buffer = new float[1024];

        for (int j = 0; j < 256; j++) {
            if (j == 0 || random.nextInt(3) == 0) {
                f2 = 1.0F + random.nextFloat() * random.nextFloat() * 1.0F;
            }

            buffer[j] = f2 * f2;
        }

        for (; tunnel < tunnelCount; tunnel++) {
            double d9 = 1.5 + ReferenceMath.sin(tunnel * (float) Math.PI / tunnelCount) * baseWidth * 1.0F;
            double d2 = d9 * widthHeightRatio;
            d9 *= random.nextFloat() * 0.25 + 0.75;
            d2 *= random.nextFloat() * 0.25 + 0.75;
            float f3 = ReferenceMath.cos(pitch);
            float f4 = ReferenceMath.sin(pitch);
            x += ReferenceMath.cos(yaw) * f3;
            y += f4;
            z += ReferenceMath.sin(yaw) * f3;
            pitch *= 0.7F;
            pitch += f1 * 0.05F;
            yaw += f * 0.05F;
            f1 *= 0.8F;
            f *= 0.5F;
            f1 += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            f += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (flag1 || random.nextInt(4) != 0) {
                double d3 = x - d0;
                double d4 = z - d1;
                double d5 = tunnelCount - tunnel;
                double d6 = baseWidth + 2.0F + 16.0F;
                if (d3 * d3 + d4 * d4 - d5 * d5 > d6 * d6) {
                    return;
                }

                if (!(x < d0 - 16.0 - d9 * 2.0) && !(z < d1 - 16.0 - d9 * 2.0) && !(x > d0 + 16.0 + d9 * 2.0) && !(z > d1 + 16.0 + d9 * 2.0)) {
                    int k2 = floor(x - d9) - chunkX * 16 - 1;
                    int k = floor(x + d9) - chunkX * 16 + 1;
                    int l2 = floor(y - d2) - 1;
                    int l = floor(y + d2) + 1;
                    int i3 = floor(z - d9) - chunkZ * 16 - 1;
                    int i1 = floor(z + d9) - chunkZ * 16 + 1;
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

                    boolean flag2 = false;

                    for (int j1 = k2; !flag2 && j1 < k; j1++) {
                        for (int k1 = i3; !flag2 && k1 < i1; k1++) {
                            for (int l1 = l + 1; !flag2 && l1 >= l2 - 1; l1--) {
                                if (l1 >= 0 && l1 < 256) {
                                    BlockType blockstate = chunk.get(j1, l1, k1);
                                    if (CaveCarver.isWaterFamily(blockstate)) {
                                        flag2 = true;
                                    }

                                    if (l1 != l2 - 1 && j1 != k2 && j1 != k - 1 && k1 != i3 && k1 != i1 - 1) {
                                        l1 = l2;
                                    }
                                }
                            }
                        }
                    }

                    if (!flag2) {
                        for (int j3 = k2; j3 < k; j3++) {
                            double d10 = (j3 + chunkX * 16 + 0.5 - x) / d9;

                            for (int i2 = i3; i2 < i1; i2++) {
                                double d7 = (i2 + chunkZ * 16 + 0.5 - z) / d9;
                                boolean flag = false;
                                if (d10 * d10 + d7 * d7 < 1.0) {
                                    for (int j2 = l; j2 > l2; j2--) {
                                        double d8 = (j2 - 1 + 0.5 - y) / d2;
                                        if ((d10 * d10 + d7 * d7) * buffer[j2 - 1] + d8 * d8 / 6.0 < 1.0) {
                                            BlockType blockstate1 = chunk.get(j3, j2, i2);
                                            if (isGrass(blockstate1)) {
                                                flag = true;
                                            }

                                            if (isStone(blockstate1)
                                                || isDirt(blockstate1)
                                                || isGrass(blockstate1)) {
                                                if (j2 - 1 < 10) {
                                                    chunk.set(j3, j2, i2, flowingLava);
                                                } else {
                                                    chunk.set(j3, j2, i2, air);
                                                    if (flag && isDirt(chunk.get(j3, j2 - 1, i2))) {
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

                        if (flag1) {
                            break;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ the material consults
    // (the ravine's own narrow family — stone, dirt, grass alone)

    private static String value(BlockType type) {
        net.zaminmc.torch.util.Identifier id = type.identifier();
        return id.namespace().equals("minecraft") ? id.value() : id.toString();
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

    /** The reference MathHelper.floor (the truncation-then-check shape). */
    private static int floor(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }
}
