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
 * <p>The wall-clock backstop is a liveness guard for a STALLED engine
 * (60x the nominal window, floored at 30 seconds), never a performance
 * assertion: a test fails on it only when the engine itself stopped
 * ticking, which is a real defect worth failing on. Failure detection is
 * never loosened — the awaited condition or tick count is exactly what
 * the converted wall sleep meant to cover.</p>
 */
public final class EngineAwaits {

    private EngineAwaits() {
    }

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
        long stalled = System.currentTimeMillis()
                + Math.max(30_000L, ticks * 50L * 60L);
        while (world.totalTicks() < target) {
            if (System.currentTimeMillis() > stalled) {
                throw new AssertionError("The engine stalled mid-wait: " + what
                        + " (awaited " + ticks + " ticks; only "
                        + (world.totalTicks() - start) + " completed)");
            }
            Thread.sleep(25);
        }
    }

    /**
     * Awaits a condition with a TICK-SCALED deadline: the wait fails only
     * when the engine COMPLETED {@code tickBudget} ticks without the
     * condition landing — the honest bound for tick-cadence conditions
     * (a wall deadline expires by wall time even when the engine simply
     * runs slow; a tick budget never does). The stalled-engine backstop
     * still guards a dead engine.
     *
     * @param world      the engine world providing the tick clock
     * @param condition  the engine-visible condition to poll
     * @param tickBudget the engine ticks the condition may take; the
     *                   product's own cadence, not a wall guess
     * @param what       a human-readable purpose for the failure message
     */
    public static void awaitCondition(EngineWorld world, BooleanSupplier condition,
            long tickBudget, String what) throws InterruptedException {
        long start = world.totalTicks();
        long tickDeadline = start + tickBudget;
        long stalled = System.currentTimeMillis()
                + Math.max(30_000L, tickBudget * 50L * 60L);
        while (!condition.getAsBoolean()) {
            if (world.totalTicks() >= tickDeadline) {
                throw new AssertionError("Condition not met within " + tickBudget
                        + " engine ticks: " + what);
            }
            if (System.currentTimeMillis() > stalled) {
                throw new AssertionError("The engine stalled before the condition"
                        + " landed: " + what);
            }
            Thread.sleep(25);
        }
    }
}
