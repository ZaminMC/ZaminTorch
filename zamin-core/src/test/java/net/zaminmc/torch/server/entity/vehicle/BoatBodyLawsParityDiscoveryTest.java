package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE 2026-11-08 CENSUS ROWS BB-1..BB-4 — the boat's own BODY laws, each
 * read from the reference's BoatEntity.tick 1:1 and each RED on the
 * pre-fix engine:
 *
 * <ul>
 * <li>BB-1 — THE 5-SLICE BUOYANCY (lines 189-199 + 248-257): the hull's
 * band sliced into five (each slice shifted −0.125), the wet fraction
 * d0 steering the ±0.04 bob — the d0=0 form IS the raw gravity — and
 * the fully-submerged hull halving a falling vy and floating up at
 * +0.007. The pre-fix engine's zero-vertical water rest never
 * bobbed.</li>
 * <li>BB-2 — THE HEADING LAW (lines 328-346): the yaw follows the
 * MOVEMENT — the reference's fastAtan2 over the NEGATED displacement —
 * clamped ±20°/tick, NEVER the rider's look. The pre-fix engine's
 * followRiderLook pointed the hull at the look whole.</li>
 * <li>BB-3 — THE WRECK DROPS (lines 315-325 + takeDamage lines 99-108 +
 * checkFallDamage lines 396-411): the crash and the 3.0+ fall break
 * the hull into 3 planks + 2 sticks; the damage walk's own break hands
 * the boat item. The pre-fix engine handed the boat item for every
 * break alike — the routing AND the real wiring's five-drop form.</li>
 * <li>BB-4 — THE FLORA MOW (lines 286-302): the hull's four corners ×
 * the two y-bands — a snow layer vanishes WITHOUT drops, a lily pad
 * breaks WITH its drops. The pre-fix engine mowed nothing.</li>
 * </ul>
 */
class BoatBodyLawsParityDiscoveryTest {

    /** Water at y ∈ [4, 5): the surface at 5.0, no ground, no walls. */
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

    /** Dry ground at y < 4 (the fall-break landing). */
    private static VehicleEntity.WorldQuery dryGround() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }
        };
    }

    // ------------------------------------------------------------------
    // BB-1 — the 5-slice buoyancy
    // ------------------------------------------------------------------

    /**
     * THE SUBMERGED RISE: a hull deep in the band (every slice's centre
     * wet, d0 = 1) halves nothing (vy starts 0) and floats up at
     * +0.007 — the post-drag carry 0.00665. RED pre-fix: the water rest
     * zeroed vy whole.
     */
    @Test
    void theFullySubmergedHullFloatsUp() {
        BoatEntity boat = new BoatEntity(9930,
                new Position(0.5, 4.5, 0.5), new Random(7L));
        boat.tick(openWater());
        assertEquals(BoatEntity.SUBMERGED_RISE * BoatEntity.VERTICAL_DRAG,
                boat.velocityY(), 1e-12,
                "the submerged hull rises at +0.007 (the drag after the move)");
        assertEquals(4.5 + BoatEntity.SUBMERGED_RISE, boat.position().y(), 1e-12,
                "the rise moved the hull");
    }

    /**
     * THE PARTIAL BAND'S BOB: the waterline (y=5) cuts the slice band so
     * exactly three of five centres stay wet (d0 = 0.6 — the hull at
     * y=5.08) — the bob adds 0.04·(2·0.6 − 1) = +0.008; two of five
     * (d0 = 0.4 — y=5.16) — the bob adds −0.008 (the sink side). RED
     * pre-fix: none of these bands moved the hull at all.
     */
    @Test
    void thePartialBandBobsByItsWetFraction() {
        BoatEntity mostlyWet = new BoatEntity(9931,
                new Position(0.5, 5.08, 0.5), new Random(7L));
        mostlyWet.tick(openWater());
        assertEquals(BoatEntity.BUOYANCY_GAIN * (0.6 * 2.0 - 1.0)
                        * BoatEntity.VERTICAL_DRAG,
                mostlyWet.velocityY(), 1e-12, "the 0.6-wet hull bobs up (+0.008)");

        BoatEntity mostlyDry = new BoatEntity(9932,
                new Position(0.5, 5.16, 0.5), new Random(7L));
        mostlyDry.tick(openWater());
        assertEquals(BoatEntity.BUOYANCY_GAIN * (0.4 * 2.0 - 1.0)
                        * BoatEntity.VERTICAL_DRAG,
                mostlyDry.velocityY(), 1e-12, "the 0.4-wet hull sinks (−0.008)");
    }

    /**
     * THE WATER-BELOW FALL GATE (checkFallDamage line 415's own consult):
     * the debt never fires over water — the landing consult is the
     * ground's alone. A hull dropped from y=8 splashes into the band and
     * bobs, unbroken (the same drop onto dry ground breaks it — the
     * fall seat below).
     */
    @Test
    void theFallIntoWaterNeverBreaksTheHull() {
        BoatEntity intoWater = new BoatEntity(9934,
                new Position(0.5, 8.0, 0.5), new Random(7L));
        for (int i = 0; i < 80 && !intoWater.dead(); i++) {
            intoWater.tick(openWater());
        }
        assertFalse(intoWater.dead(), "the water landing never breaks the hull");
        assertTrue(intoWater.position().y() > 4.0 && intoWater.position().y() < 5.6,
                "the hull splashed into the band and bobs: " + intoWater.position());
    }

    // ------------------------------------------------------------------
    // BB-2 — the heading law
    // ------------------------------------------------------------------

    /**
     * THE MOVEMENT-FOLLOW: a hull paddled SOUTH (the rider looking
     * south, the look convention 0) turns its yaw to the movement at
     * the ±20° clamp — south is the reference convention's −90, reached
     * in clamped ticks. RED pre-fix: the look-follow pointed the hull
     * at 0 (the look) whole, no clamp, no movement read.
     */
    @Test
    void theHeadingFollowsTheMovementNotTheLook() {
        BoatEntity boat = new BoatEntity(9940,
                new Position(0.5, 4.5, 0.5), new Random(7L));
        assertTrue(boat.mount(4242), "the seat takes the rider");
        // The first tick only warms the momentum (the drive starts at
        // zero) — the hull does not move, the heading keeps.
        boat.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false, 0.0f));
        boat.tick(openWater());
        assertEquals(0.0f, boat.rotation().yaw(), 1e-9f,
                "the still hull keeps its heading (the 0.001 gate)");
        double firstZ = boat.position().z();
        // The clamp: each moving tick turns at most 20° toward the
        // movement (south = −90 in the reference convention) — the
        // momentum's own 35-tick ramp means the hull only crosses the
        // 0.001 displacement gate once the paddle has warmed.
        for (int i = 0; i < 45; i++) {
            boat.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false, 0.0f));
            double before = boat.rotation().yaw();
            boat.tick(openWater());
            double turned = boat.rotation().yaw() - before;
            assertTrue(turned <= 20.0 + 1e-9 && turned >= -20.0 - 1e-9,
                    "the turn clamps at ±20°/tick: " + turned);
        }
        assertTrue(boat.position().z() > firstZ, "the hull paddled south");
        assertEquals(0.0, VehicleMath.wrapDegrees(boat.rotation().yaw() - (-90.0)),
                1e-6, "the heading reached the movement's own angle (south −90)");
        assertEquals(0.0f, boat.rotation().pitch(), 1e-9f, "the pitch zeroes");
    }

    /**
     * THE LOOK NEVER THE HEADING: the rider LOOKS EAST (−90) but paddles
     * ASTERN (forward = −1) — the drive heads WEST, the movement is
     * westward, and the heading must follow THE MOVEMENT (the reference
     * convention's 0), never the look (180). RED pre-fix: the
     * look-follow handed 180 whole.
     */
    @Test
    void theBackwardPaddleHeadsAgainstTheLook() {
        BoatEntity boat = new BoatEntity(9941,
                new Position(0.5, 4.5, 0.5), new Random(7L));
        assertTrue(boat.mount(4243), "the seat takes the rider");
        for (int i = 0; i < 25; i++) {
            boat.steer(new VehicleEntity.SteerInput(0.0f, -1.0f, false, false, -90.0f));
            boat.tick(openWater());
        }
        assertTrue(boat.position().x() < 0.42, "the hull paddled west: " + boat.position());
        assertEquals(0.0, VehicleMath.wrapDegrees(boat.rotation().yaw() - 0.0),
                1e-6, "the heading is the movement's (west 0), never the look's (180)");
    }

    // ------------------------------------------------------------------
    // BB-3 — the wreck drops (the routing + the real wiring's form)
    // ------------------------------------------------------------------

    /** The routing-capturing sink (which arm the sweep hands the break). */
    private static final class RoutingDrops implements VehicleManager.DropSink {
        final List<String> arms = new ArrayList<>();

        @Override
        public void dropVehicleItem(VehicleEntity vehicle) {
            arms.add("item");
        }

        @Override
        public void dropBoatWreck(VehicleEntity vehicle) {
            arms.add("wreck");
        }
    }

    /**
     * THE CRASH WRECK (the routing): the high-speed wall hit breaks the
     * hull through the WRECK arm — never the vehicle-item arm the
     * damage walk owns. RED pre-fix: the crash handed the item arm.
     */
    @Test
    void theCrashRoutesToTheWreckArm() {
        RoutingDrops drops = new RoutingDrops();
        VehicleManager manager = new VehicleManager(dryGround(), drops,
                new Random(7L), 9900);
        VehicleEntity boat = manager.spawnBoat(new Position(0.5, 4.2, 0.5), 0);
        // The wall: everything at x >= 0.8 is solid (the manual walk's
        // block refusal) — the crash band's own gate, hit on the very
        // first tick at the full 0.4.
        VehicleEntity.WorldQuery walled = new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0 || x >= 0.8;
            }
        };
        boat.setVelocity(0.4, 0.0, 0.0); // over the 0.2975 crash band
        boat.tick(walled);
        assertTrue(boat.dead(), "the high-speed wall hit broke the hull");
        assertTrue(boat.wrecked(), "the crash is a wreck break");
        manager.tick(); // the dead sweep carries the drops
        assertEquals(0, manager.all().size(), "the broken hull left the world");
        assertEquals(List.of("wreck"), drops.arms,
                "the crash routes to the wreck arm: " + drops.arms);
    }

    /**
     * THE DAMAGE WALK'S OWN BREAK KEEPS THE BOAT ITEM (takeDamage lines
     * 99-108 — the >40 damage break drops the boat, the CONTRAST arm of
     * the wreck law): four damage through the manager's hurt hands the
     * item arm alone.
     */
    @Test
    void theDamageBreakKeepsTheBoatItem() {
        RoutingDrops drops = new RoutingDrops();
        VehicleManager manager = new VehicleManager(dryGround(), drops,
                new Random(7L), 9901);
        VehicleEntity boat = manager.spawnBoat(new Position(0.5, 4.2, 0.5), 0);
        manager.hurt(boat, 4.0f);
        assertTrue(boat.dead(), "four damage breaks the boat");
        assertFalse(boat.wrecked(), "the damage walk's break is no wreck");
        manager.tick();
        assertEquals(List.of("item"), drops.arms,
                "the damage break routes to the vehicle-item arm: " + drops.arms);
    }

    /**
     * THE FALL BREAK (checkFallDamage lines 396-411): a dry fall past
     * 3.0 breaks the hull through the WRECK arm; a fall inside the band
     * leaves it whole. RED pre-fix: no fall break existed at all.
     */
    @Test
    void theLongDryFallBreaksTheHullThroughTheWreckArm() {
        RoutingDrops drops = new RoutingDrops();
        VehicleManager manager = new VehicleManager(dryGround(), drops,
                new Random(7L), 9902);
        VehicleEntity longFall = manager.spawnBoat(new Position(0.5, 8.0, 0.5), 0);
        for (int i = 0; i < 60 && !longFall.dead(); i++) {
            longFall.tick(dryGround());
        }
        assertTrue(longFall.dead(), "the 3.8-block dry fall broke the hull");
        assertTrue(longFall.wrecked(), "the fall break is a wreck break");

        VehicleEntity shortFall = manager.spawnBoat(new Position(0.5, 6.5, 0.5), 0);
        for (int i = 0; i < 60 && !shortFall.dead(); i++) {
            shortFall.tick(dryGround());
        }
        assertFalse(shortFall.dead(), "the 2.3-block dry fall left the hull whole");
        manager.tick(); // the sweep carries the long fall's wreck
        assertEquals(List.of("wreck"), drops.arms,
                "the fall break routes to the wreck arm: " + drops.arms);
    }

    // ------------------------------------------------------------------
    // BB-3 (the real wiring) — the crash hands 3 planks + 2 sticks
    // ------------------------------------------------------------------

    /**
     * THE WRECK'S OWN FORM over the REAL wiring: a booted engine's boat
     * crashed into a wall drops THREE planks and TWO sticks as item
     * entities beside the wreck — five separate drops, never the boat
     * item. RED pre-fix: the wiring handed the boat item (the one drop
     * the damage walk owns).
     */
    @Test
    void theCrashDropsThreePlanksAndTwoSticksOnTheRealEngine(
            @org.junit.jupiter.api.io.TempDir Path dataDir) throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "boatwreck" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            // The ground row and the wall two cells east of the spawn.
            server.ticker().submit(() -> {
                for (int x = 0; x < 10; x++) {
                    for (int z = 0; z < 4; z++) {
                        server.world().setBlock(new BlockPosition(x, 8, z),
                                BuiltinBlocks.STONE);
                    }
                }
                for (int y = 9; y < 12; y++) {
                    for (int z = 0; z < 4; z++) {
                        server.world().setBlock(new BlockPosition(4, y, z),
                                BuiltinBlocks.STONE);
                    }
                }
            });
            EngineAwaits.awaitTicks(server.world(), 3, "the ground and the wall");
            AtomicReference<VehicleEntity> hull = new AtomicReference<>();
            server.ticker().submit(() -> {
                // The spawn one first-move from the wall's face (the box's
                // east edge at 3.85 + the 0.2 grounded move crosses 4.0):
                // the crash consult reads the tick-HEAD speed, so the
                // collision must land on the launch tick itself — the
                // grounded halving would eat the band by the second.
                VehicleEntity boat = server.vehicles()
                        .spawnBoat(new Position(3.1, 9.2, 1.5), 0);
                if (boat != null) {
                    boat.setVelocity(0.4, 0.0, 0.0); // over the crash band
                    hull.set(boat);
                }
            });
            await(() -> hull.get() != null, "the hull spawned");
            await(() -> hull.get().dead(), "the wall at x=4 broke the hull");
            EngineAwaits.awaitTicks(server.world(), 3, "the wreck drops land");
            List<String> near = new ArrayList<>();
            server.ticker().submit(() -> {
                for (var item : server.itemEntities().all()) {
                    Position at = item.position();
                    if (at.x() > 1.0 && at.x() < 4.5 && at.y() > 7.0 && at.y() < 12.0
                            && at.z() > -0.5 && at.z() < 3.5) {
                        // The five same-spot drops MERGE into their stacks
                        // (the engine's item-merge, vanilla's own): the
                        // counts carry the law — three planks, two sticks.
                        for (int i = 0; i < item.stack().count(); i++) {
                            near.add(item.stack().type().identifier().value());
                        }
                    }
                }
            });
            await(() -> !near.isEmpty(), "the drops read");
            int planks = 0;
            int sticks = 0;
            int boats = 0;
            for (String id : near) {
                switch (id) {
                    case "planks" -> planks++;
                    case "stick" -> sticks++;
                    case "boat" -> boats++;
                    default -> { }
                }
            }
            assertEquals(3, planks, "three planks: " + near);
            assertEquals(2, sticks, "two sticks: " + near);
            assertEquals(0, boats, "never the boat item: " + near);
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------
    // BB-4 — the flora mow
    // ------------------------------------------------------------------

    /**
     * THE MOW: the hull at (0.7, 4.5, 0.7) — the four corner consults
     * read the cells (0,·,0) and (1,·,1) at the y-bands floor(4.5)=4 and
     * 5: a SNOW LAYER at (1, 4, 1) vanishes WITHOUT drops, a LILY PAD
     * at (1, 5, 1) breaks WITH its drops, and the cells OUTSIDE the
     * consult — (0, 6, 0) above the bands, (2, 4, 2) beyond the corners
     * — keep their flora (the stub's blockIdAt keeps answering them,
     * and neither arm ever fires for them). RED pre-fix: nothing was
     * ever mowed.
     */
    @Test
    void theHullMowsSnowLayersAndLilyPadsUnderItsCorners() {
        List<int[]> removed = new ArrayList<>();
        List<int[]> broken = new ArrayList<>();
        VehicleEntity.WorldQuery mowWorld = new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return false;
            }

            @Override public Identifier blockIdAt(int x, int y, int z) {
                if (x == 1 && y == 4 && z == 1) {
                    return Identifier.parse("minecraft:snow_layer");
                }
                if (x == 1 && y == 5 && z == 1) {
                    return Identifier.parse("minecraft:waterlily");
                }
                if (x == 0 && y == 6 && z == 0) {
                    return Identifier.parse("minecraft:snow_layer"); // above the bands
                }
                if (x == 2 && y == 4 && z == 2) {
                    return Identifier.parse("minecraft:waterlily"); // beyond the corners
                }
                return null;
            }

            @Override public void removeBlockAt(int x, int y, int z) {
                removed.add(new int[] {x, y, z});
            }

            @Override public void breakBlockWithDrops(int x, int y, int z) {
                broken.add(new int[] {x, y, z});
            }
        };
        BoatEntity boat = new BoatEntity(9950,
                new Position(0.7, 4.5, 0.7), new Random(7L));
        boat.tick(mowWorld);
        assertEquals(1, removed.size(), "one snow layer vanished (no drops)");
        assertCell(removed.get(0), 1, 4, 1, "the snow layer under the + corner");
        assertEquals(1, broken.size(), "one lily pad broke (with drops)");
        assertCell(broken.get(0), 1, 5, 1, "the lily pad in the upper band");
    }

    private static void assertCell(int[] cell, int x, int y, int z, String what) {
        assertTrue(cell[0] == x && cell[1] == y && cell[2] == z,
                what + ": expected (" + x + "," + y + "," + z + ")"
                        + " got (" + cell[0] + "," + cell[1] + "," + cell[2] + ")");
    }

    // ------------------------------------------------------------------
    // the heading's own seat anchor (the wire yaw's consumer)
    // ------------------------------------------------------------------

    /**
     * THE SEAT ANCHOR RIDES THE WIRE YAW (updateRiderPositon lines
     * 367-371 — cos/sin of the yaw, the reference's own form over its
     * own atan2 convention): a south-heading hull (yaw −90) seats its
     * rider at (cos(−90°), sin(−90°))·0.4 = (0, −0.4) — the reference's
     * own quadrant arithmetic, copied verbatim.
     */
    @Test
    void theSeatAnchorRidesTheWireYaw() {
        BoatEntity boat = new BoatEntity(9960,
                new Position(0.5, 4.5, 0.5), new Random(7L));
        boat.setRotation(new Rotation(-90.0f, 0.0f));
        Position anchor = boat.seatAnchor(0.0);
        assertEquals(0.5, anchor.x(), 1e-9, "the rider sits on the hull's x");
        assertEquals(0.5 - 0.4, anchor.z(), 1e-9,
                "the rider's offset is the wire yaw's own (cos, sin)·0.4");
    }

    private static void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
