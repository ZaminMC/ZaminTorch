package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The riding follow surface (the §22 demand-named row — the reference's
 * updateRiderPositon, Entity.java line 1353, carried at the engine's
 * follow arms): a seated body's position lands at the seat law every
 * tick — the mount's own height plus the rider's per-family offset —
 * never the mount's flat origin. The per-tick order mirrors the
 * reference's rideTick (Entity.java lines 1297-1349): the rider's own
 * walk first (the mobs section), then the seat re-anchor (the
 * vehicleRiders/mobRiders sections) — so whatever the rider's AI did
 * that tick, the body ends the tick AT the anchor.
 *
 * <p>The assertions check the LAW's numbers directly (the deltas against
 * the mount's live position), not the engine's own anchor formula — the
 * cart family's flush 0.0 (MinecartEntity line 123), the boat's -0.3
 * with its 0.4 forward offset (BoatEntity lines 79 and 367-371), the
 * pig's 0.55 seat, and the rider table (RiderSeatLaw: the adult zombie
 * and skeleton families at -0.35, the silverfish at +0.2, players at
 * -0.35 — the reference's getRideHeight table).</p>
 */
class RidingFollowTest {

    @TempDir
    Path dataDir;

    net.zaminmc.torch.server.EngineServer server;
    net.zaminmc.torch.server.player.PlayerSession rider;
    Position ground;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "rf" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new net.zaminmc.torch.server.EngineServer(config);
        server.start();
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(new SilentLink(), "Rider",
                        UUID.nameUUIDFromBytes("OfflinePlayer:Rider".getBytes()));
        rider = accepted.session();
        server.ticker().submit(rider::markPlaying);
        await(() -> rider.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, "playing");
        // The join anchor is the engine's own safe-standing spot: the
        // scene entities ride two blocks east of it (inside the generated
        // spawn chunk).
        ground = new Position(rider.position().x() + 2.0,
                rider.position().y(), rider.position().z());
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    @Test
    void aSeatedZombieFollowsTheCartSeatLaw() throws Exception {
        var cart = spawnCart();
        var zombie = seatZombieOn(cart);
        long from = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= from + 3, "three follow ticks");
        // The cart family's own law (MinecartEntity.getMountHeight line
        // 123: 0.0) plus the adult zombie's -0.35 (ZombieEntity line 584):
        // the body rides flush above the cart's floor level, X/Z pinned
        // to the cart's center.
        assertEquals(cart.position().x(), zombie.position().x(), 1e-9,
                "the X follows the cart's center");
        assertEquals(cart.position().z(), zombie.position().z(), 1e-9,
                "the Z follows the cart's center");
        assertEquals(cart.position().y() - 0.35, zombie.position().y(), 1e-9,
                "the Y rides the cart's flush 0.0 + the zombie's -0.35");
    }

    @Test
    void aSeatedZombieFollowsTheBoatSeatLaw() throws Exception {
        final net.zaminmc.torch.server.entity.vehicle.VehicleEntity[] boatRef =
                new net.zaminmc.torch.server.entity.vehicle.VehicleEntity[1];
        final net.zaminmc.torch.server.entity.MobEntity[] zombieRef =
                new net.zaminmc.torch.server.entity.MobEntity[1];
        server.ticker().submit(() -> {
            var boat = server.vehicles().spawnBoat(ground);
            // A known heading: yaw 90 degrees puts the seat 0.4 south of
            // the hull's center (cos/sin of the yaw in degrees — the
            // reference's own trig, BoatEntity lines 367-371).
            boat.setRotation(new Rotation(90.0f, 0.0f));
            var zombie = server.mobs().spawnAt(MobType.ZOMBIE, ground);
            server.vehicles().mount(boat, zombie.entityId());
            boatRef[0] = boat;
            zombieRef[0] = zombie;
        });
        await(() -> boatRef[0] != null && zombieRef[0] != null, "the boat scene spawned");
        var boat = boatRef[0];
        var zombie = zombieRef[0];
        long from = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= from + 3, "three follow ticks");
        // The boat's own law (BoatEntity.getMountHeight line 79: -0.3, the
        // 0.4 forward offset at yaw 90): X pinned, Z +0.4 forward, Y at
        // -0.3 - 0.35 (the hull seat plus the zombie family's offset).
        assertEquals(boat.position().x(), zombie.position().x(), 1e-9,
                "the X stays at the hull's center (cos 90 = 0)");
        assertEquals(boat.position().z() + 0.4, zombie.position().z(), 1e-9,
                "the Z rides 0.4 forward of the heading (sin 90 = 1)");
        assertEquals(boat.position().y() - 0.65, zombie.position().y(), 1e-9,
                "the Y rides the hull's -0.3 plus the zombie's -0.35");
    }

    @Test
    void aSeatedMobFollowsTheMobSeatLaw() throws Exception {
        var pig = server.mobs().spawnAt(MobType.PIG, ground);
        var zombie = server.mobs().spawnAt(MobType.ZOMBIE, ground);
        server.ticker().submit(() ->
                server.mobs().seatImportedRider(pig, zombie.entityId()));
        long from = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= from + 3, "three follow ticks");
        // The pig's own 0.55 seat plus the zombie family's -0.35: the
        // body rides +0.20 over the pig's feet, X/Z pinned to the mount.
        assertEquals(pig.position().x(), zombie.position().x(), 1e-9,
                "the X follows the pig's center");
        assertEquals(pig.position().z(), zombie.position().z(), 1e-9,
                "the Z follows the pig's center");
        assertEquals(pig.position().y() + 0.20, zombie.position().y(), 1e-9,
                "the Y rides the pig's 0.55 seat plus the zombie's -0.35");
    }

    @Test
    void aBabyZombieSitsFlushAndASilverfishRidesHigh() throws Exception {
        var cart = spawnCart();
        var baby = server.mobs().spawnAt(MobType.ZOMBIE, ground);
        var silverfish = server.mobs().spawnAt(MobType.SILVERFISH, ground);
        server.ticker().submit(() -> {
            // The baby branch (ZombieEntity line 584's isBaby consult):
            // a negative breeding age makes the body a child — a child
            // rider sits flush (0.0), the reference's own table.
            baby.setBreedingAge(-24000);
            server.vehicles().mount(cart, baby.entityId());
        });
        long from = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= from + 3, "three follow ticks");
        assertEquals(cart.position().y(), baby.position().y(), 1e-9,
                "the baby sits flush (the isBaby branch's 0.0)");
        // The silverfish's own row (SilverfishEntity line 39: +0.2): swap
        // the seat and check the high ride.
        server.ticker().submit(() -> {
            server.vehicles().dismount(cart);
            server.vehicles().mount(cart, silverfish.entityId());
        });
        long next = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= next + 3, "three follow ticks");
        assertEquals(cart.position().y() + 0.2, silverfish.position().y(), 1e-9,
                "the silverfish rides +0.2 (its own table row)");
        assertNotEquals(cart.position().y(), silverfish.position().y(), 1e-9,
                "the high ride is a real offset, not the flush band");
    }

    @Test
    void aSeatedPlayerRidesTheAnchorNotTheFlatOrigin() throws Exception {
        var cart = spawnCart();
        server.ticker().submit(() ->
                server.vehicles().mount(cart, rider.engineEntityId()));
        long from = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= from + 3, "three follow ticks");
        // The player's own row (PlayerEntity line 937: -0.35): the session
        // rider's body rides the cart's anchor — the anchor repair (the
        // old arm wrote the flat vehicle origin, a full 0.35 high).
        assertEquals(cart.position().x(), rider.position().x(), 1e-9,
                "the X follows the cart's center");
        assertEquals(cart.position().z(), rider.position().z(), 1e-9,
                "the Z follows the cart's center");
        assertEquals(cart.position().y() - 0.35, rider.position().y(), 1e-9,
                "the Y rides the flush 0.0 + the player's -0.35");
        // The dismount: the ex-rider's body lands at the engine's
        // exit-beside convention (the dispatch's new mob arm carries the
        // same law for mob riders; the session arm is the pre-existing
        // surface).
        server.ticker().submit(() -> server.vehicles().dismount(cart));
        long dis = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= dis + 3, "the dismount ticks");
        assertNotEquals(cart.position().y() - 0.35, rider.position().y(), 1e-9,
                "the ex-rider left the anchor");
    }

    @Test
    void theSeatedMobKeepsItsSeatAcrossItsOwnAiWalk() throws Exception {
        var cart = spawnCart();
        var zombie = seatZombieOn(cart);
        long from = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= from + 3, "three follow ticks");
        assertEquals(cart.position().y() - 0.35, zombie.position().y(), 1e-9,
                "the first band holds");
        // Ten more ticks: the mobs section (the rider's own walk) runs
        // BEFORE the seat re-anchor every tick — the reference's rideTick
        // order — so the body ends every tick AT the anchor whatever the
        // walk did in between.
        await(() -> server.world().totalTicks() >= from + 13, "ten more ticks");
        assertEquals(cart.position().y() - 0.35, zombie.position().y(), 1e-9,
                "the band holds across the rider's own AI walk");
    }

    // ------------------------------------------------------------- the helpers

    private net.zaminmc.torch.server.entity.vehicle.VehicleEntity spawnCart() {
        var cart = server.vehicles().spawnMinecart(ground);
        awaitCart(cart);
        return cart;
    }

    private void awaitCart(net.zaminmc.torch.server.entity.vehicle.VehicleEntity cart) {
        try {
            await(() -> server.vehicles().all().stream()
                            .anyMatch(v -> v.entityId() == cart.entityId()),
                    "the cart spawned");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted awaiting the cart", e);
        }
    }

    private net.zaminmc.torch.server.entity.MobEntity seatZombieOn(
            net.zaminmc.torch.server.entity.vehicle.VehicleEntity mount) {
        var zombie = server.mobs().spawnAt(MobType.ZOMBIE, ground);
        server.ticker().submit(() -> server.vehicles().mount(mount, zombie.entityId()));
        return zombie;
    }

    private static final class SilentLink implements net.zaminmc.torch.server.net.ClientLink {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
