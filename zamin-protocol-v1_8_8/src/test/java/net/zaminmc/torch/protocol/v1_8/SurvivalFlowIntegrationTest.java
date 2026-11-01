package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.BlockPosition;

import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

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

    /** Chases the wire-declared drop like a real player: waits for it to settle, walks to it. */
    private void walkToTheDrop(TestClient18 client, int[] spawn) throws Exception {
        int entityId = spawn[0];
        // Settle check is filtered by the entity id we already read from the
        // wire: the engine list may gain or lose other entities concurrently,
        // so an index-based peek here raced with removals (the old get(0)).
        try {
            awaitCondition(() -> server.itemEntities().all().stream()
                    .anyMatch(e -> e.entityId() == entityId && e.onGround()), "the drop settled");
        } catch (AssertionError timeout) {
            // Run 591's unexplained one-off (the engine ran at NOMINAL 20 TPS
            // through the whole 400-tick await, on a fresh flat world, with
            // every removal and id-space candidate eliminated by the static
            // analysis): the NEXT failure carries the item census — which
            // bodies the engine list holds, where they are, and whether any
            // is grounded — so the missing-settle's fate reads from the CI
            // log without a local reproduction.
            StringBuilder census = new StringBuilder("the item census at the timeout: [");
            for (var item : server.itemEntities().all()) {
                census.append(String.format("#%d(x=%.2f,y=%.2f,z=%.2f,dim=%d,ground=%s) ",
                        item.entityId(), item.position().x(), item.position().y(),
                        item.position().z(), item.dimension(), item.onGround()));
            }
            throw new AssertionError(timeout.getMessage() + " — " + census
                    + "] (the awaited wire id was " + entityId + ")", timeout);
        }
        // Stand where the drop rests: a real client falls into the mined hole
        // (gravity), which is what brings the item into pickup range.
        client.walkTo(spawn[2] / 32.0, spawn[3] / 32.0 - 0.125, spawn[4] / 32.0, true);
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
                        "OfflinePlayer:Survivor".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"Survivor\", \"level\": 4, \"bypassesPlayerLimit\": false},",
                " {\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:Toolman".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"Toolman\", \"level\": 4, \"bypassesPlayerLimit\": false}]"
        ));
        super.bootStack();
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
            EngineAwaits.awaitTicks(server.world(), 19, "grass by hand: 18 ticks nominal (900ms)");
            client.sendDigging(2, 2, 4, 2, 1);

            // ML-019's order: the immediate drop spawn + its metadata precede
            // the deferred block-change flush (the tick's chunkMap section) —
            // read the immediates first, the air change last.
            int[] spawn = client.readSpawnItem(10_000);
            assertEquals(Protocol18.OBJECT_ITEM, spawn[1]); // object 2 = Item (boat-era literal retired)
            assertEquals(3, spawn[5]); // objectData: legacy dirt id
            int itemEntityId = spawn[0];
            assertTrue(spawn[6] != 0 || spawn[7] != 0 || spawn[8] != 0,
                    "item spawns with the historical pop velocity");

            int[] meta = client.readItemMetadata(10_000);
            assertEquals(itemEntityId, meta[0]);
            assertEquals(3, meta[1]); // dirt
            assertEquals(1, meta[2]); // one unit

            client.readBlockChangeAt(2, 4, 2, 0, 10_000); // air: the deferred flush

            // --- walk onto the item and collect it ---
            walkToTheDrop(client, spawn);
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
            // ML-019's order: the immediate consume sync precedes the deferred
            // broadcast flush — the window sync first, the block change after.
            int[] afterPlace = client.readWindowItems(10_000);
            assertEquals(-1, afterPlace[2]); // consumption emptied the hotbar
            client.readBlockChangeAt(2, 4, 2, 3, 10_000); // dirt placed (the deferred flush)

            // --- mine again, then drop the held item with Q (status 4) ---
            client.sendDigging(0, 2, 4, 2, 1);
            EngineAwaits.awaitTicks(server.world(), 19, "dirt by hand: 15 ticks nominal (750ms)");
            client.sendDigging(2, 2, 4, 2, 1);
            int[] redropped = client.readSpawnItem(10_000); // drop (before the deferred air change)
            client.readItemMetadata(10_000);
            client.readBlockChangeAt(2, 4, 2, 0, 10_000); // air (the deferred flush)
            walkToTheDrop(client, redropped);
            client.readCollectItem(15_000);
            client.readWindowItems(15_000); // inventory holds 1 dirt again

            client.sendDigging(4, 0, 0, 0, 0); // Q: drop one held item
            int[] thrown = client.readSpawnItem(10_000);
            assertEquals(Protocol18.OBJECT_ITEM, thrown[1]); // object 2 = Item
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
            client.sendChat("/give Toolman wooden_pickaxe 1");
            client.drainGiveGhostWake(150);
            int[] given = client.readWindowItems(10_000);
            assertEquals(36, given[2]);  // hotbar slot 0 (wire slot 36)
            assertEquals(270, given[3]); // community-dataset legacy id: wooden_pickaxe
            assertEquals(1, given[4]);
            assertEquals(0, given[5]);   // fresh tool carries no damage

            // Seed one stone above ground through the engine's own placement path.
            // The commit broadcasts a Block Change to every visible client, so this
            // client must drain that packet before its own dig result is read.
            var player = server.playerRegistry().byName("Toolman").orElseThrow();
            var stone = new net.zaminmc.torch.block.BlockPosition(2, 5, 2);
            server.blockInteraction().submitPlace(player, stone.offset(0, -1, 0), 1,
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
            awaitCondition(() -> server.world().getBlock(stone)
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.STONE), "stone seeded");
            client.readBlockChangeAt(2, 5, 2, 1, 10_000); // legacy stone

            // Mine it with the pickaxe: 1.5 * 30 / 2 = 23 ticks (1.15s), floor 805ms.
            client.sendDigging(0, 2, 5, 2, 1);
            EngineAwaits.awaitTicks(server.world(), 24, "engine progress (a 1_200 wall settle)");
            client.sendDigging(2, 2, 5, 2, 1);

            // Wire order under ML-019: the immediate drop spawn -> its
            // metadata -> the durability wear's held-slot sync -> the
            // deferred block-change flush. The harvested drop is cobblestone
            // (legacy 4), not stone.
            int[] spawn = client.readSpawnItem(10_000);
            assertEquals(Protocol18.OBJECT_ITEM, spawn[1]); // object 2 = Item (boat-era literal retired)
            assertEquals(4, spawn[5]);
            client.readItemMetadata(10_000);

            // The durability wear re-syncs the held slot after the drop packets.
            int[] worn = client.readWindowItems(10_000);
            assertEquals(270, worn[3]);
            assertEquals(1, worn[5], "one dig wears the wooden pickaxe by one unit");

            client.readBlockChangeAt(2, 5, 2, 0, 10_000); // air: the deferred flush, last

            walkToTheDrop(client, spawn);
            client.readCollectItem(15_000);
            int[] picked = client.readWindowItems(15_000);
            assertEquals(270, picked[3]); // the pickaxe is still held...
            assertEquals(1, picked[5]);   // ...with its wear intact
            // (the cobblestone landed in hotbar slot 1; the pickaxe stays first)
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }
}
