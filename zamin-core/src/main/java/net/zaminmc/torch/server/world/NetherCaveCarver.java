package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.world.noise.ReferenceMath;

import java.util.Random;

/**
 * Ported from reference/1.8.8 net/minecraft/world/gen/carver/NetherCaveCarver.java
 * (lines 1-220) with the base Generator's chunk loop (Generator.java lines
 * 15-30): rooms and tunnels carved through the netherrack. The Generator
 * shell (the {@code carve} entry) is the reference's
 * {@code place(ChunkSource, World, chunkX, chunkZ, storage)}: seed the shared
 * Random with the world seed, draw the two per-world hashing longs, then walk
 * every chunk within {@code range} (8) of the built chunk — each start chunk
 * seeds {@code (startX * j) ^ (startZ * k) ^ worldSeed} and may originate a
 * cave that reaches INTO the chunk being built. The reference's carveTunnel
 * already clips its writes to the built chunk's 16x16x128 local grid, so the
 * neighbor-originated carves land exactly where vanilla puts them.
 *
 * <p>The carve rules are verbatim: the room branch (1-in-4 chance per origin,
 * 1.0..7.0 base width, ratio 0.5), the tunnel walk (the sin-shaped width
 * d2 = 1.5 + sin(t * PI / count) * width, the pitch/yaw wander with the
 * 0.92/0.7 decay and the f/f1 drift pools, the mid-walk fork at tunnel == j
 * forking both directions with PI/2 yaw offsets and pitch/3), the lava
 * bail-out scan (any LAVA cell in the carve column box aborts the whole
 * carve), and the ellipsoid carve test (d9 > -0.7 && d10² + d9² + d8² < 1.0)
 * that only removes NETHERRACK/DIRT/GRASS.</p>
 */
public final class NetherCaveCarver {

    private static final int RANGE = 8;

    private final BlockRegistry registry;
    private final Random random = new Random();

    public NetherCaveCarver(BlockRegistry registry) {
        this.registry = registry;
    }

    /**
     * The Generator.place shell: every chunk within range of the built chunk
     * gets its seed-derived shot at originating a cave (the reference's
     * range-8 loop).
     */
    public void carve(long worldSeed, int chunkX, int chunkZ, EngineChunk chunk) {
        this.random.setSeed(worldSeed);
        long j = this.random.nextLong();
        long k = this.random.nextLong();

        for (int l = chunkX - RANGE; l <= chunkX + RANGE; l++) {
            for (int i1 = chunkZ - RANGE; i1 <= chunkZ + RANGE; i1++) {
                long j1 = l * j;
                long k1 = i1 * k;
                this.random.setSeed(j1 ^ k1 ^ worldSeed);
                this.place(chunk, l, i1, chunkX, chunkZ);
            }
        }
    }

    /** The reference NetherCaveCarver.place (lines 195-219). */
    private void place(EngineChunk chunk, int startChunkX, int startChunkZ, int chunkX, int chunkZ) {
        int i = this.random.nextInt(this.random.nextInt(this.random.nextInt(10) + 1) + 1);
        if (this.random.nextInt(5) != 0) {
            i = 0;
        }

        for (int j = 0; j < i; j++) {
            double d0 = startChunkX * 16 + this.random.nextInt(16);
            double d1 = this.random.nextInt(128);
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
                this.carveTunnel(this.random.nextLong(), chunkX, chunkZ, chunk, d0, d1, d2, f2 * 2.0F, f, f1, 0, 0, 0.5);
            }
        }
    }

    /** The reference carveRoom (lines 12-14): the wide single chamber. */
    private void carveRoom(long seed, int chunkX, int chunkZ, EngineChunk chunk, double x, double y, double z) {
        this.carveTunnel(seed, chunkX, chunkZ, chunk, x, y, z, 1.0F + this.random.nextFloat() * 6.0F, 0.0F, 0.0F, -1, -1, 0.5);
    }

    /** The reference carveTunnel (lines 16-193), verbatim walk. */
    private void carveTunnel(
        long seed,
        int chunkX,
        int chunkZ,
        EngineChunk chunk,
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
        Random random = new Random(seed);
        if (tunnelCount <= 0) {
            int i = RANGE * 16 - 16;
            tunnelCount = i - random.nextInt(i / 4);
        }

        boolean flag1 = false;
        if (tunnel == -1) {
            tunnel = tunnelCount / 2;
            flag1 = true;
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
            if (!flag1 && tunnel == j && baseWidth > 1.0F) {
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

            if (flag1 || random.nextInt(4) != 0) {
                double d4 = x - d0;
                double d5 = z - d1;
                double d6 = tunnelCount - tunnel;
                double d7 = baseWidth + 2.0F + 16.0F;
                if (d4 * d4 + d5 * d5 - d6 * d6 > d7 * d7) {
                    return;
                }

                if (!(x < d0 - 16.0 - d2 * 2.0) && !(z < d1 - 16.0 - d2 * 2.0) && !(x > d0 + 16.0 + d2 * 2.0) && !(z > d1 + 16.0 + d2 * 2.0)) {
                    int j2 = floorInt(x - d2) - chunkX * 16 - 1;
                    int k = floorInt(x + d2) - chunkX * 16 + 1;
                    int k2 = floorInt(y - d3) - 1;
                    int l = floorInt(y + d3) + 1;
                    int l2 = floorInt(z - d2) - chunkZ * 16 - 1;
                    int i1 = floorInt(z + d2) - chunkZ * 16 + 1;
                    if (j2 < 0) {
                        j2 = 0;
                    }

                    if (k > 16) {
                        k = 16;
                    }

                    if (k2 < 1) {
                        k2 = 1;
                    }

                    if (l > 120) {
                        l = 120;
                    }

                    if (l2 < 0) {
                        l2 = 0;
                    }

                    if (i1 > 16) {
                        i1 = 16;
                    }

                    boolean flag2 = false;

                    for (int j1 = j2; !flag2 && j1 < k; j1++) {
                        for (int k1 = l2; !flag2 && k1 < i1; k1++) {
                            for (int l1 = l + 1; !flag2 && l1 >= k2 - 1; l1--) {
                                if (l1 >= 0 && l1 < 128) {
                                    net.zaminmc.torch.block.BlockType blockstate = chunk.getBlock(j1, l1, k1);
                                    if (isLava(blockstate)) {
                                        flag2 = true;
                                    }

                                    if (l1 != k2 - 1 && j1 != j2 && j1 != k - 1 && k1 != l2 && k1 != i1 - 1) {
                                        l1 = k2;
                                    }
                                }
                            }
                        }
                    }

                    if (!flag2) {
                        for (int i3 = j2; i3 < k; i3++) {
                            double d10 = (i3 + chunkX * 16 + 0.5 - x) / d2;

                            for (int j3 = l2; j3 < i1; j3++) {
                                double d8 = (j3 + chunkZ * 16 + 0.5 - z) / d2;

                                for (int i2 = l; i2 > k2; i2--) {
                                    double d9 = (i2 - 1 + 0.5 - y) / d3;
                                    if (d9 > -0.7 && d10 * d10 + d9 * d9 + d8 * d8 < 1.0) {
                                        net.zaminmc.torch.block.BlockType blockstate1 = chunk.getBlock(i3, i2, j3);
                                        if (blockstate1.equals(registry.require(BuiltinBlocks.NETHERRACK.identifier()))
                                            || blockstate1.equals(registry.require(BuiltinBlocks.DIRT.identifier()))
                                            || blockstate1.equals(registry.require(BuiltinBlocks.GRASS_BLOCK.identifier()))) {
                                            chunk.setBlock(i3, i2, j3, registry.require(BuiltinBlocks.AIR.identifier()));
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

            tunnel++;
        }
    }

    private boolean isLava(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        return value.equals("lava") || value.equals("falling_lava") || value.startsWith("flowing_lava");
    }

    /** The reference MathHelper.floor: largest int not above the value. */
    private static int floorInt(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
