package net.zaminmc.torch.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-025's fix pinned — the /tps window math over the retained history.
 * The window once held only sixty seconds, so the command's 5m/15m columns
 * printed the 1m average under Math.min(60, …) clamps; the window now
 * retains fifteen minutes (TPS_WINDOW_SECONDS = 900) and the command asks
 * for the real spans. The synthetic history rides the package-private
 * appendTpsSecond seam — a 900-second past cannot be lived out in a unit
 * test, and the math's honesty is the point.
 */
class EngineTickerTpsWindowTest {

    /** One billion nanos per second, spelled for the synthetic stamps. */
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    /**
     * Fills the window with three eras: the newest sixty seconds at 10 tps,
     * the 240 before them at 15, the oldest 600 at 20 — every reported
     * window then reads a different number, which is exactly what the
     * clamped version could not do (it printed 10, 10, 10 for this
     * history's newest era).
     */
    @Test
    void theThreeWindowsReadTheirOwnEras() {
        EngineTicker ticker = new EngineTicker(20);
        long anchor = System.nanoTime();
        // stamp_i = anchor - (900 - i) seconds, i = 0..899: the oldest
        // entry sits exactly 900 seconds back, the newest one second back.
        for (int i = 0; i < 900; i++) {
            long stamp = anchor - (900 - i) * SECOND;
            long tps;
            if (i >= 840) {
                tps = 10;            // the newest 60 seconds
            } else if (i >= 600) {
                tps = 15;            // the 240 seconds before them
            } else {
                tps = 20;            // the oldest 600 seconds
            }
            ticker.appendTpsSecond(stamp, tps);
        }
        // 1m reads its own era (10 tps); the boundary entry (exactly 60s
        // back) falls outside the read's >= window by the call's own
        // nanosecond drift, and the tolerance absorbs that one second.
        assertEquals(10.0, ticker.averageTps(60), 0.15,
                "the 1m window reads the newest era");
        assertEquals((60 * 10 + 240 * 15) / 300.0, ticker.averageTps(300), 0.15,
                "the 5m window reads the two newest eras");
        assertEquals((60 * 10 + 240 * 15 + 600 * 20) / 900.0, ticker.averageTps(900), 0.15,
                "the 15m window reads the whole retained history");
    }

    /** A young engine answers 20.0 for every window — the honest default. */
    @Test
    void aYoungWindowAnswersTheFullRate() {
        EngineTicker ticker = new EngineTicker(20);
        assertEquals(20.0, ticker.averageTps(60), 1e-9, "1m before data");
        assertEquals(20.0, ticker.averageTps(300), 1e-9, "5m before data");
        assertEquals(20.0, ticker.averageTps(900), 1e-9, "15m before data");
    }

    /**
     * The retention bound: appending past nine hundred seconds evicts the
     * oldest — the window's memory stays O(900) no matter how long the
     * server runs.
     */
    @Test
    void theWindowEvictsPastNineHundredSeconds() {
        EngineTicker ticker = new EngineTicker(20);
        long anchor = System.nanoTime();
        for (int i = 0; i < 950; i++) {
            ticker.appendTpsSecond(anchor - (950 - i) * SECOND, 20);
        }
        assertTrue(ticker.tpsWindowRetainedSeconds() <= 900,
                "the retention bound holds: " + ticker.tpsWindowRetainedSeconds());
        // The eviction is the OLDEST: every retained entry now sits within
        // the last 900 seconds, so the 15m window reads the full rate.
        assertEquals(20.0, ticker.averageTps(900), 0.15,
                "only the newest 900 seconds remain");
    }
}
