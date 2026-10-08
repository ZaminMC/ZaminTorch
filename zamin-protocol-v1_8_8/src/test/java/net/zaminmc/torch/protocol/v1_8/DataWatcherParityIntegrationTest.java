package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vanilla client's DataWatcher cross-check as a regression net: 1.8's
 * client throws when a metadata entry's type does not match the type its
 * entity class registered for that index — a wrong index/type pair crashes
 * real clients the moment the packet lands, while the community metadata
 * parser (which never cross-validates) sails through. The living-entity map
 * the engine may emit is therefore pinned here: flags byte at 0, health
 * float at 6 (EntityLiving.DATA_HEALTH_ID; index 7 is the potion color int
 * and a float there took real clients down — see Protocol18's note).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataWatcherParityIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 2_000;
    }

    @Test
    void livingMobMetadataMatchesTheVanillaDataWatcherMap() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Watcher");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            client.sendChat("/spawnmob pig 1");
            client.readWindowItems(5_000); // join tail: authoritative inventory
            int[][] rows = client.readSpawnMobDataWatcher(5_000, 90); // pig only

            assertEquals(2, rows.length,
                    "flags + health only, got: " + java.util.Arrays.deepToString(rows));
            assertEquals(Protocol18.LIVING_FLAGS_METADATA_INDEX, rows[0][0]);
            assertEquals(Protocol18.METADATA_TYPE_BYTE, rows[0][1]);
            assertEquals(Protocol18.LIVING_HEALTH_METADATA_INDEX, rows[1][0],
                    "1.8 EntityLiving registers health at index 6 (7 = potion color int)");
            assertEquals(Protocol18.METADATA_TYPE_FLOAT, rows[1][1]);
            assertTrue(rows[1][2] > 0, "the health value is a positive float");
        }
    }
}
