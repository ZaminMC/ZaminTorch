package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Wave VC — THE VEHICLE FAMILY'S OWN COLLISION WALKS (the re-pin's named
 * row, each law read from the reference's own source 1:1):
 * MinecartEntity.tick lines 295-299 — the cart's own gather over its
 * shape grown (0.2, 0.0, 0.2) pushing CARTS ALONE through
 * {@code entity.push(this)}; BoatEntity.tick lines 347-356 — the boat's
 * same gather pushing BOATS ALONE. The pair laws:
 *
 * <p>THE CART-CART BLEND (MinecartEntity.push lines 652-734, the callee
 * the pushed cart and the initiator the walker): the squared-distance
 * axis with the 1.0E-4 floor, the 0.1-then-0.5 scale, THE 0.8
 * DOT-PRODUCT GATE over the callee's own yaw axis (the crosswise pair
 * never touches, however the boxes overlap), THE FURNACE ASYMMETRY
 * (the coal-fed pusher rams — the other body damped to 0.2 and handed
 * the furnace's whole velocity, the furnace keeping 0.95), and THE
 * MOMENTUM BLEND (both damped to 0.2, both handed the halved momentum
 * sum, minus the separation for the callee and plus it for the
 * initiator). NO rider gates on the blend — an occupied cart still
 * takes it, the vanilla quirk.</p>
 *
 * <p>THE BOAT-BOAT PUSH (BoatEntity overriding nothing — Entity.push
 * lines 969-1005): the absMax-sqrt form with the 0.05 scale, and each
 * side's OWN rider gate (a boat with a passenger never moves).</p>
 */
class VehicleFamilyCollisionParityDiscoveryTest {

    /** A dry flat world at y=4 the pure-law seats ride (no rails — the walk never consults them). */
    private static VehicleEntity.WorldQuery dryWorld() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }
        };
    }

    /**
     * Flat ground at y=4 with a north-south rail along x=5 for z in
     * [0, 20): the full-tick seat's own track (the engine's rail-axis
     * stub form — VehicleManagerTest's railWorld shape).
     */
    private static VehicleEntity.WorldQuery northSouthRailWorld() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public int railAxisAt(double x, double y, double z) {
                if (y < 3.9 || y > 5.0) {
                    return -1;
                }
                int cellX = (int) Math.floor(x);
                int cellZ = (int) Math.floor(z);
                if (cellX == 5 && cellZ >= 0 && cellZ < 20) {
                    return 0; // AXIS_Z: the north-south leg
                }
                return -1;
            }
        };
    }

    private static VehicleManager managerIn(VehicleEntity.WorldQuery world, long seed) {
        class NoDrops implements VehicleManager.DropSink {
            @Override public void dropVehicleItem(VehicleEntity vehicle) { }
            @Override public void dropChestMinecartExtras(VehicleEntity vehicle,
                    java.util.List<net.zaminmc.torch.item.ItemStack> contents) { }
        }
        return new VehicleManager(world, new NoDrops(), new Random(seed), 996_000);
    }

    // ------------------------------------------------------------------
    // Seat 1 — the resting pair's momentum blend through the full tick
    // ------------------------------------------------------------------

    /**
     * Two plain carts resting 0.5 apart on a north-south rail (both yaw
     * 0 — the axis the fresh body carries): after the manager's whole
     * tick the pair has separated through the momentum blend, the
     * vanilla doubling included. The exact sequence — A's walk writes
     * A -0.05 / B +0.05; B's own tick then moves B south +0.05 (the
     * sequential-tick shape: the walk's write feeds the later body's
     * move the SAME tick); B's walk blends again over the fresh values:
     * A -0.05*0.2 - 0.05 = -0.06, B 0.05*0.2 + 0.05 = +0.06. RED on
     * the pre-fix engine (the carts never interacted — the velocities
     * stayed exactly zero however crowded).
     */
    @Test
    void theRestingPairBlendsThroughTheFullTick() {
        VehicleManager manager = managerIn(northSouthRailWorld(), 91L);
        MinecartEntity a = (MinecartEntity) manager.spawnMinecart(
                new Position(5.5, 4.06, 10.0), 0);
        MinecartEntity b = (MinecartEntity) manager.spawnMinecart(
                new Position(5.5, 4.06, 10.5), 0);
        assertNotNull(a, "the spawn never cancels in this seat");
        assertNotNull(b, "the spawn never cancels in this seat");

        manager.tick();

        // A's walk then B's walk (the list order = the spawn order):
        // the momentum blend twice, B's own move between them — and B's
        // own move DRAGS its carry before the second blend reads it
        // (applySlowdown ×0.96 lands inside B's rail walk, the gather
        // after it — the reference's own composition).
        assertEquals(-0.061, a.velocityZ(), 1e-9,
                "the trailing-body's arm: -0.05*0.2 + (-0.001 - 0.05)"
                        + " — the second blend over the dragged carry"
                        + " (B's 0.05 damped to 0.048 by its own move)");
        assertEquals(0.0586, b.velocityZ(), 1e-9,
                "the leading-body's arm: 0.048*0.2 + (-0.001 + 0.05)");
        assertEquals(10.0, a.position().z(), 1e-9,
                "A never moved (its tick read the pre-blend zero)");
        assertEquals(10.55, b.position().z(), 1e-9,
                "B moved south +0.05 — A's walk wrote B's velocity"
                        + " before B's own tick read it (the reference's"
                        + " own sequential shape)");
        assertEquals(0.0, a.velocityX(), 1e-9,
                "the blend is horizontal alone");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the 0.8 dot-product gate (the crosswise refusal)
    // ------------------------------------------------------------------

    /**
     * The gate's own discrimination at the SAME geometry: a diagonal
     * pair 0.671 apart, the offset (0.6, 0.3). The gate reads the
     * CALLEE'S OWN YAW — the pushed cart's axis, never the walker's
     * (the reference's {@code this.yaw} inside the callee's own push,
     * line 692): with the CALLEE facing east (yaw -90, the X axis) the
     * normalized offset's |dot| is 0.894 &gt;= 0.8 — the pair blends
     * (the single walk's exact law, the WALKER left at yaw 0 proving
     * its yaw never enters); with the callee at yaw 0 (the Z axis) the
     * |dot| is 0.447 &lt; 0.8 — the pair NEVER TOUCHES, both walks
     * driven, however the boxes overlap. The reference's axis form
     * (cos(yaw), sin(yaw)) over the atan2 convention and the engine's
     * look axis over the look convention differ by a negation that the
     * absolute value folds — the seat pins the verdict, never the
     * convention.
     */
    @Test
    void theDotGateRefusesTheCrosswisePair() {
        // The aligned arm: the CALLEE's axis X, the offset within ~27
        // degrees of it — the single walk's exact blend. The walker's
        // own yaw stays 0: the gate never consults it.
        VehicleManager aligned = managerIn(dryWorld(), 92L);
        MinecartEntity p = (MinecartEntity) aligned.spawnMinecart(
                new Position(10.0, 4.06, 10.0), 0);
        MinecartEntity q = (MinecartEntity) aligned.spawnMinecart(
                new Position(10.6, 4.06, 10.3), 0);
        q.setRotation(new Rotation(-90.0f, 0.0f)); // the CALLEE faces east

        aligned.tickVehicleCollisions(p);

        // The single-call law: d0 = -0.6/sqrt(0.45), d1 = -0.3/sqrt(0.45),
        // capped at 1.0, scaled 0.1 then 0.5; both resting, the average
        // zero — the callee MINUS the separation, the initiator PLUS it.
        double dx = 0.6 / Math.sqrt(0.45) * 0.05;
        double dz = 0.3 / Math.sqrt(0.45) * 0.05;
        assertEquals(dx, q.velocityX(), 1e-9,
                "the callee's away arm on the offset's x component");
        assertEquals(dz, q.velocityZ(), 1e-9,
                "the callee's away arm on the offset's z component");
        assertEquals(-dx, p.velocityX(), 1e-9,
                "the initiator's away arm (the momentum blend's plus)");
        assertEquals(-dz, p.velocityZ(), 1e-9,
                "the initiator's away arm on z");

        // The refused arm: the same geometry, the CALLEE's axis Z — the
        // |dot| 0.447 < 0.8, the return whole. BOTH walks driven.
        VehicleManager crosswise = managerIn(dryWorld(), 93L);
        MinecartEntity s = (MinecartEntity) crosswise.spawnMinecart(
                new Position(10.0, 4.06, 10.0), 0);
        MinecartEntity t = (MinecartEntity) crosswise.spawnMinecart(
                new Position(10.6, 4.06, 10.3), 0);
        // both yaw 0 (the fresh bodies' own axis): the callee's Z axis

        crosswise.tickVehicleCollisions(s);
        crosswise.tickVehicleCollisions(t);

        assertEquals(0.0, s.velocityX(), 1e-9,
                "the crosswise pair never touches (the gate's return)");
        assertEquals(0.0, s.velocityZ(), 1e-9,
                "the crosswise pair never touches on z either");
        assertEquals(0.0, t.velocityX(), 1e-9,
                "the candidate stays whole");
        assertEquals(0.0, t.velocityZ(), 1e-9,
                "the candidate stays whole on z");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the furnace asymmetry (both arms)
    // ------------------------------------------------------------------

    /**
     * The coal-fed pusher never blends. THE FURNACE INITIATOR (lines
     * 702-708): a furnace cart at +0.3 ramming a plain cart at +0.05,
     * 0.5 ahead on the same axis — the plain cart damped to 0.2 then
     * handed the furnace's WHOLE velocity minus the separation
     * (0.05*0.2 + 0.3 + 0.05 = 0.36 — FASTER than the furnace itself),
     * the furnace keeping 0.95 (0.285). THE FURNACE CALLEE (lines
     * 709-715): a plain cart at +0.3 running into a furnace cart at
     * +0.05 — the plain cart damped to 0.2 then handed the furnace's
     * whole velocity plus the separation (0.3*0.2 + 0.05 - 0.05 = 0.06
     * — STOPPED DEAD), the furnace keeping 0.95 (0.0475 — the wall
     * stands). RED on the pre-fix engine (neither pair ever
     * interacted).
     */
    @Test
    void theFurnaceAsymmetryRamsAndStands() {
        // The furnace initiator.
        VehicleManager ramming = managerIn(dryWorld(), 94L);
        MinecartEntity furnace = (MinecartEntity) ramming.spawnFurnaceMinecart(
                new Position(10.0, 4.06, 5.5), 0);
        MinecartEntity plain = (MinecartEntity) ramming.spawnMinecart(
                new Position(10.5, 4.06, 5.5), 0);
        furnace.setRotation(new Rotation(-90.0f, 0.0f));
        plain.setRotation(new Rotation(-90.0f, 0.0f));
        furnace.setVelocity(0.3, 0.0, 0.0);
        plain.setVelocity(0.05, 0.0, 0.0);

        ramming.tickVehicleCollisions(furnace);

        assertEquals(0.36, plain.velocityX(), 1e-9,
                "the rammed cart: 0.05*0.2 + (0.3 + 0.05) — handed the"
                        + " furnace's whole velocity, minus the separation");
        assertEquals(0.285, furnace.velocityX(), 1e-9,
                "the furnace keeps 0.95 (the coal-fed pusher barely"
                        + " slows)");
        assertEquals(0.0, plain.velocityZ(), 1e-9,
                "the ram is horizontal alone");

        // The furnace callee.
        VehicleManager wall = managerIn(dryWorld(), 95L);
        MinecartEntity runner = (MinecartEntity) wall.spawnMinecart(
                new Position(10.0, 4.06, 5.5), 0);
        MinecartEntity stood = (MinecartEntity) wall.spawnFurnaceMinecart(
                new Position(10.5, 4.06, 5.5), 0);
        runner.setRotation(new Rotation(-90.0f, 0.0f));
        stood.setRotation(new Rotation(-90.0f, 0.0f));
        runner.setVelocity(0.3, 0.0, 0.0);
        stood.setVelocity(0.05, 0.0, 0.0);

        wall.tickVehicleCollisions(runner);

        assertEquals(0.06, runner.velocityX(), 1e-9,
                "the runner: 0.3*0.2 + (0.05 - 0.05) — stopped dead at"
                        + " the furnace wall");
        assertEquals(0.0475, stood.velocityX(), 1e-9,
                "the furnace wall stands (0.05 * 0.95)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — the boat-boat mutual push and the passenger gate
    // ------------------------------------------------------------------

    /**
     * Two boats 0.2 apart: Entity.push's ordinary mutual law — the
     * absMax-sqrt normalization (the vector NOT unit after it: the
     * pure-axis 0.2 offset gives the sqrt(0.2)*0.05 impulse), both
     * walks driven, the vanilla doubling (2*sqrt(0.2)*0.05 each way).
     * THE PASSENGER GATE (lines 995-1001): a boat with a passenger
     * never moves — the free boat carries both arms, the occupied one
     * stays whole. RED on the pre-fix engine (the boats never
     * interacted).
     */
    @Test
    void theBoatsPushMutuallyAndThePassengerGates() {
        VehicleManager manager = managerIn(dryWorld(), 96L);
        BoatEntity a = (BoatEntity) manager.spawnBoat(
                new Position(0.5, 5.0, 0.5), 0);
        BoatEntity b = (BoatEntity) manager.spawnBoat(
                new Position(0.7, 5.0, 0.5), 0);

        manager.tickVehicleCollisions(a);
        manager.tickVehicleCollisions(b);

        double impulse = Math.sqrt(0.2) * 0.05;
        assertEquals(-2.0 * impulse, a.velocityX(), 1e-9,
                "the free pair doubles (both walks, the vanilla shape)");
        assertEquals(2.0 * impulse, b.velocityX(), 1e-9,
                "the pushed boat's own away arm each walk");
        assertEquals(0.0, a.velocityZ(), 1e-9,
                "the push is horizontal alone");

        // The passenger gate: the occupied boat never moves.
        VehicleManager gated = managerIn(dryWorld(), 97L);
        BoatEntity free = (BoatEntity) gated.spawnBoat(
                new Position(0.5, 5.0, 0.5), 0);
        BoatEntity occupied = (BoatEntity) gated.spawnBoat(
                new Position(0.7, 5.0, 0.5), 0);
        occupied.mount(700_001);

        gated.tickVehicleCollisions(free);
        gated.tickVehicleCollisions(occupied);

        assertEquals(-2.0 * impulse, free.velocityX(), 1e-9,
                "the free boat carries both arms (its own walk's"
                        + " initiator arm + the occupied boat's walk's"
                        + " callee arm)");
        assertEquals(0.0, occupied.velocityX(), 1e-9,
                "a boat with a passenger never moves out from under it");
    }

    // ------------------------------------------------------------------
    // Seat 5 — the family membership and the dimension band
    // ------------------------------------------------------------------

    /**
     * The gather's own membership gates (the instanceof consults —
     * MinecartEntity.tick line 296 / BoatEntity.tick line 352): a cart
     * and a boat overlapping NEVER interact (the cart's gather carries
     * carts alone, the boat's boats alone) — in the reference the
     * families' own walks never cross, the living gather's PX arm the
     * only bridge. And the dimension band (each vanilla World is one
     * dimension): two carts at the same coordinates in DIFFERENT
     * dimensions never touch.
     */
    @Test
    void theFamilyMembershipAndDimensionBandsHold() {
        VehicleManager manager = managerIn(dryWorld(), 98L);
        MinecartEntity cart = (MinecartEntity) manager.spawnMinecart(
                new Position(10.0, 4.06, 5.5), 0);
        BoatEntity boat = (BoatEntity) manager.spawnBoat(
                new Position(10.2, 4.06, 5.5), 0);
        cart.setRotation(new Rotation(-90.0f, 0.0f));
        cart.setVelocity(0.3, 0.0, 0.0);

        manager.tickVehicleCollisions(cart);
        manager.tickVehicleCollisions(boat);

        assertEquals(0.3, cart.velocityX(), 1e-9,
                "the cart's walk never carries the boat (the instanceof"
                        + " membership gate)");
        assertEquals(0.0, boat.velocityX(), 1e-9,
                "the boat's walk never carries the cart either");

        VehicleManager split = managerIn(dryWorld(), 99L);
        MinecartEntity overworld = (MinecartEntity) split.spawnMinecart(
                new Position(10.0, 4.06, 5.5), 0);
        MinecartEntity nether = (MinecartEntity) split.spawnMinecart(
                new Position(10.5, 4.06, 5.5), 1);
        overworld.setRotation(new Rotation(-90.0f, 0.0f));
        nether.setRotation(new Rotation(-90.0f, 0.0f));
        overworld.setVelocity(0.3, 0.0, 0.0);
        nether.setVelocity(0.05, 0.0, 0.0);

        split.tickVehicleCollisions(overworld);
        split.tickVehicleCollisions(nether);

        assertEquals(0.3, overworld.velocityX(), 1e-9,
                "the overworld cart never blends across the dimension"
                        + " band");
        assertEquals(0.05, nether.velocityX(), 1e-9,
                "the nether cart stays whole at the same coordinates");
    }

    // ------------------------------------------------------------------
    // Seat 6 — the occupied cart still blends (no rider gates)
    // ------------------------------------------------------------------

    /**
     * The cart law's own quirk vs the boat's: the reference's blend
     * arms write both bodies' velocities OUTRIGHT (lines 719-724 — no
     * rider consult anywhere in the wholesale override; the only rider
     * gate is line 656's pusher-not-the-passenger form, vacuous for a
     * cart pair) — an OCCUPIED cart still takes the blend. A resting
     * plain cart 0.5 behind an occupied one, both on the X axis: the
     * single walk writes both ±0.05. RED on the pre-fix engine (no
     * walk at all).
     */
    @Test
    void theOccupiedCartStillBlends() {
        VehicleManager manager = managerIn(dryWorld(), 100L);
        MinecartEntity walker = (MinecartEntity) manager.spawnMinecart(
                new Position(10.0, 4.06, 5.5), 0);
        MinecartEntity occupied = (MinecartEntity) manager.spawnMinecart(
                new Position(10.5, 4.06, 5.5), 0);
        walker.setRotation(new Rotation(-90.0f, 0.0f));
        occupied.setRotation(new Rotation(-90.0f, 0.0f));
        occupied.mount(700_002);

        manager.tickVehicleCollisions(walker);

        assertEquals(-0.05, walker.velocityX(), 1e-9,
                "the initiator's away arm (the momentum blend's plus)");
        assertEquals(0.05, occupied.velocityX(), 1e-9,
                "the OCCUPIED cart still takes the blend — no rider"
                        + " gates on the cart law (the vanilla quirk the"
                        + " boat's gates refuse)");
    }
}
