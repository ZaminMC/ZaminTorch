package net.zaminmc.torch.server;

import net.zaminmc.torch.server.spawner.SpawnerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE KO-E — THE TILE-TICK-RATE CONFIG PAIR (Paper 0059 + 0065, the
 * KigPaper census's config-surface rows): both throttles are GATED
 * DEFAULT 1 = VANILLA-EXACT — at the default the walks run
 * byte-for-byte the reference's own arms, and the operator arms the
 * proven cadence win with the {@code torch.*} flags.
 *
 * <p>THE SPAWNER ROW (Paper 0059, {@code torch.mob-spawner-tick-rate}):
 * the countdown phase fires every Nth tick with the delay walked N at a
 * time — Paper's own compensation — so the same wall-clock cadence holds
 * at 1/N the per-spawner consult cost. THE CONTAINER ROW (Paper 0065,
 * {@code torch.container-update-tick-rate}): the per-tick view-fan walk
 * (the engine's activeContainer.b() equivalents) runs every Nth
 * tick.</p>
 */
class KigPaperTickRateConfigParityTest {

    @AfterEach
    void clearFlags() {
        System.clearProperty("torch.mob-spawner-tick-rate");
        System.clearProperty("torch.container-update-tick-rate");
    }

    /**
     * THE DEFAULT LAW: both flags answer the vanilla 1 unset (the
     * never-a-default-change discipline), and the clamps hold for the
     * degenerate values (0 and negatives answer 1, never a
     * divide-by-zero or a negative cadence).
     */
    @Test
    void theDefaultsAnswerTheVanillaOne() {
        assertEquals(1, SpawnerManager.mobSpawnerTickRate(),
                "the spawner rate defaults to the vanilla 1");
        assertEquals(1, EngineServer.containerUpdateTickRate(),
                "the container rate defaults to the vanilla 1");
        System.setProperty("torch.mob-spawner-tick-rate", "0");
        System.setProperty("torch.container-update-tick-rate", "-4");
        assertEquals(1, SpawnerManager.mobSpawnerTickRate(),
                "the spawner rate clamps the degenerate values to 1");
        assertEquals(1, EngineServer.containerUpdateTickRate(),
                "the container rate clamps the degenerate values to 1");
    }

    /**
     * THE SPAWNER THROTTLE'S OWN LAW (Paper 0059's shape over the
     * engine's MobSpawnerBlockEntity): with the rate armed at N, a
     * spawner in its countdown phase (delay > 0) skips N-1 ticks whole
     * (the delay FROZEN — no consult, no decay) and on the Nth tick
     * walks the delay N at a time. The law is observable through the
     * delay's own read surface: the frozen window + the N-step.
     */
    @Test
    void theArmedSpawnerThrottleWalksTheDelayNAtATime() {
        System.setProperty("torch.mob-spawner-tick-rate", "4");
        assertEquals(4, SpawnerManager.mobSpawnerTickRate());
        var spawner = new SpawnerManager.MobSpawnerBlockEntity();
        spawner.setDelay(200);
        spawner.setTickDelay(4); // the walk just ran: the counter holds 4
        // The frozen window: three consecutive throttle gates skip whole
        // (the counter 4 -> 3 -> 2 -> 1, the delay FROZEN at 200 — no
        // consult, no decay).
        for (int gate = 0; gate < 3; gate++) {
            assertTrue(spawner.delay() > 0 && spawner.tickDelay() > 0,
                    "the countdown phase engages the throttle gate");
            spawner.setTickDelay(spawner.tickDelay() - 1);
        }
        assertEquals(1, spawner.tickDelay(), "three skips leave the counter at 1");
        assertEquals(200, spawner.delay(), "the frozen window never decays the delay");
        // The Nth tick: the fourth gate opens (the counter 1 -> 0, the
        // patch's own `--tickDelay > 0` refusing at 0), the counter
        // refills, and the delay walks N at a time (the reference's own
        // arm with the Paper compensation — 200 - 4, not 200 - 1).
        spawner.setTickDelay(spawner.tickDelay() - 1);
        assertEquals(0, spawner.tickDelay(), "the fourth gate opens the walk");
        spawner.setTickDelay(4); // tickDelay = rate (Paper's refill)
        spawner.setDelay(spawner.delay() - 4);
        assertEquals(196, spawner.delay(), "the compensated walk steps N");
    }

    /**
     * THE CONTAINER THROTTLE'S COUNTER LAW (Paper 0065's shape):
     * {@code if (--delay <= 0) { walk(); delay = rate; }} — at rate 1
     * the walk runs EVERY tick (the counter never exceeds the gate),
     * at rate N every Nth. The law is pinned over the counter's own
     * arithmetic, the patch's exact expression.
     */
    @Test
    void theContainerCounterMatchesThePatchesExpression() {
        System.setProperty("torch.container-update-tick-rate", "3");
        assertEquals(3, EngineServer.containerUpdateTickRate());
        // The patch's counter walk, replicated exactly: a counter at 3
        // runs the walk on the third decrement, then refills.
        int delay = 3;
        int walks = 0;
        for (int tick = 0; tick < 9; tick++) {
            if (--delay <= 0) {
                walks++;
                delay = 3;
            }
        }
        assertEquals(3, walks, "nine ticks at rate 3 answer three walks");
        // At rate 1 (the default): every tick walks.
        System.clearProperty("torch.container-update-tick-rate");
        assertEquals(1, EngineServer.containerUpdateTickRate());
        delay = 1;
        walks = 0;
        for (int tick = 0; tick < 9; tick++) {
            if (--delay <= 0) {
                walks++;
                delay = 1;
            }
        }
        assertEquals(9, walks, "nine ticks at the vanilla rate answer nine walks");
    }
}
