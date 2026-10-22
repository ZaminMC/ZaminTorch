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
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The §27 window family's re-sync wave over the real wire: a container
 * that changes while a viewer holds its window open must re-sync the
 * viewer's Window Items — the plugin's {@code setSlot} path, the hopper
 * system's own transfer into an open-viewed chest, and the engine side's
 * dispenser write all bump the container's slotsSerial, and the per-tick
 * view fan re-syncs only on a real change (the furnace's own diff path
 * generalized to the families that had none).
 *
 * <p>Every test is self-contained on its own ground column (the flat
 * world's grass at y=4) with its own player: any execution order, any
 * subset, one class-shared world. The columns sit inside the spawn chunk
 * (x/z within 0..15).</p>
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

    // per-test columns (all inside the spawn chunk, one column per test — no shared fixture).
    // The client-placed chests must sit inside the survival placement reach of
    // the spawn point (0.5, 5, 0.5): the eye-to-block-center squared distance
    // against 4.5^2 — column 2 (x^2+z^2=8) and column (3,1) (=10) pass; the
    // farther columns are engine-setBlock only (the open path carries no gate).
    private static final BlockPosition GROUND_A = new BlockPosition(2, 4, 2);
    private static final BlockPosition CHEST_A = new BlockPosition(2, 5, 2);
    private static final BlockPosition GROUND_B = new BlockPosition(3, 4, 1);
    private static final BlockPosition CHEST_B = new BlockPosition(3, 5, 1);
    private static final BlockPosition HOPPER_B = new BlockPosition(3, 6, 1);
    private static final BlockPosition HOPPER_C = new BlockPosition(10, 6, 2);
    private static final BlockPosition DISPENSER_D = new BlockPosition(14, 6, 2);

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
        return server.playerRegistry().byName(name).orElseThrow();
    }

    /**
     * Joins, places a chest through the held item (the proven placement
     * flow) at the column's own ground, opens it, reads the open's
     * authoritative Window Items, and returns the window id. The chest at
     * {@code chestAt} lands on the ground block's top face — the flat
     * world's grass row at y=4 means the chest stands at y=5.
     */
    private int joinPlaceAndOpenChest(TestClient18 client, String name,
                                      BlockPosition ground, BlockPosition chestAt) throws Exception {
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
        awaitPlayer(name);
        client.readWindowItems(10_000); // join sync: all empty

        // stock a chest, then place it: the ground's top face -> the chest above it
        client.sendChat("/give chest 1");
        client.readWindowSlotTable(10_000); // the give's sync
        client.sendBlockPlacement(ground.x(), ground.y(), ground.z(), 1, CHEST);
        client.readBlockChange(10_000); // the placement's publish
        client.readWindowSlotTable(10_000); // the held chest was consumed

        // right-click opens the chest GUI
        client.sendBlockPlacement(chestAt.x(), chestAt.y(), chestAt.z(), 1, -1);
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
            int windowId = joinPlaceAndOpenChest(client, "Resyncer", GROUND_A, CHEST_A);

            // the plugin's write path (§27.6): the public container view on
            // the world's owner context — the engine object behind the handle
            server.world().execute(() -> {
                Optional<net.zaminmc.torch.container.Containers.Chest> chest =
                        server.world().containers().chestAt(CHEST_A);
                assertTrue(chest.isPresent(), "the open-viewed chest answers the lookup");
                chest.orElseThrow().setSlot(4, ItemStack.of(BuiltinItems.DIAMOND, 7));
            });

            // the per-tick view fan re-syncs the open viewer's Window Items
            int[][] window = client.readWindowSlotTable(10_000, windowId);
            assertEquals(DIAMOND, window[4][0], "chest slot 4 took the diamond");
            assertEquals(7, window[4][1]);
            assertEquals(-1, window[5][0], "the neighboring chest slot stays empty");

            // the world state matches what the viewer sees
            awaitCondition(() -> server.chests().peek(CHEST_A) != null, "chest state materialized");
            assertEquals(7, server.chests().peek(CHEST_A).snapshotSlots()[4].count(),
                    "the diamond is inside the chest");

            // close: the chest KEEPS its contents (world state)
            client.sendCloseWindow(windowId);
            client.readWindowSlotTable(10_000, 0, true); // window 0 resync after close
        }
    }

    @Test
    void theHoppersOwnTransferIntoAnOpenViewedChestResyncsTheViewer() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            // self-contained: this test's own chest at its own column
            int windowId = joinPlaceAndOpenChest(client, "Feeder", GROUND_B, CHEST_B);

            // a diamond at slot 4 through the plugin's write path (the proven
            // arm) — the hopper's transfer must then land in the FIRST EMPTY
            // slot (the vanilla pushItem walk), not on top of it
            server.world().execute(() -> server.world().containers().chestAt(CHEST_B)
                    .orElseThrow().setSlot(4, ItemStack.of(BuiltinItems.DIAMOND, 1)));
            int[][] seeded = client.readWindowSlotTable(10_000, windowId);
            assertEquals(DIAMOND, seeded[4][0], "the plugin's seed landed at slot 4");
            assertEquals(1, seeded[4][1]);

            // a down-facing hopper directly above the chest: its facing cell
            // IS the chest, so its push lands in the open-viewed container
            // (placed through the engine's own dispatch, the owner context)
            server.world().execute(() -> server.world().setBlock(
                    HOPPER_B, net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true)));

            // one dirt in the hopper through the public view (the plugin path);
            // the hopper's own 8-tick cadence then pushes it into the chest —
            // a DIFFERENT writer than the plugin, the same serial walk
            server.world().execute(() -> {
                server.hopper().getOrCreate(HOPPER_B)
                        .setItem(0, ItemStack.of(BuiltinItems.DIRT, 1));
            });

            // the transfer lands in the world first, then the fan re-syncs
            awaitCondition(() -> {
                var chest = server.chests().peek(CHEST_B);
                return chest != null && chest.snapshotSlots()[0].count() == 1;
            }, "the hopper pushed its dirt into the chest");
            int[][] window = client.readWindowSlotTable(10_000, windowId);
            assertEquals(DIRT, window[0][0], "the hopper's transfer re-synced the chest window");
            assertEquals(1, window[0][1]);
            // one item per transfer (pushItem's removeItem(i, 1)), the first empty slot
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

            // this test's own hopper at its own column — facing down into
            // open air (nothing to push into: no cross-test coupling)
            server.world().execute(() -> server.world().setBlock(
                    HOPPER_C, net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true)));
            // The handler's 8-block body gate (Slice N2): the engine-side
            // teleport walks the player to the hopper's column (the real
            // client's movement guard rejects teleport-sized jumps — the
            // authoritative move is the test's own verb here).
            server.teleportPlayer(awaitPlayer("Hopperer"),
                    new net.zaminmc.torch.util.Position(
                            HOPPER_C.x() - 1.5, HOPPER_C.y() + 0.5,
                            HOPPER_C.z() + 0.5));
            EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
            // The teleport window's acknowledgment (Slice N5): the real
            // client answers the re-anchor Position And Look with its own
            // position at the anchor — the use gate opens.
            client.sendPosition(HOPPER_C.x() - 1.5, HOPPER_C.y() + 0.5,
                    HOPPER_C.z() + 0.5, true);
            EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 150 wall settle)");
            client.sendBlockPlacement(HOPPER_C.x(), HOPPER_C.y(), HOPPER_C.z(), 1, -1);
            Object[] open = client.readOpenWindow(10_000);
            int windowId = (Integer) open[0];
            assertEquals("minecraft:hopper", open[1]);
            assertEquals(5, open[2], "the GUI's own slots: the hopper's five");
            int[][] opened = client.readWindowSlotTable(10_000, windowId);
            assertEquals(41, opened.length, "5 hopper slots + 27 main + 9 hotbar");

            // the plugin's write path over the public hopper view
            server.world().execute(() -> {
                Optional<net.zaminmc.torch.container.Containers.Hopper> hopper =
                        server.world().containers().hopperAt(HOPPER_C);
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

            // this test's own dispenser at its own column
            server.world().execute(() -> server.world().setBlock(
                    DISPENSER_D, net.zaminmc.torch.server.dispenser.DispenserBlocks.dispenserOf(1, false)));
            // The handler's 8-block body gate (Slice N2): the engine-side
            // teleport walks the player to the dispenser's column.
            server.teleportPlayer(awaitPlayer("Dispenserer"),
                    new net.zaminmc.torch.util.Position(
                            DISPENSER_D.x() - 1.5, DISPENSER_D.y() + 0.5,
                            DISPENSER_D.z() + 0.5));
            EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
            // The teleport window's acknowledgment (Slice N5).
            client.sendPosition(DISPENSER_D.x() - 1.5, DISPENSER_D.y() + 0.5,
                    DISPENSER_D.z() + 0.5, true);
            EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 150 wall settle)");
            client.sendBlockPlacement(DISPENSER_D.x(), DISPENSER_D.y(), DISPENSER_D.z(), 1, -1);
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
                var dispenser = server.dispensers().peek(DISPENSER_D);
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
