package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The silverfish's infest gate (Slice TF-7a — InfestStoneGoal.canStart
 * line 147's navigation gate): the 1-in-10 roll only fires on a body
 * whose navigation is DONE — a walking silverfish never merges into
 * stone. The structural pin: over a long seeded window, no infest
 * verdict ever surfaces on a tick whose walk was live at the clock's
 * read point (the stroll state carried INTO the tick, the same state
 * the clock's isDone consult sees).
 */
class SilverfishInfestGateTest {

    /**
     * The stub world: a stone floor below y=8 (both the collision read
     * and the block identity — the silverfish's stone-floor favor reads
     * 10.0 everywhere, so every stroll candidate scores alike), no
     * players, full sky.
     */
    private static final class StoneFloorWorld implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public Identifier blockIdAt(int x, int y, int z) {
            return Identifier.parse("minecraft:stone");
        }
    }

    @Test
    void theRollNeverFiresOnAWalkingBody() {
        StoneFloorWorld world = new StoneFloorWorld();
        MobEntity body = new MobEntity(961, MobType.SILVERFISH,
                new Position(0.5, 8.0, 0.5), new Random(20261026L), world);

        boolean everWalked = false;
        int infestVerdicts = 0;
        for (int tick = 0; tick < 600; tick++) {
            // The walk state the clock's isDone consult reads: carried in
            // from the previous tick's mind walk (the clock runs before
            // the mind in the body's own tick order).
            boolean walkingAtClock = body.isStrollActive();
            body.tick(true);
            if (body.consumeSilverfishInfest()) {
                infestVerdicts++;
                assertFalse(walkingAtClock,
                        "the infest roll fired on a walking body (tick " + tick + ")");
            }
            everWalked |= body.isStrollActive();
        }
        assertTrue(everWalked, "the window saw the stroll walk (the gate's own exposure)");
        assertTrue(infestVerdicts > 0, "the window saw the idle roll fire "
                + "(the 1-in-10 over the stone-adjacent floor)");
    }
}
