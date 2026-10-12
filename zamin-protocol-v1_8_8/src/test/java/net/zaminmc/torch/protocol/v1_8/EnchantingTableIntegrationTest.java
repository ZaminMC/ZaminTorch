package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enchanting table over the real wire (the reference
 * inventory/menu/EnchantingTableMenu + block/EnchantingTableBlock): the
 * right-click opens the vanilla GUI (Open Window "minecraft:enchanting_table"
 * with the client-built layout, size byte 0), the seven Window Properties
 * push on open (costs, the masked seed, the clues), the two slots play the
 * gated click semantics (a sword never lands in the lapis slot, blue dye
 * does), the Enchant Item button pays slot+1 levels and slot+1 lapis with
 * the XP bar re-syncing, and the close drops both menu slots to the world.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnchantingTableIntegrationTest extends ProtocolTestBase {

    /** Several click round trips at 20 Hz; keep-alives must not interfere. */
    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    /**
     * Pre-seeds ops.json so the test player can run /xp (op level 2): the
     * offline-mode UUID convention (vanilla nameUUIDFromBytes) resolves the
     * name deterministically before the engine boots.
     */
    @Override
    @org.junit.jupiter.api.BeforeAll
    void bootStack() throws Exception {
        java.util.UUID enchanter = java.util.UUID.nameUUIDFromBytes(
                "OfflinePlayer:Enchanter".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.write(dataDir.resolve("ops.json"), java.util.List.of(
                "[{\"uuid\": \"" + enchanter + "\", \"name\": \"Enchanter\",",
                " \"level\": 4, \"bypassesPlayerLimit\": false}]"));
        super.bootStack();
    }

    // legacy wire ids (community data)
    private static final int ENCHANTING_TABLE = 116;
    private static final int BOOKSHELF = 47;
    private static final int DIAMOND_SWORD = 276;
    private static final int LAPIS = 351;

    @Test
    void tableOpensGatesEnchantsAndDropsOnClose() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Enchanter");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitCondition(() -> server.playerRegistry().byName("Enchanter").isPresent(),
                    "player joined");
            client.readWindowItems(10_000); // join sync: all empty

            // --- the table first: survival placement spends the HELD slot
            // (engine 0), so the give order stages what each placement holds ---
            client.sendChat("/give enchanting_table 1");
            assertTrue(client.readChatLine(10_000).contains("Enchantment Table"),
                    "table granted");
            client.sendBlockPlacement(2, 4, 2, 1, ENCHANTING_TABLE);
            client.readBlockChangeAt(2, 5, 2, ENCHANTING_TABLE, 10_000);
            drainWindowItems(client, 800); // the held table consumed

            // --- the NE corner sextuple (all six cells within reach and
            // clear of the player's bounding box): shelves at (4,5,0)/(4,6,0),
            // (4,5,1)/(4,6,1), (3,5,0)/(3,6,0) — power 6, whose
            // enchantability base term keeps slot 0's cost above zero for any
            // seed (the reference ladder's floor) ---
            client.sendChat("/give bookshelf 6");
            assertTrue(client.readChatLine(10_000).contains("Bookshelf"), "shelves granted");
            int[][] shelfPlacements = {
                    {3, 4, 0, 3, 5, 0}, {3, 5, 0, 3, 6, 0},
                    {4, 4, 0, 4, 5, 0}, {4, 5, 0, 4, 6, 0},
                    {4, 4, 1, 4, 5, 1}, {4, 5, 1, 4, 6, 1}};
            for (int[] placement : shelfPlacements) {
                client.sendBlockPlacement(placement[0], placement[1], placement[2], 1, BOOKSHELF);
                client.readBlockChangeAt(placement[3], placement[4], placement[5], BOOKSHELF, 10_000);
                drainWindowItems(client, 800);
            }

            // --- the tool stock (lands in the emptied hotbar: sword engine 0,
            // lapis engine 1 -> enchanting-window wires 29 and 30) ---
            client.sendChat("/give diamond_sword 1");
            assertTrue(client.readChatLine(10_000).contains("Diamond Sword"), "sword granted");
            client.sendChat("/give lapis 5 4");
            assertTrue(client.readChatLine(10_000).contains("Lapis Lazuli"), "lapis granted");
            client.sendChat("/xp 30L Enchanter");
            client.readSetExperience(10_000); // the level grant resync
            drainWindowItems(client, 1500);

            // --- right-click the table: the window opens ---
            client.sendBlockPlacement(2, 5, 2, 1, -1);
            Object[] open = client.readOpenWindow(10_000);
            int windowId = (Integer) open[0];
            assertTrue(windowId > 0, "container ids start at 1");
            assertEquals("minecraft:enchanting_table", open[1]);
            assertEquals(0, open[2]); // the client builds the layout from the type
            int[][] window = client.readWindowSlotTable(10_000, windowId);
            assertEquals(38, window.length, "2 menu + 27 main + 9 hotbar");

            // --- the seven initial properties (costs, masked seed, clues) ---
            Map<Integer, Integer> properties = new HashMap<>();
            for (int i = 0; i < 7; i++) {
                int[] property = client.readWindowProperty(10_000);
                assertEquals(windowId, property[0]);
                properties.put(property[1], property[2]);
            }
            for (int id = 0; id < 7; id++) {
                assertTrue(properties.containsKey(id), "property " + id + " pushed on open");
            }

            // --- the gated clicks: the sword lands in slot 0, the lapis in 1 ---
            // hotbar wires: engine 0 -> 29, engine 1 -> 30. Each click's sweep
            // collects the per-tick property diffs (they ride the ticks BETWEEN
            // clicks — a late-only collector would miss the sword-click burst).
            Map<Integer, Integer> costs = new HashMap<>(properties);
            assertTrue(click(client, windowId, 29, 0, costs).accepted(), "sword onto the cursor");
            assertTrue(click(client, windowId, 0, 0, costs).accepted(), "sword into the item slot");
            assertTrue(click(client, windowId, 30, 0, costs).accepted(), "lapis onto the cursor");
            assertTrue(click(client, windowId, 1, 0, costs).accepted(), "blue lapis into the lapis slot");
            collectProperties(client, costs, 800);
            drainWindowItems(client, 800);
            int button = -1;
            for (int id = 0; id < 3; id++) {
                if (costs.getOrDefault(id, 0) > 0) {
                    button = id;
                    break;
                }
            }
            assertTrue(button >= 0,
                    "power-6 keeps slot 0 above zero (costs " + costs + ")");

            // The clicks' own resyncs drain before the enchant, so the next
            // Window Items read is the post-enchant one.
            drainWindowItems(client, 800);

            // --- the enchant: pays button+1 levels and button+1 lapis ---
            client.sendEnchantItem(windowId, button);
            float[] xp = client.readSetExperience(10_000);
            assertEquals(30 - (button + 1), (int) xp[1], "the level walk is slot+1");
            int[][] after = client.readWindowSlotTable(10_000, windowId);
            assertEquals(5 - (button + 1), after[1][1], "the lapis consumption is slot+1");
            assertEquals(DIAMOND_SWORD, after[0][0], "the sword stays in the item slot");

            // --- the close drops both menu slots to the world ---
            client.sendCloseWindow(windowId);
            awaitCondition(() -> server.itemEntities().all().size() >= 2,
                    "the menu's two slots drop on close");
        }
    }

    /** Consumes queued Window Items until the wire falls silent (no coupling). */
    private void drainWindowItems(TestClient18 client, long ms) {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            try {
                client.readWindowItems(Math.max(1, deadline - System.currentTimeMillis()));
            } catch (java.io.IOException done) {
                return;
            }
        }
    }

    /** One click round trip: verdict from the transaction confirm. */
    private record ClickVerdict(boolean accepted) {
    }

    private ClickVerdict click(TestClient18 client, int windowId, int wireSlot, int button,
                               Map<Integer, Integer> costs) throws Exception {
        client.sendWindowClick(windowId, wireSlot, button, 0, 0);
        int[] confirm = client.readConfirmTransaction(10_000);
        client.readCursorSlot(10_000);
        collectProperties(client, costs, 400); // the recompute's diffs ride the next tick
        return new ClickVerdict(confirm[2] == 1);
    }

    /** Collects Window Property packets into the map until the wire falls quiet. */
    private void collectProperties(TestClient18 client, Map<Integer, Integer> costs, long ms) {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            try {
                int[] property = client.readWindowProperty(Math.max(1,
                        deadline - System.currentTimeMillis()));
                costs.put(property[1], property[2]);
            } catch (java.io.IOException quiet) {
                return;
            }
        }
    }

}
