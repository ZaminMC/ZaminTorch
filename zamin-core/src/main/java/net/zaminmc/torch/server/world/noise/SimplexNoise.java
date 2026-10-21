package net.zaminmc.torch.server.world.noise;

import java.util.Random;

/**
 * Ported from reference/1.8.8 net/minecraft/world/gen/noise/SimplexNoise.java
 * (lines 1-117): the 2D simplex noise the reference's {@code PerlinSimplexNoise}
 * stacks — the permutation shuffle (the seeded Fisher-Yates over 256, the
 * doubled table), the three-corner skewed-grid walk ({@code getValue(x, y)},
 * lines 71-116), and the fixed gradient table's dot products. The seed is the
 * caller's {@code Random} — the reference constructs each level's noise from
 * the shared stream in construction order, so the consumption order is part
 * of the determinism.
 *
 * <p>This slice carries the 2D {@code getValue} form alone (the tulip band's
 * consult — {@code FOLIAGE_NOISE.getValue(x / 200.0, z / 200.0)}); the
 * {@code add} region form and the 3D sampling stay unported (no engine
 * surface reads them — the counted-note convention).</p>
 */
public class SimplexNoise {

    /** The reference's own gradient table (lines 5-7): twelve 3D rows, the
     *  2D consult reading the first two components. */
    private static final int[][] GRADIENTS = new int[][]{
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};

    public static final double SQRT_THREE = Math.sqrt(3.0);

    private final int[] permutations = new int[512];
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
        return x > 0.0 ? (int) x : (int) x - 1;
    }

    /** Computes the dot product of the given gradient vector and the vector {@code x, y}. */
    private static double dot(int[] gradient, double x, double y) {
        return gradient[0] * x + gradient[1] * y;
    }

    /**
     * The 2D sample (lines 71-116): the skewed grid's three corners — the
     * cell's own corner, the mid-edge pair chosen by the coordinate order,
     * and the far corner — each with the {@code 0.5 - d*d} spherical falloff
     * and the {@code 70.0} scale, zero outside the unit spheriproject.
     */
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
}
