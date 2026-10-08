package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Window clicks over the real wire (§429): the client clicks, the engine
 * decides, the transaction is confirmed and the authoritative state re-syncs.
 * Every click answers with Confirm Transaction -> cursor Set Slot -> Window
 * Items, in that order (commit -> verdict -> resync).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WindowClickIntegrationTest extends ProtocolTestBase {

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
        return server.playerRegistry().byName(name).orElseThrow();
    }

    /** Reads one full click response: {accepted, cursorId, cursorCount, slot, itemId, itemCount}. */
    private int[] readClickResponse(TestClient18 client) throws Exception {
        int[] confirm = client.readConfirmTransaction(10_000);
        int[] cursor = client.readCursorSlot(10_000);
        int[] window = client.readWindowItems(10_000);
        return new int[]{confirm[2], cursor[1], cursor[2], window[2], window[3], window[4]};
    }

    @Test
    void clicksMoveItemsAndCraftSlotsAreRejected() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Clicker");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Clicker");
            client.readWindowItems(10_000); // join sync: empty

            // 30 dirt into hotbar slot 0 (wire slot 36)
            client.sendChat("/give dirt 30");
            client.readWindowItems(10_000);

            // shift-click the hotbar stack: quick-move to main slot 9
            client.sendWindowClick(36, 0, 1, 0);
            int[] quick = readClickResponse(client);
            assertEquals(1, quick[0], "accepted");
            assertEquals(-1, quick[1], "cursor empty");
            assertEquals(9, quick[3], "the stack now sits in main slot 9");
            assertEquals(3, quick[4]);

            // left-click picks the whole stack onto the cursor
            client.sendWindowClick(9, 0, 0, 0);
            int[] picked = readClickResponse(client);
            assertEquals(1, picked[0]);
            assertEquals(3, picked[1], "cursor carries dirt");
            assertEquals(30, picked[2], "the whole stack");
            assertEquals(-1, picked[3], "no slot holds anything");

            // second left-click places it back into the hotbar
            client.sendWindowClick(36, 0, 0, 0);
            int[] placed = readClickResponse(client);
            assertEquals(1, placed[0]);
            assertEquals(-1, placed[1], "cursor empty again");
            assertEquals(36, placed[3], "the stack returned to hotbar slot 0");

            // number key 2 (mode 2, button 1): main slot 9 <-> hotbar 1 swap
            client.sendWindowClick(9, 1, 2, 0);
            int[] swapped = readClickResponse(client);
            assertEquals(1, swapped[0], "number-key swap accepted");
            assertEquals(36, swapped[3], "both swap sides are empty: the dirt stays put");

            // craft-grid clicks are accepted window state now: an empty-hand
            // click on an empty cell is an accepted no-op and the state resyncs
            client.sendWindowClick(1, 0, 0, 0); // craft grid slot
            int[] craftNoop = readClickResponse(client);
            assertEquals(1, craftNoop[0], "craft slot click accepted");
            assertEquals(-1, craftNoop[1], "cursor still empty");
            assertEquals(36, craftNoop[3], "the authoritative state still holds the dirt");

            // drag painting rejected as well
            client.sendWindowClick(-1, 0, 5, 5);
            int[] dragRejected = readClickResponse(client);
            assertEquals(0, dragRejected[0], "drag painting rejected");

            // right-click splits half onto the cursor (slot 36: 30 dirt)
            client.sendWindowClick(36, 1, 0, 0);
            int[] half = readClickResponse(client);
            assertEquals(1, half[0]);
            assertEquals(3, half[1], "half of the stack is on the cursor");
            assertEquals(15, half[2]);
            assertEquals(36, half[3], "the remainder stays in the hotbar");
            assertEquals(15, half[5], "fifteen dirt remain in the slot");
        }
        awaitCondition(() -> server.players().isEmpty(), "player removed after disconnect");
    }
}
