package net.minecraft.world.gen.noise;

import java.util.Random;

public class ImprovedNoise extends Noise {
    /**
     * A randomly generated permutation table used to determine
     * the gradient vector for each grid intersection.
     */
    private int[] permutations = new int[512];
    public double offsetX;
    public double offsetY;
    public double offsetZ;
    private static final double[] SEED_POOL_0 = new double[]{1.0, -1.0, 1.0, -1.0, 1.0, -1.0, 1.0, -1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0, 0.0};
    private static final double[] SEED_POOL_1 = new double[]{1.0, 1.0, -1.0, -1.0, 0.0, 0.0, 0.0, 0.0, 1.0, -1.0, 1.0, -1.0, 1.0, -1.0, 1.0, -1.0};
    private static final double[] SEED_POOL_2 = new double[]{0.0, 0.0, 0.0, 0.0, 1.0, 1.0, -1.0, -1.0, 1.0, 1.0, -1.0, -1.0, 0.0, 1.0, 0.0, -1.0};
    private static final double[] SEED_POOL_3 = new double[]{1.0, -1.0, 1.0, -1.0, 1.0, -1.0, 1.0, -1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0, 0.0};
    private static final double[] SEED_POOL_4 = new double[]{0.0, 0.0, 0.0, 0.0, 1.0, 1.0, -1.0, -1.0, 1.0, 1.0, -1.0, -1.0, 0.0, 1.0, 0.0, -1.0};

    public ImprovedNoise() {
        this(new Random());
    }

    public ImprovedNoise(Random random) {
        this.offsetX = random.nextDouble() * 256.0;
        this.offsetY = random.nextDouble() * 256.0;
        this.offsetZ = random.nextDouble() * 256.0;
        int i = 0;

        while (i < 256) {
            this.permutations[i] = i++;
        }

        for (int l = 0; l < 256; l++) {
            int j = random.nextInt(256 - l) + l;
            int k = this.permutations[l];
            this.permutations[l] = this.permutations[j];
            this.permutations[j] = k;
            this.permutations[l + 256] = this.permutations[l];
        }
    }

    public final double lerp(double delta, double min, double max) {
        return min + delta * (max - min);
    }

    /**
     * Computes the dot product of the gradient vector at a grid point and the vector {@code x, 0, z}.
     * The gradient vector is identified by the given hash of the grid point.
     */
    public final double gradientDot(int hash, double x, double z) {
        int i = hash & 15;
        return SEED_POOL_3[i] * x + SEED_POOL_4[i] * z;
    }

    /**
     * Computes the dot product of the gradient vector at a grid point and the vector {@code x, y, z}.
     * The gradient vector is identified by the given hash of the grid point.
     */
    public final double gradientDot(int hash, double x, double y, double z) {
        int i = hash & 15;
        return SEED_POOL_0[i] * x + SEED_POOL_1[i] * y + SEED_POOL_2[i] * z;
    }

    public void add(
        double[] values, double x, double y, double z, int sizeX, int sizeY, int sizeZ, double scaleX, double scaleY, double scaleZ, double noiseScale
    ) {
        if (sizeY == 1) {
            int i5 = 0;
            int j5 = 0;
            int j = 0;
            int k5 = 0;
            double d14 = 0.0;
            double d15 = 0.0;
            int l5 = 0;
            double d16 = 1.0 / noiseScale;

            for (int j2 = 0; j2 < sizeX; j2++) {
                double d17 = x + j2 * scaleX + this.offsetX;
                int i6 = (int)d17;
                if (d17 < i6) {
                    i6--;
                }

                int k2 = i6 & 0xFF;
                d17 -= i6;
                double d18 = d17 * d17 * d17 * (d17 * (d17 * 6.0 - 15.0) + 10.0);

                for (int j6 = 0; j6 < sizeZ; j6++) {
                    double d19 = z + j6 * scaleZ + this.offsetZ;
                    int k6 = (int)d19;
                    if (d19 < k6) {
                        k6--;
                    }

                    int l6 = k6 & 0xFF;
                    d19 -= k6;
                    double d20 = d19 * d19 * d19 * (d19 * (d19 * 6.0 - 15.0) + 10.0);
                    i5 = this.permutations[k2] + 0;
                    j5 = this.permutations[i5] + l6;
                    j = this.permutations[k2 + 1] + 0;
                    k5 = this.permutations[j] + l6;
                    d14 = this.lerp(d18, this.gradientDot(this.permutations[j5], d17, d19), this.gradientDot(this.permutations[k5], d17 - 1.0, 0.0, d19));
                    d15 = this.lerp(
                        d18,
                        this.gradientDot(this.permutations[j5 + 1], d17, 0.0, d19 - 1.0),
                        this.gradientDot(this.permutations[k5 + 1], d17 - 1.0, 0.0, d19 - 1.0)
                    );
                    double d21 = this.lerp(d20, d14, d15);
                    values[l5++] += d21 * d16;
                }
            }
        } else {
            int i = 0;
            double d0 = 1.0 / noiseScale;
            int k = -1;
            int l = 0;
            int i1 = 0;
            int j1 = 0;
            int k1 = 0;
            int l1 = 0;
            int i2 = 0;
            double d1 = 0.0;
            double d2 = 0.0;
            double d3 = 0.0;
            double d4 = 0.0;

            for (int l2 = 0; l2 < sizeX; l2++) {
                double d5 = x + l2 * scaleX + this.offsetX;
                int i3 = (int)d5;
                if (d5 < i3) {
                    i3--;
                }

                int j3 = i3 & 0xFF;
                d5 -= i3;
                double d6 = d5 * d5 * d5 * (d5 * (d5 * 6.0 - 15.0) + 10.0);

                for (int k3 = 0; k3 < sizeZ; k3++) {
                    double d7 = z + k3 * scaleZ + this.offsetZ;
                    int l3 = (int)d7;
                    if (d7 < l3) {
                        l3--;
                    }

                    int i4 = l3 & 0xFF;
                    d7 -= l3;
                    double d8 = d7 * d7 * d7 * (d7 * (d7 * 6.0 - 15.0) + 10.0);

                    for (int j4 = 0; j4 < sizeY; j4++) {
                        double d9 = y + j4 * scaleY + this.offsetY;
                        int k4 = (int)d9;
                        if (d9 < k4) {
                            k4--;
                        }

                        int l4 = k4 & 0xFF;
                        d9 -= k4;
                        double d10 = d9 * d9 * d9 * (d9 * (d9 * 6.0 - 15.0) + 10.0);
                        if (j4 == 0 || l4 != k) {
                            k = l4;
                            l = this.permutations[j3] + l4;
                            i1 = this.permutations[l] + i4;
                            j1 = this.permutations[l + 1] + i4;
                            k1 = this.permutations[j3 + 1] + l4;
                            l1 = this.permutations[k1] + i4;
                            i2 = this.permutations[k1 + 1] + i4;
                            d1 = this.lerp(d6, this.gradientDot(this.permutations[i1], d5, d9, d7), this.gradientDot(this.permutations[l1], d5 - 1.0, d9, d7));
                            d2 = this.lerp(
                                d6, this.gradientDot(this.permutations[j1], d5, d9 - 1.0, d7), this.gradientDot(this.permutations[i2], d5 - 1.0, d9 - 1.0, d7)
                            );
                            d3 = this.lerp(
                                d6,
                                this.gradientDot(this.permutations[i1 + 1], d5, d9, d7 - 1.0),
                                this.gradientDot(this.permutations[l1 + 1], d5 - 1.0, d9, d7 - 1.0)
                            );
                            d4 = this.lerp(
                                d6,
                                this.gradientDot(this.permutations[j1 + 1], d5, d9 - 1.0, d7 - 1.0),
                                this.gradientDot(this.permutations[i2 + 1], d5 - 1.0, d9 - 1.0, d7 - 1.0)
                            );
                        }

                        double d11 = this.lerp(d10, d1, d2);
                        double d12 = this.lerp(d10, d3, d4);
                        double d13 = this.lerp(d8, d11, d12);
                        values[i++] += d13 * d0;
                    }
                }
            }
        }
    }
}
