package net.zamin.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crafting-table container over the real wire: right-clicking a placed
 * table opens the 10-slot GUI (Open Window 0x2D -> Window Items, order
 * pinned), the 3x3 grid crafts community-data recipes (the wooden pickaxe),
 * craft-all chains into the inventory, and closing returns every carried
 * stack — nothing lost, the client never keeps predicted state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CraftingTableIntegrationTest extends ProtocolTestBase {

    /** Many click round trips at 20 Hz ticks; keep-alives must not interfere. */
    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    // legacy ids used by the assertions (community data)
    private static final int OAK_PLANKS = 5;
    private static final int WOODEN_PICKAXE = 270;
    private static final int CHEST = 54;
    private static final int CRAFTING_TABLE = 58;

    private int actionNumber = 0;

    private void awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
    }

    /** One full click round trip in a window: verdict, cursor, that window's items. */
    private record Click(boolean accepted, int confirmWindow, int cursorId, int cursorCount,
                         int[][] window) {
    }

    private Click click(TestClient18 client, int windowId, int wireSlot, int button, int mode)
            throws Exception {
        client.sendWindowClick(windowId, wireSlot, button, mode, actionNumber++);
        int[] confirm = client.readConfirmTransaction(10_000);
        int[] cursor = client.readCursorSlot(10_000);
        int[][] window = client.readWindowSlotTable(10_000, windowId);
        return new Click(confirm[2] == 1, confirm[0], cursor[1], cursor[2], window);
    }

    @Test
    void tableOpensCraftsAPickaxeAndReturnsOnClose() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Tablewright");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Tablewright");
            client.readWindowItems(10_000); // join sync: all empty

            // --- stock the inventory: table, 3 planks, 2 sticks ---
            client.sendChat("/give crafting_table 1");
            int[][] given = client.readWindowSlotTable(10_000);
            assertEquals(CRAFTING_TABLE, given[36][0]);
            client.sendChat("/give oak_planks 3");
            assertEquals(OAK_PLANKS, client.readWindowSlotTable(10_000)[37][0]);
            client.sendChat("/give stick 2");
            assertEquals(280, client.readWindowSlotTable(10_000)[38][0]);

            // --- place the table (held slot 0 = the table): click the grass top
            // face at (2,4,2) -> the table lands at (2,5,2), one above the surface
            client.sendBlockPlacement(2, 4, 2, 1, CRAFTING_TABLE);
            int[] placed = client.readBlockChange(10_000);
            assertEquals(2, placed[0]);
            assertEquals(5, placed[1]);
            assertEquals(2, placed[2]);
            assertEquals(CRAFTING_TABLE, placed[3]);
            client.readWindowSlotTable(10_000); // placement consumed the held table

            // --- right-click the table: the container window opens ---
            client.sendBlockPlacement(2, 5, 2, 1, -1);
            Object[] open = client.readOpenWindow(10_000);
            int windowId = (Integer) open[0];
            assertTrue(windowId > 0, "container ids start at 1");
            assertEquals("minecraft:crafting_table", open[1]);
            assertEquals(10, open[2]); // the GUI's own slots: result + 3x3 grid
            int[][] table = client.readWindowSlotTable(10_000, windowId);
            assertEquals(46, table.length, "result + grid + 27 main + 9 hotbar");
            assertEquals(-1, table[0][0], "empty grid previews nothing");

            // --- fill the pickaxe pattern: PPP / .S. / .S. (wires 1-3, 5, 8) ---
            // table-window hotbar wires: engine 0->37, 1->38, 2->39
            Click picked = click(client, windowId, 38, 0, 0); // 3 planks onto the cursor
            assertTrue(picked.accepted());
            assertEquals(OAK_PLANKS, picked.cursorId());
            assertEquals(3, picked.cursorCount());
            for (int wire : new int[]{1, 2, 3}) {
                Click cell = click(client, windowId, wire, 1, 0);
                assertTrue(cell.accepted(), "plank into wire " + wire);
            }
            Click sticks = click(client, windowId, 39, 0, 0);
            assertTrue(sticks.accepted());
            assertEquals(280, sticks.cursorId());
            for (int wire : new int[]{5, 8}) {
                assertTrue(click(client, windowId, wire, 1, 0).accepted(), "stick into " + wire);
            }

            Click filled = click(client, windowId, 10, 1, 0); // a harmless inventory click
            assertEquals(WOODEN_PICKAXE, filled.window()[0][0],
                    "the pickaxe preview rides every resync");
            assertTrue(click(client, windowId, 10, 0, 0).accepted()); // park it back

            // --- take the result: the pickaxe moves onto the cursor ---
            Click taken = click(client, windowId, 0, 0, 0);
            assertTrue(taken.accepted());
            assertEquals(WOODEN_PICKAXE, taken.cursorId());
            assertEquals(1, taken.cursorCount());
            assertEquals(-1, taken.window()[0][0], "the consumed grid no longer matches");
            for (int wire : new int[]{1, 2, 3, 5, 8}) {
                assertEquals(-1, taken.window()[wire][0], "wire " + wire + " consumed");
            }

            // stow the pickaxe in the main inventory (wire 10 = engine slot 9)
            Click stowed = click(client, windowId, 10, 0, 0);
            assertTrue(stowed.accepted());
            assertEquals(-1, stowed.cursorId());
            assertEquals(WOODEN_PICKAXE, stowed.window()[10][0]);

            // --- close: the client's inventory window syncs as window 0 ---
            client.sendCloseWindow(windowId);
            int[][] closed = client.readWindowSlotTable(10_000);
            assertEquals(WOODEN_PICKAXE, closed[9][0], "the pickaxe shows in window 0");
            assertEquals(-1, closed[36][0], "the placed table left the hotbar");
            assertEquals(-1, closed[37][0], "the planks were consumed");
            assertEquals(-1, closed[38][0], "the sticks were consumed");
        }
        awaitEmptyServer();
    }

    @Test
    void craftAllChainsChestsAndCloseReturnsTheCursor() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Cooper");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Cooper");
            client.readWindowItems(10_000);

            client.sendChat("/give crafting_table 1");
            client.readWindowSlotTable(10_000);
            client.sendChat("/give oak_planks 16");
            assertEquals(OAK_PLANKS, client.readWindowSlotTable(10_000)[37][0]);

            client.sendBlockPlacement(-2, 4, -2, 1, CRAFTING_TABLE);
            int[] placed = client.readBlockChange(10_000);
            assertEquals(-2, placed[0]);
            assertEquals(5, placed[1]);
            assertEquals(-2, placed[2]);
            assertEquals(CRAFTING_TABLE, placed[3]);
            client.readWindowSlotTable(10_000);

            client.sendBlockPlacement(-2, 5, -2, 1, -1);
            Object[] open = client.readOpenWindow(10_000);
            int windowId = (Integer) open[0];
            client.readWindowSlotTable(10_000, windowId);

            // fill the chest ring (wires 1-9 minus the center 5) with TWO planks
            // per cell: 16 on the cursor -> 16 right-clicks -> cursor empty
            assertTrue(click(client, windowId, 38, 0, 0).accepted()); // 16 planks on cursor
            for (int round = 0; round < 2; round++) {
                for (int wire = 1; wire <= 9; wire++) {
                    if (wire == 5) {
                        continue;
                    }
                    assertTrue(click(client, windowId, wire, 1, 0).accepted(),
                            "ring wire " + wire);
                }
            }

            // shift-click the result: two crafts, two chests top up engine slot 0
            Click all = click(client, windowId, 0, 0, 1);
            assertTrue(all.accepted(), "craft-all accepted");
            assertTrue(all.cursorId() == -1, "the cursor emptied into the ring");
            assertEquals(CHEST, all.window()[37][0], "the chests landed in the freed hotbar slot");
            assertEquals(2, all.window()[37][1]);
            for (int wire = 1; wire <= 9; wire++) {
                assertEquals(-1, all.window()[wire][0], "wire " + wire + " drained");
            }

            // the 2x crafts drained the ring; closing re-syncs window 0 where
            // engine slot 0 (the chests) lives at wire 36
            client.sendCloseWindow(windowId);
            int[][] closed = client.readWindowSlotTable(10_000);
            assertEquals(CHEST, closed[36][0]);
            assertEquals(2, closed[36][1]);
            assertEquals(-1, closed[37][0], "every plank was consumed");
        }
        awaitEmptyServer();
    }

    @Test
    void unknownWindowIdsAreRejectedWithResync() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Skeptic");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Skeptic");
            client.readWindowItems(10_000);

            client.sendChat("/give oak_planks 4");
            assertEquals(OAK_PLANKS, client.readWindowSlotTable(10_000)[36][0]);

            // a click for a window the engine never opened: declined, resync follows
            client.sendWindowClick(7, 0, 0, 0, actionNumber++);
            int[] confirm = client.readConfirmTransaction(10_000);
            assertEquals(7, confirm[0], "the confirm echoes the clicked window id");
            assertEquals(0, confirm[2], "the click is declined");
            client.readCursorSlot(10_000);
            int[][] resync = client.readWindowSlotTable(10_000);
            assertEquals(OAK_PLANKS, resync[36][0], "state untouched");
        }
        awaitEmptyServer();
    }
}
