package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.player.PlayerSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The §27 window family's re-sync wave over the real wire: a container
 * that changes while a viewer holds its window open must re-sync the
 * viewer's Window Items — the plugin's {@code setSlot} path, the hopper
 * system's own transfer into an open-viewed chest, and the engine side's
 * dispenser write all bump the container's slotsSerial, and the per-tick
 * view fan re-syncs only on a real change (the furnace's own diff path
 * generalized to the families that had none).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WindowResyncIntegrationTest extends ProtocolTestBase {

    /** The scripted client never answers keep-alives; the multi-read flows must not be kicked. */
    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    // legacy ids used by the assertions (community data)
    private static final int DIAMOND = 264;
    private static final int DIRT = 3;
    private static final int CHEST = 54;

    private static final BlockPosition CHEST_AT = new BlockPosition(2, 5, 2);
    private static final BlockPosition HOPPER_AT = new BlockPosition(2, 6, 2);

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
        return server.playerRegistry().byName(name).orElseThrow();
    }

    /** Joins, places a chest through the held item (the proven placement flow), opens it, returns the window id. */
    private int joinPlaceAndOpenChest(TestClient18 client, String name) throws Exception {
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
        awaitPlayer(name);
        client.readWindowItems(10_000); // join sync: all empty

        // stock a chest, then place it: top face at (2,4,2) -> lands at (2,5,2)
        client.sendChat("/give chest 1");
        client.readWindowSlotTable(10_000); // the give's sync
        client.sendBlockPlacement(2, 4, 2, 1, CHEST);
        client.readBlockChange(10_000); // the placement's publish
        client.readWindowSlotTable(10_000); // the held chest was consumed

        // right-click opens the chest GUI
        client.sendBlockPlacement(2, 5, 2, 1, -1);
        Object[] open = client.readOpenWindow(10_000);
        assertEquals("minecraft:chest", open[1]);
        assertEquals(27, open[2], "the GUI's own slots: 3 rows of 9");
        int[][] window = client.readWindowSlotTable(10_000, (Integer) open[0]);
        assertEquals(63, window.length, "27 chest slots + 27 main + 9 hotbar");
        return (Integer) open[0];
    }

    @Test
    void aPluginsSetSlotOnAnOpenViewedChestResyncsTheViewer() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            int windowId = joinPlaceAndOpenChest(client, "Resyncer");
            client.readWindowSlotTable(10_000, windowId); // the open's authoritative contents

            // the plugin's write path (§27.6): the public container view on
            // the world's owner context — the engine object behind the handle
            server.world().execute(() -> {
                Optional<net.zaminmc.torch.container.Containers.Chest> chest =
                        server.world().containers().chestAt(CHEST_AT);
                assertTrue(chest.isPresent(), "the open-viewed chest answers the lookup");
                chest.orElseThrow().setSlot(4, ItemStack.of(BuiltinItems.DIAMOND, 7));
            });

            // the per-tick view fan re-syncs the open viewer's Window Items
            int[][] window = client.readWindowSlotTable(10_000, windowId);
            assertEquals(DIAMOND, window[4][0], "chest slot 4 took the diamond");
            assertEquals(7, window[4][1]);
            assertEquals(-1, window[5][0], "the neighboring chest slot stays empty");

            // the world state matches what the viewer sees
            awaitCondition(() -> server.chests().peek(CHEST_AT) != null, "chest state materialized");
            assertEquals(7, server.chests().peek(CHEST_AT).snapshotSlots()[4].count(),
                    "the diamond is inside the chest");

            // close: the chest KEEPS its contents (world state)
            client.sendCloseWindow(windowId);
            client.readWindowSlotTable(10_000, 0, true); // window 0 resync after close
        }
    }

    @Test
    void theHoppersOwnTransferIntoAnOpenViewedChestResyncsTheViewer() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            // reopen the same chest (the diamond from the previous test waits inside)
            client.sendHandshake(47, 2);
            client.sendLoginStart("Feeder");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Feeder");
            client.readWindowItems(10_000); // join sync: all empty

            client.sendBlockPlacement(2, 5, 2, 1, -1);
            Object[] open = client.readOpenWindow(10_000);
            int windowId = (Integer) open[0];
            int[][] opened = client.readWindowSlotTable(10_000, windowId);
            assertEquals(DIAMOND, opened[4][0], "the diamond from the earlier test waits inside");

            // a down-facing hopper directly above the chest: its facing cell
            // IS the chest, so its push lands in the open-viewed container
            // (placed through the engine's own dispatch, the owner context)
            server.world().execute(() -> server.world().setBlock(
                    HOPPER_AT, net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true)));

            // one dirt in the hopper through the public view (the plugin path);
            // the hopper's own 8-tick cadence then pushes it into the chest —
            // a DIFFERENT writer than the plugin, the same serial walk
            server.world().execute(() -> {
                server.hopper().getOrCreate(HOPPER_AT)
                        .setItem(0, ItemStack.of(BuiltinItems.DIRT, 1));
            });

            // the transfer lands in the world first, then the fan re-syncs
            awaitCondition(() -> {
                var chest = server.chests().peek(CHEST_AT);
                return chest != null && chest.snapshotSlots()[0].count() == 1;
            }, "the hopper pushed its dirt into the chest");
            int[][] window = client.readWindowSlotTable(10_000, windowId);
            assertEquals(DIRT, window[0][0], "the hopper's transfer re-synced the chest window");
            assertEquals(1, window[0][1]);
            // one item per transfer (pushItem's removeItem(i, 1))
            assertEquals(DIAMOND, window[4][0], "the diamond still waits at slot 4");

            client.sendCloseWindow(windowId);
            client.readWindowSlotTable(10_000, 0, true);
        }
    }

    @Test
    void aPluginsSetSlotOnAnOpenViewedHopperResyncsTheViewer() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Hopperer");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Hopperer");
            client.readWindowItems(10_000); // join sync: all empty

            // the hopper fixture stands above the chest from the earlier tests
            server.world().execute(() -> server.world().setBlock(
                    HOPPER_AT, net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true)));
            client.sendBlockPlacement(2, 6, 2, 1, -1);
            Object[] open = client.readOpenWindow(10_000);
            int windowId = (Integer) open[0];
            assertEquals("minecraft:hopper", open[1]);
            assertEquals(5, open[2], "the GUI's own slots: the hopper's five");
            int[][] opened = client.readWindowSlotTable(10_000, windowId);
            assertEquals(41, opened.length, "5 hopper slots + 27 main + 9 hotbar");

            // the plugin's write path over the public hopper view
            server.world().execute(() -> {
                Optional<net.zaminmc.torch.container.Containers.Hopper> hopper =
                        server.world().containers().hopperAt(HOPPER_AT);
                assertTrue(hopper.isPresent(), "the open-viewed hopper answers the lookup");
                hopper.orElseThrow().setSlot(2, ItemStack.of(BuiltinItems.DIRT, 3));
            });

            int[][] window = client.readWindowSlotTable(10_000, windowId);
            assertEquals(DIRT, window[2][0], "hopper slot 2 took the dirt");
            assertEquals(3, window[2][1]);
            assertEquals(-1, window[0][0], "the hopper's own slots stay untouched elsewhere");

            client.sendCloseWindow(windowId);
            client.readWindowSlotTable(10_000, 0, true);
        }
    }

    @Test
    void anEngineSideDispenserWriteResyncsTheOpenViewer() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Dispenserer");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Dispenserer");
            client.readWindowItems(10_000); // join sync: all empty

            // a dispenser at the column: the fixture replaces the hopper
            server.world().execute(() -> server.world().setBlock(
                    HOPPER_AT, net.zaminmc.torch.server.dispenser.DispenserBlocks.dispenserOf(1, false)));
            client.sendBlockPlacement(2, 6, 2, 1, -1);
            Object[] open = client.readOpenWindow(10_000);
            int windowId = (Integer) open[0];
            assertEquals("minecraft:dispenser", open[1]);
            assertEquals(9, open[2], "the GUI's own slots: 3 rows of 3");
            int[][] opened = client.readWindowSlotTable(10_000, windowId);
            assertEquals(45, opened.length, "9 container slots + 27 main + 9 hotbar");

            // the engine-side write (the dispenser has no public container
            // view yet — the honest gap this test documents): the open path
            // materialized the block entity, so the write rides the same BE
            server.world().execute(() -> {
                var dispenser = server.dispensers().peek(HOPPER_AT);
                assertTrue(dispenser != null, "the open-viewed dispenser answers the lookup");
                dispenser.setItem(3, ItemStack.of(BuiltinItems.DIRT, 5));
            });

            int[][] window = client.readWindowSlotTable(10_000, windowId);
            assertEquals(DIRT, window[3][0], "dispenser slot 3 took the dirt");
            assertEquals(5, window[3][1]);

            client.sendCloseWindow(windowId);
            client.readWindowSlotTable(10_000, 0, true);
        }
    }
}
