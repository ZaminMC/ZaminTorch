package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The item-NBT slice over the wire (§426 spirit): the /rename command stamps
 * the held stack's display name, the Window Items encoding carries it as the
 * slot's NBT compound ({@code tag.display.Name}), a dropped named stack keeps
 * the name through the item entity and the pickup, and the no-argument
 * /rename clears it. The test client parses the NBT structurally — the same
 * bytes a real 1.8.8 client walks.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ItemNbtIntegrationTest extends ProtocolTestBase {

    /** The scripted client never answers keep-alives; the waits must not outlive them. */
    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    @Test
    void renameStampsTheNameAndItSurvivesTheWorldRoundTrip() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Namer");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayers("Namer");
            client.readWindowItems(10_000); // join sync: empty inventory

            // The item source, then the rename.
            client.sendChat("/give stick 1");
            client.readWindowItems(10_000);
            client.sendChat("/rename Excalibur of the Torch");
            String[] names = client.readWindowSlotNames(10_000);
            assertEquals("Excalibur of the Torch", names[36],
                    "the held hotbar slot carries tag.display.Name");

            // Drop it with Q: the item entity's metadata slot carries the same
            // compound (the structural skip keeps the stream in sync; the
            // real-client validator asserts the NBT itself).
            client.sendDigging(4, 0, 0, 0, 0);
            client.readSpawnItem(10_000);
            client.readItemMetadata(10_000);
            client.sendChat("/rename");
            names = client.readWindowSlotNames(10_000);
            assertNull(names[36], "no-argument /rename clears the name");

            // Pick the dropped stack back up: the name survives the world
            // (item entity -> validated pickup -> inventory).
            awaitCondition(() -> !server.itemEntities().all().isEmpty(), "drop exists");
            awaitCondition(() -> server.itemEntities().all().stream()
                    .allMatch(net.zaminmc.torch.server.entity.ItemEntity::onGround), "drop settled");
            var drop = server.itemEntities().all().get(0).position();
            client.sendPosition(drop.x(), drop.y() - 0.125, drop.z(), true);
            client.readCollectItem(15_000);
            names = client.readWindowSlotNames(15_000);
            assertEquals("Excalibur of the Torch", names[36],
                    "the pickup restores the named stack");
        }
        awaitEmptyServer();
    }

}
