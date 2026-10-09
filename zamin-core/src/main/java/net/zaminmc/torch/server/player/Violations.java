package net.zaminmc.torch.server.player;

import java.util.concurrent.TimeUnit;

/**
 * One player's anti-cheat violation ledger (the Hawk/NCP punishment shape):
 * the checks flag, the counters accumulate with a per-second decay, and the
 * thresholds kick. The event rings carry the nuker/fastplace budgets (the
 * rolling windows the community checks use).
 */
public final class Violations {

    /** Ring capacity: the creative break budget is the largest window. */
    private static final int RING_SIZE = 48;
    /** The MultiBreak band: two finishes inside one tick (25 ms). */
    public static final long SAME_TICK_NANOS = 25_000_000L;

    // The reach ladder (the Acid-adopted shape): flag, decay, kick at 20.
    private double reach;
    private long reachLastNanos;

    // The nuker ladder (the NCP Frequency shape): budget + flag, kick at 10.
    private final long[] breakRing = new long[RING_SIZE];
    private int breakRingIndex;
    private long lastBreakNanos;
    private Object lastBreakTarget;
    private int nuker;
    private long nukerLastNanos;

    // The fastplace ladder (the NCP FastPlace shape): kick at 10.
    private final long[] placeRing = new long[RING_SIZE];
    private int placeRingIndex;
    private int fastPlace;
    private long fastPlaceLastNanos;

    /**
     * @return whether the break fits the rolling budget (false = over).
     * Callers pass the player's mode budget (25/20t survival, 45/20t creative).
     */
    public synchronized boolean recordBreak(long nowNanos, long windowNanos, int budget) {
        long count = 0;
        for (long stamp : breakRing) {
            if (stamp != 0 && nowNanos - stamp <= windowNanos) {
                count++;
            }
        }
        breakRing[breakRingIndex] = nowNanos;
        breakRingIndex = (breakRingIndex + 1) % RING_SIZE;
        lastBreakNanos = nowNanos;
        return count < budget;
    }

    /** Notes the finished dig's target (the MultiBreak different-target rule). */
    public synchronized void noteBreakTarget(long nowNanos, Object target) {
        this.lastBreakNanos = nowNanos;
        this.lastBreakTarget = target;
    }

    /** @return whether this finish landed inside one tick of a DIFFERENT target. */
    public synchronized boolean isMultiBreak(long nowNanos, Object target) {
        return lastBreakTarget != null
                && nowNanos - lastBreakNanos <= SAME_TICK_NANOS
                && !lastBreakTarget.equals(target);
    }

    /** @return whether the placement fits the rolling budget (false = over). */
    public synchronized boolean recordPlace(long nowNanos, long windowNanos, int budget) {
        long count = 0;
        for (long stamp : placeRing) {
            if (stamp != 0 && nowNanos - stamp <= windowNanos) {
                count++;
            }
        }
        placeRing[placeRingIndex] = nowNanos;
        placeRingIndex = (placeRingIndex + 1) % RING_SIZE;
        return count < budget;
    }

    /** Flags one reach hit (the decay runs first: one point per second out). */
    public synchronized void addReachViolation() {
        reach = decay(reach, reachLastNanos);
        reachLastNanos = System.nanoTime();
        reach += 1.0;
    }

    public synchronized double reachViolations() {
        return reach;
    }

    /** Flags one nuker window (same decay rule). */
    public synchronized void addNukerViolation() {
        nuker = (int) decay(nuker, nukerLastNanos);
        nukerLastNanos = System.nanoTime();
        nuker += 1;
    }

    public synchronized int nukerViolations() {
        return nuker;
    }

    /** Flags one fastplace window (same decay rule). */
    public synchronized void addFastPlaceViolation() {
        fastPlace = (int) decay(fastPlace, fastPlaceLastNanos);
        fastPlaceLastNanos = System.nanoTime();
        fastPlace += 1;
    }

    public synchronized int fastPlaceViolations() {
        return fastPlace;
    }

    /** The shared decay: one point per elapsed second, floored at zero. */
    private static double decay(double value, long lastNanos) {
        if (lastNanos == 0) {
            return value;
        }
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - lastNanos);
        return Math.max(0, value - elapsedSeconds);
    }
}
