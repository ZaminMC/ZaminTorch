package net.zaminmc.torch.server.player;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The movement guard's contract: the historical physics envelope bounds every
 * proposal, the grace window forgives teleports and knockback bursts, and the
 * hover tell catches sustained flight without rights.
 */
class MovementGuardTest {

    /** The test link: a no-op engine-internal connection. */
    private static net.zaminmc.torch.server.net.ClientLink noLink() {
        return new net.zaminmc.torch.server.net.ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    @Test
    void legitimateStepsPass() {
        PlayerSession player = new PlayerSession(java.util.UUID.randomUUID(), "walk", noLink());
        Position from = new Position(0, 64, 0);
        // Walking step (0.215/tick), sprint-jump step (0.352/tick), a jump
        // ascent tick (0.42), terminal fall (3.92).
        assertTrue(MovementGuard.permits(player, from, new Position(0.215, 64, 0), true, false));
        assertTrue(MovementGuard.permits(player, from, new Position(0.25, 64.42, 0.25), false, false));
        assertTrue(MovementGuard.permits(player, from, new Position(0, 60.08, 0), false, false));
        // Water: the client drifts slowly; a small rise while in fluid passes.
        assertTrue(MovementGuard.permits(player, from, new Position(0.1, 64.1, 0.1), false, true));
    }

    @Test
    void speedHacksAreRejected() {
        PlayerSession player = new PlayerSession(java.util.UUID.randomUUID(), "speed", noLink());
        Position from = new Position(0, 64, 0);
        // A speed-hacked stride (1.5 blocks/tick, more than 3x sprint-jump).
        assertFalse(MovementGuard.permits(player, from, new Position(1.5, 64, 0), true, false));
        // Beyond-terminal descent: a teleport-down hack.
        assertFalse(MovementGuard.permits(player, from, new Position(0, 50, 0), false, false));
        // Rising beyond the jump envelope without flight rights.
        assertFalse(MovementGuard.permits(player, from, new Position(0, 65.5, 0), false, false));
    }

    @Test
    void theGraceWindowForgivesTeleportBursts() {
        PlayerSession player = new PlayerSession(java.util.UUID.randomUUID(), "grace", noLink());
        player.setGraceTicks(100);
        Position from = new Position(0, 64, 0);
        // A teleport-arrival burst beyond the normal caps but inside the
        // grace envelope rides the window.
        assertTrue(MovementGuard.permits(player, from, new Position(2.0, 68, 2.0), false, false));
        // The window decays: the same burst after expiry is a violation again.
        for (int i = 0; i < 100; i++) {
            player.tickGrace();
        }
        assertFalse(MovementGuard.permits(player, from, new Position(2.0, 68, 2.0), false, false));
    }

    @Test
    void flightRightsSuspendTheEnvelope() {
        PlayerSession player = new PlayerSession(java.util.UUID.randomUUID(), "flyer", noLink());
        player.setAllowedToFly(true);
        player.setFlying(true);
        Position from = new Position(0, 64, 0);
        // Creative flight climbs and crosses freely.
        assertTrue(MovementGuard.permits(player, from, new Position(0.2, 66, 0.2), false, false));
        player.setAllowedToFly(false);
        player.setFlying(false);
        // Without rights the same climb is a violation.
        assertFalse(MovementGuard.permits(player, from, new Position(0.2, 66, 0.2), false, false));
    }

    @Test
    void sustainedHoverWithoutRightsIsRejected() {
        PlayerSession player = new PlayerSession(java.util.UUID.randomUUID(), "hover", noLink());
        Position from = new Position(0, 64, 0);
        // Micro-rises (the cheat's hover jiggle) accumulate the tell.
        for (int i = 0; i < MovementGuard.HOVER_LIMIT_TICKS - 1; i++) {
            assertTrue(MovementGuard.permits(player, from, new Position(0, 64.01, 0), false, false));
        }
        // The tick past the limit rejects and snaps back.
        assertFalse(MovementGuard.permits(player, from, new Position(0, 64.01, 0), false, false));
        // The accumulator cleared: an honest landing resumes.
        assertTrue(MovementGuard.permits(player, from, new Position(0, 64, 0), true, false));
    }
}
