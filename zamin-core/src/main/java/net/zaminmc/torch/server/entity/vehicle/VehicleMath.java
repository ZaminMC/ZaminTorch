package net.zaminmc.torch.server.entity.vehicle;

/**
 * The vehicle family's own math (the reference MathHelper's two members
 * the wire laws read, ported verbatim — the 2026-11-08 census rows: the
 * cart's yaw convention and the boat's heading law both compute their
 * angles through the reference's own approximation, never the platform
 * atan2, so the wire values carry the reference's own error band too).
 *
 * <p>THE BIT TRICK, documented (fastAtan2's table index): the magic
 * constant is exactly 2^44, so {@code MAGIC + a} with a in [0, 1] walks
 * the double's mantissa in ULP steps of 2^(44-52) = 1/256 — the low 32
 * bits of the raw long ARE the table index a·256, in [0, 256], the
 * 257-entry tables' own span.</p>
 */
public final class VehicleMath {

    private VehicleMath() {
    }

    /** 2^44 exactly — the ULP-stepped index base (see class doc). */
    private static final double MAGIC_ATAN2_CONSTANT =
            Double.longBitsToDouble(4805340802404319232L);

    private static final double[] ASIN_TABLE = new double[257];
    private static final double[] Y_TO_X_TABLE = new double[257];

    static {
        for (int j = 0; j < 257; j++) {
            double d0 = j / 256.0;
            double d1 = Math.asin(d0);
            Y_TO_X_TABLE[j] = Math.cos(d1);
            ASIN_TABLE[j] = d1;
        }
    }

    /**
     * The reference's fastAtan2 (MathHelper lines 291-342), verbatim: the
     * quadrant flips, the a/b swap, the fastInvSqrt normalization, the
     * table read over the magic constant's mantissa index, the
     * sixth-degree correction, and the closing quadrant unwinds.
     */
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
        int i = (int) Double.doubleToRawLongBits(d2);
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
     * The reference's fastInvSqrt (MathHelper lines 343-349), verbatim:
     * the Quake bit hack with one Newton step.
     */
    private static double fastInvSqrt(double x) {
        double d0 = 0.5 * x;
        long i = Double.doubleToRawLongBits(x);
        i = 6910469410427058090L - (i >> 1);
        x = Double.longBitsToDouble(i);
        return x * (1.5 - d0 * x * x);
    }

    /**
     * The reference's wrapDegrees over doubles (MathHelper lines
     * 168-177), verbatim.
     */
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
}
