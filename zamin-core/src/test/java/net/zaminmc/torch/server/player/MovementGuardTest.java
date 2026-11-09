package net.zaminmc.torch.server.player;

import net.zaminmc.torch.server.physics.Aabb;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * THE VANILLA MOVEMENT CHECK'S CONTRACT (Wave M1 — the setback repair):
 * ServerPlayNetworkHandler.handlePlayerMove lines 296-357 pinned whole.
 * The retired per-tick caps (0.45/0.55/5.0) never existed in the
 * reference; the law is the quickly band (ten blocks a packet), the
 * collision-walk wrongly residual (creative exempt), and the floating
 * kick at the eighty-FIRST non-descending tick over nothing (the
 * reference's {@code floatingTime++ THEN > 80} order).
 */
class MovementGuardTest {

    /** The floor's top surface (stone below, air above). */
    private static final double FLOOR_TOP = 56.0;

    /**
     * The test world: an infinite floor (stone below {@link #FLOOR_TOP},
     * air above) — the reference's own flatland shape.
     */
    private static class FlatWorld implements MovementGuard.MovementWorld {

        @Override
        public net.zaminmc.torch.block.BlockType blockAt(int x, int y, int z) {
            return y < FLOOR_TOP ? net.zaminmc.torch.server.block.BuiltinBlocks.STONE
                    : net.zaminmc.torch.server.block.BuiltinBlocks.AIR;
        }

        @Override
        public boolean chunkLoaded(int x, int z) {
            return true;
        }

        @Override
        public boolean containsNonAir(Aabb region) {
            // World.containsNonAir over the same block reads.
            int minX = (int) Math.floor(region.minX);
            int maxX = (int) Math.floor(region.maxX);
            int minY = (int) Math.floor(region.minY);
            int maxY = (int) Math.floor(region.maxY);
            int minZ = (int) Math.floor(region.minZ);
            int maxZ = (int) Math.floor(region.maxZ);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (y < FLOOR_TOP) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public boolean cobwebAt(Position feet) {
            return false;
        }
    }

    /**
     * The test world with NO floor at all: every cell reads air (the
     * void body's own surface).
     */
    private static final class VoidWorld extends FlatWorld {
        @Override
        public net.zaminmc.torch.block.BlockType blockAt(int x, int y, int z) {
            return net.zaminmc.torch.server.block.BuiltinBlocks.AIR;
        }

        @Override
        public boolean containsNonAir(Aabb region) {
            return false;
        }
    }

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

    private static PlayerSession player(String name) {
        return new PlayerSession(java.util.UUID.randomUUID(), name, noLink());
    }

    // ------------------------------------------------------------ the law

    @Test
    void honestStridesNeverMeetTheCapsTheReferenceNeverCarried() {
        PlayerSession walk = player("walk");
        FlatWorld world = new FlatWorld();
        Position from = new Position(0, 64, 0);
        // The retired guard REJECTED every one of these honest forms
        // (0.6+ strides, 0.9 rises, terminal falls, lag-spaced bursts);
        // the reference's law accepts them all — the quickly band is ten
        // blocks a packet and nothing else bounds an honest body.
        assertAccept(walk, world, from, new Position(0.352, 64, 0), true);
        assertAccept(walk, world, from, new Position(0.65, 64, 0.65), true);
        assertAccept(walk, world, from, new Position(0, 60.08, 0), false);
        assertAccept(walk, world, from, new Position(1.9, 64.9, 1.9), false);
        // A lag-spaced burst (two ticks of sprint-jump in one packet).
        assertAccept(walk, world, from, new Position(0.72, 64.35, 0.72), false);
        // A three-tick burst at Speed-II sprint-jump pace — 2.4 blocks.
        assertAccept(walk, world, from, new Position(2.4, 64.5, 2.4), false);
    }

    @Test
    void theQuicklyBandIsTenBlocksAPacket() {
        PlayerSession burst = player("burst");
        FlatWorld world = new FlatWorld();
        Position from = new Position(0, 64, 0);
        // d15 - d14 > 100.0: a 10.05-block stride crosses, a 9.9-block
        // stride stays inside (the band is squared).
        assertSnapBack(burst, world, from, new Position(10.05, 64, 0), true);
        assertAccept(burst, world, from, new Position(9.9, 64, 0), true);
        // The body's own server velocity pays into the band (line 300:
        // d15 - d14): a knockback launch at 10 velocity rides inside a
        // 12-block proposal.
        burst.setMotion(10, 0, 0);
        assertAccept(burst, world, from, new Position(12, 64, 0), true);
    }

    @Test
    void theWronglyResidualAcceptsAnHonestWalkedLanding() {
        PlayerSession step = player("step");
        FlatWorld world = new FlatWorld();
        // A stride onto the open air above the floor: the solver's own
        // walk lands the same place the client claims, the residual is
        // zero, the wrongly band never arms.
        Position from = new Position(0, 64, 0);
        assertAccept(step, world, from, new Position(0.3, 64, 0.4), true);
        // The stepped landing on the floor itself: the feet ride the
        // floor's own top, the residual stays inside the band.
        Position onFloor = new Position(0, FLOOR_TOP, 0);
        assertAccept(step, world, onFloor, new Position(0.3, FLOOR_TOP, 0.4), true);
    }

    @Test
    void theFloatingKickFiresOnTheEightyFirstTickOverNothing() {
        PlayerSession hover = player("hover");
        VoidWorld world = new VoidWorld();
        Position from = new Position(0, 64, 0);
        Position to = new Position(0, 64.01, 0);
        // Eighty non-descending ticks stand (the reference increments
        // THEN consults {@code > 80}); the eighty-FIRST is the kick.
        for (int i = 0; i < 80; i++) {
            assertAccept(hover, world, from, to, false);
        }
        assertEquals(80, hover.floatingTicks());
        assertKick(hover, world, from, to, false);
        // The kick clears the counter (the session never carries a
        // stale window into a rejoin's own body).
        assertEquals(0, hover.floatingTicks());
    }

    @Test
    void theFloatingCounterResetsOnGroundBelowAndStandsOnADescent() {
        PlayerSession stander = player("stander");
        FlatWorld world = new FlatWorld();
        // A ground cell below the probe resets the counter whole: the
        // feet ride the floor's top, the probe reaches the stone.
        Position onFloor = new Position(0, FLOOR_TOP, 0);
        for (int i = 0; i < 200; i++) {
            assertAccept(stander, world, onFloor, new Position(0, FLOOR_TOP + 0.01, 0), false);
        }
        assertEquals(0, stander.floatingTicks());
        // A descending tick over nothing leaves the counter STANDING —
        // neither the reset nor the increment arm runs (lines 344-352:
        // the else-if band alone) — a long fall never climbs it.
        PlayerSession faller = player("faller");
        VoidWorld voidWorld = new VoidWorld();
        Position high = new Position(0, 100, 0);
        for (int i = 0; i < 40; i++) {
            assertAccept(faller, voidWorld, high, new Position(0, 99.5, 0), false);
        }
        assertEquals(0, faller.floatingTicks());
        // ... and the hover that follows a descent accumulates from
        // where the chain left it (the pause-descend-pause shape).
        for (int i = 0; i < 30; i++) {
            assertAccept(faller, voidWorld, high, new Position(0, 100.01, 0), false);
        }
        assertEquals(30, faller.floatingTicks());
    }

    @Test
    void flightRightsResetTheFloatingCounter() {
        PlayerSession flyer = player("flyer");
        VoidWorld world = new VoidWorld();
        Position from = new Position(0, 64, 0);
        flyer.setAllowedToFly(true);
        // Flight rights: the counter resets every tick, the kick never
        // fires however long the body holds the air.
        for (int i = 0; i < 500; i++) {
            assertAccept(flyer, world, from, new Position(0, 64.01, 0), false);
        }
        assertEquals(0, flyer.floatingTicks());
    }

    @Test
    void theJumpArmPaysTheServersOwnMotion() {
        PlayerSession jumper = player("jumper");
        FlatWorld world = new FlatWorld();
        Position from = new Position(0, 64, 0);
        // The grounded body leaving the ground upward pays the server's
        // own 0.42 (+ the ladder's bonus) — lines 312-314.
        jumper.applyMovement(from, new net.zaminmc.torch.util.Rotation(0, 0), true);
        MovementGuard.check(world, jumper, from, new Position(0.1, 64.3, 0), false, 0.1);
        assertEquals(0.52, jumper.motionY(), 1.0E-9);
        // An airborne body never pays the impulse twice.
        MovementGuard.check(world, jumper, from, new Position(0.1, 64.6, 0), false, 0.1);
        assertEquals(0.52, jumper.motionY(), 1.0E-9);
    }

    // ------------------------------------------------------------ helpers

    private static void assertAccept(PlayerSession session, MovementGuard.MovementWorld world,
                                     Position from, Position to, boolean onGround) {
        var verdict = MovementGuard.check(world, session, from, to, onGround, 0.0);
        assertInstanceOf(MovementGuard.Verdict.Accept.class, verdict,
                () -> session.name() + " expected accept, got " + verdict);
    }

    private static void assertSnapBack(PlayerSession session, MovementGuard.MovementWorld world,
                                       Position from, Position to, boolean onGround) {
        var verdict = MovementGuard.check(world, session, from, to, onGround, 0.0);
        assertInstanceOf(MovementGuard.Verdict.SnapBack.class, verdict,
                () -> session.name() + " expected snap-back, got " + verdict);
    }

    private static void assertKick(PlayerSession session, MovementGuard.MovementWorld world,
                                   Position from, Position to, boolean onGround) {
        var verdict = MovementGuard.check(world, session, from, to, onGround, 0.0);
        assertInstanceOf(MovementGuard.Verdict.Kick.class, verdict,
                () -> session.name() + " expected kick, got " + verdict);
        assertEquals("Flying is not enabled on this server",
                ((MovementGuard.Verdict.Kick) verdict).reason());
    }
}
