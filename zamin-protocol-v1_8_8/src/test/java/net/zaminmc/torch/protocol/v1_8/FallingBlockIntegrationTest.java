package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Falling blocks over the real wire, from the client's point of view: a sand
 * column losing its base converts into Spawn Entity object 70 with the
 * historical objectData (legacy id, low 12 bits), falls through Entity
 * Teleports, and ends with Destroy Entities plus the landing Block Changes.
 * The torch rule rides the same queue: losing support pops the item.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FallingBlockIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
        return server.playerRegistry().byName(name).orElseThrow();
    }

    @Test
    void sandColumnFallsAsObject70AndLandsBackAsBlocks() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("GravityEngine");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("GravityEngine");
            client.readWindowItems(15_000);

            // A three-high sand column on the surface grass at (2,4,2).
            client.sendChat("/give sand 3");
            client.sendBlockPlacement(2, 4, 2, 1, 12); // onto the grass top
            // Engine-driven rules share the wire (the covered grass decays the
            // same tick), so every read names the position it waits for.
            client.readBlockChangeAt(2, 5, 2, 12, 10_000);
            client.sendBlockPlacement(2, 5, 2, 1, 12);
            client.readBlockChangeAt(2, 6, 2, 12, 10_000);
            client.sendBlockPlacement(2, 6, 2, 1, 12);
            client.readBlockChangeAt(2, 7, 2, 12, 10_000);
            BlockPosition top = new BlockPosition(2, 7, 2);
            awaitCondition(() -> server.world().getBlock(top)
                    .identifier().toString().equals("minecraft:sand"), "column built");

            // Dig the base: sand by hand is 15 ticks (750ms); the server-validated
            // commit removes the block, drops one sand item, and the update queue
            // converts the two unsupported blocks into falling entities this tick.
            client.sendDigging(0, 2, 5, 2, 1);
            Thread.sleep(1_200);
            client.sendDigging(2, 2, 5, 2, 1);
            client.readBlockChangeAt(2, 5, 2, 0, 10_000); // the dug base becomes air

            // The conversion is engine-visible the same tick: both unsupported
            // sand blocks became falling entities.
            awaitCondition(() -> server.fallingEntities().size() == 2,
                    "two falling entities spawned");

            // Two falling blocks spawn as object 70; the objectData is the
            // legacy block id (12) with metadata 0 above it — the historical
            // encoding the 1.8.9 client decodes via getStateById(data & 0xFFFF).
            int[] fallOne = client.readSpawnObjectOfType(Protocol18.OBJECT_FALLING_BLOCK, 10_000);
            int[] fallTwo = client.readSpawnObjectOfType(Protocol18.OBJECT_FALLING_BLOCK, 10_000);
            assertEquals(12, fallOne[5], "objectData carries the sand legacy id");
            assertEquals(12, fallTwo[5]);
            assertEquals(208, fallOne[3], "the lower entity centers in its cell (6.5 * 32)");
            assertEquals(240, fallTwo[3], "the upper entity centers in its cell (7.5 * 32)");
            assertTrue(fallOne[2] / 32.0 > 2.0 && fallOne[2] / 32.0 < 3.0, "the fall is vertical");
            assertNotEquals(fallOne[0], fallTwo[0], "two distinct engine ids");

            // Movement rides Entity Teleport until each lands; the engine-side
            // queue drains when both transitions finished.
            awaitCondition(() -> server.fallingEntities().size() == 0,
                    "both falling blocks landed");

            // Each landing destroys its wire entity; the block changes ride the
            // world listeners: sand re-materializes at (2,5,2) and (2,6,2). The
            // two packet kinds interleave on the wire, so one drain loop reads
            // both without skipping either.
            int destroyed = 0;
            int landings = 0;
            long deadline = System.currentTimeMillis() + 15_000;
            while ((destroyed < 2 || landings < 2) && System.currentTimeMillis() < deadline) {
                int[] event = client.readBlockChangeOrDestroy(
                        Math.max(1, deadline - System.currentTimeMillis()));
                if (event[0] == 1) {
                    for (int i = 1; i < event.length; i++) {
                        destroyed += (event[i] == fallOne[0] || event[i] == fallTwo[0]) ? 1 : 0;
                    }
                } else if ((event[1] == 2 && event[2] == 5 && event[3] == 2
                        || event[1] == 2 && event[2] == 6 && event[3] == 2)
                        && event[4] == 12) {
                    landings++;
                }
            }
            assertEquals(2, destroyed, "both falling entities left the client's world");
            assertEquals(2, landings, "both landings synced as sand block changes");
            assertEquals("minecraft:sand",
                    server.world().getBlock(new BlockPosition(2, 5, 2)).identifier().toString(),
                    "the lower sand landed back");
            assertEquals("minecraft:sand",
                    server.world().getBlock(new BlockPosition(2, 6, 2)).identifier().toString(),
                    "the upper sand landed one step down");
        }
    }

    @Test
    void torchLosingSupportPopsAsAnItemOverTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("TorchBearer");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("TorchBearer");
            client.readWindowItems(15_000);

            // A torch on the surface grass, then the grass breaks beneath it.
            // Close to spawn: the survival reach (eye-based) is the historical
            // ~4.5 blocks, and (1,4,1) sits well inside it.
            client.sendChat("/give torch 1");
            client.sendBlockPlacement(1, 4, 1, 1, 50);
            client.readBlockChangeAt(1, 5, 1, 50, 10_000); // the placed torch
            BlockPosition torchAt = new BlockPosition(1, 5, 1);
            awaitCondition(() -> server.world().getBlock(torchAt)
                    .identifier().toString().equals("minecraft:torch"), "torch placed");

            client.sendDigging(0, 1, 4, 1, 1);
            Thread.sleep(1_400); // grass by hand: 18 ticks (900ms)
            client.sendDigging(2, 1, 4, 1, 1);

            // The pop: the torch cell becomes air and a torch item entity
            // spawns through the standard drop path (legacy id 50).
            client.readBlockChangeAt(1, 5, 1, 0, 15_000); // the pop empties the cell
            int[] torchItem = client.readSpawnObjectOfType(Protocol18.OBJECT_ITEM, 10_000);
            assertEquals(50, torchItem[5], "the popped torch rides the item path");
            awaitCondition(() -> server.itemEntities().all().stream()
                    .anyMatch(entity -> entity.stack().type().identifier()
                            .toString().equals("minecraft:torch")),
                    "the torch item exists in the world");
        }
    }
}
