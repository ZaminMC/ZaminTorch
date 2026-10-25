package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The chest over the real wire: right-clicking a placed chest opens the
 * community-verified 63-slot GUI (Open Window 0x2D "minecraft:chest" -> Window
 * Items 27 chest + 27 main + 9 hotbar), shift-click moves stacks both ways,
 * number keys on chest slots are declined, closing keeps the chest's slots in
 * the world (a re-open still shows them), and a survival break spills the
 * contents as item entities. The container trio is complete: table, furnace,
 * chest.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChestIntegrationTest extends ProtocolTestBase {

    /** The chest dig takes ~3.75 s of hand mining; keep-alives must not kick. */
    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    // legacy ids used by the assertions (community data)
    private static final int CHEST = 54;
    private static final int DIRT = 3;

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
                        "OfflinePlayer:Packrat".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"Packrat\", \"level\": 4, \"bypassesPlayerLimit\": false},",
                " {\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:Storer".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"Storer\", \"level\": 4, \"bypassesPlayerLimit\": false}]"
        ));
        super.bootStack();
    }

    @Test
    void chestStoresRetainsAndSpillsOverTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Storer");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Storer");
            client.readWindowItems(10_000); // join sync: all empty

            // --- stock: chest, then dirt ---
            client.sendChat("/give Storer chest 1");
            client.drainGiveGhostWake(150);
            assertEquals(CHEST, client.readWindowSlotTable(10_000)[36][0]);

            // --- place the chest: top face at (2,4,2) -> lands at (2,5,2) ---
            client.sendBlockPlacement(2, 4, 2, 1, CHEST);
            // ML-019's order: the immediate consume sync precedes the deferred
            // broadcast flush — the table first, the block change after (the
            // use walk's resync unicast may also precede both; the block-change
            // reader routes by position so the deferred broadcast answers it).
            client.readWindowSlotTable(10_000); // the held chest was consumed
            int[] placed = client.readBlockChangeAt(2, 5, 2, CHEST, 10_000);
            assertEquals(2, placed[0]);
            assertEquals(5, placed[1]);
            assertEquals(CHEST, placed[3]);

            // --- right-click the chest: the container window opens ---
            client.sendBlockPlacement(2, 5, 2, 1, -1);
            Object[] open = client.readOpenWindow(10_000);
            int windowId = (Integer) open[0];
            assertTrue(windowId > 0, "container ids start at 1");
            assertEquals("minecraft:chest", open[1]);
            assertEquals(27, open[2], "the GUI's own slots: 3 rows of 9");
            int[][] window = client.readWindowSlotTable(10_000, windowId);
            assertEquals(63, window.length, "27 chest slots + 27 main + 9 hotbar");
            assertEquals(-1, window[0][0], "the chest starts empty");

            // --- stock dirt while the chest window is open (the visible
            // inventory lives in the container layout, so the sync arrives
            // for the chest window; engine slot 0 = wire 54) ---
            client.sendChat("/give Storer dirt 30");
            client.drainGiveGhostWake(150);
            window = client.readWindowSlotTable(10_000, windowId);
            assertEquals(DIRT, window[54][0], "the hotbar mirror shows the dirt");
            assertEquals(30, window[54][1]);

            // --- shift-click it in: chest slot 0 takes the stack ---
            Click in = click(client, windowId, 54, 0, 1);
            assertTrue(in.accepted(), "dirt shift-click accepted");
            assertEquals(DIRT, in.window()[0][0], "the chest slot took the dirt");
            assertEquals(30, in.window()[0][1]);
            assertEquals(-1, in.window()[54][0], "the hotbar slot emptied");

            // --- number keys on chest slots are declined (resync restores) ---
            client.sendWindowClick(windowId, 0, 3, 2, actionNumber++);
            int[] declined = client.readConfirmTransaction(10_000);
            assertEquals(0, declined[2], "mode 2 on a chest slot is rejected");

            // --- close: the chest KEEPS its contents (world state) ---
            client.sendCloseWindow(windowId);
            client.readWindowSlotTable(10_000, 0, true); // window 0 resync after close
            awaitCondition(() -> server.chests()
                            .peek(new net.zaminmc.torch.block.BlockPosition(2, 5, 2)) != null,
                    "chest state survives the window close");
            assertEquals(30, server.chests()
                            .peek(new net.zaminmc.torch.block.BlockPosition(2, 5, 2))
                            .snapshotSlots()[0].count(),
                    "the dirt is inside the chest");

            // --- re-open: the dirt is still there ---
            client.sendBlockPlacement(2, 5, 2, 1, -1);
            Object[] reopen = client.readOpenWindow(10_000);
            int reopenedId = (Integer) reopen[0];
            assertEquals("minecraft:chest", reopen[1]);
            int[][] reopened = client.readWindowSlotTable(10_000, reopenedId, true);
            assertEquals(DIRT, reopened[0][0], "the dirt waits inside");
            assertEquals(30, reopened[0][1]);

            // --- left click lifts it back onto the cursor ---
            Click out = click(client, reopenedId, 0, 0, 0);
            assertTrue(out.accepted(), "dirt pickup accepted");
            assertEquals(DIRT, out.cursorId());
            assertEquals(30, out.cursorCount());
            assertEquals(-1, out.window()[0][0], "the chest slot emptied");
            // put it back so the break spills exactly one stack
            Click back = click(client, reopenedId, 0, 0, 0);
            assertTrue(back.accepted());
            assertEquals(DIRT, back.window()[0][0]);

            // --- close, then survival-break the chest: spill + forget ---
            client.sendCloseWindow(reopenedId);
            client.readWindowSlotTable(10_000, 0, true);
            client.sendDigging(0, 2, 5, 2, 1);
            EngineAwaits.awaitTicks(server.world(), 76, "chest by hand: 75 ticks nominal (3.75 s)");
            client.sendDigging(2, 2, 5, 2, 1);

            // ML-019's order: the immediate drops + metadata precede the
            // deferred air change — the immediates first, the flush last.
            // The block's own drop comes first (chest drops itself), then the spill.
            int[] chestDrop = client.readSpawnItem(10_000);
            assertEquals(Protocol18.OBJECT_ITEM, chestDrop[1], "the block drop is an item entity");
            assertEquals(CHEST, chestDrop[5], "the chest itself dropped");
            client.readItemMetadata(10_000);
            int[] spill = client.readSpawnItem(10_000);
            assertEquals(Protocol18.OBJECT_ITEM, spill[1], "the spill is an item entity");
            assertEquals(DIRT, spill[5], "the dirt spilled out");
            client.readItemMetadata(10_000);
            int[] removal = client.readBlockChangeAt(2, 5, 2, 0, 10_000); // the deferred air
            assertEquals(0, removal[3], "the chest block is gone "
                    + java.util.Arrays.toString(removal));
            awaitCondition(() -> server.chests()
                            .peek(new net.zaminmc.torch.block.BlockPosition(2, 5, 2)) == null,
                    "the broken chest's state is forgotten");
        }
        awaitEmptyServer();
    }

    @Test
    void chestShiftMoveOutReturnsRemainderOnFullInventory() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Packrat");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Packrat");
            client.readWindowItems(10_000);

            client.sendChat("/give Packrat chest 1");
            client.drainGiveGhostWake(150);
            assertEquals(CHEST, client.readWindowSlotTable(10_000)[36][0]);
            client.sendBlockPlacement(2, 4, 2, 1, CHEST);
            client.readWindowSlotTable(10_000); // the immediate consume sync
            assertEquals(CHEST, client.readBlockChangeAt(2, 5, 2, CHEST, 10_000)[3]);

            client.sendBlockPlacement(2, 5, 2, 1, -1);
            Object[] open = client.readOpenWindow(10_000);
            int windowId = (Integer) open[0];

            // Fill the whole inventory with full stacks (give merges partials,
            // so only 64s guarantee fullness): 36 x /give dirt 64.
            for (int i = 0; i < 36; i++) {
                client.sendChat("/give Packrat dirt 64");
                client.drainGiveGhostWake(150);
                client.readWindowSlotTable(10_000, windowId);
            }
            // Seed one stack inside the chest through the engine (the
            // authoritative state), then shift it out: a full inventory
            // leaves it inside.
            var position = new net.zaminmc.torch.block.BlockPosition(2, 5, 2);
            server.chests().getOrCreate(position).setSlot(0,
                    net.zaminmc.torch.item.ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.DIRT, 5));
            Click out = click(client, windowId, 0, 0, 1);
            assertTrue(out.accepted(), "shift-click out accepted");
            assertEquals(5, out.window()[0][1],
                    "a full inventory leaves the stack inside the chest");

            // Free one hotbar slot (Ctrl+Q drops the whole held stack), retry:
            // the stack moves to the freed slot.
            client.sendDigging(3, 0, 0, 0, 0);
            client.readSpawnItem(10_000);
            client.readItemMetadata(10_000);
            Click retry = click(client, windowId, 0, 0, 1);
            assertTrue(retry.accepted());
            assertEquals(-1, retry.window()[0][0], "the chest drained");
        }
        awaitEmptyServer();
    }
}
