package net.zamin.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Item metadata over the real wire: /give with a variant argument produces
 * stacks whose historical damage field carries the variant (charcoal = coal
 * damage 1), variants never merge with their base item (two coal stacks stay
 * apart), item entities carry the variant in their slot metadata (a thrown
 * charcoal renders as charcoal), and the sand/glass block pair round-trips
 * placement and mining (glass shatters to nothing).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetadataIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    // legacy ids used by the assertions (community data)
    private static final int COAL = 263;
    private static final int SAND = 12;
    private static final int GLASS = 20;

    private void awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
    }

    @Test
    void giveVariantsStayDistinctAndRideItemEntities() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Variant");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Variant");
            client.readWindowItems(10_000);

            // --- charcoal: /give coal with metadata 1 ---
            client.sendChat("/give coal 5 1");
            int[][] after = client.readWindowSlotTable(10_000);
            assertEquals(COAL, after[36][0]);
            assertEquals(5, after[36][1]);
            assertEquals(1, after[36][2], "the wire damage field carries the variant");

            // --- plain coal after it: a separate stack, never merged ---
            client.sendChat("/give coal 3");
            after = client.readWindowSlotTable(10_000);
            assertEquals(5, after[36][1], "the charcoal stack kept its count");
            assertEquals(COAL, after[37][0]);
            assertEquals(3, after[37][1]);
            assertEquals(0, after[37][2], "plain coal carries metadata 0");

            // --- red sand variant via /give ---
            client.sendChat("/give sand 4 1");
            after = client.readWindowSlotTable(10_000);
            assertEquals(SAND, after[38][0]);
            assertEquals(1, after[38][2], "red sand is sand with metadata 1");

            // --- a thrown charcoal rides its variant on the entity metadata ---
            client.sendDigging(4, 0, 0, 0, 0); // Q: drop one from the held slot
            int[] spawn = client.readSpawnItem(10_000);
            assertEquals(1, spawn[1], "the drop is an item entity");
            int[] meta = client.readItemMetadata(10_000);
            assertEquals(COAL, meta[1]);
            assertEquals(1, meta[2], "one unit");
            assertEquals(1, meta[3], "the item entity renders charcoal, not coal");
            after = client.readWindowSlotTable(10_000);
            assertEquals(4, after[36][1], "the held stack lost one unit, kept its variant");
            assertEquals(1, after[36][2]);
        }
        awaitEmptyServer();
    }

    @Test
    void sandPlacesDropsAndGlassShatters() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Smelter");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Smelter");
            client.readWindowItems(10_000);

            client.sendChat("/give sand 4");
            int[][] after = client.readWindowSlotTable(10_000);
            assertEquals(SAND, after[36][0]);
            client.sendChat("/give glass 2");
            after = client.readWindowSlotTable(10_000);
            assertEquals(GLASS, after[37][0]);

            // --- place sand on the surface: held slot 0, top face -> (2,5,2) ---
            client.sendBlockPlacement(2, 4, 2, 1, SAND);
            int[] placed = client.readBlockChange(10_000);
            assertEquals(2, placed[0]);
            assertEquals(5, placed[1]);
            assertEquals(SAND, placed[3]);
            client.readWindowSlotTable(10_000); // the held sand was consumed

            // --- mine it by hand: 15 ticks nominal (750 ms) ---
            client.sendDigging(0, 2, 5, 2, 1);
            Thread.sleep(850);
            client.sendDigging(2, 2, 5, 2, 1);
            int[] removal = client.readBlockChange(10_000);
            assertEquals(0, removal[3], "the sand block is gone");
            int[] spawn = client.readSpawnItem(10_000);
            assertEquals(SAND, spawn[5], "sand drops itself (metadata 0)");
            int[] meta = client.readItemMetadata(10_000);
            assertEquals(SAND, meta[1]);
            assertEquals(0, meta[3], "the drop carries no variant");

            // --- glass places, then shatters to nothing ---
            client.sendHeldItemChange(1); // the glass stack
            client.sendBlockPlacement(2, 4, 2, 1, GLASS);
            int[] glassPlaced = client.readBlockChange(10_000);
            assertEquals(GLASS, glassPlaced[3]);
            client.readWindowSlotTable(10_000); // one glass consumed

            client.sendDigging(0, 2, 5, 2, 1);
            Thread.sleep(850); // glass: 9 ticks nominal; the floor keeps it honest
            client.sendDigging(2, 2, 5, 2, 1);
            int[] glassGone = client.readBlockChange(10_000);
            assertEquals(0, glassGone[3], "the glass block is gone");
            assertThrows(IOException.class, () -> client.readSpawnItem(1_500),
                    "glass drops nothing (the historical shatter)");
        }
        awaitEmptyServer();
    }
}
