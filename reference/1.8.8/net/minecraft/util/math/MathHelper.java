package net.minecraft.util.math;

import java.util.Random;
import java.util.UUID;

public class MathHelper {
    public static final float SQRT_TWO = sqrt(2.0F);
    /**
     * A table of sine values in the range [0, 2pi) with steps of 2pi / 65536.
     */
    private static final float[] SINE_TABLE = new float[65536];
    private static final int[] MULTIPLY_DE_BRUIJN_BIT_POSITION;
    private static final double MAGIC_ATAN2_CONSTANT;
    private static final double[] ASIN_TABLE;
    /**
     * Converts a given x coordinate on a unit circle to its corresponding y coordinate.
     */
    private static final double[] Y_TO_X_TABLE;

    public static float sin(float x) {
        return SINE_TABLE[(int)(x * 10430.378F) & 65535];
    }

    public static float cos(float x) {
        return SINE_TABLE[(int)(x * 10430.378F + 16384.0F) & 65535];
    }

    public static float sqrt(float x) {
        return (float)Math.sqrt(x);
    }

    public static float sqrt(double x) {
        return (float)Math.sqrt(x);
    }

    public static int floor(float x) {
        int i = (int)x;
        return x < i ? i - 1 : i;
    }

    public static int fastFloor(double x) {
        return (int)(x + 1024.0) - 1024;
    }

    public static int floor(double x) {
        int i = (int)x;
        return x < i ? i - 1 : i;
    }

    public static long lfloor(double x) {
        long i = (long)x;
        return x < i ? i - 1L : i;
    }

    public static int abs(double x) {
        return (int)(x >= 0.0 ? x : -x + 1.0);
    }

    public static float abs(float x) {
        return x >= 0.0F ? x : -x;
    }

    public static int abs(int x) {
        return x >= 0 ? x : -x;
    }

    public static int ceil(float x) {
        int i = (int)x;
        return x > i ? i + 1 : i;
    }

    public static int ceil(double x) {
        int i = (int)x;
        return x > i ? i + 1 : i;
    }

    public static int clamp(int x, int min, int max) {
        if (x < min) {
            return min;
        } else {
            return x > max ? max : x;
        }
    }

    public static float clamp(float x, float min, float max) {
        if (x < min) {
            return min;
        } else {
            return x > max ? max : x;
        }
    }

    public static double clamp(double x, double min, double max) {
        if (x < min) {
            return min;
        } else {
            return x > max ? max : x;
        }
    }

    public static double clampedLerp(double min, double max, double delta) {
        if (delta < 0.0) {
            return min;
        } else {
            return delta > 1.0 ? max : min + (max - min) * delta;
        }
    }

    public static double absMax(double a, double b) {
        if (a < 0.0) {
            a = -a;
        }

        if (b < 0.0) {
            b = -b;
        }

        return a > b ? a : b;
    }

    public static int floorDiv(int a, int b) {
        return a < 0 ? -((-a - 1) / b) - 1 : a / b;
    }

    public static int nextInt(Random random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    public static float nextFloat(Random random, float min, float max) {
        return min >= max ? min : random.nextFloat() * (max - min) + min;
    }

    public static double nextDouble(Random random, double min, double max) {
        return min >= max ? min : random.nextDouble() * (max - min) + min;
    }

    public static double average(long[] s) {
        long i = 0L;

        for (long j : s) {
            i += j;
        }

        return (double)i / s.length;
    }

    public static boolean equalsApproximate(float a, float b) {
        return abs(b - a) < 1.0E-5F;
    }

    public static int floorMod(int dividend, int divisor) {
        return (dividend % divisor + divisor) % divisor;
    }

    public static float wrapDegrees(float degrees) {
        degrees %= 360.0F;
        if (degrees >= 180.0F) {
            degrees -= 360.0F;
        }

        if (degrees < -180.0F) {
            degrees += 360.0F;
        }

        return degrees;
    }

    public static double wrapDegrees(double degrees) {
        degrees %= 360.0;
        if (degrees >= 180.0) {
            degrees -= 360.0;
        }

        if (degrees < -180.0) {
            degrees += 360.0;
        }

        return degrees;
    }

    public static int parseInt(String s, int defaultValue) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable throwable) {
            return defaultValue;
        }
    }

    public static int parseInt(String s, int defaultValue, int min) {
        return Math.max(min, parseInt(s, defaultValue));
    }

    public static double parseDouble(String s, double defaultValue) {
        try {
            return Double.parseDouble(s);
        } catch (Throwable throwable) {
            return defaultValue;
        }
    }

    public static double parseDouble(String s, double defaultValue, double min) {
        return Math.max(min, parseDouble(s, defaultValue));
    }

    public static int smallestEncompassingPowerOfTwo(int x) {
        int i = x - 1;
        i |= i >> 1;
        i |= i >> 2;
        i |= i >> 4;
        i |= i >> 8;
        i |= i >> 16;
        return i + 1;
    }

    private static boolean isPowerOfTwo(int x) {
        return x != 0 && (x & x - 1) == 0;
    }

    private static int log2DeBruijn(int x) {
        x = isPowerOfTwo(x) ? x : smallestEncompassingPowerOfTwo(x);
        return MULTIPLY_DE_BRUIJN_BIT_POSITION[(int)(x * 125613361L >> 27) & 31];
    }

    public static int log2(int x) {
        return log2DeBruijn(x) - (isPowerOfTwo(x) ? 0 : 1);
    }

    public static int roundUp(int x, int interval) {
        if (interval == 0) {
            return 0;
        }

        if (x == 0) {
            return interval;
        }

        if (x < 0) {
            interval *= -1;
        }

        int i = x % interval;
        return i == 0 ? x : x + interval - i;
    }

    public static int packRGB(float r, float g, float b) {
        return packRGB(floor(r * 255.0F), floor(g * 255.0F), floor(b * 255.0F));
    }

    public static int packRGB(int r, int g, int b) {
        int i = r;
        i = (i << 8) + g;
        return (i << 8) + b;
    }

    public static int mulARGB(int argb1, int argb2) {
        int i = (argb1 & 0xFF0000) >> 16;
        int j = (argb2 & 0xFF0000) >> 16;
        int k = (argb1 & 0xFF00) >> 8;
        int l = (argb2 & 0xFF00) >> 8;
        int i1 = (argb1 & 0xFF) >> 0;
        int j1 = (argb2 & 0xFF) >> 0;
        int k1 = (int)((float)i * j / 255.0F);
        int l1 = (int)((float)k * l / 255.0F);
        int i2 = (int)((float)i1 * j1 / 255.0F);
        return argb1 & 0xFF000000 | k1 << 16 | l1 << 8 | i2;
    }

    public static double floorDiff(double x) {
        return x - Math.floor(x);
    }

    public static long hashCode(Vec3i vec) {
        return hashCode(vec.getX(), vec.getY(), vec.getZ());
    }

    public static long hashCode(int x, int y, int z) {
        long i = x * 3129871 ^ z * 116129781L ^ y;
        return i * i * 42317861L + i * 11L;
    }

    public static UUID nextUuid(Random random) {
        long i = random.nextLong() & -61441L | 16384L;
        long j = random.nextLong() & 4611686018427387903L | Long.MIN_VALUE;
        return new UUID(i, j);
    }

    public static double inverseLerp(double a, double b, double c) {
        return (a - b) / (c - b);
    }

    public static double fastAtan2(double a, double b) {
        double d0 = b * b + a * a;
        if (Double.isNaN(d0)) {
            return Double.NaN;
        }

        boolean flag = a < 0.0;
        if (flag) {
            a = -a;
        }

        boolean flag1 = b < 0.0;
        if (flag1) {
            b = -b;
        }

        boolean flag2 = a > b;
        if (flag2) {
            double d1 = b;
            b = a;
            a = d1;
        }

        double d9 = fastInvSqrt(d0);
        b *= d9;
        a *= d9;
        double d2 = MAGIC_ATAN2_CONSTANT + a;
        int i = (int)Double.doubleToRawLongBits(d2);
        double d3 = ASIN_TABLE[i];
        double d4 = Y_TO_X_TABLE[i];
        double d5 = d2 - MAGIC_ATAN2_CONSTANT;
        double d6 = a * d4 - b * d5;
        double d7 = (6.0 + d6 * d6) * d6 * 0.16666666666666666;
        double d8 = d3 + d7;
        if (flag2) {
            d8 = (Math.PI / 2) - d8;
        }

        if (flag1) {
            d8 = Math.PI - d8;
        }

        if (flag) {
            d8 = -d8;
        }

        return d8;
    }

    /**
     * A fast approximation of 1/sqrt(x). See {@link https://en.wikipedia.org/wiki/Fast_inverse_square_root}
     */
    public static double fastInvSqrt(double x) {
        double d0 = 0.5 * x;
        long i = Double.doubleToRawLongBits(x);
        i = 6910469410427058090L - (i >> 1);
        x = Double.longBitsToDouble(i);
        return x * (1.5 - d0 * x * x);
    }

    public static int toRgb(float hue, float saturation, float value) {
        int i = (int)(hue * 6.0F) % 6;
        float f = hue * 6.0F - i;
        float f1 = value * (1.0F - saturation);
        float f2 = value * (1.0F - f * saturation);
        float f3 = value * (1.0F - (1.0F - f) * saturation);
        float f4;
        float f5;
        float f6;
        switch (i) {
            case 0:
                f4 = value;
                f5 = f3;
                f6 = f1;
                break;
            case 1:
                f4 = f2;
                f5 = value;
                f6 = f1;
                break;
            case 2:
                f4 = f1;
                f5 = value;
                f6 = f3;
                break;
            case 3:
                f4 = f1;
                f5 = f2;
                f6 = value;
                break;
            case 4:
                f4 = f3;
                f5 = f1;
                f6 = value;
                break;
            case 5:
                f4 = value;
                f5 = f1;
                f6 = f2;
                break;
            default:
                throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + hue + ", " + saturation + ", " + value);
        }

        int j = clamp((int)(f4 * 255.0F), 0, 255);
        int k = clamp((int)(f5 * 255.0F), 0, 255);
        int l = clamp((int)(f6 * 255.0F), 0, 255);
        return j << 16 | k << 8 | l;
    }

    static {
        for (int i = 0; i < 65536; i++) {
            SINE_TABLE[i] = (float)Math.sin(i * Math.PI * 2.0 / 65536.0);
        }

        MULTIPLY_DE_BRUIJN_BIT_POSITION = new int[]{
            0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9
        };
        MAGIC_ATAN2_CONSTANT = Double.longBitsToDouble(4805340802404319232L);
        ASIN_TABLE = new double[257];
        Y_TO_X_TABLE = new double[257];

        for (int j = 0; j < 257; j++) {
            double d0 = j / 256.0;
            double d1 = Math.asin(d0);
            Y_TO_X_TABLE[j] = Math.cos(d1);
            ASIN_TABLE[j] = d1;
        }
    }
}
