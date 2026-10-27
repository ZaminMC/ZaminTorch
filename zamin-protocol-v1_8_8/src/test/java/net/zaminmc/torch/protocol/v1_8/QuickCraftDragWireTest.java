package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The quick-craft drag law over the real wire (window click mode 5 — the
 * reference InventoryMenu.onClickSlot lines 113-175 + the -999 outside-drop
 * lines 178-192): the exact packet chain a real 1.8.8 client sends on a drag
 * release (START at slot -999 with the stage 0|mode&lt;&lt;2 button, one ADD per
 * painted slot with stage 1, END at -999 with stage 2), the even left-split
 * ({@code floor(cursor / slots)} per slot), the one-per-slot right-split, the
 * interruption reset on any non-quick-craft click, and the outside click's
 * whole-stack/one-unit button law.
 *
 * <p>This suite is the regression seat for the reported inventory bug: the
 * engine's old -999 arm ignored the click MODE, so every real drag's START
 * marker threw the whole cursor stack into the world (the reported "stack on
 * the cursor thrown into the world after about two slot interactions") and
 * the ADD packets were rejected outright (the drag never applied). The old
 * outside-click arm also ignored the BUTTON — a right-click outside the
 * window threw the whole stack where the reference drops exactly ONE unit
 * ({@code split(1)}).</p>
 *
 * <p>Wire accounting (the §429 law every helper obeys): each processed
 * window click answers with exactly ONE Confirm Transaction, ONE cursor Set
 * Slot and ONE Window Items resync, in that order — immediates (the Spawn
 * Entity of a thrown stack) precede the triple. The helpers below consume
 * each click's triple exactly once and the slot-table assertions reuse the
 * END's own resync.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuickCraftDragWireTest extends ProtocolTestBase {

    /** legacy ids used by the assertions (community data). */
    private static final int DIRT = 3;
    private static final int CHEST = 54;

    /** One processed click's full answer: the verdict, the cursor, the window table. */
    private record ClickResult(boolean accepted, int[] cursor, int[][] window) {
        int cursorId() {
            return cursor[1];
        }

        int cursorCount() {
            return cursor[2];
        }
    }

    /**
     * Reads one full click response: Confirm Transaction + cursor Set Slot +
     * the window-0 Window Items resync (the click's own ONE triple).
     */
    private ClickResult readClickResponse(TestClient18 client) throws Exception {
        int[] confirm = client.readConfirmTransaction(10_000);
        int[] cursor = client.readCursorSlot(10_000);
        int[][] window = client.readWindowSlotTable(10_000);
        return new ClickResult(confirm[2] == 1, cursor, window);
    }

    /** The container-window form: the resync rides the open window's id, not window 0. */
    private ClickResult readContainerClickResponse(TestClient18 client, int windowId) throws Exception {
        int[] confirm = client.readConfirmTransaction(10_000);
        assertEquals(windowId, confirm[0], "the confirm carries the container's window id");
        int[] cursor = client.readCursorSlot(10_000);
        int[][] window = client.readWindowSlotTable(10_000, windowId);
        return new ClickResult(confirm[2] == 1, cursor, window);
    }

    /**
     * Asserts NO Spawn Entity packet arrives inside the window: the drag's
     * own packets must never throw a stack into the world (the reported
     * item-loss arm). The probe consumes-and-skips whatever else flows
     * (heartbeats answer inline); silence is the pass.
     */
    private static void assertNothingThrown(TestClient18 client, String what)
            throws InterruptedException {
        try {
            client.readSpawnItem(400);
            fail("an item entity spawned where nothing should have been thrown: " + what);
        } catch (IOException expectedTimeout) {
            // silence is the pass: no Spawn Entity arrived
        }
    }

    /**
     * Joins, seeds five dirt into hotbar slot 36 (wire slot 36) and picks the
     * whole stack onto the cursor, leaving the inventory at rest.
     */
    private void joinSeedAndPickup(TestClient18 client, String name) throws Exception {
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
        client.readWindowItems(10_000); // join sync: empty
        client.sendChat("/give " + name + " dirt 5");
        client.drainGiveGhostWake(150);
        client.readWindowSlotTable(10_000); // the give's sync
        // left-click picks the whole 5-stack onto the cursor
        client.sendWindowClick(36, 0, 0, 0);
        ClickResult picked = readClickResponse(client);
        assertTrue(picked.accepted(), "the pickup accepted");
        assertEquals(DIRT, picked.cursorId(), "the cursor carries the dirt");
        assertEquals(5, picked.cursorCount(), "the whole stack");
    }

    /**
     * Pre-seeds ops.json so the test players can run the op-gated verbs
     * (/give among them — the vanilla GiveCommand sits at op level 2).
     */
    @Override
    @org.junit.jupiter.api.BeforeAll
    void bootStack() throws Exception {
        String[] names = {"Dragger", "Splitter", "Painter", "Interrupter",
                "Dropper", "Placer", "Straggler", "ChestDragger"};
        StringBuilder ops = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            ops.append("{\"uuid\": \"")
                    .append(java.util.UUID.nameUUIDFromBytes(
                            ("OfflinePlayer:" + names[i])
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .append("\", \"name\": \"").append(names[i])
                    .append("\", \"level\": 4, \"bypassesPlayerLimit\": false}")
                    .append(i == names.length - 1 ? "" : ",");
        }
        java.nio.file.Files.write(dataDir.resolve("ops.json"), java.util.List.of("[" + ops + "]"));
        super.bootStack();
    }

    @Test
    void aRealClientLeftDragDistributesEvenlyAndKeepsTheRemainder() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            joinSeedAndPickup(client, "Dragger");

            // The exact real-client drag chain over TWO painted slots (the
            // reported "approximately two slot interactions"): START, ADD,
            // ADD, END — all on mode 5, the stage in the button's low bits.
            client.sendWindowClick(0, -999, 0, 5, 1);   // START (left drag)
            assertTrue(readClickResponse(client).accepted(), "the START marker processed");
            client.sendWindowClick(0, 9, 1, 5, 2);      // ADD main slot 9
            assertTrue(readClickResponse(client).accepted(), "the first ADD processed");
            client.sendWindowClick(0, 10, 1, 5, 3);     // ADD main slot 10
            assertTrue(readClickResponse(client).accepted(), "the second ADD processed");
            client.sendWindowClick(0, -999, 2, 5, 4);   // END (left drag)
            ClickResult ended = readClickResponse(client);
            assertTrue(ended.accepted(), "the END marker processed");

            // The reference's distribution law (updateClickDragStackSize at
            // mode 0): floor(5 / 2) = 2 into each painted slot, the cursor
            // keeping the running remainder — floor(5/2)*2 = 4 placed, 1 left.
            assertEquals(DIRT, ended.cursorId(), "the cursor still carries the remainder");
            assertEquals(1, ended.cursorCount(), "5 - floor(5/2) - floor(5/2) = 1 stays carried");
            assertEquals(DIRT, ended.window()[9][0], "main slot 9 took its even share");
            assertEquals(2, ended.window()[9][1]);
            assertEquals(DIRT, ended.window()[10][0], "main slot 10 took its even share");
            assertEquals(2, ended.window()[10][1]);
            assertEquals(-1, ended.window()[11][0], "the unpainted neighbor stays empty");

            // THE ITEM-LOSS REGRESSION: the old -999 arm threw the whole
            // cursor stack into the world on the drag's START marker; the
            // whole chain must stay inside the window.
            assertNothingThrown(client, "the left drag's markers and distribution");
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }

    @Test
    void theRightButtonDragPlacesExactlyOnePerSlot() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            joinSeedAndPickup(client, "Splitter");

            // The RIGHT-button drag chain: buttons 4 (start) / 5 (add) / 6
            // (end) — the drag mode 1 in the button's high half.
            client.sendWindowClick(0, -999, 4, 5, 1);
            assertTrue(readClickResponse(client).accepted(), "the right START processed");
            client.sendWindowClick(0, 9, 5, 5, 2);
            assertTrue(readClickResponse(client).accepted(), "the right ADD processed");
            client.sendWindowClick(0, -999, 6, 5, 3);
            ClickResult ended = readClickResponse(client);
            assertTrue(ended.accepted(), "the right END processed");

            // updateClickDragStackSize at mode 1: exactly one unit per slot.
            assertEquals(DIRT, ended.window()[9][0], "the painted slot took exactly one");
            assertEquals(1, ended.window()[9][1]);
            assertEquals(-1, ended.window()[10][0], "the unpainted slot stays empty");
            assertEquals(4, ended.cursorCount(), "5 - 1 placed = 4 still carried");
            assertNothingThrown(client, "the right drag's markers");
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }

    @Test
    void aDragIntoTheCraftingGridDistributesIntoTheCells() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            joinSeedAndPickup(client, "Painter");

            // The reported gesture: dragging a stack INTO the player
            // inventory's own 2x2 crafting-grid cells (wire slots 1-4).
            // Three cells painted from a 5-stack: the ADD gate needs the
            // cursor to hold more units than the painted count each time
            // (5 > 0, 5 > 1, 5 > 2 all pass).
            client.sendWindowClick(0, -999, 0, 5, 1);   // START
            assertTrue(readClickResponse(client).accepted());
            client.sendWindowClick(0, 1, 1, 5, 2);      // ADD grid cell 1
            assertTrue(readClickResponse(client).accepted());
            client.sendWindowClick(0, 2, 1, 5, 3);      // ADD grid cell 2
            assertTrue(readClickResponse(client).accepted());
            client.sendWindowClick(0, 3, 1, 5, 4);      // ADD grid cell 3
            assertTrue(readClickResponse(client).accepted());
            client.sendWindowClick(0, -999, 2, 5, 5);   // END
            ClickResult ended = readClickResponse(client);
            assertTrue(ended.accepted());

            // floor(5 / 3) = 1 per cell, the cursor keeping 5 - 3 = 2.
            assertEquals(DIRT, ended.window()[1][0], "grid cell 1 took its share");
            assertEquals(1, ended.window()[1][1]);
            assertEquals(DIRT, ended.window()[2][0], "grid cell 2 took its share");
            assertEquals(1, ended.window()[2][1]);
            assertEquals(DIRT, ended.window()[3][0], "grid cell 3 took its share");
            assertEquals(1, ended.window()[3][1]);
            assertEquals(-1, ended.window()[4][0], "the fourth cell stays empty");
            assertEquals(2, ended.cursorCount(), "the remainder stays on the cursor");
            assertNothingThrown(client, "the crafting-grid drag");
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }

    @Test
    void anInterruptedDragDistributesNothingOnTheStragglerEnd() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            joinSeedAndPickup(client, "Interrupter");

            // START + one ADD, then a plain left-click (the interruption:
            // the reference's clickDragStage != 0 arm drops the drag), then
            // the straggling END — the reset law consumes it with nothing
            // painted, and nothing ever distributes.
            client.sendWindowClick(0, -999, 0, 5, 1);
            assertTrue(readClickResponse(client).accepted());
            client.sendWindowClick(0, 9, 1, 5, 2);
            assertTrue(readClickResponse(client).accepted());
            // the interruption: a plain left-click on another slot — the
            // reference's own place-all law puts the whole carried stack
            // INTO that slot (the cursor empties), the drag state dropping
            // with it.
            client.sendWindowClick(0, 20, 0, 0, 3);
            ClickResult interrupted = readClickResponse(client);
            assertTrue(interrupted.accepted(), "the interrupting click processed");
            // the straggler END: the previous stage reset to 0, so the
            // 0 -> 2 transition is out-of-order and consumed silently.
            client.sendWindowClick(0, -999, 2, 5, 4);
            ClickResult straggler = readClickResponse(client);
            assertTrue(straggler.accepted(), "the straggler END consumed");

            // The drag's painted slot NEVER distributed (the interrupt's
            // place-all owns slot 20; the straggler END painted nothing).
            assertEquals(-1, straggler.window()[9][0],
                    "the interrupted ADD never distributed into slot 9");
            assertEquals(-1, straggler.window()[10][0]);
            assertEquals(DIRT, straggler.window()[20][0],
                    "the interrupting click placed the stack per its own law");
            assertEquals(5, straggler.window()[20][1]);
            assertEquals(-1, straggler.cursorId(), "the cursor emptied into the interrupted slot");
            assertNothingThrown(client, "the interrupted drag");
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }

    @Test
    void theOutsideClickDropsOneUnitOnTheRightButtonAndTheStackOnTheLeft() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            joinSeedAndPickup(client, "Dropper");

            // The reference's -999 arm (InventoryMenu lines 178-192): the
            // right button drops EXACTLY ONE unit (the old engine threw the
            // whole stack for both buttons). The immediates precede the
            // triple: the Spawn Entity + its item metadata first.
            client.sendWindowClick(0, -999, 1, 0, 1);
            int[] spawn = client.readSpawnItem(10_000);
            assertEquals(Protocol18.OBJECT_ITEM, spawn[1], "the spawned entity is an item");
            assertEquals(DIRT, spawn[5], "the thrown stack is dirt");
            int[] thrownMeta = client.readItemMetadata(10_000);
            assertEquals(spawn[0], thrownMeta[0], "the metadata rides the thrown entity");
            assertEquals(1, thrownMeta[2], "exactly ONE unit left the cursor");
            ClickResult oneDrop = readClickResponse(client);
            assertTrue(oneDrop.accepted(), "the one-unit outside drop accepted");
            assertEquals(4, oneDrop.cursorCount(), "four units stay carried");

            // The left button drops the WHOLE remaining stack.
            client.sendWindowClick(0, -999, 0, 0, 2);
            int[] wholeSpawn = client.readSpawnItem(10_000);
            assertEquals(Protocol18.OBJECT_ITEM, wholeSpawn[1]);
            int[] wholeMeta = client.readItemMetadata(10_000);
            assertEquals(4, wholeMeta[2], "the whole four-unit stack left");
            ClickResult allDrop = readClickResponse(client);
            assertTrue(allDrop.accepted(), "the whole-stack outside drop accepted");
            assertEquals(-1, allDrop.cursorId(), "the cursor empties");
            assertEquals(-1, allDrop.cursorCount());
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }

    @Test
    void repeatedRightClicksIntoTheCraftingGridKeepWorkingWithoutRejections() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            joinSeedAndPickup(client, "Placer");

            // The reported gesture, plain mode-0 form: three right-click
            // place-one gestures into the 2x2 grid's first cell — every one
            // accepted, the cursor draining one unit at a time, no
            // rejection, no closure, no thrown stack.
            ClickResult last = null;
            for (int placed = 1; placed <= 3; placed++) {
                client.sendWindowClick(1, 1, 0, placed);
                ClickResult round = readClickResponse(client);
                assertTrue(round.accepted(), "place-one accepted (round " + placed + ")");
                assertEquals(5 - placed, round.cursorCount(),
                        "the cursor drains one unit per right-click");
                assertNothingThrown(client, "the place-one round");
                last = round;
            }
            // The LAST round's resync carries the placed units.
            assertEquals(DIRT, last.window()[1][0], "the grid cell holds the placed units");
            assertEquals(3, last.window()[1][1]);
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }

    @Test
    void anAddBeforeAnyStartIsConsumedWithoutPaintingAnything() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            joinSeedAndPickup(client, "Straggler");

            // The invalid sequence: an ADD (stage 1) with no drag in flight.
            // The reference's out-of-order reset consumes it; the END that
            // follows has nothing painted and distributes nothing.
            client.sendWindowClick(0, 9, 1, 5, 1);
            assertTrue(readClickResponse(client).accepted(), "the orphan ADD consumed");
            client.sendWindowClick(0, -999, 2, 5, 2);
            ClickResult ended = readClickResponse(client);
            assertTrue(ended.accepted(), "the empty END consumed");
            assertEquals(-1, ended.window()[9][0], "nothing was painted by the invalid sequence");
            assertEquals(5, ended.cursorCount(), "the stack never left the cursor");
            assertNothingThrown(client, "the invalid drag sequence");
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }

    @Test
    void theChestWindowDragDistributesIntoTheChestSlots() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            // The drag over an open container (the ChestIntegrationTest's
            // proven placement flow): place a chest at (2,5,2), open it,
            // pick the dirt stack up from the container window's player
            // tail and drag it into TWO chest slots (the container band
            // 0-26 rides the same quick-craft law).
            client.sendHandshake(47, 2);
            client.sendLoginStart("ChestDragger");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitCondition(() -> server.playerRegistry().byName("ChestDragger").isPresent(),
                    "player joined");
            client.readWindowItems(10_000);
            client.sendChat("/give ChestDragger chest 1");
            client.drainGiveGhostWake(150);
            client.readWindowSlotTable(10_000); // the chest lands in hotbar slot 0
            client.sendChat("/give ChestDragger dirt 5");
            client.drainGiveGhostWake(150);
            client.readWindowSlotTable(10_000); // the dirt lands in hotbar slot 1

            // place the chest at the flat world's ground (the proven flow)
            client.sendBlockPlacement(2, 4, 2, 1, CHEST);
            client.readWindowSlotTable(10_000); // the held chest consumed
            client.readBlockChangeAt(2, 5, 2, CHEST, 10_000);
            // open it
            client.sendBlockPlacement(2, 5, 2, 1, -1);
            Object[] open = client.readOpenWindow(10_000);
            int windowId = (Integer) open[0];
            client.readWindowSlotTable(10_000, windowId);

            // pick the dirt up (wire slot 55 = the container window's hotbar
            // band, hotbar slot 1) and drag it into two chest slots
            client.sendWindowClick(windowId, 55, 0, 0, 1);
            ClickResult picked = readContainerClickResponse(client, windowId);
            assertTrue(picked.accepted(), "the container pickup accepted");
            assertEquals(DIRT, picked.cursorId());
            assertEquals(5, picked.cursorCount());

            client.sendWindowClick(windowId, -999, 0, 5, 2);
            assertTrue(readContainerClickResponse(client, windowId).accepted());
            client.sendWindowClick(windowId, 0, 1, 5, 3);
            assertTrue(readContainerClickResponse(client, windowId).accepted());
            client.sendWindowClick(windowId, 1, 1, 5, 4);
            assertTrue(readContainerClickResponse(client, windowId).accepted());
            client.sendWindowClick(windowId, -999, 2, 5, 5);
            ClickResult ended = readContainerClickResponse(client, windowId);
            assertTrue(ended.accepted());

            // floor(5/2) = 2 per chest slot, 1 back on the cursor.
            assertEquals(DIRT, ended.window()[0][0], "chest slot 0 took its share");
            assertEquals(2, ended.window()[0][1]);
            assertEquals(DIRT, ended.window()[1][0], "chest slot 1 took its share");
            assertEquals(2, ended.window()[1][1]);
            assertEquals(1, ended.cursorCount(), "the remainder carries");
            assertNothingThrown(client, "the chest-window drag");
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }
}
