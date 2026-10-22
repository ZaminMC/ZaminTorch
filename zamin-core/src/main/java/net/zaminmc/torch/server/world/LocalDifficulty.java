package net.zaminmc.torch.server.world;

/**
 * The local difficulty at a position (the reference's
 * {@code net.minecraft.world.LocalDifficulty} 1:1 — World.getLocalDifficulty's
 * own answer): the regional difficulty the spawn dice and the door-break
 * arming rolls read, computed from the four inputs the reference's ctor
 * carries — the base difficulty id (the engine's {@code 0 peaceful ..
 * 3 hard} over the reference's {@code Difficulty} enum), the RAW day clock
 * (the reference's unwrapped {@code WorldData.timeOfDay} — the day count
 * rides its high bits, so the time term grows over the world's life; the
 * wrapped sky-cycle clock the engine's {@link EngineWorld#timeOfDay()}
 * answers would cap inside the first day and never grows), the chunk's
 * inhabited time (the ticks the chunk has spent inside a watch ring —
 * {@link EngineChunk#inhabitedTime()}), and the moon size
 * ({@link EngineWorld#moonSize()}).
 *
 * <p>THE LAW (the reference's {@code calculate} verbatim): the peaceful
 * base reads 0 outright; every other base walks the two bands — the time
 * term {@code clamp((timeOfDay - 72000) / 1440000, 0, 1) * 0.25} (the
 * world's first ~63 days, saturating at 1.0), then the bonus band: the
 * inhabited term {@code clamp(inhabitedTime / 3600000, 0, 1) * (hard ?
 * 1.0 : 0.75)} (three hours of watch, the hard base carrying the full
 * weight), plus the moon term {@code clamp(moonSize * 0.25, 0, timeTerm)}
 * — CAPPED by the time term (a full moon doubles nothing on a fresh
 * world: the moon's bonus never exceeds the time band's own
 * contribution), the easy base shaving the whole bonus band in half —
 * and the base's own id multiplies the sum.</p>
 *
 * <p>{@link #getMultiplier()} is the consumer read (the reference's own):
 * the regional value clamped into 0..1 over the 2..4 band — below 2 the
 * multiplier reads 0 (the fresh-world read every spawn dice's band
 * consults), above 4 it reads 1 (the deep-geared late-world read).</p>
 */
public final class LocalDifficulty {

    /** The peaceful base id (the reference's Difficulty.PEACEFUL). */
    private static final int PEACEFUL = 0;
    /** The easy base id (the reference's Difficulty.EASY — the bonus shave). */
    private static final int EASY = 1;
    /** The hard base id (the reference's Difficulty.HARD — the full inhabited weight). */
    private static final int HARD = 3;

    private final int base;
    private final float difficulty;

    /**
     * The reference's ctor: the four inputs, the answer computed once.
     *
     * @param base           the difficulty id (0 peaceful, 1 easy, 2 normal, 3 hard)
     * @param timeOfDay      the RAW day clock (the unwrapped day count ticks)
     * @param inhabitedTime  the chunk's accrued inhabited ticks (0 at an
     *                       unloaded position — the reference's own gate)
     * @param moonSize       the moon size 1.0..0.0 (0.0 at an unloaded
     *                       position — the reference's own gate)
     */
    public LocalDifficulty(int base, long timeOfDay, long inhabitedTime, float moonSize) {
        this.base = base;
        this.difficulty = calculate(base, timeOfDay, inhabitedTime, moonSize);
    }

    /** The raw regional value (the reference's {@code get}). */
    public float get() {
        return difficulty;
    }

    /**
     * The consumer read (the reference's {@code getMultiplier}): the
     * regional value clamped into 0..1 over the 2..4 band.
     */
    public float getMultiplier() {
        if (this.difficulty < 2.0F) {
            return 0.0F;
        }
        return this.difficulty > 4.0F ? 1.0F : (this.difficulty - 2.0F) / 2.0F;
    }

    /** The reference's {@code calculate} verbatim, the engine's int ids in the base's seat. */
    private static float calculate(int base, long timeOfDay, long inhabitedTime, float moonSize) {
        if (base == PEACEFUL) {
            return 0.0F;
        }

        boolean hard = base == HARD;
        float f = 0.75F;
        // The time term: the world's first 1,440,000 raw ticks (60 days
        // past the -72,000 offset), scaled to a quarter.
        float f1 = clamp(((float) timeOfDay + -72000.0F) / 1440000.0F, 0.0F, 1.0F) * 0.25F;
        f += f1;
        float f2 = 0.0F;
        // The inhabited term: three hours of watch (3,600,000 ticks), the
        // hard base carrying the full weight, the rest 0.75 of it.
        f2 += clamp((float) inhabitedTime / 3600000.0F, 0.0F, 1.0F) * (hard ? 1.0F : 0.75F);
        // The moon term: a quarter of the moon's size, never more than the
        // time term's own contribution (the fresh-world cap).
        f2 += clamp(moonSize * 0.25F, 0.0F, f1);
        if (base == EASY) {
            f2 *= 0.5F;
        }

        f += f2;
        return base * f;
    }

    /** The reference's MathHelper.clamp — the float band clamp. */
    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }
}
