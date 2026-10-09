package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crafting flow over the real wire (§429 family): planks into the 2x2
 * grid via clicks, the result preview in window slot 0, the craft-once cursor
 * take with grid consumption, craft-all via shift-click, and the
 * return-on-close of grid contents. Every click answers with Confirm
 * Transaction -> cursor Set Slot -> Window Items (commit -> verdict -> resync),
 * and every response is read in that pinned order.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CraftingFlowIntegrationTest extends ProtocolTestBase {

    // legacy ids used by the assertions (community data)
    private static final int OAK_PLANKS = 5;
    private static final int CRAFTING_TABLE = 58;
    private static final int STICK = 280;

    private int actionNumber = 0;

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
        return server.playerRegistry().byName(name).orElseThrow();
    }

    /** One full click round trip: verdict, authoritative cursor, full window. */
    private record Click(boolean accepted, int cursorId, int cursorCount, int[][] window) {
    }

    private Click click(TestClient18 client, int wireSlot, int button, int mode) throws Exception {
        client.sendWindowClick(wireSlot, button, mode, actionNumber++);
        int[] confirm = client.readConfirmTransaction(10_000);
        int[] cursor = client.readCursorSlot(10_000);
        int[][] window = client.readWindowSlotTable(10_000);
        return new Click(confirm[2] == 1, cursor[1], cursor[2], window);
    }

    @Test
    void planksInGridShowPreviewAndCraftOnceThenCraftAll() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Crafter");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Crafter");
            client.readWindowItems(10_000); // join sync: all empty

            // 32 planks arrive in the hotbar (wire slot 36)
            client.sendChat("/give oak_planks 32");
            int[][] afterGive = client.readWindowSlotTable(10_000);
            assertEquals(OAK_PLANKS, afterGive[36][0]);
            assertEquals(32, afterGive[36][1]);

            // left-click picks the whole stack onto the cursor
            Click picked = click(client, 36, 0, 0);
            assertTrue(picked.accepted());
            assertEquals(OAK_PLANKS, picked.cursorId());
            assertEquals(32, picked.cursorCount());
            assertEquals(-1, picked.window()[36][0], "the hotbar slot emptied");

            // right-click one plank into each of the four grid cells
            Click fourth = null;
            for (int cell = 1; cell <= 4; cell++) {
                fourth = click(client, cell, 1, 0);
                assertTrue(fourth.accepted());
            }
            int[][] gridFilled = fourth.window();
            assertEquals(28, fourth.cursorCount(), "one plank left the cursor per click");
            assertEquals(CRAFTING_TABLE, gridFilled[0][0], "the result preview sits in wire slot 0");
            assertEquals(1, gridFilled[0][1]);
            for (int cell = 1; cell <= 4; cell++) {
                assertEquals(OAK_PLANKS, gridFilled[cell][0], "cell " + cell);
                assertEquals(1, gridFilled[cell][1], "cell " + cell);
            }

            // taking the result with a mismatched cursor is refused
            Click refused = click(client, 0, 0, 0);
            assertTrue(!refused.accepted(), "planks on the cursor cannot take a crafting table");
            assertEquals(OAK_PLANKS, refused.cursorId(), "the cursor keeps its planks");
            assertEquals(CRAFTING_TABLE, refused.window()[0][0], "the preview survives the refusal");

            // place the planks back into the hotbar, then take the result
            Click parked = click(client, 36, 0, 0);
            assertTrue(parked.accepted());
            assertEquals(-1, parked.cursorId());
            assertEquals(28, parked.window()[36][1]);

            Click taken = click(client, 0, 0, 0);
            assertTrue(taken.accepted(), "craft accepted");
            assertEquals(CRAFTING_TABLE, taken.cursorId(), "the crafting table is on the cursor");
            assertEquals(1, taken.cursorCount());
            assertEquals(-1, taken.window()[0][0], "the consumed grid no longer matches");
            for (int cell = 1; cell <= 4; cell++) {
                assertEquals(-1, taken.window()[cell][0], "cell " + cell + " consumed");
            }

            // put the table away, then craft-all with multi-unit cells: the
            // cursor never auto-feeds the grid, so the chain length is the
            // smallest cell stack (28 planks in the top cell, 64 below)
            Click stowed = click(client, 37, 0, 0);
            assertTrue(stowed.accepted());
            assertEquals(CRAFTING_TABLE, stowed.window()[37][0], "the table waits in hotbar slot 1");

            Click repicked = click(client, 36, 0, 0);
            assertEquals(28, repicked.cursorCount(), "the 28 planks back on the cursor");
            Click topCell = click(client, 1, 0, 0); // whole stack into r0c0
            assertEquals(-1, topCell.cursorId());
            client.sendChat("/give oak_planks 64");
            int[][] given = client.readWindowSlotTable(10_000);
            assertEquals(OAK_PLANKS, given[36][0], "the fresh stack lands in the freed hotbar slot");
            Click repicked2 = click(client, 36, 0, 0);
            assertEquals(64, repicked2.cursorCount());
            Click bottomCell = click(client, 3, 0, 0); // whole stack into r1c0
            assertEquals(-1, bottomCell.cursorId());
            assertEquals(STICK, bottomCell.window()[0][0], "two stacked planks rows preview sticks");
            assertEquals(4, bottomCell.window()[0][1]);

            // shift-click the result: min(28, 64) = 28 crafts -> 112 sticks,
            // filling the first empty engine slot 0 and spilling into slot 2
            Click craftAll = click(client, 0, 0, 1);
            assertTrue(craftAll.accepted(), "craft-all accepted");
            assertEquals(-1, craftAll.cursorId(), "craft-all never touches the cursor");
            int[][] done = craftAll.window();
            assertEquals(-1, done[0][0], "the grid drained completely");
            assertEquals(-1, done[1][0], "the smallest cell drained");
            assertEquals(OAK_PLANKS, done[3][0], "the larger cell keeps its remainder");
            assertEquals(36, done[3][1], "64 - 28 crafts");
            assertEquals(STICK, done[36][0], "the first 64 sticks fill the hotbar");
            assertEquals(64, done[36][1]);
            assertEquals(STICK, done[38][0], "the remaining 48 spill into main slot 0");
            assertEquals(48, done[38][1]);
            assertEquals(CRAFTING_TABLE, done[37][0], "the crafted table stays put");
            assertEquals(1, done[37][1]);
        }
        awaitEmptyServer();
    }

    @Test
    void sticksCraftFromTwoPlanksAndCloseDropsTheGrid() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Stickler");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Stickler");
            client.readWindowItems(10_000);

            client.sendChat("/give oak_planks 6");
            int[][] afterGive = client.readWindowSlotTable(10_000);
            assertEquals(OAK_PLANKS, afterGive[36][0]);
            assertEquals(6, afterGive[36][1]);

            // place two planks vertically: wire 1 (r0c0) and wire 3 (r1c0)
            Click picked = click(client, 36, 0, 0);
            assertEquals(6, picked.cursorCount());
            Click top = click(client, 1, 1, 0);
            assertEquals(5, top.cursorCount());
            Click bottom = click(client, 3, 1, 0);
            assertEquals(4, bottom.cursorCount());
            assertEquals(STICK, bottom.window()[0][0], "two vertical planks preview sticks");
            assertEquals(4, bottom.window()[0][1]);

            // a planks cursor cannot absorb sticks
            Click refused = click(client, 0, 0, 0);
            assertTrue(!refused.accepted());
            assertEquals(OAK_PLANKS, refused.cursorId());

            // park the planks, take the sticks, stow them
            click(client, 36, 0, 0);
            Click sticks = click(client, 0, 0, 0);
            assertTrue(sticks.accepted());
            assertEquals(STICK, sticks.cursorId());
            assertEquals(4, sticks.cursorCount());
            Click stowed = click(client, 37, 0, 0);
            assertEquals(STICK, stowed.window()[37][0], "the sticks wait in hotbar slot 1");

            // drop the remaining 4 planks into the grid, then close the window:
            // the vanilla close (ContainerPlayer.onMenuClose) DROPS the grid
            // stacks to the world at the player — they never return.
            Click reparked = click(client, 36, 0, 0);
            assertEquals(4, reparked.cursorCount());
            Click whole = click(client, 1, 0, 0);
            assertEquals(-1, whole.cursorId(), "the whole stack parked in the grid");
            assertEquals(OAK_PLANKS, whole.window()[1][0]);
            assertEquals(4, whole.window()[1][1]);

            client.sendCloseWindow();
            int[] dropped = client.readSpawnItemOfType(OAK_PLANKS, 10_000);
            assertTrue(dropped[0] > 0, "the grid stack dropped as an item entity");
            int[][] closed = client.readWindowSlotTable(10_000);
            assertEquals(-1, closed[1][0], "the grid emptied on close");
            assertEquals(-1, closed[36][0], "the planks did NOT return to the hotbar (vanilla drop)");
            assertEquals(-1, closed[0][0], "no preview after the close");
            assertEquals(STICK, closed[37][0], "the sticks stayed put");
        }
        awaitEmptyServer();
    }
}
