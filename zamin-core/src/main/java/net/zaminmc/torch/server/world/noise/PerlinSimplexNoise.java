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
 * <p>The engine's reader is the tulip band's {@code FOLIAGE_NOISE} — the
 * reference's own static {@code new PerlinSimplexNoise(new Random(2345L), 1)}
 * (Biome.java line 595): a FIXED seed, world-seed independent, one level —
 * so the flower bands' noise field is identical across every world, the
 * reference's own determinism. The {@code getRegion} bulk form stays
 * unported (no engine surface reads it — the counted-note convention).</p>
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
}
