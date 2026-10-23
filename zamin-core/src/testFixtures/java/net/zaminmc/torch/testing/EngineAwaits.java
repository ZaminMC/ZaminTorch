package net.zaminmc.torch.testing;

import java.util.function.BooleanSupplier;

import net.zaminmc.torch.server.world.EngineWorld;

/**
 * The shared engine-condition awaits (the test-deadline census's ML-002
 * conversions — docs/TEST_DEADLINE_CENSUS.md).
 *
 * <p>The engine's own tick clock is the honest clock for every wait that
 * means "let the engine progress": {@code EngineTicker}'s loop is
 * rate-limited to the nominal cadence with NO burst catch-up, so under a
 * loaded runner a wall-clock sleep observes FEWER engine ticks than
 * intended — the drift that made the release pipeline load-flaky (runs
 * #361/#362, docs/CYCLE3_AUDIT.md). These awaits count COMPLETED TICKS
 * ({@link EngineWorld#totalTicks()}, the volatile counter every tick
 * advances), so a slow runner changes WHEN the wait ends, never HOW MUCH
 * engine progress it covers.</p>
 *
 * <p>The liveness guard is TWO-SIDED (the run #375/#376 lesson: a single
 * total wall deadline cannot tell a stalled engine from a slow one):
 * <ul>
 *   <li>the STALL window — no completed tick for {@link #STALL_WINDOW_MS}
 *       means the engine STOPPED (a real defect worth failing on, and the
 *       only "stalled" verdict there is);</li>
 *   <li>the PATIENCE cap — {@link #capMillis(long)}, 15x the nominal
 *       window floored at 30s and capped at 15 minutes, bounds the suite
 *       against an engine that keeps ticking but averages under ~1.3 TPS
 *       — too slow to test against, a different (and honestly named)
 *       failure than a stall.</li>
 * </ul>
 * A slow-but-alive runner (transient GC pauses, scheduler bursts — the
 * shapes the release runners actually show) NEVER trips either side: the
 * stall window resets on every completed tick, and the cap tolerates a
 * 15x slowdown. Failure detection is never loosened — the awaited
 * condition or tick count is exactly what the converted wall sleep meant
 * to cover.</p>
 */
public final class EngineAwaits {

    private EngineAwaits() {
    }

    /** No completed tick for this long = the engine STOPPED. */
    private static final long STALL_WINDOW_MS = 60_000L;

    /**
     * Awaits {@code ticks} more COMPLETED engine ticks on the world's own
     * tick clock. The converted form of every "let the settle land" /
     * "past the N-tick window" / negative-proof wall sleep: the wait
     * covers exactly the intended engine progress regardless of load.
     *
     * @param world the engine world whose tick clock is awaited (the
     *              server's overworld — {@code server.world()} — is the
     *              canonical clock; every dimension advances on it)
     * @param ticks how many more ticks must COMPLETE before returning
     * @param what a human-readable purpose for the failure message
     */
    public static void awaitTicks(EngineWorld world, long ticks, String what)
            throws InterruptedException {
        long start = world.totalTicks();
        long target = start + ticks;
        long wallStart = System.currentTimeMillis();
        long cap = wallStart + capMillis(ticks);
        long lastTicks = start;
        long lastProgress = wallStart;
        while (world.totalTicks() < target) {
            long now = world.totalTicks();
            if (now > lastTicks) {
                lastTicks = now;
                lastProgress = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastProgress > STALL_WINDOW_MS) {
                throw new AssertionError("The engine stalled mid-wait: " + what
                        + " (awaited " + ticks + " ticks; only "
                        + (world.totalTicks() - start) + " completed)");
            }
            if (System.currentTimeMillis() > cap) {
                throw new AssertionError("The engine is running too slowly to test: "
                        + what + " (awaited " + ticks + " ticks; only "
                        + (world.totalTicks() - start) + " completed in "
                        + (System.currentTimeMillis() - wallStart) + "ms)");
            }
            Thread.sleep(25);
        }
    }

    /**
     * The patience cap: 15x the nominal window, floored at 30s and capped
     * at 15 minutes — a suite-time bound against a persistently crawling
     * engine, never a performance assertion and never an unbounded hang.
     */
    private static long capMillis(long ticks) {
        return Math.max(30_000L, Math.min(ticks * 50L * 15L, 900_000L));
    }

    /**
     * Awaits a condition with a TICK-SCALED deadline: the wait fails only
     * when the engine COMPLETED {@code tickBudget} ticks without the
     * condition landing — the honest bound for tick-cadence conditions
     * (a wall deadline expires by wall time even when the engine simply
     * runs slow; a tick budget never does). The two-sided liveness guard
     * still covers a stopped engine (the stall window) and bounds the
     * suite against a crawling one (the patience cap).
     *
     * @param world      the engine world providing the tick clock
     * @param condition  the engine-visible condition to poll
     * @param tickBudget the engine ticks the condition may take; the
     *                   product's own cadence, not a wall guess
     * @param what       a human-readable purpose for the failure message
     */
    public static void awaitCondition(EngineWorld world, BooleanSupplier condition,
            long tickBudget, String what) throws InterruptedException {
        if (!awaitConditionWithin(world, condition, tickBudget, what)) {
            throw new AssertionError("Condition not met within " + tickBudget
                    + " engine ticks: " + what);
        }
    }

    /**
     * The expected-miss form: polls the condition for up to
     * {@code tickBudget} COMPLETED engine ticks and answers whether it
     * landed — for the probabilistic outcomes a throwing await can never
     * cover (the thorns roll's 30% no-bite arm, a loot window's miss).
     * The liveness guard still throws: a stopped or crawling engine is a
     * defect mid-test regardless of the polled outcome.
     *
     * @return whether the condition landed within the tick budget
     */
    public static boolean awaitConditionWithin(EngineWorld world, BooleanSupplier condition,
            long tickBudget, String what) throws InterruptedException {
        long start = world.totalTicks();
        long tickDeadline = start + tickBudget;
        long cap = System.currentTimeMillis() + capMillis(tickBudget);
        long lastTicks = start;
        long lastProgress = System.currentTimeMillis();
        while (!condition.getAsBoolean()) {
            long now = world.totalTicks();
            if (now >= tickDeadline) {
                return false;
            }
            if (now > lastTicks) {
                lastTicks = now;
                lastProgress = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastProgress > STALL_WINDOW_MS) {
                throw new AssertionError("The engine stalled before the condition"
                        + " landed: " + what);
            }
            if (System.currentTimeMillis() > cap) {
                throw new AssertionError("The engine is running too slowly to test"
                        + " before the condition landed: " + what);
            }
            Thread.sleep(25);
        }
        return true;
    }
}
