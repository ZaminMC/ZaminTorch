package net.zaminmc.torch.server.world.noise;

import java.util.Random;

/**
 * Ported from reference/1.8.8 net/minecraft/world/gen/noise/PerlinNoise.java
 * (lines 1-52): the octave stack. {@code levels} ImprovedNoise instances are
 * constructed from the caller's Random IN ORDER (each construction consumes
 * the stream, so construction order is part of the world's determinism), and
 * {@code getRegion} sums every octave at halving amplitude.
 *
 * <p>The coordinate wrap is the reference's own: each octave's x/z sample
 * wraps its integer part into ±16777216 so far-from-origin sampling keeps the
 * permutation lookups in range — the historical precision trick, preserved
 * exactly (including the odd y path, which does not wrap: the reference wraps
 * x and z only).</p>
 */
public class PerlinNoise {

    private final ImprovedNoise[] noiseLevels;
    private final int levels;

    public PerlinNoise(Random random, int levels) {
        this.levels = levels;
        this.noiseLevels = new ImprovedNoise[levels];

        for (int i = 0; i < levels; i++) {
            this.noiseLevels[i] = new ImprovedNoise(random);
        }
    }

    public double[] getRegion(double[] values, int x, int y, int z, int sizeX, int sizeY, int sizeZ, double scaleX, double scaleY, double scaleZ) {
        if (values == null) {
            values = new double[sizeX * sizeY * sizeZ];
        } else {
            for (int i = 0; i < values.length; i++) {
                values[i] = 0.0;
            }
        }

        double d3 = 1.0;

        for (int j = 0; j < this.levels; j++) {
            double d0 = x * d3 * scaleX;
            double d1 = y * d3 * scaleY;
            double d2 = z * d3 * scaleZ;
            long k = lfloor(d0);
            long l = lfloor(d2);
            d0 -= k;
            d2 -= l;
            k %= 16777216L;
            l %= 16777216L;
            d0 += k;
            d2 += l;
            this.noiseLevels[j].add(values, d0, d1, d2, sizeX, sizeY, sizeZ, scaleX * d3, scaleY * d3, scaleZ * d3, d3);
            d3 /= 2.0;
        }

        return values;
    }

    /** The 2D convenience overload (the reference's y=10 / sizeY=1 shape). */
    public double[] getRegion(double[] values, int x, int z, int sizeX, int sizeZ, double scaleX, double scaleZ, double d) {
        return this.getRegion(values, x, 10, z, sizeX, 1, sizeZ, scaleX, 1.0, scaleZ);
    }

    /** The reference MathHelper.lfloor: floor as long (the truncation-then-check shape). */
    private static long lfloor(double value) {
        long i = (long) value;
        return value < (double) i ? i - 1L : i;
    }
}
