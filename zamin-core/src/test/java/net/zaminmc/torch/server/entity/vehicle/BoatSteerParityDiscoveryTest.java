package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave BS — THE BOAT'S OWN STEER MODEL (the re-pin's named row: "the
 * ridden boat's W-key paddling — the boat family's own law, untouched
 * by the cart's kick row"), each law read from the reference's
 * BoatEntity.tick 1:1:
 *
 * <ul>
 * <li>THE PADDLING DRIVE (lines 259-264): the heading f = the RIDER'S
 * OWN yaw − sideways × 90° (the A/D strafe rotates the paddle heading a
 * quarter turn), the drive the momentum's speedBoost × forward × 0.05
 * per tick — the CONTINUOUS law, W ahead and S astern alike. The
 * pre-fix engine's invented 0.035/0.02 × 2.0 impulse retired: a
 * different number, a different form, no momentum at all.</li>
 * <li>THE MOMENTUM (lines 274-284): the speedBoost ramps 0.07-0.35
 * over its 35-tick curve — accelerating under the ceiling climbs toward
 * the full force, anything else decays back toward the idle floor
 * (Java's own 0.0 first-tick start included: a fresh hull warms up).</li>
 * <li>THE HORIZONTAL CAP (lines 266-272): 0.35 scaled onto the
 * direction, every surface — the pre-fix engine's separate 0.25 land
 * cap retired.</li>
 * <li>THE GLIDE DRAG (lines 312-314): the post-move 0.99 horizontal /
 * 0.95 vertical — the pre-fix engine's invented 0.85 water / 0.6 land
 * decay retired; the reference's own numbers alone.</li>
 * <li>THE CRASH (lines 311-326): the PRE-move speed over 0.2975 into a
 * horizontal collision breaks the hull — the pre-fix engine ground
 * every wall off at any speed.</li>
 * <li>THE RAW LAND GRAVITY (the buoyancy arm's d0=0 form, line 249's
 * own −0.04): the pre-fix engine's pre-multiplied (v − 0.04) × 0.98
 * retired — the same repair the OR wave landed for the cart.</li>
 * </ul>
 */
class BoatSteerParityDiscoveryTest {

    /** The glide drag (line 312: the 0.99). */
    private static final double GLIDE = BoatEntity.GLIDE_DRAG;

    /** Open water at y ∈ [4, 5): no walls, no bed. */
    private static VehicleEntity.WorldQuery openWater() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return false;
            }

            @Override public boolean inFluid(double x, double y, double z) {
                return y >= 4.0 && y < 5.0;
            }
        };
    }

    /** Dry air (the falling seat's own world — no fluid, no ground yet). */
    private static VehicleEntity.WorldQuery dryAir() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return false;
            }
        };
    }

    // ------------------------------------------------------------------
    // Seat 1 — the paddling drive and the momentum's first steps
    // ------------------------------------------------------------------

    /**
     * THE DRIVE: a fresh hull's first paddle tick carries speedBoost 0
     * (Java's own field start — the reference's first tick paddles
     * nothing), the momentum's decay arm lands 0.002 and the FLOOR
     * CLAMP lifts it whole to the 0.07 idle floor (the reference's own
     * {@code if (speedBoost < 0.07) speedBoost = 0.07}), and the SECOND
     * tick's drive is that floor's own 0.05 along the rider's look.
     * RED on the pre-fix engine: the invented impulse moved the hull on
     * the FIRST tick (0.035 × 2.0 — a different number, a different
     * form, no momentum field at all).
     */
    @Test
    void thePaddlingDriveIsTheMomentumsOwn() {
        VehicleEntity.WorldQuery water = openWater();
        BoatEntity boat = new BoatEntity(9920,
                new Position(0.5, 4.5, 0.5), new Random(31L));
        assertTrue(boat.mount(4242), "the seat takes the rider");

        boat.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false, 0.0f));
        boat.tick(water); // the rider looks south (+Z), W held
        assertEquals(0.0, boat.velocityZ(), 1e-12,
                "the first tick paddles nothing (the momentum starts at zero)");
        assertEquals(BoatEntity.MOMENTUM_FLOOR, boat.momentum(), 1e-12,
                "the decay arm from zero, lifted to the idle floor by the"
                        + " reference's own clamp");

        boat.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false, 0.0f));
        boat.tick(water);
        assertEquals(BoatEntity.MOMENTUM_FLOOR
                        * 1.0f * BoatEntity.PADDLE_DRIVE * GLIDE,
                boat.velocityZ(), 1e-12,
                "the second tick's drive is the idle floor's own 0.05 (the"
                        + " drag after the move)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the momentum's ramp and floor
    // ------------------------------------------------------------------

    /**
     * THE RAMP: forty W-held ticks — the accelerating hull climbs the
     * momentum toward the 0.35 ceiling on the reference's own /35
     * curve (the mirror recurrence asserted exactly). THE RELEASE: the
     * neutral steer decays the momentum back toward the 0.07 idle
     * floor on the same curve's falling side.
     */
    @Test
    void theMomentumRampsAndDecays() {
        VehicleEntity.WorldQuery water = openWater();
        BoatEntity boat = new BoatEntity(9921,
                new Position(0.5, 4.5, 0.5), new Random(32L));
        assertTrue(boat.mount(4242), "the seat takes the rider");

        double expected = 0.0;
        for (int i = 0; i < 40; i++) {
            boat.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false, 0.0f));
            boat.tick(water);
            if (i == 0) {
                // The decay arm from zero, the floor clamp to the idle.
                expected = BoatEntity.MOMENTUM_FLOOR;
            } else {
                expected = expected + (BoatEntity.MOMENTUM_CEILING - expected)
                        / BoatEntity.MOMENTUM_RAMP;
            }
        }
        assertEquals(expected, boat.momentum(), 1e-12,
                "the accelerating hull ramps the momentum (the /35 curve)");
        assertTrue(boat.momentum() > 0.2 && boat.momentum() < BoatEntity.MOMENTUM_CEILING,
                "the ramp is well underway but under the ceiling: " + boat.momentum());

        for (int i = 0; i < 20; i++) {
            boat.steer(VehicleEntity.SteerInput.NEUTRAL);
            boat.tick(water);
            expected = expected - (expected - BoatEntity.MOMENTUM_FLOOR)
                    / BoatEntity.MOMENTUM_RAMP;
        }
        assertEquals(expected, boat.momentum(), 1e-12,
                "the released hull decays toward the idle floor (the same"
                        + " curve's falling side)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the 0.35 horizontal cap
    // ------------------------------------------------------------------

    /**
     * THE CAP: a hull flung at 0.5 clamps to the reference's 0.35
     * before the move (scaled onto the direction), and the glide drag
     * lands after — 0.35 × 0.99 carried. RED on the pre-fix engine: no
     * cap in water at all (the land's separate 0.25 retired with it).
     */
    @Test
    void theHorizontalCapIsTheReferenceOwn() {
        VehicleEntity.WorldQuery water = openWater();
        BoatEntity boat = new BoatEntity(9922,
                new Position(0.5, 4.5, 0.5), new Random(33L));
        boat.setVelocity(0.5, 0.0, 0.0);
        boat.tick(water);
        assertEquals(BoatEntity.HORIZONTAL_CAP * GLIDE, boat.velocityX(), 1e-12,
                "the 0.35 cap then the glide drag");
        assertEquals(0.0, boat.velocityY(), 1e-12, "the water rest holds the vertical");
    }

    // ------------------------------------------------------------------
    // Seat 4 — the backward paddle and the strafed heading
    // ------------------------------------------------------------------

    /**
     * THE HEADING LAW: the paddle heading f = the rider's yaw −
     * sideways × 90° — S (forward −1) drives ASTERN along the look,
     * and the A/D strafe rotates the drive a quarter turn off it.
     */
    @Test
    void theBackwardPaddleAndTheStrafedHeading() {
        VehicleEntity.WorldQuery water = openWater();
        // S astern: the drive reverses along the south look.
        BoatEntity stern = new BoatEntity(9923,
                new Position(0.5, 4.5, 0.5), new Random(34L));
        assertTrue(stern.mount(4243), "the seat takes the rider");
        // two warm-up ticks build a known momentum (the floor, then the
        // ramp's first step)
        stern.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false, 0.0f));
        stern.tick(water);
        stern.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false, 0.0f));
        stern.tick(water);
        double warm = BoatEntity.MOMENTUM_FLOOR
                + (BoatEntity.MOMENTUM_CEILING - BoatEntity.MOMENTUM_FLOOR)
                        / BoatEntity.MOMENTUM_RAMP;
        double sternSpeedBefore = stern.velocityZ();
        stern.steer(new VehicleEntity.SteerInput(0.0f, -1.0f, false, false, 0.0f));
        stern.tick(water);
        assertEquals(sternSpeedBefore * GLIDE - warm * BoatEntity.PADDLE_DRIVE * GLIDE,
                stern.velocityZ(), 1e-12,
                "the S paddle drives astern (the same momentum, the negated"
                        + " forward, the glide on the whole)");

        // The strafed heading: the south-looked rider strafing right (the
        // wire's +1) paddles at the look's own quarter turn — east.
        BoatEntity strafed = new BoatEntity(9924,
                new Position(0.5, 4.5, 0.5), new Random(35L));
        assertTrue(strafed.mount(4244), "the seat takes the rider");
        strafed.steer(new VehicleEntity.SteerInput(1.0f, 1.0f, false, false, 0.0f));
        strafed.tick(water); // the warm-up tick (the momentum from zero)
        strafed.steer(new VehicleEntity.SteerInput(1.0f, 1.0f, false, false, 0.0f));
        strafed.tick(water);
        double warmStrafe = BoatEntity.MOMENTUM_FLOOR;
        double drive = warmStrafe * 1.0f * BoatEntity.PADDLE_DRIVE;
        double radians = Math.toRadians(0.0f + -1.0f * 90.0f);
        assertEquals(-Math.sin(radians) * drive * GLIDE, strafed.velocityX(), 1e-12,
                "the strafed paddle heads the look's quarter turn (east)");
        assertEquals(Math.cos(radians) * drive * GLIDE, strafed.velocityZ(), 1e-12,
                "the strafed paddle's cross component");
    }

    // ------------------------------------------------------------------
    // Seat 5 — the riderless glide (the 0.99/0.95 drag)
    // ------------------------------------------------------------------

    /**
     * THE GLIDE: a riderless hull's given velocity decays at the
     * reference's own 0.99 per tick — 0.2 × 0.99^40 after forty. RED
     * on the pre-fix engine: the invented 0.85 water decay (0.2 ×
     * 0.85^40 — a stopped hull inside twenty ticks).
     */
    @Test
    void theRiderlessHullGlidesAtTheReferenceDrag() {
        VehicleEntity.WorldQuery water = openWater();
        BoatEntity boat = new BoatEntity(9925,
                new Position(0.5, 4.5, 0.5), new Random(36L));
        boat.setVelocity(0.2, 0.0, 0.0);
        for (int i = 0; i < 40; i++) {
            boat.tick(water);
        }
        assertEquals(0.2 * Math.pow(GLIDE, 40), boat.velocityX(), 1e-12,
                "the riderless hull glides at 0.99 per tick (lines 312-314)");
        assertTrue(boat.velocityX() > 0.13,
                "the vanilla hull still carries most of its drift: "
                        + boat.velocityX());
    }

    // ------------------------------------------------------------------
    // Seat 6 — the crash band (the pre-move speed into the wall)
    // ------------------------------------------------------------------

    /**
     * THE CRASH: a hull at the PRE-move speed 0.4 (over the reference's
     * 0.2975 band) grinding into a wall column breaks — the hull's
     * dead flag (the manager's sweep carries the removal and the
     * drops); the same wall at 0.2 never breaks the hull. RED on the
     * pre-fix engine: every wall was ground off at any speed.
     */
    @Test
    void theHighSpeedWallHitBreaksTheHull() {
        VehicleEntity.WorldQuery walled = new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return (int) Math.floor(x) >= 6 && y >= 4.0 && y < 5.0;
            }

            @Override public boolean inFluid(double x, double y, double z) {
                return y >= 4.0 && y < 5.0;
            }
        };
        BoatEntity fast = new BoatEntity(9926,
                new Position(5.7, 4.5, 0.5), new Random(37L));
        fast.setVelocity(0.4, 0.0, 0.0);
        fast.tick(walled);
        assertTrue(fast.dead(),
                "the 0.4 hull breaks on the wall (over the 0.2975 band)");

        BoatEntity slow = new BoatEntity(9927,
                new Position(5.7, 4.5, 0.5), new Random(38L));
        slow.setVelocity(0.2, 0.0, 0.0);
        for (int i = 0; i < 30; i++) {
            slow.tick(walled);
        }
        assertFalse(slow.dead(),
                "the slow grind never breaks the hull (the reference's band)");
        assertTrue(slow.velocityX() == 0.0 || slow.position().x() < 6.0,
                "the slow hull either rests at the wall or grinds along it");
    }

    // ------------------------------------------------------------------
    // Seat 7 — the raw land gravity
    // ------------------------------------------------------------------

    /**
     * THE RAW GRAVITY: the falling hull's vertical is the plain −0.04
     * per tick under the 0.95 vertical drag — the recurrence v ← (v −
     * 0.04) × 0.95, −0.108395 after three ticks of free fall. RED on
     * the pre-fix engine: the invented pre-multiplied (v − 0.04) × 0.98
     * with the 0.6 land decay (−0.0127 — a different curve whole).
     */
    @Test
    void theLandGravityIsTheRawArm() {
        VehicleEntity.WorldQuery air = dryAir();
        BoatEntity boat = new BoatEntity(9928,
                new Position(0.5, 6.0, 0.5), new Random(39L));
        boat.tick(air);
        boat.tick(air);
        boat.tick(air);
        double expected = 0.0;
        for (int i = 0; i < 3; i++) {
            expected = (expected - VehicleEntity.GRAVITY_PER_TICK)
                    * BoatEntity.VERTICAL_DRAG;
        }
        assertEquals(expected, boat.velocityY(), 1e-12,
                "the raw −0.04 arm under the 0.95 vertical drag (never the"
                        + " ×0.98 pre-multiply)");
    }
}
