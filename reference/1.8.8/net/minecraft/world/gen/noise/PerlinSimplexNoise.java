package net.minecraft.world.gen.noise;

import java.util.Random;

public class PerlinSimplexNoise extends Noise {
    private SimplexNoise[] noiseLevels;
    private int levels;

    public PerlinSimplexNoise(Random random, int levels) {
        this.levels = levels;
        this.noiseLevels = new SimplexNoise[levels];

        for (int i = 0; i < levels; i++) {
            this.noiseLevels[i] = new SimplexNoise(random);
        }
    }

    public double getValue(double x, double y) {
        double d0 = 0.0;
        double d1 = 1.0;

        for (int i = 0; i < this.levels; i++) {
            d0 += this.noiseLevels[i].getValue(x * d1, y * d1) / d1;
            d1 /= 2.0;
        }

        return d0;
    }

    public double[] getRegion(double[] values, double x, double y, int sizeX, int sizeY, double scaleX, double scaleY, double scaleExponent) {
        return this.getRegion(values, x, y, sizeX, sizeY, scaleX, scaleY, scaleExponent, 0.5);
    }

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
}
