package net.zaminmc.torch.server.stat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The login-sync / tick-lane crossing seat (Wave BU-2 — the CI CME
 * window's root, the SlimePad failure's line-500 frame):
 * {@link PlayerStats}' maps genuinely cross lanes. The channel lane's
 * login walk ({@code V18Connection}'s join burst) calls
 * {@code markAllDirty} + {@code takeAchievements} the moment
 * {@code markPlaying} admits the session — the tick lane's per-tick
 * increments (the play clock, the since-death clock) are already
 * running — and the statistics wire answer's {@code takeDirty} lands on
 * the channel lane too. The reference's own answer is the concurrent
 * map (the base PlayerStats.java line 9: {@code Maps.newConcurrentMap()});
 * the engine's pre-fix plain {@code HashMap} iterated in
 * {@code markAllDirty}'s {@code putAll} while the tick lane wrote, and
 * the ConcurrentModificationException killed the join (run 523's single
 * CI failure, a fresh session's login racing its first ticks on a
 * 2-core hosted runner).
 *
 * <p>The seat hammers both sides of the crossing over a table wide
 * enough that the iteration is a real window: the tick lane's shape
 * (increment + the since-death clock + the gameplay set) against the
 * channel lane's shape (markAllDirty + takeAchievements + takeDirty).
 * The pre-fix plain maps throw on one side or the other within the
 * window; the concurrent shape never does.</p>
 */
class PlayerStatsConcurrencyTest {

    /** The crossing hammer: two lanes, one table, no exception survives. */
    @Test
    void theLoginSyncAndTheTickIncrementsCrossSafely() throws Exception {
        PlayerStats stats = new PlayerStats();
        // A table wide enough that putAll's iteration is a real window —
        // the engine's own vocabulary carries hundreds of rows.
        for (int i = 0; i < 2000; i++) {
            stats.set("stat.test.row." + i, i);
        }

        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> tickLaneFailure = new AtomicReference<>();
        Thread ticker = new Thread(() -> {
            try {
                long tick = 0;
                while (!stop.get()) {
                    // The per-tick walk's own shape (EngineServer's tick
                    // lanes): the ungated play clock, the since-death
                    // clock, and the gameplay event sets.
                    stats.increment(EngineStats.MINUTES_PLAYED, 1);
                    stats.tickTimeSinceDeath();
                    stats.set("stat.test.row." + (tick++ % 2000), 1);
                }
            } catch (Throwable t) {
                tickLaneFailure.set(t);
            }
        }, "stats-tick-lane");
        ticker.start();

        AtomicReference<Throwable> loginLaneFailure = new AtomicReference<>();
        try {
            long deadline = System.currentTimeMillis() + 2_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    // The channel lane's own shapes: the login burst's
                    // pair and the wire answer's copy.
                    stats.markAllDirty();
                    stats.takeAchievements();
                    stats.takeDirty();
                } catch (Throwable t) {
                    loginLaneFailure.set(t);
                    break;
                }
            }
        } finally {
            stop.set(true);
            ticker.join(1_000);
        }

        assertNull(loginLaneFailure.get(),
                "the login sync never sees a broken table ("
                        + loginLaneFailure.get() + ")");
        assertNull(tickLaneFailure.get(),
                "the tick lane never sees a broken table ("
                        + tickLaneFailure.get() + ")");
        // The table still answers: the concurrent shape is a live store,
        // not a frozen one.
        assertTrue(stats.get(EngineStats.MINUTES_PLAYED) >= 0,
                "the counters still read after the crossing");
    }
}
