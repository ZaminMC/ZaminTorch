package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE 2026-11-08 CENSUS ROW CY — the cart's YAW CONVENTION (the
 * reference's MinecartEntity.tick lines 289-298): the wire yaw is the
 * reference's own {@code fastAtan2(d2, d0)·180/π} over the NEGATED
 * displacement ({@code lastX − x}, {@code lastZ − z}) — east 180,
 * south −90, west 0, north 90 — with the flipped flag's +180 riding on
 * top and the ±170° wrap check flipping the PAIR (yaw += 180 with the
 * flag toggle) to keep the wire yaw continuous tick over tick. The
 * pre-fix engine wrote the LOOK convention (east −90, south 0, west 90,
 * north 180) — a constant 90° rotation away on every cardinal, and no
 * flip bookkeeping at all. The wire's rendering stays the row's named
 * open question: the value on the wire is the law.
 */
class MinecartYawConventionParityDiscoveryTest {

    /** Flat ground at y=4 with an east-west rail along z=0, cells [0, 200). */
    private static VehicleEntity.WorldQuery eastWestRail() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public int railAxisAt(double x, double y, double z) {
                if (y < 3.9 || y > 5.0) {
                    return -1;
                }
                return (int) Math.floor(z) == 0 ? 1 : -1;
            }
        };
    }

    /** Flat ground at y=4 with a north-south rail along x=0, cells [0, 200). */
    private static VehicleEntity.WorldQuery northSouthRail() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public int railAxisAt(double x, double y, double z) {
                if (y < 3.9 || y > 5.0) {
                    return -1;
                }
                return (int) Math.floor(x) == 0 ? 0 : -1;
            }
        };
    }

    /**
     * THE EASTBOUND CARDINAL: a riderless cart seeded east on the track
     * — the raw target is 180 (fastAtan2 over the negated eastward
     * displacement), and the ±170° wrap check FLIPS the pair on the
     * very tick (the 180 jump from the spawn's 0): the wire yaw lands
     * at 360 with the flipped flag armed. The pre-fix engine wrote −90
     * (the look convention). RED pre-fix.
     */
    @Test
    void theEastboundCartCarriesTheReferenceYaw() {
        MinecartEntity cart = new MinecartEntity(9970,
                new Position(0.5, 4.06, 0.5), new Random(3L));
        cart.setVelocity(0.2, 0.0, 0.0);
        cart.tick(eastWestRail());
        assertTrue(cart.position().x() > 0.5, "the cart rolled east");
        assertEquals(360.0, cart.rotation().yaw(), 1e-6,
                "the raw 180 target flipped by the wrap check (the pair's own"
                        + " continuity law) — never the look convention's −90");
        cart.tick(eastWestRail());
        assertEquals(360.0, cart.rotation().yaw(), 1e-6,
                "the flipped pair holds (the +180 rides the flag)");
    }

    /**
     * THE SOUTHBOUND CARDINAL: a cart seeded south — the target is −90
     * (the reference convention), no flip (the 90° turn from the spawn's
     * 0 sits inside the ±170 band), reached exactly. The pre-fix engine
     * wrote 0 (the look convention). RED pre-fix.
     */
    @Test
    void theSouthboundCartCarriesTheReferenceYaw() {
        MinecartEntity cart = new MinecartEntity(9971,
                new Position(0.5, 4.06, 0.5), new Random(3L));
        cart.setVelocity(0.0, 0.0, 0.2);
        cart.tick(northSouthRail());
        assertTrue(cart.position().z() > 0.5, "the cart rolled south");
        assertEquals(-90.0, cart.rotation().yaw(), 1e-6,
                "south is the reference convention's −90 (the look wrote 0)");
    }

    /**
     * THE WESTBOUND CARDINAL: a cart seeded west — the target is 0, the
     * spawn's own yaw, no turn at all. The pre-fix engine wrote 90 (the
     * look convention). RED pre-fix.
     */
    @Test
    void theWestboundCartCarriesTheReferenceYaw() {
        MinecartEntity cart = new MinecartEntity(9972,
                new Position(0.5, 4.06, 0.5), new Random(3L));
        cart.setVelocity(-0.2, 0.0, 0.0);
        cart.tick(eastWestRail());
        assertTrue(cart.position().x() < 0.5, "the cart rolled west");
        assertEquals(0.0, cart.rotation().yaw(), 1e-6,
                "west is the reference convention's 0 (the look wrote 90)");
    }

    /**
     * THE NORTHBOUND CARDINAL: a cart seeded north — the target is 90.
     * The pre-fix engine wrote 180 (the look convention). RED pre-fix.
     */
    @Test
    void theNorthboundCartCarriesTheReferenceYaw() {
        MinecartEntity cart = new MinecartEntity(9973,
                new Position(0.5, 4.06, 0.5), new Random(3L));
        cart.setVelocity(0.0, 0.0, -0.2);
        cart.tick(northSouthRail());
        assertTrue(cart.position().z() < 0.5, "the cart rolled north");
        assertEquals(90.0, cart.rotation().yaw(), 1e-6,
                "north is the reference convention's 90 (the look wrote 180)");
    }

    /**
     * THE STILL CART KEEPS ITS HEADING (the 0.001 gate, line 293): a
     * halted cart (the brake's full stop) never re-points — and the
     * internal consults (the W-kick's fallback) read the LOOK
     * conversion of the wire yaw, so a still cart's next kick still
     * heads where its wire yaw faces.
     */
    @Test
    void theStillCartKeepsTheHeadingAndTheKickReadsTheLookConversion() {
        MinecartEntity cart = new MinecartEntity(9974,
                new Position(0.5, 4.06, 0.5), new Random(3L));
        // An east-facing wire yaw (the reference convention's east = 180).
        cart.setRotation(new Rotation(180.0f, 0.0f));
        cart.tick(eastWestRail()); // no velocity: the still tick
        assertEquals(180.0, cart.rotation().yaw(), 1e-6,
                "the still cart keeps its heading");
        // The W-kick from rest reads the LOOK conversion of the wire yaw
        // (east = the look's −90): the NaN-look fallback heads east.
        assertTrue(cart.mount(4244), "the seat takes the rider");
        cart.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false));
        cart.tick(eastWestRail());
        assertEquals(0.1 * 0.997, cart.velocityX(), 1e-9,
                "the kick headed east (the wire yaw's look conversion)");
        assertEquals(0.0, cart.velocityZ(), 1e-9, "no perpendicular drift");
    }
}
