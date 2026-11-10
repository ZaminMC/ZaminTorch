package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.entity.ai.pathing.CellMaterial;
import net.zaminmc.torch.server.entity.ai.pathing.MobNavigation;
import net.zaminmc.torch.server.entity.ai.pathing.MobView;
import net.zaminmc.torch.server.entity.ai.pathing.PathWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE KO-D — PAPER 0087'S PATHFINDING FAILURE THROTTLE (gated,
 * default OFF): a goal that restarts its path every tick — the
 * arrow-attack spam the patch was written for — backs off after ten
 * consecutive failures: while no standing path is held and the last
 * failure sits inside the 40-tick window, the next {@code moveTo}
 * never even runs the search. Any success clears the ladder whole, and
 * {@code stop()} clears it too (the patch's own resets).
 *
 * <p>The seat observes the throttle through the WORLD CONSULT COUNT: a
 * counting world over a walled-off goal answers every failed search
 * with a known consult footprint; the throttled call must consult
 * NOTHING — the search never ran.</p>
 */
class KigPaperPathfindingThrottleTest {

    @AfterEach
    void clearFlag() {
        System.clearProperty("torch.optimize-pathfinding");
    }

    /**
     * THE THROTTLE SEAT: the goal sits behind an unbroken wall of solid
     * cells (every search fails — no route exists inside the follow
     * range), the flag ARMED. Ten failed attempts build the ladder; the
     * eleventh call must return false WITHOUT a single world consult
     * (the counting world's counter frozen) — and with the flag CLEARED
     * the very next call consults the world again (the vanilla walk).
     */
    @Test
    void theArmedThrottleBacksOffTheSpammingGoal() {
        System.setProperty("torch.optimize-pathfinding", "true");
        CountingWorld world = new CountingWorld();
        world.seal(); // the sealed shaft: every search answers null
        MobNavigation navigation = new MobNavigation(mob(0.5, 4.0, 0.5), world, 16.0f);

        // Eleven failed attempts: the ladder builds past the patch's own
        // ten-failure arm (> 10 — the twelfth call is the first one the
        // backoff refuses).
        for (int attempt = 0; attempt < 11; attempt++) {
            assertFalse(navigation.moveTo(6.5, 4.0, 0.5, 1.0),
                    "the sealed mob never routes (attempt " + attempt + ")");
            // NO stop() between attempts: the failed attempt already holds
            // no standing path (the patch's own shape — the spamming goal
            // restarts its search, it does not stop its navigation; the
            // stop() reset is the patch's OWN ladder-clearing law).
        }

        // The throttle is armed: the next call returns false with the
        // world NEVER CONSULTED (the search never ran).
        long consultsBefore = world.consults;
        assertFalse(navigation.moveTo(6.5, 4.0, 0.5, 1.0),
                "the throttled call still answers false");
        assertTrue(world.consults == consultsBefore,
                "the throttled call never consulted the world ("
                        + (world.consults - consultsBefore) + " consults — the search ran)");

        // The vanilla walk restored: the flag cleared, the very next
        // call consults the world again.
        System.clearProperty("torch.optimize-pathfinding");
        consultsBefore = world.consults;
        assertFalse(navigation.moveTo(6.5, 4.0, 0.5, 1.0),
                "the sealed mob still never routes");
        assertTrue(world.consults > consultsBefore,
                "the unthrottled call ran the search ("
                        + (world.consults - consultsBefore) + " consults)");
    }

    /**
     * THE SUCCESS RESET SEAT: a reachable goal alternated with the
     * walled one never arms the throttle — the ladder clears on every
     * success (the patch's own reset), so even after ten interleaved
     * failures the eleventh walled attempt still runs its search.
     */
    @Test
    void theSuccessfulRouteClearsTheLadderWhole() {
        System.setProperty("torch.optimize-pathfinding", "true");
        CountingWorld world = new CountingWorld();
        world.seal();
        MobNavigation navigation = new MobNavigation(mob(0.5, 4.0, 0.5), world, 16.0f);
        for (int round = 0; round < 10; round++) {
            assertFalse(navigation.moveTo(6.5, 4.0, 0.5, 1.0),
                    "the sealed mob never routes (round " + round + ")");
            // The reachable goal east (the seal's east side opened): a
            // success clears the ladder whole.
            world.unsealEast();
            assertTrue(navigation.moveTo(3.5, 4.0, 0.5, 1.0),
                    "the opened route east walks (round " + round + ")");
            navigation.stop(); // the goal switch: the success leg's own stop
            world.seal(); // the next failure round: sealed again
        }
        // The ladder never armed (every failure was followed by a
        // success): the next sealed attempt still consults the world.
        long consultsBefore = world.consults;
        assertFalse(navigation.moveTo(6.5, 4.0, 0.5, 1.0),
                "the sealed mob still never routes");
        assertTrue(world.consults > consultsBefore,
                "the interleaved failure never armed the throttle ("
                        + (world.consults - consultsBefore) + " consults)");
    }

    // ------------------------------------------------------------------ harness

    /** The counting world: ground below 4, a mutable seal, a consult counter. */
    private static final class CountingWorld implements PathWorld {
        private final Map<Long, CellMaterial> cells = new HashMap<>();
        long consults;

        CountingWorld put(int x, int y, int z, CellMaterial material) {
            cells.put(key(x, y, z), material);
            return this;
        }

        /** Seals the mob's 1x1 shaft: solid on every horizontal neighbor
         *  at the feet band and the climb band above (the one-block climb
         *  blocked) — no reachable neighbor exists, every search answers
         *  the start node alone and the path comes back NULL. */
        void seal() {
            for (int y = 4; y <= 6; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue; // the mob's own shaft stays open
                        }
                        put(dx, y, dz, CellMaterial.SOLID);
                    }
                }
            }
        }

        /** Opens the seal's east side (a straight open route east). */
        void unsealEast() {
            for (int y = 4; y <= 6; y++) {
                cells.remove(key(1, y, 0));
                cells.remove(key(2, y, 0));
            }
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
        }

        @Override
        public CellMaterial materialAt(int x, int y, int z) {
            consults++;
            CellMaterial explicit = cells.get(key(x, y, z));
            if (explicit != null) {
                return explicit;
            }
            return y <= 3 ? CellMaterial.SOLID : CellMaterial.AIR;
        }
    }

    private static MobView mob(double x, double y, double z) {
        return new MobView() {
            @Override public double x() { return x; }

            @Override public double y() { return y; }

            @Override public double z() { return z; }

            @Override public double width() { return 0.6; }

            @Override public double height() { return 1.8; }

            @Override public boolean onGround() { return true; }

            @Override public boolean inWater() { return false; }

            @Override public boolean inLava() { return false; }
        };
    }
}
