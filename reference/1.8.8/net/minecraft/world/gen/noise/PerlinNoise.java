package net.minecraft.world.gen.noise;

import java.util.Random;
import net.minecraft.util.math.MathHelper;

public class PerlinNoise extends Noise {
    private ImprovedNoise[] noiseLevels;
    private int levels;

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
            long k = MathHelper.lfloor(d0);
            long l = MathHelper.lfloor(d2);
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

    public double[] getRegion(double[] values, int x, int z, int sizeX, int sizeZ, double scaleX, double scaleZ, double d) {
        return this.getRegion(values, x, 10, z, sizeX, 1, sizeZ, scaleX, 1.0, scaleZ);
    }
}
