package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.entity.vehicle.BoatEntity;
import net.zaminmc.torch.server.entity.vehicle.VehicleEntity;
import net.zaminmc.torch.server.entity.vehicle.VehicleManager;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave SM — THE SEATED-MOB MODEL + THE AUTO-MOUNT (the re-pin's held tail
 * LANDED, each law read from the reference's own source 1:1):
 * MinecartEntity.push lines 657-665 — a MOVING RIDEABLE EMPTY cart
 * scooping a non-player non-golem living body that walks FREE onto the
 * seat ({@code entity.startRiding(this)}, line 664); the push math then
 * running ANYWAY (the reference's own order — the consult no early
 * return). The seated model the makesSteps gate's javadoc named absent:
 * the seat row on the body (the reference's {@code this.vehicle}),
 * stamped by the manager from the vehicles' own passenger seats; the
 * MOVEMENT FREEZE (the seat owns the position — the reference's
 * apply-then-overwrite shape adapted to the engine's immediate-publish
 * model); the makesSteps gate (Entity line 715's
 * {@code vehicle == null}); the line-656/970 rider gates (the seated
 * body never pushes its own mount); the dismount-on-break (the seat
 * opens with the vehicle's death).
 */
class SeatedMobAutoMountParityDiscoveryTest {

    /** A flat-world stub carrying the seats' own consults. */
    private static final class SeatWorld implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean inLava(double x, double y, double z) {
            return false;
        }
    }

    private static MobManager mobManagerIn(SeatWorld world, long seed) {
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        return new MobManager(world, new Random(seed), new NoLoot(),
                996_000, (x, z) -> 8);
    }

    private static VehicleManager vehicleManagerIn(long seed) {
        class NoDrops implements VehicleManager.DropSink {
            @Override public void dropVehicleItem(VehicleEntity vehicle) { }
            @Override public void dropChestMinecartExtras(VehicleEntity vehicle,
                    java.util.List<net.zaminmc.torch.item.ItemStack> contents) { }
        }
        return new VehicleManager(new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }
        }, new NoDrops(), new Random(seed), 997_000);
    }

    /** The pinned body (NoAI): the mind and the movement stand still, but
     *  the push walk still runs — the scoop consult rides the walk. */
    private static MobEntity pinned(MobManager manager, MobType type, Position at) {
        MobEntity mob = manager.spawnAt(type, at);
        assert mob != null : "the spawn never cancels in this seat";
        mob.setNoAi(true);
        return mob;
    }

    // ------------------------------------------------------------------
    // Seat 1 — the scoop (the AUTO-MOUNT consult whole)
    // ------------------------------------------------------------------

    /**
     * A cow crowding a MOVING RIDEABLE EMPTY cart takes the seat —
     * MinecartEntity.push lines 657-665, the six conjuncts whole: the
     * pusher living (the mob walk's membership), no golem, the cart
     * RIDEABLE, the cart moving ({@code vx*vz-sum > 0.01}), the seat
     * empty, the body walking free. The push math then runs anyway (the
     * vanilla order — the freshly-seated rider still separates from its
     * mount that tick). RED on the pre-fix engine (the consult absent —
     * the cart's seat stayed empty however crowded).
     */
    @Test
    void theMovingRideableCartScoopsTheCrowdingMob() {
        SeatWorld world = new SeatWorld();
        MobManager mobs = mobManagerIn(world, 101L);
        VehicleManager vehicles = vehicleManagerIn(102L);
        VehicleEntity cart = vehicles.spawnMinecart(
                new Position(0.5, 8.0, 0.5), 0);
        assertNotNull(cart, "the spawn never cancels in this seat");
        cart.setVelocity(0.2, 0.0, 0.0); // the cart MOVING (line 661)
        mobs.setVehicleSource(vehicles::all);
        mobs.setVehicleMount(vehicles::mount);
        MobEntity cow = pinned(mobs, MobType.COW,
                new Position(0.7, 8.0, 0.5)); // crowding the cart's box

        mobs.tick(List.of(), 6000L);

        assertTrue(cart.hasPassenger(), "the scoop took the seat (line 664)");
        assertEquals(cow.entityId(), cart.passengerId(),
                "the seat's pairing is the crowding body");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the six gates' refusals
    // ------------------------------------------------------------------

    /**
     * The consult's own refusals, each the reference's conjunct: the
     * CHEST cart never scoops (line 660's RIDEABLE-alone gate — the
     * cargo variants are containers, never seats); the STATIONARY cart
     * never scoops (line 661's {@code > 0.01} — however crowded); the
     * OCCUPIED cart never scoops (line 662's empty-seat gate); the GOLEM
     * never rides (line 659's exclusion — the village's guard walks,
     * never rides); the SEATED body never re-scoops (line 663's
     * {@code entity.vehicle == null}).
     */
    @Test
    void theConsultsRefusalsHold() {
        // The chest cart: RIDEABLE alone.
        SeatWorld world = new SeatWorld();
        MobManager mobs = mobManagerIn(world, 103L);
        VehicleManager vehicles = vehicleManagerIn(104L);
        VehicleEntity chest = vehicles.spawnChestMinecart(
                new Position(0.5, 8.0, 0.5), 0);
        chest.setVelocity(0.2, 0.0, 0.0);
        mobs.setVehicleSource(vehicles::all);
        mobs.setVehicleMount(vehicles::mount);
        pinned(mobs, MobType.COW, new Position(0.7, 8.0, 0.5));
        mobs.tick(List.of(), 6000L);
        assertFalse(chest.hasPassenger(),
                "the chest cart is a container, never a seat (line 660)");

        // The stationary cart: the moving gate.
        MobManager still = mobManagerIn(world, 105L);
        VehicleManager stillVehicles = vehicleManagerIn(106L);
        VehicleEntity parked = stillVehicles.spawnMinecart(
                new Position(0.5, 8.0, 0.5), 0);
        still.setVehicleSource(stillVehicles::all);
        still.setVehicleMount(stillVehicles::mount);
        pinned(still, MobType.COW, new Position(0.7, 8.0, 0.5));
        still.tick(List.of(), 6000L);
        assertFalse(parked.hasPassenger(),
                "a standing cart never scoops, however crowded (line 661)");

        // The occupied cart: the empty-seat gate.
        MobManager shared = mobManagerIn(world, 107L);
        VehicleManager sharedVehicles = vehicleManagerIn(108L);
        VehicleEntity taken = sharedVehicles.spawnMinecart(
                new Position(0.5, 8.0, 0.5), 0);
        taken.setVelocity(0.2, 0.0, 0.0);
        assertTrue(taken.mount(888_001), "the seat's first taker");
        shared.setVehicleSource(sharedVehicles::all);
        shared.setVehicleMount(sharedVehicles::mount);
        pinned(shared, MobType.COW, new Position(0.7, 8.0, 0.5));
        shared.tick(List.of(), 6000L);
        assertEquals(888_001, taken.passengerId(),
                "the occupied seat never re-scoops (line 662)");

        // The golem: the exclusion.
        MobManager guarded = mobManagerIn(world, 109L);
        VehicleManager golemVehicles = vehicleManagerIn(110L);
        VehicleEntity carrier = golemVehicles.spawnMinecart(
                new Position(0.5, 8.0, 0.5), 0);
        carrier.setVelocity(0.2, 0.0, 0.0);
        guarded.setVehicleSource(golemVehicles::all);
        guarded.setVehicleMount(golemVehicles::mount);
        pinned(guarded, MobType.IRON_GOLEM, new Position(0.7, 8.0, 0.5));
        guarded.tick(List.of(), 6000L);
        assertFalse(carrier.hasPassenger(),
                "the golem never rides (line 659)");

        // The seated body: the walking-free gate.
        MobManager riding = mobManagerIn(world, 111L);
        VehicleManager ridingVehicles = vehicleManagerIn(112L);
        VehicleEntity first = ridingVehicles.spawnMinecart(
                new Position(10.5, 8.0, 10.5), 0);
        first.setVelocity(0.2, 0.0, 0.0);
        VehicleEntity second = ridingVehicles.spawnMinecart(
                new Position(12.5, 8.0, 10.5), 0);
        second.setVelocity(0.2, 0.0, 0.0);
        riding.setVehicleSource(ridingVehicles::all);
        riding.setVehicleMount(ridingVehicles::mount);
        MobEntity passenger = pinned(riding, MobType.COW,
                new Position(10.7, 8.0, 10.5));
        riding.tick(List.of(), 6000L); // the scoop takes the first cart
        assertTrue(first.passengerId() == passenger.entityId(),
                "the first cart took the body");
        // The body now seated beside the second moving cart: no re-scoop.
        second.setPosition(new Position(10.6, 8.0, 10.5)); // crowding the seat
        riding.tick(List.of(), 6000L);
        assertFalse(second.hasPassenger(),
                "a body holding a seat never re-scoops (line 663)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the movement freeze (the seat owns the position)
    // ------------------------------------------------------------------

    /**
     * The seated body's own walk integrates NO motion — the reference's
     * apply-then-overwrite shape (the mob's move applies, the vehicle's
     * updateRiderPositon overwrites) adapted to the engine's
     * immediate-publish model: the freeze holds the body at the anchor.
     * The seat's own proof: a SEATED cow held midair NEVER FALLS — the
     * gravity arm never runs for the seat (the pre-fix engine's cow, its
     * seat row absent, fell through). The body rides UNPINNED (the
     * NoAI pin's own midair freeze would hold any body — the fall is
     * the freeze's own proof, never the pin's); the stamp itself is the
     * manager's per-tick write (the second tick's consult state; the
     * same-tick mount rides the seat-follow's anchor whole).
     */
    @Test
    void theSeatedBodyNeverFalls() {
        SeatWorld world = new SeatWorld();
        MobManager mobs = mobManagerIn(world, 113L);
        VehicleManager vehicles = vehicleManagerIn(114L);
        VehicleEntity cart = vehicles.spawnMinecart(
                new Position(0.5, 12.0, 0.5), 0);
        mobs.setVehicleSource(vehicles::all);
        mobs.setVehicleMount(vehicles::mount);
        MobEntity cow = mobs.spawnAt(MobType.COW,
                new Position(0.7, 12.0, 0.5));
        assertNotNull(cow, "the spawn never cancels in this seat");
        assertTrue(cart.mount(cow.entityId()), "the seat taken");
        cart.setVelocity(0.2, 0.0, 0.0); // the cart moving under the seat

        // The first tick stamps the seat row; the freeze holds from it.
        mobs.tick(List.of(), 6000L);
        mobs.tick(List.of(), 6000L);
        mobs.tick(List.of(), 6000L);

        assertEquals(12.0, cow.position().y(), 1e-9,
                "the seated body never falls — the seat owns the position"
                        + " whole (no gravity, no walk, no ground snap)");
        assertTrue(cow.seatedInVehicle(),
                "the stamp wrote the seat row (the body's derived consult"
                        + " state)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — the rider gates (the seated body never pushes its mount)
    // ------------------------------------------------------------------

    /**
     * The line-656 gate (MinecartEntity.push: {@code entity !=
     * this.rider}) and the boat's line-970 vehicle arm — REAL since this
     * wave: a body holding a mount's seat never pushes that mount,
     * however crowded the overlap. A seated cow's walk leaves its own
     * cart's velocity EXACTLY as the rail model wrote it (the stationary
     * cart: exactly zero), and the seated boat's hull the same.
     */
    @Test
    void theSeatedBodyNeverPushesItsOwnMount() {
        SeatWorld world = new SeatWorld();
        MobManager mobs = mobManagerIn(world, 115L);
        VehicleManager vehicles = vehicleManagerIn(116L);
        VehicleEntity cart = vehicles.spawnMinecart(
                new Position(0.5, 8.0, 0.5), 0);
        mobs.setVehicleSource(vehicles::all);
        mobs.setVehicleMount(vehicles::mount);
        MobEntity cow = pinned(mobs, MobType.COW,
                new Position(0.6, 8.0, 0.5));
        assertTrue(cart.mount(cow.entityId()), "the seat taken");

        mobs.tick(List.of(), 6000L);
        mobs.tick(List.of(), 6000L);

        assertEquals(0.0, cart.velocityX(), 1e-9,
                "the seated body never pushes its own cart (line 656)");
        assertEquals(0.0, cart.velocityZ(), 1e-9,
                "the push is refused whole, both axes");

        // The boat's own gate (Entity.push line 970's vehicle arm).
        MobManager boatMobs = mobManagerIn(world, 117L);
        VehicleManager boatVehicles = vehicleManagerIn(118L);
        BoatEntity boat = (BoatEntity) boatVehicles.spawnBoat(
                new Position(0.5, 8.0, 0.5), 0);
        boatMobs.setVehicleSource(boatVehicles::all);
        boatMobs.setVehicleMount(boatVehicles::mount);
        MobEntity sailor = pinned(boatMobs, MobType.COW,
                new Position(0.6, 8.0, 0.5));
        assertTrue(boat.mount(sailor.entityId()), "the hull's seat taken");

        boatMobs.tick(List.of(), 6000L);
        boatMobs.tick(List.of(), 6000L);

        assertEquals(0.0, boat.velocityX(), 1e-9,
                "the seated body never shoves its own hull (line 970)");
    }

    // ------------------------------------------------------------------
    // Seat 5 — the dismount-on-break (the seat opens with the death)
    // ------------------------------------------------------------------

    /**
     * The vehicle's death opens the seat (VehicleManager's own removal
     * walk — the reference's dropRiders arm): a broken cart's passenger
     * walks free again — the next tick's stamp clears the seat row and
     * the body's own movement returns (the midair cow falls once more).
     * The body rides UNPINNED here (the pin's own midair freeze would
     * hold any body — the fall is the freeze's own proof, never the
     * pin's).
     */
    @Test
    void theBrokenVehiclesSeatOpens() {
        SeatWorld world = new SeatWorld();
        MobManager mobs = mobManagerIn(world, 119L);
        VehicleManager vehicles = vehicleManagerIn(120L);
        VehicleEntity cart = vehicles.spawnMinecart(
                new Position(0.5, 12.0, 0.5), 0);
        mobs.setVehicleSource(vehicles::all);
        mobs.setVehicleMount(vehicles::mount);
        MobEntity cow = mobs.spawnAt(MobType.COW,
                new Position(0.7, 12.0, 0.5));
        assertNotNull(cow, "the spawn never cancels in this seat");
        assertTrue(cart.mount(cow.entityId()), "the seat taken");

        // The seat holds while the cart lives — the freeze's own proof
        // (the unpinned body midair never falls while the seat stands).
        mobs.tick(List.of(), 6000L);
        mobs.tick(List.of(), 6000L);
        assertTrue(cow.seatedInVehicle(), "the seat row stamped");
        assertEquals(12.0, cow.position().y(), 1e-9,
                "the seated body holds midair (the freeze, never the pin)");

        // The cart breaks: the removal walk dismounts.
        vehicles.hurt(cart, 1000.0f);
        vehicles.tick();
        assertFalse(cart.hasPassenger(),
                "the broken vehicle's seat opened (the removal walk)");

        // The body walks free again — its own movement returns.
        mobs.tick(List.of(), 6000L);
        assertFalse(cow.seatedInVehicle(),
                "the stamp cleared with the vehicle's departure");
        assertTrue(cow.position().y() < 12.0,
                "the free body falls once more (the freeze lifted)");
    }
}
