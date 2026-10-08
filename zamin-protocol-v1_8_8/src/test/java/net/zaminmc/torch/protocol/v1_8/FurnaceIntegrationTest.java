package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.BlockPosition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The furnace over the real wire: right-clicking a placed furnace opens the
 * community-verified 3-slot GUI (Open Window 0x2D -> Window Items 39 slots),
 * shift-click routes smeltables to the input and fuel to the fuel slot, the
 * 200-tick smelt runs on real server ticks and its progress rides Window
 * Property 0x31 (fuel left / fuel max / progress / progress max), the ingot
 * is collectible, and closing keeps the furnace's slots in the world — a
 * re-open still shows them.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FurnaceIntegrationTest extends ProtocolTestBase {

    /** The smelt itself takes 10 s of real ticks; keep-alives must not kick. */
    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    // legacy ids used by the assertions (community data)
    private static final int FURNACE = 61;
    private static final int IRON_ORE = 15;
    private static final int COAL = 263;
    private static final int IRON_INGOT = 265;

    private int actionNumber = 0;

    private void awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
    }

    /** One full click round trip: verdict, cursor, that window's items. */
    private record Click(boolean accepted, int cursorId, int cursorCount, int[][] window) {
    }

    private Click click(TestClient18 client, int windowId, int wireSlot, int button, int mode)
            throws Exception {
        client.sendWindowClick(windowId, wireSlot, button, mode, actionNumber++);
        int[] confirm = client.readConfirmTransaction(10_000);
        int[] cursor = client.readCursorSlot(10_000);
        int[][] window = client.readWindowSlotTable(10_000, windowId);
        return new Click(confirm[2] == 1, cursor[1], cursor[2], window);
    }

    @Test
    void furnaceSmeltsIronOreWithLiveProgress() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Smelter");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Smelter");
            client.readWindowItems(10_000); // join sync: all empty

            // --- stock: furnace, 3 iron ore, 2 coal ---
            client.sendChat("/give furnace 1");
            assertEquals(FURNACE, client.readWindowSlotTable(10_000)[36][0]);
            client.sendChat("/give iron_ore 3");
            assertEquals(IRON_ORE, client.readWindowSlotTable(10_000)[37][0]);
            client.sendChat("/give coal 2");
            assertEquals(COAL, client.readWindowSlotTable(10_000)[38][0]);

            // --- place the furnace: top face at (2,4,2) -> lands at (2,5,2) ---
            client.sendBlockPlacement(2, 4, 2, 1, FURNACE);
            int[] placed = client.readBlockChange(10_000);
            assertEquals(2, placed[0]);
            assertEquals(5, placed[1]);
            assertEquals(FURNACE, placed[3]);
            client.readWindowSlotTable(10_000); // the held furnace was consumed

            // --- right-click the furnace: the container window opens ---
            client.sendBlockPlacement(2, 5, 2, 1, -1);
            Object[] open = client.readOpenWindow(10_000);
            int windowId = (Integer) open[0];
            assertTrue(windowId > 0, "container ids start at 1");
            assertEquals("minecraft:furnace", open[1]);
            assertEquals(3, open[2], "the GUI's own slots: input, fuel, output");
            int[][] window = client.readWindowSlotTable(10_000, windowId);
            assertEquals(39, window.length, "3 furnace slots + 27 main + 9 hotbar");
            assertEquals(-1, window[0][0], "no input yet");
            assertEquals(-1, window[30][0], "hotbar wire 30 mirrors engine slot 0 (now empty)");

            // --- the first per-tick view announces the inert baseline ---
            int[] burnLeft0 = client.readWindowProperty(10_000, 0);
            assertEquals(0, burnLeft0[2], "nothing burning yet");
            int[] cook0 = client.readWindowProperty(10_000, 2);
            assertEquals(0, cook0[2], "no cook progress yet");
            int[] cookTotal = client.readWindowProperty(10_000, 3);
            assertEquals(200, cookTotal[2], "the cook cycle is 200 ticks");

            // --- shift-click the ore (wire 31 = engine hotbar 1; the held
            // furnace's consumption freed engine slot 0) into the input ---
            Click oreMoved = click(client, windowId, 31, 0, 1);
            assertTrue(oreMoved.accepted(), "ore shift-click accepted");
            assertEquals(IRON_ORE, oreMoved.window()[0][0], "the input slot took the ore");
            assertEquals(3, oreMoved.window()[0][1]);
            assertEquals(-1, oreMoved.window()[31][0], "the hotbar slot emptied");

            // --- shift-click the coal (wire 32 = engine hotbar 2) into the fuel ---
            Click coalMoved = click(client, windowId, 32, 0, 1);
            assertTrue(coalMoved.accepted(), "coal shift-click accepted");
            assertEquals(COAL, coalMoved.window()[1][0], "the fuel slot took the coal");
            assertEquals(2, coalMoved.window()[1][1]);

            // --- ignition: the burn properties light up (1600 ticks of coal) ---
            int[] burnLeft = client.readWindowProperty(10_000, 0);
            assertTrue(burnLeft[2] > 1500, "a fresh coal burn started: " + burnLeft[2]);
            int[] burnTotal = client.readWindowProperty(10_000, 1);
            assertEquals(1600, burnTotal[2], "one coal = 1600 ticks (community value)");
            client.readWindowProperty(10_000, 2);  // cook progress started

            // --- wait for the first smelt on real ticks (200 ticks = 10 s) ---
            int[][] smelted = awaitFurnaceOutput(client, windowId, IRON_INGOT, 25_000);
            assertEquals(IRON_INGOT, smelted[2][0], "the output slot produced an ingot");
            assertEquals(1, smelted[2][1]);
            assertEquals(2, smelted[0][1], "one ore was consumed");
            assertEquals(1, smelted[1][1], "the fuel still burns (1600 ticks = 8 smelts)");

            // --- shift-click the ingot out: it lands in the inventory ---
            Click ingotOut = click(client, windowId, 2, 0, 1);
            assertTrue(ingotOut.accepted(), "ingot shift-click accepted");
            assertEquals(IRON_INGOT, ingotOut.window()[30][0],
                    "the ingot refilled the freed hotbar slot");
            assertEquals(-1, ingotOut.window()[2][0], "the output drained");

            // --- close: the furnace KEEPS its contents (world state) ---
            client.sendCloseWindow(windowId);
            client.readWindowSlotTable(10_000, 0, true); // window 0 resync after close
            awaitCondition(() -> server.furnaces()
                            .peek(new net.zaminmc.torch.block.BlockPosition(2, 5, 2)) != null,
                    "furnace state survives the window close");

            // --- re-open: the leftover ore + burning coal are still there ---
            client.sendBlockPlacement(2, 5, 2, 1, -1);
            Object[] reopen = client.readOpenWindow(10_000);
            int reopenedId = (Integer) reopen[0];
            assertEquals("minecraft:furnace", reopen[1]);
            int[][] reopened = client.readWindowSlotTable(10_000, reopenedId, true);
            assertEquals(2, reopened[0][1], "the remaining ore waits inside");
            assertEquals(1, reopened[1][1], "the remaining coal waits inside");
        }
        awaitEmptyServer();
    }

    /** Polls Window Items until the furnace's output slot shows the expected item. */
    private int[][] awaitFurnaceOutput(TestClient18 client, int windowId, int legacyId, long timeoutMs)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                int[][] table = client.readWindowSlotTable(
                        Math.min(2_000, deadline - System.currentTimeMillis()), windowId);
                if (table[2][0] == legacyId) {
                    return table;
                }
            } catch (IOException e) {
                last = e; // read timeout: the smelt is still cooking, keep polling
            }
        }
        throw new IOException("Furnace output never appeared", last);
    }
}
