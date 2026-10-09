package net.minecraft.world.gen.carver;

import com.google.common.base.Objects;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.SandBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.gen.Generator;

public class CaveWorldCarver extends Generator {
    protected void carveRoom(long seed, int chunkX, int chunkZ, BlockStateStorage blocks, double x, double y, double z) {
        this.carveTunnel(seed, chunkX, chunkZ, blocks, x, y, z, 1.0F + this.random.nextFloat() * 6.0F, 0.0F, 0.0F, -1, -1, 0.5);
    }

    protected void carveTunnel(
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
        double d0 = chunkX * 16 + 8;
        double d1 = chunkZ * 16 + 8;
        float f = 0.0F;
        float f1 = 0.0F;
        Random random = new Random(seed);
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
            double d2 = 1.5 + MathHelper.sin(tunnel * (float) Math.PI / tunnelCount) * baseWidth * 1.0F;
            double d3 = d2 * widthHeightRatio;
            float f2 = MathHelper.cos(pitch);
            float f3 = MathHelper.sin(pitch);
            x += MathHelper.cos(yaw) * f2;
            y += f3;
            z += MathHelper.sin(yaw) * f2;
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
                    blocks,
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
                    blocks,
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
                    int k2 = MathHelper.floor(x - d2) - chunkX * 16 - 1;
                    int k = MathHelper.floor(x + d2) - chunkX * 16 + 1;
                    int l2 = MathHelper.floor(y - d3) - 1;
                    int l = MathHelper.floor(y + d3) + 1;
                    int i3 = MathHelper.floor(z - d2) - chunkZ * 16 - 1;
                    int i1 = MathHelper.floor(z + d2) - chunkZ * 16 + 1;
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
                                    BlockState blockstate = blocks.get(j1, l1, k1);
                                    if (blockstate.getBlock() == Blocks.FLOWING_WATER || blockstate.getBlock() == Blocks.WATER) {
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
                        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

                        for (int j3 = k2; j3 < k; j3++) {
                            double d10 = (j3 + chunkX * 16 + 0.5 - x) / d2;

                            for (int i2 = i3; i2 < i1; i2++) {
                                double d8 = (i2 + chunkZ * 16 + 0.5 - z) / d2;
                                boolean flag1 = false;
                                if (d10 * d10 + d8 * d8 < 1.0) {
                                    for (int j2 = l; j2 > l2; j2--) {
                                        double d9 = (j2 - 1 + 0.5 - y) / d3;
                                        if (d9 > -0.7 && d10 * d10 + d9 * d9 + d8 * d8 < 1.0) {
                                            BlockState blockstate1 = blocks.get(j3, j2, i2);
                                            BlockState blockstate2 = Objects.firstNonNull(blocks.get(j3, j2 + 1, i2), Blocks.AIR.defaultState());
                                            if (blockstate1.getBlock() == Blocks.GRASS || blockstate1.getBlock() == Blocks.MYCELIUM) {
                                                flag1 = true;
                                            }

                                            if (this.canReplace(blockstate1, blockstate2)) {
                                                if (j2 - 1 < 10) {
                                                    blocks.set(j3, j2, i2, Blocks.LAVA.defaultState());
                                                } else {
                                                    blocks.set(j3, j2, i2, Blocks.AIR.defaultState());
                                                    if (blockstate2.getBlock() == Blocks.SAND) {
                                                        blocks.set(
                                                            j3,
                                                            j2 + 1,
                                                            i2,
                                                            blockstate2.get(SandBlock.VARIANT) == SandBlock.Variant.RED_SAND
                                                                ? Blocks.RED_SANDSTONE.defaultState()
                                                                : Blocks.SANDSTONE.defaultState()
                                                        );
                                                    }

                                                    if (flag1 && blocks.get(j3, j2 - 1, i2).getBlock() == Blocks.DIRT) {
                                                        blockpos$mutable.set(j3 + chunkX * 16, 0, i2 + chunkZ * 16);
                                                        blocks.set(j3, j2 - 1, i2, this.world.getBiome(blockpos$mutable).surfaceBlock.getBlock().defaultState());
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

    protected boolean canReplace(BlockState state, BlockState with) {
        return state.getBlock() == Blocks.STONE
            || state.getBlock() == Blocks.DIRT
            || state.getBlock() == Blocks.GRASS
            || state.getBlock() == Blocks.HARDENED_CLAY
            || state.getBlock() == Blocks.STAINED_HARDENED_CLAY
            || state.getBlock() == Blocks.SANDSTONE
            || state.getBlock() == Blocks.RED_SANDSTONE
            || state.getBlock() == Blocks.MYCELIUM
            || state.getBlock() == Blocks.SNOW_LAYER
            || (state.getBlock() == Blocks.SAND || state.getBlock() == Blocks.GRAVEL) && with.getBlock().getMaterial() != Material.WATER;
    }

    @Override
    protected void place(World world, int startChunkX, int startChunkZ, int chunkX, int chunkZ, BlockStateStorage blocks) {
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
                this.carveRoom(this.random.nextLong(), chunkX, chunkZ, blocks, d0, d1, d2);
                k += this.random.nextInt(4);
            }

            for (int l = 0; l < k; l++) {
                float f = this.random.nextFloat() * (float) Math.PI * 2.0F;
                float f1 = (this.random.nextFloat() - 0.5F) * 2.0F / 8.0F;
                float f2 = this.random.nextFloat() * 2.0F + this.random.nextFloat();
                if (this.random.nextInt(10) == 0) {
                    f2 *= this.random.nextFloat() * this.random.nextFloat() * 3.0F + 1.0F;
                }

                this.carveTunnel(this.random.nextLong(), chunkX, chunkZ, blocks, d0, d1, d2, f2, f, f1, 0, 0, 1.0);
            }
        }
    }
}
