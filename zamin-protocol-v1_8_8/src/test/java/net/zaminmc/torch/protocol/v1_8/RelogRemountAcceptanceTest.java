package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-016 — the relog-while-riding boundary, pinned over the real wire: a
 * player who quits riding a boat/minecart/pig and rejoins remounts at the
 * VEHICLE'S position, exactly like the reference's restore-from-NBT walk.
 *
 * <p><b>The reference's law</b> (reference/1.8.8): the player's .dat carries
 * the "Riding" row (Entity.writeNbtWithoutId, Entity.java lines 1134-1139),
 * and the join walk consumes it AFTER the whole join burst — PlayerManager.
 * onLogin lines 157-165 re-creates the mount and seats the player; the
 * remount's own wire sequence is ServerPlayerEntity.startRiding lines
 * 516-523: the Attach Entity packet, then the teleport to the seat.</p>
 *
 * <p><b>Torch's shape</b>: the quit path opens the seat (the vehicle never
 * holds a ghost) but remembers the ride (engine-side, keyed by player
 * UUID); the vehicle stays LIVE in the running engine with its stable
 * entity id, so the rejoin's restore walk needs no NBT recreation — it
 * remounts the live body through the same {@code VehicleManager.mount} /
 * {@code MobManager.seatImportedRider} the live interact path uses, and the
 * mount's own listeners publish the Attach Entity pair while the walk's
 * re-anchor publishes the rider's teleport (the reference's sequence,
 * faithfully).</p>
 *
 * <p>The cycle here is a real one: a scripted client over the real socket,
 * the mount through the real interact packet, the quit through a hard
 * socket close (the engine's own clientDisconnected walk), and the rejoin
 * through a full second login. The boat and the minecart cover the vehicle
 * family; the pig covers the mob-seat family (pigs need no taming — the
 * plain seat).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RelogRemountAcceptanceTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 300_000;
    }

    /** The full login walk, returning the wired client. */
    private TestClient18 join(String name) throws Exception {
        TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
        awaitPlayers(name);
        return client;
    }

    /** Awaits the named player's seat assignment (the engine-side mount). */
    private void awaitRidingVehicle(String name, int vehicleId) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).orElseThrow()
                .ridingVehicleId() == vehicleId, "the session rides the vehicle");
    }

    /** Awaits the named player's mob-seat assignment (the engine-side mount). */
    private void awaitRidingMob(String name, int mobId) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).orElseThrow()
                .ridingMobId() == mobId, "the session rides the mob");
    }

    /** The rejoiner's remount wire sequence: the attach naming the mount. */
    private int[] readRemountAttach(TestClient18 rejoiner, int mountId) throws IOException {
        int[] attach = rejoiner.readAttachEntity(20_000);
        assertEquals(mountId, attach[1], "the remount attach names the live mount");
        assertEquals(1, attach[2], "the remount attach is a mount");
        return attach;
    }

    /**
     * The remount teleport (the reference's startRiding line 521): the
     * rejoiner's own client re-anchors at the authoritative seat — read
     * the Position-and-Look that follows the attach and return {x, y, z}.
     */
    private double[] readRemountTeleport(TestClient18 rejoiner) throws IOException {
        byte[] payload = rejoiner.readPacketOfType(Protocol18.S2C_PLAYER_POSITION_AND_LOOK, 20_000);
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        return new double[] {x, y, z};
    }

    @Test
    void relogWhileRidingTheBoatRemountsAtItsPosition() throws Exception {
        final int[] boatId = {-1};
        try (TestClient18 first = join("BoatReloger")) {
            var anchor = server.playerRegistry().byName("BoatReloger").orElseThrow().position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int by = (int) Math.floor(anchor.y()) - 1; // the ground level
            // A 3x3 still-water pool (the boat's own medium; still water
            // never drifts an unsteered boat).
            var water = net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                    net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER);
            server.ticker().submit(() -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        server.world().setBlock(
                                new net.zaminmc.torch.block.BlockPosition(bx + dx, by, bz + dz),
                                water);
                    }
                }
            });
            awaitCondition(() -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(bx, by, bz)).equals(water),
                    "pool seeded");
            // The boat, engine-side, in the pool's center cell.
            server.ticker().submit(() -> {
                var boat = server.vehicles().spawnBoat(new Position(bx + 0.5, by, bz + 0.5));
                boatId[0] = boat.entityId();
            });
            awaitCondition(() -> boatId[0] > 0, "the boat spawned");
            int[] spawn = first.readSpawnObjectOfType(1, 20_000);
            assertEquals(boatId[0], spawn[0], "the spawn names the engine id");

            // The mount over the wire — the real interact path.
            first.sendUseEntity(boatId[0], Protocol18.USE_ENTITY_INTERACT);
            int[] mount = first.readAttachEntity(20_000);
            assertEquals(boatId[0], mount[1], "the first session's mount names the boat");
            assertEquals(1, mount[2], "the first session took the seat");
            awaitRidingVehicle("BoatReloger", boatId[0]);
        }
        // The quit: the hard socket close drives the engine's own walk.
        awaitCondition(() -> server.playerRegistry().byName("BoatReloger").isEmpty(),
                "the leaver left the registry");
        awaitCondition(() -> server.vehicles().byId(boatId[0]) != null,
                "the boat stays live across the quit, same id");

        try (TestClient18 second = join("BoatReloger")) {
            // THE REMOUNT: the rejoiner is back in the seat of the SAME boat.
            awaitRidingVehicle("BoatReloger", boatId[0]);
            var session = server.playerRegistry().byName("BoatReloger").orElseThrow();
            var boat = server.vehicles().byId(boatId[0]);
            Position seat = boat.seatAnchor(
                    net.zaminmc.torch.server.entity.vehicle.RiderSeatLaw.PLAYER_RIDE_HEIGHT);
            assertEquals(seat.x(), session.position().x(), 1e-9,
                    "the remounted body rides the seat anchor (x)");
            assertEquals(seat.y(), session.position().y(), 1e-9,
                    "the remounted body rides the seat anchor (y)");
            assertEquals(seat.z(), session.position().z(), 1e-9,
                    "the remounted body rides the seat anchor (z)");
            // The wire sequence the reference sends on remount: the attach,
            // then the teleport to the seat.
            readRemountAttach(second, boatId[0]);
            double[] teleport = readRemountTeleport(second);
            assertEquals(seat.y(), teleport[1], 1e-6,
                    "the resync teleport lands the rejoiner's client at the seat");

            // The shared-server cleanup: dismount, then break the boat.
            server.ticker().submit(() -> {
                var ridden = server.vehicles().byId(boatId[0]);
                if (ridden == null) {
                    return;
                }
                if (ridden.hasPassenger()) {
                    server.vehicles().dismount(ridden);
                }
                server.vehicles().hurt(ridden, 1000.0f);
            });
            awaitCondition(() -> server.vehicles().byId(boatId[0]) == null, "the boat consumed");
        }
    }

    @Test
    void relogWhileRidingTheMinecartRemountsAtItsPosition() throws Exception {
        final int[] cartId = {-1};
        try (TestClient18 first = join("CartReloger")) {
            var anchor = server.playerRegistry().byName("CartReloger").orElseThrow().position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int by = (int) Math.floor(anchor.y()); // the feet-level air cell
            var spot = new Position(bx + 0.5, by, bz + 0.5);
            server.ticker().submit(() -> {
                var cart = server.vehicles().spawnMinecart(spot);
                cartId[0] = cart.entityId();
            });
            awaitCondition(() -> cartId[0] > 0, "the cart spawned");
            int[] spawn = first.readSpawnOfMinecartType(0, 20_000);
            assertEquals(cartId[0], spawn[0], "the spawn names the engine id");

            first.sendUseEntity(cartId[0], Protocol18.USE_ENTITY_INTERACT);
            int[] mount = first.readAttachEntity(20_000);
            assertEquals(cartId[0], mount[1], "the first session's mount names the cart");
            assertEquals(1, mount[2], "the first session took the seat");
            awaitRidingVehicle("CartReloger", cartId[0]);
        }
        awaitCondition(() -> server.playerRegistry().byName("CartReloger").isEmpty(),
                "the leaver left the registry");
        awaitCondition(() -> server.vehicles().byId(cartId[0]) != null,
                "the cart stays live across the quit, same id");

        try (TestClient18 second = join("CartReloger")) {
            awaitRidingVehicle("CartReloger", cartId[0]);
            var session = server.playerRegistry().byName("CartReloger").orElseThrow();
            var cart = server.vehicles().byId(cartId[0]);
            Position seat = cart.seatAnchor(
                    net.zaminmc.torch.server.entity.vehicle.RiderSeatLaw.PLAYER_RIDE_HEIGHT);
            assertEquals(seat.x(), session.position().x(), 1e-9,
                    "the remounted body rides the cart's seat anchor (x)");
            assertEquals(seat.y(), session.position().y(), 1e-9,
                    "the remounted body rides the cart's seat anchor (y)");
            assertEquals(seat.z(), session.position().z(), 1e-9,
                    "the remounted body rides the cart's seat anchor (z)");
            readRemountAttach(second, cartId[0]);
            double[] teleport = readRemountTeleport(second);
            assertEquals(seat.y(), teleport[1], 1e-6,
                    "the resync teleport lands the rejoiner's client at the cart's seat");

            server.ticker().submit(() -> {
                var ridden = server.vehicles().byId(cartId[0]);
                if (ridden == null) {
                    return;
                }
                if (ridden.hasPassenger()) {
                    server.vehicles().dismount(ridden);
                }
                server.vehicles().hurt(ridden, 1000.0f);
            });
            awaitCondition(() -> server.vehicles().byId(cartId[0]) == null, "the cart consumed");
        }
    }

    @Test
    void relogWhileRidingThePigRemountsAtItsPosition() throws Exception {
        final int[] pigId = {-1};
        try (TestClient18 first = join("PigReloger")) {
            var anchor = server.playerRegistry().byName("PigReloger").orElseThrow().position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int by = (int) Math.floor(anchor.y()); // the feet-level air cell
            var spot = new Position(bx + 0.5, by, bz + 0.5);
            server.ticker().submit(() -> {
                var pig = server.mobs().spawnAt(
                        net.zaminmc.torch.server.entity.MobType.PIG, spot);
                pigId[0] = pig.entityId();
            });
            awaitCondition(() -> pigId[0] > 0, "the pig spawned");
            // Drain the mob-spawn stream until THIS pig's engine id (the
            // join snapshot may have carried natural spawns first).
            int mobWireId = -1;
            long spawnDeadline = System.currentTimeMillis() + 20_000;
            while (mobWireId == -1 && System.currentTimeMillis() < spawnDeadline) {
                int[] mobSpawn = first.readSpawnMob(
                        Math.max(1, spawnDeadline - System.currentTimeMillis()));
                if (mobSpawn[0] == pigId[0]) {
                    mobWireId = mobSpawn[0];
                }
            }
            assertEquals(pigId[0], mobWireId, "the spawn names the pig's engine id");

            // The mount over the wire: pigs need no taming — the plain seat.
            first.sendUseEntity(pigId[0], Protocol18.USE_ENTITY_INTERACT);
            int[] mount = first.readAttachEntity(20_000);
            assertEquals(pigId[0], mount[1], "the first session's mount names the pig");
            assertEquals(1, mount[2], "the first session took the pig's seat");
            awaitRidingMob("PigReloger", pigId[0]);
        }
        awaitCondition(() -> server.playerRegistry().byName("PigReloger").isEmpty(),
                "the leaver left the registry");
        // The pig waits (the animals' own never-despawn law), same id.
        awaitCondition(() -> server.mobs().byId(pigId[0]) != null,
                "the pig stays live across the quit, same id");

        try (TestClient18 second = join("PigReloger")) {
            // THE REMOUNT: the rejoiner is back on the pig's back, at the
            // mob seat's own anchor (mount position + saddle height + the
            // rider's own -0.35).
            awaitRidingMob("PigReloger", pigId[0]);
            var session = server.playerRegistry().byName("PigReloger").orElseThrow();
            var pig = server.mobs().byId(pigId[0]);
            double seatY = pig.position().y() + pig.seatHeight()
                    + net.zaminmc.torch.server.entity.vehicle.RiderSeatLaw.PLAYER_RIDE_HEIGHT;
            assertEquals(seatY, session.position().y(), 1e-9,
                    "the remounted body rides the pig's seat anchor (y)");
            assertEquals(pig.position().x(), session.position().x(), 1e-9,
                    "the remounted body pins to the pig's center (x)");
            assertEquals(pig.position().z(), session.position().z(), 1e-9,
                    "the remounted body pins to the pig's center (z)");
            readRemountAttach(second, pigId[0]);
            double[] teleport = readRemountTeleport(second);
            assertEquals(seatY, teleport[1], 1e-6,
                    "the resync teleport lands the rejoiner's client on the pig's back");

            // The shared-server cleanup: dismount, then the pig's own death.
            server.ticker().submit(() -> {
                var ridden = server.mobs().byId(pigId[0]);
                if (ridden == null) {
                    return;
                }
                if (ridden.hasRider()) {
                    server.mobs().dismountMob(ridden, false);
                }
                server.mobs().hurt(ridden, 1000.0f, 0.0);
            });
            awaitCondition(() -> server.mobs().byId(pigId[0]) == null, "the pig consumed");
        }
    }
}
