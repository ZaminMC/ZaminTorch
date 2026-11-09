package net.zaminmc.torch.server.world.noise;

import java.util.Random;

/**
 * Ported from reference/1.8.8 net/minecraft/world/gen/noise/PerlinSimplexNoise.java
 * (lines 1-69): the simplex-octave stack. {@code levels} SimplexNoise
 * instances construct from the caller's Random IN ORDER (the consumption
 * order is part of the determinism), and {@code getValue(x, y)} sums every
 * octave at halving amplitude (lines 21-31: {@code noiseLevels[i].getValue(x
 * * d1, y * d1) / d1} with {@code d1} halving per level).
 *
 * <p>The engine's readers are the tulip band's {@code FOLIAGE_NOISE} — the
 * reference's own static {@code new PerlinSimplexNoise(new Random(2345L), 1)}
 * (Biome.java line 595): a FIXED seed, world-seed independent, one level —
 * and the terrain noise core's depth consult (Wave TN — the
 * OverworldChunkGenerator's own {@code perlinNoise3}, the fourth noise of
 * the generator's construction stream, read through {@link #getRegion} at
 * the surface pass).</p>
 */
public class PerlinSimplexNoise {

    private final SimplexNoise[] noiseLevels;
    private final int levels;

    public PerlinSimplexNoise(Random random, int levels) {
        this.levels = levels;
        this.noiseLevels = new SimplexNoise[levels];

        for (int i = 0; i < levels; i++) {
            this.noiseLevels[i] = new SimplexNoise(random);
        }
    }

    /**
     * The 2D sample (lines 21-31): every octave at its own scale, summed at
     * the halving amplitude — with a single level (the FOLIAGE_NOISE shape)
     * this is the one octave's raw value.
     */
    public double getValue(double x, double y) {
        double d0 = 0.0;
        double d1 = 1.0;

        for (int i = 0; i < this.levels; i++) {
            d0 += this.noiseLevels[i].getValue(x * d1, y * d1) / d1;
            d1 /= 2.0;
        }

        return d0;
    }

    /**
     * The bulk region form (the reference's lines 30-33): the convenience
     * overload carrying the default y exponent 0.5 — the surface depth
     * consult's own call shape (buildSurfaces line 160).
     */
    public double[] getRegion(double[] values, double x, double y, int sizeX, int sizeY, double scaleX, double scaleY, double scaleExponent) {
        return this.getRegion(values, x, y, sizeX, sizeY, scaleX, scaleY, scaleExponent, 0.5);
    }

    /**
     * The bulk region form (the reference's lines 34-55): every octave's
     * {@code add} over the area at its own scale ({@code scaleX * d0 * d1}
     * with {@code d0} carrying the x exponent and {@code d1} halving per
     * level) and its own amplitude ({@code 0.55 / d1}, DOUBLING per level)
     * — the depth band's own 0.0625/0.55 halving-doubling walk. The output
     * layout is the add form's own: x-fastest.
     */
    public double[] getRegion(
        double[] values, double x, double y, int sizeX, int sizeY, double scaleX, double scaleY, double scaleExponentX, double scaleExponentY
    ) {
        if (values != null && values.length >= sizeX * sizeY) {
            for (int i = 0; i < values.length; i++) {
                values[i] = 0.0;
            }
        } else {
            values = new double[sizeX * sizeY];
        }

        double d1 = 1.0;
        double d0 = 1.0;

        for (int j = 0; j < this.levels; j++) {
            this.noiseLevels[j].add(values, x, y, sizeX, sizeY, scaleX * d0 * d1, scaleY * d0 * d1, 0.55 / d1);
            d0 *= scaleExponentX;
            d1 *= scaleExponentY;
        }

        return values;
    }

    /** The level accessor (the per-column depth probe's walk — the shared instance's own levels). */
    public SimplexNoise level(int index) {
        return this.noiseLevels[index];
    }

    /** The level count (the per-column probe's bound). */
    public int levelCount() {
        return this.levels;
    }
}
