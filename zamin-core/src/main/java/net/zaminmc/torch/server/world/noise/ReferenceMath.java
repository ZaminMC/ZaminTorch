package net.zaminmc.torch.server.world.noise;

/**
 * Ported from reference/1.8.8 net/minecraft/util/math/MathHelper.java (the
 * SINE_TABLE block, lines 11/401-404, and the sin/cos accessors, lines
 * 20-26): the vanilla 65536-entry sine lookup. The generator's cave carver
 * walks its yaw/pitch through these, so using java.lang.Math instead would
 * drift the tunnel shapes — the table's quantization IS the vanilla cave
 * shape. {@code sin(x)} reads slot {@code (int)(x * 10430.378F) & 65535};
 * {@code cos(x)} reads the +16384.0F offset slot (the quarter-turn shift).
 */
public final class ReferenceMath {

    /** The table: sine over [0, 2pi) in 2pi/65536 steps, float-rounded. */
    private static final float[] SINE_TABLE = new float[65536];

    static {
        for (int i = 0; i < 65536; i++) {
            SINE_TABLE[i] = (float) Math.sin(i * Math.PI * 2.0 / 65536.0);
        }
    }

    /** The reference MathHelper.sin (float domain, table-quantized). */
    public static float sin(float x) {
        return SINE_TABLE[(int) (x * 10430.378F) & 65535];
    }

    /** The reference MathHelper.cos (the quarter-turn table offset). */
    public static float cos(float x) {
        return SINE_TABLE[(int) (x * 10430.378F + 16384.0F) & 65535];
    }

    private ReferenceMath() {
    }
}
