package net.minecraft.world.gen.noise;

import java.util.Random;

public class SimplexNoise {
    private static int[][] GRADIENTS = new int[][]{
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };
    public static final double SQRT_THREE = Math.sqrt(3.0);
    /**
     * A randomly generated permutation table used to determine
     * the gradient vector for each grid intersection.
     */
    private int[] permutations = new int[512];
    public double offsetX;
    public double offsetY;
    public double offsetZ;
    private static final double SKEW_FACTOR_2D = 0.5 * (SQRT_THREE - 1.0);
    private static final double UNSKEW_FACTOR_2D = (3.0 - SQRT_THREE) / 6.0;

    public SimplexNoise() {
        this(new Random());
    }

    public SimplexNoise(Random random) {
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

    private static int fastFloor(double x) {
        return x > 0.0 ? (int)x : (int)x - 1;
    }

    /**
     * Computes the dot product of the given gradient vector and the vector {@code x, y}.
     */
    private static double dot(int[] gradient, double x, double y) {
        return gradient[0] * x + gradient[1] * y;
    }

    public double getValue(double x, double y) {
        double d3 = 0.5 * (SQRT_THREE - 1.0);
        double d4 = (x + y) * d3;
        int i = fastFloor(x + d4);
        int j = fastFloor(y + d4);
        double d5 = (3.0 - SQRT_THREE) / 6.0;
        double d6 = (i + j) * d5;
        double d7 = i - d6;
        double d8 = j - d6;
        double d9 = x - d7;
        double d10 = y - d8;
        int k;
        int l;
        if (d9 > d10) {
            k = 1;
            l = 0;
        } else {
            k = 0;
            l = 1;
        }

        double d11 = d9 - k + d5;
        double d12 = d10 - l + d5;
        double d13 = d9 - 1.0 + 2.0 * d5;
        double d14 = d10 - 1.0 + 2.0 * d5;
        int i1 = i & 0xFF;
        int j1 = j & 0xFF;
        int k1 = this.permutations[i1 + this.permutations[j1]] % 12;
        int l1 = this.permutations[i1 + k + this.permutations[j1 + l]] % 12;
        int i2 = this.permutations[i1 + 1 + this.permutations[j1 + 1]] % 12;
        double d15 = 0.5 - d9 * d9 - d10 * d10;
        double d0;
        if (d15 < 0.0) {
            d0 = 0.0;
        } else {
            d15 *= d15;
            d0 = d15 * d15 * dot(GRADIENTS[k1], d9, d10);
        }

        double d16 = 0.5 - d11 * d11 - d12 * d12;
        double d1;
        if (d16 < 0.0) {
            d1 = 0.0;
        } else {
            d16 *= d16;
            d1 = d16 * d16 * dot(GRADIENTS[l1], d11, d12);
        }

        double d17 = 0.5 - d13 * d13 - d14 * d14;
        double d2;
        if (d17 < 0.0) {
            d2 = 0.0;
        } else {
            d17 *= d17;
            d2 = d17 * d17 * dot(GRADIENTS[i2], d13, d14);
        }

        return 70.0 * (d0 + d1 + d2);
    }

    public void add(double[] values, double x, double y, int sizeX, int sizeY, double scaleX, double scaleY, double noiseScale) {
        int i = 0;

        for (int j = 0; j < sizeY; j++) {
            double d0 = (y + j) * scaleY + this.offsetY;

            for (int k = 0; k < sizeX; k++) {
                double d1 = (x + k) * scaleX + this.offsetX;
                double d5 = (d1 + d0) * SKEW_FACTOR_2D;
                int l = fastFloor(d1 + d5);
                int i1 = fastFloor(d0 + d5);
                double d6 = (l + i1) * UNSKEW_FACTOR_2D;
                double d7 = l - d6;
                double d8 = i1 - d6;
                double d9 = d1 - d7;
                double d10 = d0 - d8;
                int j1;
                int k1;
                if (d9 > d10) {
                    j1 = 1;
                    k1 = 0;
                } else {
                    j1 = 0;
                    k1 = 1;
                }

                double d11 = d9 - j1 + UNSKEW_FACTOR_2D;
                double d12 = d10 - k1 + UNSKEW_FACTOR_2D;
                double d13 = d9 - 1.0 + 2.0 * UNSKEW_FACTOR_2D;
                double d14 = d10 - 1.0 + 2.0 * UNSKEW_FACTOR_2D;
                int l1 = l & 0xFF;
                int i2 = i1 & 0xFF;
                int j2 = this.permutations[l1 + this.permutations[i2]] % 12;
                int k2 = this.permutations[l1 + j1 + this.permutations[i2 + k1]] % 12;
                int l2 = this.permutations[l1 + 1 + this.permutations[i2 + 1]] % 12;
                double d15 = 0.5 - d9 * d9 - d10 * d10;
                double d2;
                if (d15 < 0.0) {
                    d2 = 0.0;
                } else {
                    d15 *= d15;
                    d2 = d15 * d15 * dot(GRADIENTS[j2], d9, d10);
                }

                double d16 = 0.5 - d11 * d11 - d12 * d12;
                double d3;
                if (d16 < 0.0) {
                    d3 = 0.0;
                } else {
                    d16 *= d16;
                    d3 = d16 * d16 * dot(GRADIENTS[k2], d11, d12);
                }

                double d17 = 0.5 - d13 * d13 - d14 * d14;
                double d4;
                if (d17 < 0.0) {
                    d4 = 0.0;
                } else {
                    d17 *= d17;
                    d4 = d17 * d17 * dot(GRADIENTS[l2], d13, d14);
                }

                values[i++] += 70.0 * (d2 + d3 + d4) * noiseScale;
            }
        }
    }
}
