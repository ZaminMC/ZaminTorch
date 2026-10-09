package net.minecraft.world.gen.carver;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.gen.Generator;

public class RavineWorldCarver extends Generator {
    private float[] buffer = new float[1024];

    protected void carveRavine(
        long seed,
        int chunkX,
        int chunkZ,
        BlockStateStorage blocks,
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
        Random random = new Random(seed);
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

        for (int j = 0; j < 256; j++) {
            if (j == 0 || random.nextInt(3) == 0) {
                f2 = 1.0F + random.nextFloat() * random.nextFloat() * 1.0F;
            }

            this.buffer[j] = f2 * f2;
        }

        for (; tunnel < tunnelCount; tunnel++) {
            double d9 = 1.5 + MathHelper.sin(tunnel * (float) Math.PI / tunnelCount) * baseWidth * 1.0F;
            double d2 = d9 * widthHeightRatio;
            d9 *= random.nextFloat() * 0.25 + 0.75;
            d2 *= random.nextFloat() * 0.25 + 0.75;
            float f3 = MathHelper.cos(pitch);
            float f4 = MathHelper.sin(pitch);
            x += MathHelper.cos(yaw) * f3;
            y += f4;
            z += MathHelper.sin(yaw) * f3;
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
                    int k2 = MathHelper.floor(x - d9) - chunkX * 16 - 1;
                    int k = MathHelper.floor(x + d9) - chunkX * 16 + 1;
                    int l2 = MathHelper.floor(y - d2) - 1;
                    int l = MathHelper.floor(y + d2) + 1;
                    int i3 = MathHelper.floor(z - d9) - chunkZ * 16 - 1;
                    int i1 = MathHelper.floor(z + d9) - chunkZ * 16 + 1;
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
                                    BlockState blockstate = blocks.get(j1, l1, k1);
                                    if (blockstate.getBlock() == Blocks.FLOWING_WATER || blockstate.getBlock() == Blocks.WATER) {
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
                        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

                        for (int j3 = k2; j3 < k; j3++) {
                            double d10 = (j3 + chunkX * 16 + 0.5 - x) / d9;

                            for (int i2 = i3; i2 < i1; i2++) {
                                double d7 = (i2 + chunkZ * 16 + 0.5 - z) / d9;
                                boolean flag = false;
                                if (d10 * d10 + d7 * d7 < 1.0) {
                                    for (int j2 = l; j2 > l2; j2--) {
                                        double d8 = (j2 - 1 + 0.5 - y) / d2;
                                        if ((d10 * d10 + d7 * d7) * this.buffer[j2 - 1] + d8 * d8 / 6.0 < 1.0) {
                                            BlockState blockstate1 = blocks.get(j3, j2, i2);
                                            if (blockstate1.getBlock() == Blocks.GRASS) {
                                                flag = true;
                                            }

                                            if (blockstate1.getBlock() == Blocks.STONE
                                                || blockstate1.getBlock() == Blocks.DIRT
                                                || blockstate1.getBlock() == Blocks.GRASS) {
                                                if (j2 - 1 < 10) {
                                                    blocks.set(j3, j2, i2, Blocks.FLOWING_LAVA.defaultState());
                                                } else {
                                                    blocks.set(j3, j2, i2, Blocks.AIR.defaultState());
                                                    if (flag && blocks.get(j3, j2 - 1, i2).getBlock() == Blocks.DIRT) {
                                                        blockpos$mutable.set(j3 + chunkX * 16, 0, i2 + chunkZ * 16);
                                                        blocks.set(j3, j2 - 1, i2, this.world.getBiome(blockpos$mutable).surfaceBlock);
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

    @Override
    protected void place(World world, int startChunkX, int startChunkZ, int chunkX, int chunkZ, BlockStateStorage blocks) {
        if (this.random.nextInt(50) == 0) {
            double d0 = startChunkX * 16 + this.random.nextInt(16);
            double d1 = this.random.nextInt(this.random.nextInt(40) + 8) + 20;
            double d2 = startChunkZ * 16 + this.random.nextInt(16);
            int i = 1;

            for (int j = 0; j < i; j++) {
                float f = this.random.nextFloat() * (float) Math.PI * 2.0F;
                float f1 = (this.random.nextFloat() - 0.5F) * 2.0F / 8.0F;
                float f2 = (this.random.nextFloat() * 2.0F + this.random.nextFloat()) * 2.0F;
                this.carveRavine(this.random.nextLong(), chunkX, chunkZ, blocks, d0, d1, d2, f2, f, f1, 0, 0, 3.0);
            }
        }
    }
}
