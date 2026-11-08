package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave SL — THE ASCENDING-SLOPE ROLL and THE RAIL-CENTER INTERPOLATION,
 * the roll family's remaining tails, each read from the reference's
 * MinecartEntity.moveOnRail 1:1:
 *
 * <ul>
 * <li>THE ASCENT'S CLIMB ARM (lines 350-368): the flat ±0.0078125 on the
 * axis component before the projection — an eastward ascent resists the
 * eastward roll and aids the westward descent. The pre-shape walk
 * carried NO arm at all: an ascent was its axis, the cart rolled it
 * flat.</li>
 * <li>THE INTERPOLATED RIDE HEIGHT (snapPositionToRail lines 557-602:
 * the d2 + d8×d0 form with the y++/y+=0.5 fold): a full cell's rise
 * across the ascent's own cell — 0 at the low face, 1 at the high one.
 * The pre-shape walk rode the cell floor + 0.06 everywhere: a climbing
 * cart never rose.</li>
 * <li>THE GRADE'S TRIM (lines 456-461): the tick's altitude change ×
 * 0.05 scaled onto the dragged state — the climb's resistance, the
 * descent's aid.</li>
 * <li>THE TWO-BAND RAIL SEARCH (tick lines 258-263): the cart's own
 * y-cell, the cell below preferred when it holds the rail — the
 * slope's high end rides a full cell above the ascent's own cell.</li>
 * <li>THE CURVE'S DIAGONAL (the d18 walk lines 414-434 + the projection
 * 379-386): the four plain-rail curves walk — the position projects
 * onto the curve's end-to-end line and advances along it, the velocity
 * IS the diagonal, and the cell crossing at the exit re-points the full
 * magnitude to the pure exit axis (lines 466-472). The pre-shape walk
 * read every curve as a dead cell: an engine-built corner track — the
 * placement's own curve states — was unwalkable whole.</li>
 * <li>THE PERPENDICULAR-LOOK KICK (lines 387-399): the kick's 2-D add
 * whole — a perpendicular look still kicks the cart (the projection
 * folds the perpendicular component onto the travel axis, the un-
 * flipped d4 the default direction); the pre-fix engine refused the
 * kick whole.</li>
 * </ul>
 */
class MinecartSlopeRollParityDiscoveryTest {

    /** The un-ridden drag (applySlowdown line 503). */
    private static final double DRAG = MinecartEntity.UNRIDDEN_DRAG;

    /**
     * The hill rig: the west flat (3, 4, 0), the ASCENDING_EAST ascent
     * (4, 4, 0), the upper flat (5+, 5, 0) — the reference's own smooth
     * layout (the ascent's low face meets the west flat's level, its
     * high face the upper flat's).
     */
    private static VehicleEntity.WorldQuery hillWorld() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return false;
            }

            @Override public int railShapeAt(double x, double y, double z) {
                int cx = (int) Math.floor(x);
                int cy = (int) Math.floor(y);
                int cz = (int) Math.floor(z);
                if (cz != 0) {
                    return -1;
                }
                if (cx == 4 && cy == 4) {
                    return 2; // ASCENDING_EAST
                }
                if (cx == 3 && cy == 4) {
                    return 1; // the west flat
                }
                if (cx >= 5 && cy == 5) {
                    return 1; // the upper flat, endless east
                }
                return -1;
            }
        };
    }

    // ------------------------------------------------------------------
    // Seat 1 — the climb's whole composition (the arm + the trim)
    // ------------------------------------------------------------------

    /**
     * THE CLIMB: an empty cart at 0.1 east on the ascent's low half.
     * The arm resists (0.1 − 0.0078125), the move advances the along
     * position, the ride height rises the same distance (the 1:1
     * grade), the 0.96 drag lands, and the trim charges the climb
     * (the rise × 0.05). RED on the pre-fix engine: the ascent was its
     * axis — the cart rolled it flat at the cell floor, no arm, no
     * rise, no trim.
     */
    @Test
    void theClimbResistsAndRises() {
        VehicleEntity.WorldQuery hill = hillWorld();
        MinecartEntity cart = new MinecartEntity(9901,
                new Position(4.05, 4.11, 0.5), new Random(21L));
        cart.setVelocity(0.1, 0.0, 0.0);
        cart.tick(hill);

        double arm = 0.1 - MinecartEntity.SLOPE_ACCEL;
        double expectedX = 4.05 + arm;
        double expectedY = 4.0 + MinecartEntity.RIDE_Y_OFFSET + (expectedX - 4.0);
        double dragged = arm * DRAG;
        double trim = (4.11 - expectedY) * MinecartEntity.SLOPE_TRIM;

        assertEquals(expectedX, cart.position().x(), 1e-9, "the arm-scaled advance");
        assertEquals(expectedY, cart.position().y(), 1e-9,
                "the ride height rises with the advance (the 1:1 grade)");
        assertEquals(0.5, cart.position().z(), 1e-9, "the perpendicular stays centred");
        assertEquals(dragged + trim, cart.velocityX(), 1e-9,
                "the drag then the climb's trim on the carried state");
        assertEquals(0.0, cart.velocityY(), 1e-9, "the vertical stays zero on rail");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the descent's aid
    // ------------------------------------------------------------------

    /**
     * THE DESCENT: the same cart at 0.1 WEST on the ascent — the arm
     * now AIDS (the axis component grows), the cart crosses onto the
     * west flat within the tick, and the boundary drop pays the trim
     * back. RED on the pre-fix engine: no arm, no drop, no trim.
     */
    @Test
    void theDescentIsAided() {
        VehicleEntity.WorldQuery hill = hillWorld();
        MinecartEntity cart = new MinecartEntity(9902,
                new Position(4.05, 4.11, 0.5), new Random(22L));
        cart.setVelocity(-0.1, 0.0, 0.0);
        cart.tick(hill);

        double arm = 0.1 + MinecartEntity.SLOPE_ACCEL;
        double expectedX = 4.05 - arm; // onto the west flat's cell
        double dragged = arm * DRAG;
        double trim = (4.11 - (4.0 + MinecartEntity.RIDE_Y_OFFSET))
                * MinecartEntity.SLOPE_TRIM;

        assertEquals(expectedX, cart.position().x(), 1e-9, "the aided advance");
        assertEquals(4.0 + MinecartEntity.RIDE_Y_OFFSET, cart.position().y(), 1e-9,
                "the west flat's own ride height (the boundary snap)");
        assertEquals(-(dragged + trim), cart.velocityX(), 1e-9,
                "the aided state, the drag, then the descent's trim");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the ride height's interpolation
    // ------------------------------------------------------------------

    /**
     * THE INTERPOLATION: the ride height tracks the along position
     * with the 1:1 grade — y = the cell floor + 0.06 + the progress
     * toward the high face, at any point on the ascent — AND the
     * resting cart SLIDES DOWNHILL (the climb arm from rest: the
     * reference has no from-rest floor, and the arm's ±0.0078125 is
     * the slide's whole cause). RED on the pre-fix engine: the flat
     * cell floor everywhere, the rest frozen.
     */
    @Test
    void theRideHeightInterpolatesAcrossTheAscent() {
        VehicleEntity.WorldQuery hill = hillWorld();
        double[] spots = {4.2, 4.5, 4.8};
        for (int i = 0; i < spots.length; i++) {
            MinecartEntity cart = new MinecartEntity(9910 + i,
                    new Position(spots[i], 4.0 + MinecartEntity.RIDE_Y_OFFSET
                            + (spots[i] - 4.0), 0.5), new Random(23L));
            cart.tick(hill);
            assertEquals(4.0 + MinecartEntity.RIDE_Y_OFFSET + (cart.position().x() - 4.0),
                    cart.position().y(), 1e-9,
                    "the ride height rides the grade at x=" + spots[i]);
            assertTrue(cart.position().x() < spots[i],
                    "the resting cart slides downhill (the arm from rest):");
            assertTrue(cart.position().x() > 4.0 && cart.position().x() < 5.0,
                    "the slide stays on the ascent's cell");
        }
    }

    // ------------------------------------------------------------------
    // Seat 4 — the high-face crossing onto the upper flat
    // ------------------------------------------------------------------

    /**
     * THE CROSSING: the cart near the ascent's top crosses onto the
     * upper flat — the ride height lands the upper flat's own line
     * (the last of the rise), the trim charges the boundary tick, and
     * the next tick rides FLAT (no arm, no trim). RED on the pre-fix
     * engine: the ascent's cell ended the track — the cart halted at
     * the cell centre one level down.
     */
    @Test
    void theHighFaceCrossesOntoTheUpperFlat() {
        VehicleEntity.WorldQuery hill = hillWorld();
        MinecartEntity cart = new MinecartEntity(9903,
                new Position(4.95, 5.01, 0.5), new Random(24L));
        cart.setVelocity(0.09, 0.0, 0.0);
        cart.tick(hill);

        double arm = 0.09 - MinecartEntity.SLOPE_ACCEL;
        double expectedX = 4.95 + arm;
        double dragged = arm * DRAG;
        double trim = (5.01 - (5.0 + MinecartEntity.RIDE_Y_OFFSET))
                * MinecartEntity.SLOPE_TRIM;
        assertEquals(expectedX, cart.position().x(), 1e-9, "the crossing advance");
        assertEquals(5.0 + MinecartEntity.RIDE_Y_OFFSET, cart.position().y(), 1e-9,
                "the upper flat's own ride height");
        assertEquals(dragged + trim, cart.velocityX(), 1e-9,
                "the boundary tick's composition");

        // The flat continuation: no arm, no trim — the drag alone.
        cart.tick(hill);
        assertEquals(5.0 + MinecartEntity.RIDE_Y_OFFSET, cart.position().y(), 1e-9,
                "the upper flat rides level");
        double flatDragged = (dragged + trim) * DRAG;
        assertEquals(flatDragged, cart.velocityX(), 1e-9,
                "the flat tick carries the drag alone (the trim is a no-op)");
    }

    // ------------------------------------------------------------------
    // Seat 5 — the perpendicular-look kick (the 2-D add whole)
    // ------------------------------------------------------------------

    /**
     * THE PERPENDICULAR KICK: a mounted rider holding W into a look
     * ten degrees off pure north on an EAST-WEST rail — the kick's 2-D
     * add lands (0.0174, −0.0985), the projection folds the look's
     * perpendicular BULK onto the travel axis WHOLE (the magnitude a
     * full 0.1, the direction the along component's sign: EAST), and
     * the cart starts. RED on the pre-fix engine: the kick was refused
     * whole — the cart never moved. (The exactly-perpendicular look
     * is float noise in both engines alike — sin(180°) is never zero
     * — so the seat pins the law through the near-perpendicular form.)
     */
    @Test
    void thePerpendicularLookStillKicks() {
        VehicleEntity.WorldQuery plain = new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return false;
            }

            @Override public int railShapeAt(double x, double y, double z) {
                return (int) Math.floor(z) == 0 && y > 3.9 && y < 5.0 ? 1 : -1;
            }
        };
        MinecartEntity cart = new MinecartEntity(9904,
                new Position(5.5, 4.06, 0.5), new Random(25L));
        assertTrue(cart.mount(4242), "the seat takes the rider");
        cart.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false,
                190.0f)); // W into a mostly-north look, east-biased
        cart.tick(plain);

        double radians = Math.toRadians(190.0f);
        double alongAfter = -Math.sin(radians) * MinecartEntity.RIDER_KICK;
        double perpAfter = Math.cos(radians) * MinecartEntity.RIDER_KICK;
        double foldedSpeed = Math.sqrt(alongAfter * alongAfter
                + perpAfter * perpAfter);
        assertEquals(5.5 + foldedSpeed * MinecartEntity.RIDER_MOVE_SCALE,
                cart.position().x(), 1e-9,
                "the perpendicular bulk folds onto the axis (the full 0.1"
                        + " magnitude moves the cart)");
        assertEquals(0.5, cart.position().z(), 1e-9, "the travel line holds");
        assertEquals(foldedSpeed * MinecartEntity.RIDDEN_DRAG,
                cart.velocityX(), 1e-9,
                "the folded state (the along sign's direction), the rider's drag");
        assertEquals(0.0, cart.velocityZ(), 1e-9, "the axis-pure carry");
    }

    // ------------------------------------------------------------------
    // Seat 6 — the curve's diagonal (the d18 interpolation)
    // ------------------------------------------------------------------

    /**
     * THE CURVE WALK: the plain rail's north_east curve — a cart
     * rolling south on the north leg enters the curve cell, the
     * velocity re-points to the curve's own diagonal (the average of
     * the legs), and the position rides the end-to-end line. RED on
     * the pre-fix engine: the curve cell was a dead cell — the cart
     * left the rail whole (the off-rail walk).
     */
    @Test
    void theCurveWalksItsOwnDiagonal() {
        VehicleEntity.WorldQuery track = curveWorld();
        MinecartEntity cart = new MinecartEntity(9905,
                new Position(4.5, 4.06, -0.45), new Random(26L));
        cart.setVelocity(0.0, 0.0, 0.2); // rolling south
        // Three flat ticks bring the cart onto the curve cell's border.
        cart.tick(track);
        cart.tick(track);
        cart.tick(track);
        assertEquals(0.12632, cart.position().z(), 1e-9, "the border arrival");
        // THE CURVE TICK: the diagonal projection and the d18 advance.
        cart.tick(track);
        double speed = 0.2 * DRAG * DRAG * DRAG;
        double lineLen = Math.sqrt(0.5);
        double d18 = 0.12632 + speed / lineLen;
        assertEquals(4.5 + 0.5 * d18, cart.position().x(), 1e-9,
                "the position rides the curve's diagonal");
        assertEquals(0.5 * d18, cart.position().z(), 1e-9,
                "the mid-rail interpolation (the d18 walk)");
        double diagonal = speed / lineLen;
        assertEquals(diagonal * 0.5 * DRAG, cart.velocityX(), 1e-9,
                "the diagonal carry, the drag after");
        assertEquals(diagonal * 0.5 * DRAG, cart.velocityZ(), 1e-9,
                "the diagonal carry, the drag after");
    }

    /**
     * THE CURVE'S EXIT: the diagonal carries the cart across the cell
     * boundary into the east leg's cell, the crossing re-points the
     * FULL magnitude to the pure east (the cell-change re-point), and
     * the east leg's own walk centres the line. RED on the pre-fix
     * engine: the dead curve cell ended the run.
     */
    @Test
    void theCurveExitRePointsToTheLeg() {
        VehicleEntity.WorldQuery track = curveWorld();
        MinecartEntity cart = new MinecartEntity(9906,
                new Position(4.5, 4.06, -0.45), new Random(27L));
        cart.setVelocity(0.0, 0.0, 0.2);
        for (int i = 0; i < 12; i++) {
            cart.tick(track);
        }
        assertTrue(cart.position().x() > 5.0,
                "the cart crossed into the east leg: " + cart.position());
        assertEquals(0.5, cart.position().z(), 1e-9,
                "the east leg's own line (the exit face's centre)");
        assertEquals(0.0, cart.velocityZ(), 1e-9,
                "the pure east carry (the cell-change re-point)");
        assertTrue(cart.velocityX() > 0.0, "the full magnitude redirected east");
    }

    /**
     * The curve rig: the north leg (4, 4, −1) north_south, the
     * NORTH_EAST curve (4, 4, 0) — the legs north and east — and the
     * east leg (5+, 4, 0) east-west, endless.
     */
    private static VehicleEntity.WorldQuery curveWorld() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return false;
            }

            @Override public int railShapeAt(double x, double y, double z) {
                int cx = (int) Math.floor(x);
                int cy = (int) Math.floor(y);
                int cz = (int) Math.floor(z);
                if (cy != 4) {
                    return -1;
                }
                if (cx == 4 && cz == -1) {
                    return 0; // the north leg
                }
                if (cx == 4 && cz == 0) {
                    return 9; // NORTH_EAST: the legs north and east
                }
                if (cx >= 5 && cz == 0) {
                    return 1; // the east leg
                }
                return -1;
            }
        };
    }

    // ------------------------------------------------------------------
    // Seat 7 — the engine-built hill (the real world)
    // ------------------------------------------------------------------

    /** The real-server rig's scratch dir (the boot's own). */
    @org.junit.jupiter.api.io.TempDir
    static Path hillDataDir;

    /**
     * THE ENGINE'S OWN HILL: a placed ascending rail between two flats
     * over the real EngineServer — the cart climbs the ascent and
     * rides the upper flat a full level up. RED on the pre-fix engine:
     * the ascent state read as a dead cell — the track ended at the
     * ascent's cell, the cart halted one level down.
     */
    @Test
    void theEngineBuiltHillIsWalkable() throws Exception {
        net.zaminmc.torch.server.config.EngineConfig config =
                new net.zaminmc.torch.server.config.EngineConfig("127.0.0.1", 0,
                        "hillwalk" + Math.abs(java.util.UUID.randomUUID().hashCode() % 100000),
                        "it", 20, 2, 20, hillDataDir.toString(),
                        net.zaminmc.torch.GameMode.CREATIVE, true, "flat", false,
                        net.zaminmc.torch.server.config.EngineConfig.WHOLE_WORLD_REGION_SIZE,
                        true);
        net.zaminmc.torch.server.EngineServer server = null;
        try {
            server = new net.zaminmc.torch.server.EngineServer(config);
            server.start();

            final int y = 40;
            final int z = 10;
            final net.zaminmc.torch.server.EngineServer srv = server;
            java.util.concurrent.atomic.AtomicReference<MinecartEntity> cart =
                    new java.util.concurrent.atomic.AtomicReference<>();
            srv.ticker().submit(() -> {
                // The bed under the lower flats, the ascent's support,
                // and the upper flat's own ground one level up.
                for (int dx = 10; dx <= 18; dx++) {
                    srv.world().setBlock(new net.zaminmc.torch.block.BlockPosition(dx, y - 1, z),
                            net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
                }
                for (int dx = 10; dx <= 18; dx++) {
                    srv.world().setBlock(new net.zaminmc.torch.block.BlockPosition(dx, y, z),
                            net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
                }
                // The lower flats (11, 12), the ascent (13), the upper flats (14+)
                // — the upper flats' own support one level below them (the
                // rails' placement consult refuses an unsupported cell).
                for (int dx = 14; dx <= 18; dx++) {
                    srv.world().setBlock(new net.zaminmc.torch.block.BlockPosition(dx, y + 1, z),
                            net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
                }
                srv.world().setBlock(new net.zaminmc.torch.block.BlockPosition(11, y + 1, z),
                        net.zaminmc.torch.server.block.RailBlocks.plainState(1));
                srv.world().setBlock(new net.zaminmc.torch.block.BlockPosition(12, y + 1, z),
                        net.zaminmc.torch.server.block.RailBlocks.plainState(1));
                srv.world().setBlock(new net.zaminmc.torch.block.BlockPosition(13, y + 1, z),
                        net.zaminmc.torch.server.block.RailBlocks.plainState(2)); // ASCENDING_EAST
                for (int dx = 14; dx <= 18; dx++) {
                    srv.world().setBlock(new net.zaminmc.torch.block.BlockPosition(dx, y + 2, z),
                            net.zaminmc.torch.server.block.RailBlocks.plainState(1));
                }
                var spawned = srv.vehicles().spawnMinecart(
                        new net.zaminmc.torch.util.Position(11.5, y + 1.35, z + 0.5), 0);
                if (spawned instanceof MinecartEntity plain) {
                    plain.setVelocity(0.35, 0.0, 0.0); // rolling east
                    cart.set(plain);
                }
            });
            net.zaminmc.torch.testing.EngineAwaits.awaitCondition(srv.world(),
                    () -> cart.get() != null, 100, "the hill cart spawned");
            net.zaminmc.torch.testing.EngineAwaits.awaitTicks(srv.world(), 40,
                    "the hill climb window");

            MinecartEntity body = cart.get();
            assertTrue(body.position().y() > y + 2.0,
                    "the cart climbed the ascent (the upper flat's level): "
                            + body.position());
            assertTrue(body.position().x() > 14.5,
                    "the cart rides the upper flat: " + body.position());
        } finally {
            if (server != null) {
                server.shutdown(null);
            }
        }
    }
}
