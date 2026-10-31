package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The riding contract over the real wire: the boat item spawns the vehicle
 * on water (Spawn Entity object type 1), the interact takes the seat
 * (Attach Entity riding=1), the steer drives the body (Entity Teleport)
 * and the unmount flag opens it (Attach Entity riding=0).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VehicleIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 300_000;
    }


    /**
     * Pre-seeds ops.json so the test players can run the op-gated verbs
     * (/give among them — the vanilla GiveCommand sits at op level 2): the
     * offline-mode UUID convention resolves each name deterministically
     * before the engine boots.
     */
    @Override
    @org.junit.jupiter.api.BeforeAll
    void bootStack() throws Exception {
        java.nio.file.Files.write(dataDir.resolve("ops.json"), java.util.List.of(
                "[{\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:BoatRider".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"BoatRider\", \"level\": 4, \"bypassesPlayerLimit\": false},",
                " {\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:ChestCartRider".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"ChestCartRider\", \"level\": 4, \"bypassesPlayerLimit\": false},",
                " {\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:FireCartWatcher".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"FireCartWatcher\", \"level\": 4, \"bypassesPlayerLimit\": false},",
                " {\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:MobSeatWatcher".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"MobSeatWatcher\", \"level\": 4, \"bypassesPlayerLimit\": false},",
                " {\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:RailRider".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"RailRider\", \"level\": 4, \"bypassesPlayerLimit\": false}]"
        ));
        super.bootStack();
    }

    @Test
    void boatPlaceRideSteerAndDismountOverTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("BoatRider");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            // A 5x5 water pool centered on the join anchor's foot block: the
            // boat spawns in the clicked cell and paddles any heading inside
            // the pool (a 1x1 hole would wall it in — the vanilla refusal).
            var anchor = server.playerRegistry().byName("BoatRider").orElseThrow().position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int by = (int) Math.floor(anchor.y()) - 1;
            var water = net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                    net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER);
            server.ticker().submit(() -> {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        server.world().setBlock(
                                new net.zaminmc.torch.block.BlockPosition(bx + dx, by, bz + dz),
                                water);
                    }
                }
            });
            awaitCondition(() -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(bx, by, bz)).equals(water)
                    && server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(bx + 2, by, bz + 2)).equals(water),
                    "pool seeded");

            // The boat item into the held slot (the default hotbar slot 0).
            client.sendChat("/give BoatRider minecraft:boat 1");
            client.drainGiveGhostWake(150);
            Thread.sleep(200);

            // The placement: use the boat on the water block's top face.
            client.sendBlockPlacement(bx, by, bz, 1, 333);
            int[] spawn = client.readSpawnObjectOfType(1, 20_000);
            int boatId = spawn[0];
            assertTrue(boatId >= 5_000_000, "the vehicle id lives in its own band");

            // The seat: interact takes it (Attach Entity riding=1).
            client.sendUseEntity(boatId, Protocol18.USE_ENTITY_INTERACT);
            int[] mount = client.readAttachEntity(20_000);
            assertEquals(1, mount[2], "the attach names the riding state");
            assertNotEquals(-1, mount[1], "the attach names the vehicle");

            // The steer: forward drives the body (the tracked-move family:
            // the Wave VI optimization rides Rel Move for the small deltas,
            // the first publication after the spawn keeps the teleport).
            // A floating boat emits no move until it actually moves, so
            // the anchor is the spawn packet's own fixed-point position.
            for (int i = 0; i < 10; i++) {
                client.sendSteerVehicle(0.0f, 1.0f, 0);
                Thread.sleep(60);
            }
            int[] after = client.readEntityMoveFamilyOf(boatId, 20_000);
            boolean moved = after[0] != Protocol18.S2C_ENTITY_TELEPORT
                    || after[1] != spawn[2] || after[3] != spawn[4];
            assertTrue(moved, "the steered boat left its spawn spot");

            // The unmount: the jump bit's 0x02 opens the seat.
            client.sendSteerVehicle(0.0f, 0.0f, 2);
            int[] dismount = client.readAttachEntity(20_000);
            assertEquals(0, dismount[2], "the seat cleared over the wire");
        }
    }

    @Test
    void creativeMinecartSpawnsOnRailsAndRides() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("RailRider");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            // Creative mode (engine-authoritative), then the creative-menu
            // pick: Set Creative Slot 0 = minecart item 328. The item claim
            // is not a block — the use dispatch must run off the real held
            // stack the creative write populated (the require() crash class).
            var session = server.playerRegistry().byName("RailRider").orElseThrow();
            server.ticker().submit(() -> session.setGamemode(
                    net.zaminmc.torch.GameMode.CREATIVE));
            Thread.sleep(200);
            client.sendCreativeSlot(36, 328); // wire slot 36 = hotbar 0 (held)
            Thread.sleep(200);

            // A straight east-west rail line in the feet cells beside spawn.
            // The class shares one world and the boat seat runs first: its
            // leftover water pool sits in the ground row DIRECTLY under this
            // line, and a rail over water is an unsupported seat that the
            // reference's own law pops on the first neighbour wake
            // (AbstractRailBlock.neighborChanged — hasSolidTop below, the
            // drop-and-remove arm). The bed is restored first so the line
            // survives in any method order: the ground row's cells are
            // written back to the flat world's own grass before the rails
            // land (the boat seat re-seeds its pool over whatever it finds,
            // so this stays order-independent in both directions).
            var anchor = server.playerRegistry().byName("RailRider").orElseThrow().position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int ry = (int) Math.floor(anchor.y()); // the air cell the feet occupy
            var rail = net.zaminmc.torch.server.block.BuiltinBlocks.RAIL_EW;
            server.ticker().submit(() -> {
                for (int dx = -2; dx <= 2; dx++) {
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(bx + dx, ry - 1, bz),
                            net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
                }
                for (int dx = -2; dx <= 2; dx++) {
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(bx + dx, ry, bz), rail);
                }
            });
            awaitCondition(() -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(bx, ry, bz)).equals(rail),
                    "rail line seeded");

            // The placement: the rideable minecart (objectData 0 — a
            // chest cart from an earlier test in the shared server is
            // skipped by the type-index read).
            client.sendBlockPlacement(bx, ry, bz, 1, 328);
            int[] spawn = client.readSpawnOfMinecartType(0, 20_000);
            int cartId = spawn[0];
            assertTrue(cartId >= 5_000_000, "the vehicle id lives in its own band");

            // The seat: the mount attach, then the steer drives the cart.
            client.sendUseEntity(cartId, Protocol18.USE_ENTITY_INTERACT);
            int[] mount = client.readAttachEntity(20_000);
            assertEquals(1, mount[2], "the creative mount took the seat");
            for (int i = 0; i < 12; i++) {
                client.sendSteerVehicle(0.0f, 1.0f, 0);
                Thread.sleep(60);
            }
            // The first per-tick move can floor to the spawn's own
            // fixed-point cell (0.02 blocks is 0.64 units), so drain the
            // stream until a move actually differs from the spawn — a
            // RELATIVE form (0x15/0x17) differs by construction (the
            // tracked-move consult only sends them past the 4/32 band).
            int[] after = client.readEntityMoveFamilyOf(cartId, 20_000);
            long movedDeadline = System.currentTimeMillis() + 5_000;
            while (after[0] == Protocol18.S2C_ENTITY_TELEPORT
                    && after[1] == spawn[2] && after[3] == spawn[4]
                    && System.currentTimeMillis() < movedDeadline) {
                after = client.readEntityMoveFamilyOf(cartId,
                        movedDeadline - System.currentTimeMillis());
            }
            assertTrue(after[0] != Protocol18.S2C_ENTITY_TELEPORT
                            || after[1] != spawn[2] || after[3] != spawn[4],
                    "the steered cart left its spawn spot");

            // The unmount clears the seat like the boat's.
            client.sendSteerVehicle(0.0f, 0.0f, 2);
            int[] dismount = client.readAttachEntity(20_000);
            assertEquals(0, dismount[2], "the cart seat cleared");
        }
    }

    /**
     * Slice 21a — the chest minecart over the real wire: the item places
     * the chest variant (Spawn Entity object type 10 with objectData 1 —
     * the chest box index — and the AddEntityS2CPacket velocity triple that
     * a nonzero data arms), and the interact opens the chest menu (Open
     * Window "minecraft:chest" + the 63-slot Window Items) instead of the
     * seat.
     */
    @Test
    void chestMinecartPlacesOpensAndServesItsMenuOverTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("ChestCartRider");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            var session = server.playerRegistry().byName("ChestCartRider").orElseThrow();
            server.ticker().submit(() -> session.setGamemode(
                    net.zaminmc.torch.GameMode.CREATIVE));
            Thread.sleep(200);
            client.sendCreativeSlot(36, 342); // the chest minecart item
            Thread.sleep(200);

            var anchor = session.position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int ry = (int) Math.floor(anchor.y());
            var rail = net.zaminmc.torch.server.block.BuiltinBlocks.RAIL_EW;
            // The rail bed is restored before the line lands (the boat
            // seat's leftover pool sits under this row — the reference's
            // neighbourChanged pops an unsupported rail; see the creative
            // seat's own note).
            server.ticker().submit(() -> {
                for (int dx = -2; dx <= 2; dx++) {
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(bx + dx, ry - 1, bz),
                            net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
                }
                for (int dx = -2; dx <= 2; dx++) {
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(bx + dx, ry, bz), rail);
                }
            });
            awaitCondition(() -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(bx, ry, bz)).equals(rail),
                    "rail line seeded");

            // The placement: objectData 1 (the chest index) + the velocity
            // triple the reference's data > 0 shape arms.
            client.sendBlockPlacement(bx, ry, bz, 1, 342);
            int[] spawn = client.readSpawnOfMinecartType(1, 20_000);
            int cartId = spawn[0];
            assertEquals(1, spawn[5], "the chest cart's objectData names the chest variant");
            assertTrue(spawn[6] == 0 && spawn[7] == 0 && spawn[8] == 0,
                    "the fresh cart carries the zero velocity triple");

            // The interact opens the chest menu — never the seat.
            client.sendUseEntity(cartId, Protocol18.USE_ENTITY_INTERACT);
            Object[] open = client.readOpenWindow(20_000);
            int windowId = (Integer) open[0];
            assertTrue(windowId > 0, "the chest menu opened over the cart");
            assertEquals("minecraft:chest", open[1],
                    "the cart serves the reference's own chest menu type");
            assertEquals(27, ((Integer) open[2]).intValue(),
                    "the GUI's own 27 slots");
            // The authoritative 63-slot contents follow the open.
            int[] items = client.readWindowItems(20_000);
            assertEquals(windowId, items[0], "the contents name the open window");
            assertEquals(63, items[1], "the chest menu's 63-slot layout");
        }
    }

    /**
     * The burn's own wire face (Slice 78 — Entity.java line 347's
     * setFlag(0, onFireTimer > 0)): a minecart standing in a fire block
     * ignites after the one-tick grace, and the ignite flip's Entity
     * Metadata carries the flags byte 0x01 at the slot 0.
     */
    @Test
    void theBurningCartPublishesItsFlagsByteOverTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("FireCartWatcher");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            var anchor = server.playerRegistry().byName("FireCartWatcher").orElseThrow().position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int by = (int) Math.floor(anchor.y()) - 1; // the ground level
            // A fire block on a guaranteed stone base (the engine's fire
            // burns itself out when floating), the cart standing inside
            // the flame cell (the body's mid-band consult).
            server.ticker().submit(() -> {
                server.world().setBlock(
                        new net.zaminmc.torch.block.BlockPosition(bx, by, bz),
                        net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
                server.world().setBlock(
                        new net.zaminmc.torch.block.BlockPosition(bx, by + 1, bz),
                        net.zaminmc.torch.server.block.BuiltinBlocks.FIRE);
                server.vehicles().spawnMinecart(
                        new net.zaminmc.torch.util.Position(bx + 0.5, by + 1.0, bz + 0.5), 0);
            });

            // The spawn (the plain minecart's object type), then the
            // ignite flip's flags byte (the spawn-time metadata carries
            // the 0x00 entry first; the search loops past it).
            int[] spawn = client.readSpawnObjectOfType(10, 20_000);
            int cartId = spawn[0];
            int[] flags = client.readFlagsMetadataOf(cartId, 0x01, 20_000);
            assertEquals(cartId, flags[0], "the flags byte names the burning cart");
        }
    }

    /**
     * The non-session rider's attach publication (the §22 demand-named
     * riding follow surface): a seated MOB publishes Attach Entity over
     * the raw-id form (the mobs' own global wire space — the same id its
     * spawn packet carried), the engine-side dismount publishes the
     * detach, and a seated PLAYER rides the anchor (the cart's flush 0.0
     * + PlayerEntity's own -0.35 — the anchor repair: the old arm wrote
     * the flat vehicle origin).
     */
    @Test
    void mobRiderAttachPublishesAndThePlayerRidesTheAnchor() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("MobSeatWatcher");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            var anchor = server.playerRegistry().byName("MobSeatWatcher").orElseThrow().position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int by = (int) Math.floor(anchor.y());
            var spot = new net.zaminmc.torch.util.Position(bx + 0.5, by, bz + 0.5);

            // Engine-side scene: a zombie and a cart, the zombie seated —
            // the same host surfaces the anvil riding chain rides.
            final int[] ids = new int[2];
            server.ticker().submit(() -> {
                var zombie = server.mobs().spawnAt(
                        net.zaminmc.torch.server.entity.MobType.ZOMBIE, spot);
                var cart = server.vehicles().spawnMinecart(spot, 0);
                server.vehicles().mount(cart, zombie.entityId());
                ids[0] = zombie.entityId();
                ids[1] = cart.entityId();
            });
            awaitCondition(() -> ids[0] != 0 && ids[1] != 0, "the scene spawned");

            // The wire: the zombie's own spawn id, then the attach naming
            // THAT id as the rider (the raw form — mobs carry the global
            // wire space, no observer-local resolution). The join
            // snapshot spawned the world's natural mobs first — drain
            // until THIS zombie's id.
            int mobWireId = -1;
            long spawnDeadline = System.currentTimeMillis() + 20_000;
            while (mobWireId == -1 && System.currentTimeMillis() < spawnDeadline) {
                int[] mobSpawn = client.readSpawnMob(
                        Math.max(1, spawnDeadline - System.currentTimeMillis()));
                if (mobSpawn[0] == ids[0]) {
                    mobWireId = mobSpawn[0];
                }
            }
            assertEquals(ids[0], mobWireId, "the spawn names the engine id");
            int[] attach = client.readAttachEntity(20_000);
            assertEquals(mobWireId, attach[0], "the attach names the mob rider (the raw id)");
            assertEquals(ids[1], attach[1], "the attach names the cart");
            assertEquals(1, attach[2], "riding=1 at the seat");

            // Free the seat for the player part (the zombie's dismount
            // publishes the detach over the raw-id form — the manager's
            // captured id, the seat already open when the listener fires).
            server.ticker().submit(() -> {
                var mounted = server.vehicles().byId(ids[1]);
                if (mounted != null && mounted.hasPassenger()) {
                    server.vehicles().dismount(mounted);
                }
            });
            int[] zombieDetach = client.readAttachEntity(20_000);
            assertEquals(mobWireId, zombieDetach[0], "the zombie's detach names the raw id");
            assertEquals(0, zombieDetach[2], "the zombie left the seat");

            // The seated player's anchor over the wire: the player takes
            // the seat through the interact (the cart's spawn object sat in
            // the queue ahead of the attach — the readers drain past it),
            // and the session body rides the ANCHOR (the flush 0.0 + the
            // player's -0.35), not the cart's flat origin.
            client.sendUseEntity(ids[1], Protocol18.USE_ENTITY_INTERACT);
            int[] playerAttach = client.readAttachEntity(20_000);
            assertEquals(1, playerAttach[2], "the player's seat publishes");
            var player = server.playerRegistry().byName("MobSeatWatcher").orElseThrow();
            awaitCondition(() -> {
                var cart = server.vehicles().byId(ids[1]);
                return cart != null && Math.abs(player.position().y()
                        - (cart.position().y() - 0.35)) < 1e-9;
            }, "the player rides the anchor");
            var cart = server.vehicles().byId(ids[1]);
            assertEquals(cart.position().y() - 0.35, player.position().y(), 1e-9,
                    "the player's Y rides the anchor, never the flat origin");
            assertEquals(cart.position().x(), player.position().x(), 1e-9,
                    "the player's X pins to the cart's center");

            // The engine-side dismount of the PLAYER rider: the detach
            // publishes (the id the manager captured).
            server.ticker().submit(() -> {
                var mounted = server.vehicles().byId(ids[1]);
                if (mounted != null && mounted.passengerId() == player.engineEntityId()) {
                    server.vehicles().dismount(mounted);
                }
            });
            int[] playerDetach = client.readAttachEntity(20_000);
            assertEquals(0, playerDetach[2], "the player's detach publishes");

            // The scene's cleanup (the shared PER_CLASS server): unseat
            // the zombie and break the cart — no seated body and no plain
            // rideable cart left behind for the next method's join
            // snapshot (the type-index readers would otherwise match this
            // scene's cart as their own).
            server.ticker().submit(() -> {
                var mounted = server.vehicles().byId(ids[1]);
                if (mounted == null) {
                    return;
                }
                if (mounted.hasPassenger()) {
                    server.vehicles().dismount(mounted);
                }
                // The break consumes the body (the destroy publishes ride
                // the removal listener; the cart item drop is a plain
                // item spawn the type-index readers skip).
                server.vehicles().hurt(mounted, 1000.0f);
            });
            awaitCondition(() -> server.vehicles().byId(ids[1]) == null,
                    "the scene's cart consumed");
        }
    }
}
