package net.zamin.protocol.v1_8;

import net.zamin.engine.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full survival loop over the real wire (§319 acceptance style): mine with
 * server-validated timing -> item entity spawns -> walk over it -> collect ->
 * place from the inventory (consumes) -> drop from the hotbar. Everything is
 * asserted from the client's point of view through protocol 47 packets.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurvivalFlowIntegrationTest extends ProtocolTestBase {

    /** This suite sleeps through real mining durations; keep-alives must not interfere. */
    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
        return server.playerRegistry().byName(name).orElseThrow();
    }

    /** Chases the one live drop like a real player: waits for it to settle, walks to it. */
    private void walkToTheDrop(TestClient18 client) throws Exception {
        awaitCondition(() -> !server.itemEntities().all().isEmpty(), "drop exists");
        awaitCondition(() -> server.itemEntities().all().stream()
                .allMatch(net.zamin.engine.entity.ItemEntity::onGround), "drop settled");
        var drop = server.itemEntities().all().get(0).position();
        // Stand where the drop rests: a real client falls into the mined hole
        // (gravity), which is what brings the item into pickup range.
        client.sendPosition(drop.x(), drop.y() - 0.125, drop.z(), true);
    }

    @Test
    void mineCollectPlaceDropOverTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Survivor");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Survivor");

            // Join ends with the authoritative (empty) inventory window sync.
            int[] initial = client.readWindowItems(10_000);
            assertEquals(0, initial[0]); // window 0: player inventory
            assertEquals(45, initial[1]); // historical slot count
            assertEquals(-1, initial[2]); // nothing held yet

            // --- mine the surface grass at (2,4,2) ---
            client.sendDigging(0, 2, 4, 2, 1);
            Thread.sleep(950); // grass by hand: 18 ticks nominal (900ms)
            client.sendDigging(2, 2, 4, 2, 1);

            client.readBlockChangeAt(2, 4, 2, 0, 10_000); // the resulting state: air

            // The drop arrives as a Spawn Entity (type 1 = item, objectData = dirt).
            int[] spawn = client.readSpawnItem(10_000);
            assertEquals(1, spawn[1]);
            assertEquals(3, spawn[5]); // objectData: legacy dirt id
            int itemEntityId = spawn[0];
            assertTrue(spawn[6] != 0 || spawn[7] != 0 || spawn[8] != 0,
                    "item spawns with the historical pop velocity");

            int[] meta = client.readItemMetadata(10_000);
            assertEquals(itemEntityId, meta[0]);
            assertEquals(3, meta[1]); // dirt
            assertEquals(1, meta[2]); // one unit

            // --- walk onto the item and collect it ---
            walkToTheDrop(client);
            int[] collect = client.readCollectItem(15_000);
            assertEquals(itemEntityId, collect[0]);
            assertTrue(collect[1] > 0, "collector is a known wire entity");

            int[] pickup = client.readWindowItems(15_000);
            assertEquals(36, pickup[2]); // hotbar slot 0 (wire slot 36)
            assertEquals(3, pickup[3]);  // dirt
            assertEquals(1, pickup[4]);  // one unit

            // --- place it back: step out of the hole first (a player cannot
            // place into their own bounding box), then refill from spawn ---
            client.sendPosition(0.5, 5.0, 0.5, true);
            client.sendBlockPlacement(2, 3, 2, 1, 3);
            client.readBlockChangeAt(2, 4, 2, 3, 10_000); // dirt placed

            int[] afterPlace = client.readWindowItems(10_000);
            assertEquals(-1, afterPlace[2]); // consumption emptied the hotbar

            // --- mine again, then drop the held item with Q (status 4) ---
            client.sendDigging(0, 2, 4, 2, 1);
            Thread.sleep(950); // dirt by hand: 15 ticks nominal (750ms)
            client.sendDigging(2, 2, 4, 2, 1);
            client.readBlockChangeAt(2, 4, 2, 0, 10_000); // air
            client.readSpawnItem(10_000);   // drop
            client.readItemMetadata(10_000);
            walkToTheDrop(client);
            client.readCollectItem(15_000);
            client.readWindowItems(15_000); // inventory holds 1 dirt again

            client.sendDigging(4, 0, 0, 0, 0); // Q: drop one held item
            int[] thrown = client.readSpawnItem(10_000);
            assertEquals(1, thrown[1]);
            assertTrue(thrown[0] != itemEntityId, "new entity id for the thrown stack");
            int[] emptied = client.readWindowItems(10_000);
            assertEquals(-1, emptied[2]); // hotbar empty after the throw
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }

    @Test
    void pickaxeDigsStoneOverTheWireAndWears() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Toolman");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Toolman");
            client.readWindowItems(10_000); // join sync: empty inventory

            // /give over chat: the administrative item source (crafting is not built yet).
            client.sendChat("/give wooden_pickaxe 1");
            int[] given = client.readWindowItems(10_000);
            assertEquals(36, given[2]);  // hotbar slot 0 (wire slot 36)
            assertEquals(270, given[3]); // community-dataset legacy id: wooden_pickaxe
            assertEquals(1, given[4]);
            assertEquals(0, given[5]);   // fresh tool carries no damage

            // Seed one stone above ground through the engine's own placement path.
            // The commit broadcasts a Block Change to every visible client, so this
            // client must drain that packet before its own dig result is read.
            var player = server.playerRegistry().byName("Toolman").orElseThrow();
            var stone = new net.zamin.api.BlockPosition(2, 5, 2);
            server.blockInteraction().submitPlace(player, stone.offset(0, -1, 0), 1,
                    net.zamin.engine.block.BuiltinBlocks.STONE);
            awaitCondition(() -> server.world().getBlock(stone)
                    .equals(net.zamin.engine.block.BuiltinBlocks.STONE), "stone seeded");
            client.readBlockChangeAt(2, 5, 2, 1, 10_000); // legacy stone

            // Mine it with the pickaxe: 1.5 * 30 / 2 = 23 ticks (1.15s), floor 805ms.
            client.sendDigging(0, 2, 5, 2, 1);
            Thread.sleep(1_200);
            client.sendDigging(2, 2, 5, 2, 1);

            client.readBlockChangeAt(2, 5, 2, 0, 10_000); // air

            // Wire order mirrors the tick order: commit -> drops -> durability
            // wear. The harvested drop is cobblestone (legacy 4), not stone.
            int[] spawn = client.readSpawnItem(10_000);
            assertEquals(1, spawn[1]);
            assertEquals(4, spawn[5]);
            client.readItemMetadata(10_000);

            // The durability wear re-syncs the held slot after the drop packets.
            int[] worn = client.readWindowItems(10_000);
            assertEquals(270, worn[3]);
            assertEquals(1, worn[5], "one dig wears the wooden pickaxe by one unit");

            walkToTheDrop(client);
            client.readCollectItem(15_000);
            int[] picked = client.readWindowItems(15_000);
            assertEquals(270, picked[3]); // the pickaxe is still held...
            assertEquals(1, picked[5]);   // ...with its wear intact
            // (the cobblestone landed in hotbar slot 1; the pickaxe stays first)
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }
}
