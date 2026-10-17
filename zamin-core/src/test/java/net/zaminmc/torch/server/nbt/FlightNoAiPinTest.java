package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flight families' NoAI interaction (Slice 92 — the C3 envelope's
 * fourth landing): the reference's isLocallyControlled gate covers every
 * body — the ghast's drift, the bat's wander, the squid's stroke and the
 * guardian's swim all ride moveRelative, so a pinned flight body freezes
 * midair with the *0.98 decay, exactly like the walker.
 */
class FlightNoAiPinTest {

    /** The air world (nothing solid, no fluid, no players). */
    private static final class AirWorld implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return false;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    }

    private static MobEntity mob(MobType type, double x, double y, double z) {
        return new MobEntity(7, type, new Position(x, y, z), new Random(11), new AirWorld());
    }

    @Test
    void thePinnedGhastFreezesMidair() {
        // A ghast with a live velocity, pinned: the position holds, the
        // velocity decays at *0.98 per tick (the reference's
        // non-controlled arm).
        MobEntity ghast = mob(MobType.GHAST, 0.5, 80.0, 0.5);
        ghast.setNoAi(true);
        ghast.setVelocity(0.5, -0.25, -0.5);
        for (int i = 0; i < 10; i++) {
            ghast.tick(false);
        }
        assertEquals(0.5, ghast.position().x(), 1e-9);
        assertEquals(80.0, ghast.position().y(), 1e-9);
        assertEquals(0.5, ghast.position().z(), 1e-9);
        assertEquals(0.5 * Math.pow(0.98, 10), ghast.velocityX(), 1e-9);
        assertEquals(-0.25 * Math.pow(0.98, 10), ghast.velocityY(), 1e-9);
    }

    @Test
    void theUnpinnedGhastStillDrifts() {
        // The control: an unpinned ghast moves (the drift integrates).
        MobEntity ghast = mob(MobType.GHAST, 0.5, 80.0, 0.5);
        ghast.setVelocity(0.5, 0.0, 0.0);
        for (int i = 0; i < 5; i++) {
            ghast.tick(false);
        }
        assertTrue(ghast.position().x() > 0.5, "the drift moved the body");
    }

    @Test
    void thePinnedSquidHoldsItsPlace() {
        MobEntity squid = mob(MobType.SQUID, 0.5, 40.0, 0.5);
        squid.setNoAi(true);
        squid.setVelocity(0.1, 0.2, 0.1);
        for (int i = 0; i < 10; i++) {
            squid.tick(false);
        }
        assertEquals(0.5, squid.position().x(), 1e-9);
        assertEquals(40.0, squid.position().y(), 1e-9);
        assertEquals(0.5, squid.position().z(), 1e-9);
    }

    @Test
    void thePinnedBatAndGuardianFreeze() {
        MobEntity bat = mob(MobType.BAT, 0.5, 60.0, 0.5);
        bat.setNoAi(true);
        bat.setVelocity(0.3, 0.0, 0.0);
        bat.tick(false);
        assertEquals(0.5, bat.position().x(), 1e-9);

        MobEntity guardian = mob(MobType.GUARDIAN, 0.5, 40.0, 0.5);
        guardian.setNoAi(true);
        guardian.setVelocity(0.3, 0.0, 0.0);
        guardian.tick(false);
        assertEquals(0.5, guardian.position().x(), 1e-9);
    }
}
