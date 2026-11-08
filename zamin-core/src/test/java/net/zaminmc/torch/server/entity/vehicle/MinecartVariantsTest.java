package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The minecart variants, part one (Slice 66 — TntMinecartEntity +
 * FurnaceMinecartEntity 1:1): the TNT cart's fuse (the activator-rail
 * prime, the 80-tick burn, the detonation at zero, the impact arm) and
 * the furnace cart's fuel law (the 3600-tick coal feed, the push aim,
 * the burn-down, the lit flag).
 */
class MinecartVariantsTest {

    /** The flat empty world (no rails — the off-rail tail). */
    private static final VehicleEntity.WorldQuery EMPTY_WORLD =
            new VehicleEntity.WorldQuery() {
                @Override public boolean isSolid(double x, double y, double z) {
                    return false;
                }
            };

    /** The recording blast sink. */
    private static final class Blasts implements TntMinecartEntity.BlastSink {
        final List<float[]> detonations = new ArrayList<>();

        @Override
        public void detonate(double x, double y, double z, float power, boolean primed, int dimension) {
            detonations.add(new float[]{(float) x, (float) y, (float) z, power, primed ? 1f : 0f});
        }
    }

    @Test
    void theTntCartPrimesBurnsAndDetonates() {
        // prime lines 141-153 + tick lines 39-60: the 80-tick fuse, the
        // detonation at zero with the rolled power (4.0 + rand * 1.5 * 0
        // at rest — the clamp band).
        Blasts blasts = new Blasts();
        TntMinecartEntity cart = new TntMinecartEntity(9700,
                new Position(0.5, 64.0, 0.5), new Random(71L));
        cart.setBlastSink(blasts);
        assertFalse(cart.primed(), "freshly placed: unprimed");
        assertEquals(-1, cart.fuseTicks());

        cart.onActivatorRail(true); // the powered activator rail
        assertTrue(cart.primed(), "the rail primed the fuse");
        assertEquals(80, cart.fuseTicks());
        cart.onActivatorRail(true); // a second prime never restarts the clock
        assertEquals(80, cart.fuseTicks());

        for (int i = 0; i < 85 && !cart.dead(); i++) {
            cart.tick(EMPTY_WORLD);
        }
        assertTrue(cart.dead(), "the fuse burned to the detonation");
        assertEquals(1, blasts.detonations.size(), "one detonation");
        float power = blasts.detonations.get(0)[3];
        assertTrue(power >= 4.0f && power < 4.0f + 1.5f * 5.0 + 1e-6,
                "the power band 4.0 + rand * 1.5 * clamp: " + power);
    }

    @Test
    void theTntCartDetonatesOnAFastHalt() {
        // tick lines 54-60 (collidingHorizontally): a cart that stops dead
        // at speed squared >= 0.01 detonates — the engine's track-end halt
        // is the matching surface.
        Blasts blasts = new Blasts();
        TntMinecartEntity cart = new TntMinecartEntity(9710,
                new Position(0.5, 64.0, 0.5), new Random(72L));
        cart.setBlastSink(blasts);
        cart.setVelocity(0.3, 0.0, 0.0); // the full rail speed
        // The off-rail world never halts the cart through the rail walk —
        // the impact arm keys on the halted state (velocity zeroed with a
        // pre-halt speed), which the tick's off-rail tail never produces.
        // The unit: the arm's own gate (the pre-halt speed + the zeroed
        // velocity) through a rail world that ends the track.
        VehicleEntity.WorldQuery endingRail = new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return false;
            }

            @Override public int railAxisAt(double x, double y, double z) {
                // A single north-south rail cell at (0, 64, 0): the track
                // ends immediately — the walk halts the cart.
                return (int) Math.floor(x) == 0 && (int) Math.floor(y + 0.4) == 64
                        && (int) Math.floor(z) == 0 ? 0 : -1;
            }
        };
        cart.tick(endingRail); // the halt: the pre-halt speed detonates
        assertTrue(cart.dead(), "the fast halt detonated");
        assertEquals(1, blasts.detonations.size(), "the impact detonation");
    }

    @Test
    void theFurnaceCartFeedsBurnsAndPushes() {
        // interact lines 106-117 + tick lines 44-52: the coal feeds 3600
        // ticks, the push aims away from the player, the fuel burns down,
        // and the empty tank zeroes the push.
        // Wave SL: the placement rides mid-cell (64.35) — the reference's
        // two-band rail search couples a body whose y sits inside the rail
        // cell's own band [64, 65); the swapped-world sequence below drops
        // the body one off-rail tick (the -0.04 fall) before the rail
        // world's tick, and a floor-level placement would fall below the
        // band whole (the old +0.4 probe's overreach retired).
        FurnaceMinecartEntity cart = new FurnaceMinecartEntity(9720,
                new Position(10.5, 64.35, 10.5), new Random(73L));
        assertFalse(cart.lit(), "unfueled at placement");
        assertEquals(0, cart.fuel());

        // The player stands west (x 8.5): the push aims east.
        cart.feedAndAim(8.5, 10.5, true);
        assertEquals(3600, cart.fuel(), "the coal's 3600-tick feed");
        assertTrue(cart.lit(), "the tank holds fuel");
        assertTrue(cart.pushX() > 0.0, "the push rolls away from the player");
        assertEquals(0.0, cart.pushZ(), 1e-9, "the aim is purely east");

        // The push arm rides the ON-RAIL drag call site alone (applySlowdown
        // lines 91-110 — the reference's moveOnRail owns it; an OFF-RAIL
        // furnace never pushes, moveOffRail's gravity walk the only law).
        cart.tick(EMPTY_WORLD);
        assertEquals(3599, cart.fuel(), "the fuel burns one per tick");
        assertEquals(0.0, cart.velocityX(), 1e-9,
                "the off-rail furnace never pushes (moveOffRail owns it)");

        // The on-rail push: an endless east-west track at the cart's bed.
        VehicleEntity.WorldQuery rail = new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 64.0;
            }

            @Override public int railAxisAt(double x, double y, double z) {
                if (y < 63.9 || y > 65.0) {
                    return -1;
                }
                return (int) Math.floor(z) == 10 ? 1 : -1;
            }
        };
        double xBefore = cart.position().x();
        cart.tick(rail);
        // The first on-rail tick from rest: the move is zero (the state
        // was zero at the walk's head), and the push arm hands the carry
        // the whole unit push damped by the un-ridden base drag:
        // 0 * 0.8 + 1.0, then * 0.96 = 0.96.
        assertEquals(xBefore, cart.position().x(), 1e-9,
                "the from-rest move is zero");
        assertEquals(0.96, cart.velocityX(), 1e-9,
                "the push arm: the unit push, the 0.8 ram, the base 0.96");
        cart.tick(rail);
        // The rolling move clamps at the furnace's own getMaxSpeed 0.2 —
        // the state above it (0.96) rides on (the reference's clamp is
        // the move's alone).
        assertEquals(0.2, cart.position().x() - xBefore, 1e-9,
                "the furnace's rolling move clamps at 0.2 (getMaxSpeed)");

        // The burn-out: the tank empties, the lit flag drops, the push zeroes.
        for (int i = 0; i < FurnaceMinecartEntity.COAL_FUEL_TICKS; i++) {
            cart.tick(rail);
        }
        assertEquals(0, cart.fuel(), "the tank emptied");
        assertFalse(cart.lit(), "the lit flag dropped");
        assertEquals(0.0, cart.pushX(), 1e-9, "the empty tank zeroes the push");
        assertFalse(cart.dead(), "the burn-out never breaks the cart");
    }
}
