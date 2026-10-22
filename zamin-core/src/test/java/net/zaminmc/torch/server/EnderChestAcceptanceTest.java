package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.chest.EnderChestBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The ender chest family (Slice 29a — the reference port of
 * block/EnderChestBlock + EnderChestBlockEntity + inventory/
 * EnderChestInventory): the per-player storage model. The placed block is
 * only the door — every use opens the PLAYER'S OWN 27-slot grid, the
 * contents never touch the world's chest store, and they travel with the
 * identity across restarts (ZPD v8, the reference's EnderItems).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnderChestAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(EngineServer server, String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private void settle(EngineServer server) throws InterruptedException {
        EngineAwaits.awaitTicks(server.world(), 6, "the settle");
    }

    @Test
    void theEnderWindowOpensOverThePrivateGrid() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession session = join(server, "Ender");
            BlockPosition at = new BlockPosition(100, 60, 100);
            server.ticker().submit(() -> server.world().setBlock(at,
                    EnderChestBlocks.enderChestOf(3)));
            await(() -> EnderChestBlocks.isFamily(server.world().getBlock(at)),
                    "the ender chest block landed");
            // The handler's 8-block body gate (Slice N2): the player
            // stands at the chest before the use.
            server.ticker().submit(() -> session.applyMovement(
                    new Position(99.5, 61.5, 100.5),
                    net.zaminmc.torch.util.Rotation.ZERO, true));
            await(() -> session.position().x() == 99.5, "the player stood at the chest");
            assertNull(server.chests().peek(at), "no world chest state at the door");

            // The use opens the player's OWN grid (EnderChestBlock.use).
            AtomicInteger opened = new AtomicInteger(-1);
            server.useItemOnBlock(session, at, 1, Optional.empty(), 0, opened::set);
            await(() -> opened.get() > 0, "the ender window opened (a container id)");
            assertEquals(PlayerSession.ContainerKind.ENDER_CHEST, session.openContainerKind(),
                    "the session records the ender chest kind");

            // A click through the window: the player holds diamonds (creative),
            // picks them up from the hotbar onto the cursor, then clicks ender
            // slot 2 — the stack lands in the SESSION grid.
            server.ticker().submit(() -> session.inventory().setSlot(
                    session.inventory().heldSlot(),
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.DIAMOND, 7)));
            settle(server);
            server.windowClick(session, opened.get(),
                    54 + session.inventory().heldSlot(), 0, 0, ok -> { });
            settle(server);
            server.windowClick(session, opened.get(), 2, 0, 0, ok -> { });
            settle(server);
            assertEquals(7, session.enderChest().getItem(2).count(),
                    "the clicked stack landed in the private grid");
            assertEquals(BuiltinItems.DIAMOND, session.enderChest().getItem(2).type());
            assertNull(server.chests().peek(at), "the world chest store stayed empty");
            server.closeWindow(session, opened.get());
            settle(server);
            assertEquals(7, session.enderChest().getItem(2).count(),
                    "closing the door keeps the grid");

            // The solid-above gate (EnderChestBlock.use line 80): a stone on
            // top refuses the open — no window id ever assigns.
            server.ticker().submit(() -> server.world().setBlock(
                    at.offset(0, 1, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE));
            settle(server);
            AtomicInteger refused = new AtomicInteger(-1);
            server.useItemOnBlock(session, at, 1, Optional.empty(), 0, refused::set);
            settle(server);
            assertEquals(-1, refused.get(), "the solid lid refused the open");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theEnderContentsTravelWithThePlayer() throws Exception {
        // Boot, open the door, land a stack in the private grid, leave (the
        // persist), shut down.
        EngineServer first = boot();
        PlayerSession player = join(first, "Traveler");
        BlockPosition at = new BlockPosition(104, 60, 104);
        first.ticker().submit(() -> first.world().setBlock(at,
                EnderChestBlocks.enderChestOf(2)));
        await(() -> EnderChestBlocks.isFamily(first.world().getBlock(at)),
                "the ender chest block landed");
        // The handler's 8-block body gate (Slice N2): the player stands
        // at the chest before the use.
        first.ticker().submit(() -> player.applyMovement(
                new Position(103.5, 61.5, 104.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        await(() -> player.position().x() == 103.5, "the player stood at the chest");
        AtomicInteger opened = new AtomicInteger(-1);
        first.useItemOnBlock(player, at, 1, Optional.empty(), 0, opened::set);
        await(() -> opened.get() > 0, "the ender window opened");
        first.ticker().submit(() -> player.inventory().setSlot(
                player.inventory().heldSlot(),
                net.zaminmc.torch.item.ItemStack.of(BuiltinItems.EMERALD, 12)));
        settle(first);
        first.windowClick(player, opened.get(),
                54 + player.inventory().heldSlot(), 0, 0, ok -> { });
        settle(first);
        first.windowClick(player, opened.get(), 5, 0, 0, ok -> { });
        settle(first);
        assertEquals(12, player.enderChest().getItem(5).count(),
                "the emerald stack landed in the grid");
        first.clientDisconnected(player, "test leave");
        first.shutdown(null);

        // Boot again over the same data dir: the same identity rejoins and
        // the private grid returns (ZPD v8 — the reference's EnderItems).
        EngineServer second = boot();
        try {
            PlayerSession returning = join(second, "Traveler");
            assertEquals(12, returning.enderChest().getItem(5).count(),
                    "the saved ender stack came back");
            assertEquals(BuiltinItems.EMERALD, returning.enderChest().getItem(5).type());
            // The fresh world's chest store never learned about any of it.
            assertNull(second.chests().peek(at), "the private grid never touched the world");
        } finally {
            second.shutdown(null);
        }
    }

    @Test
    void theBreakYieldsObsidianAndSilkTakesTheChest() {
        // EnderChestBlock lines 48-60: the foreign drop is eight obsidian;
        // silk touch takes the chest itself.
        var service = new net.zaminmc.torch.server.interaction.DropService(
                new java.util.Random(7L));
        var broken = EnderChestBlocks.enderChestOf(4);
        // The obsidian-tier harvest gate: only a diamond pickaxe yields.
        var pickaxe = BuiltinItems.DIAMOND_PICKAXE;
        var plain = service.dropsFor(broken, pickaxe, false, 0);
        assertEquals(1, plain.size());
        assertEquals(BuiltinItems.OBSIDIAN, plain.get(0).type());
        assertEquals(8, plain.get(0).count());
        var silk = service.dropsFor(broken, pickaxe, true, 0);
        assertEquals(1, silk.size());
        assertEquals(BuiltinItems.ENDER_CHEST, silk.get(0).type());
        assertEquals(1, silk.get(0).count());
        // Without the tier: nothing (the historical hand-break of obsidian).
        assertTrue(service.dropsFor(broken, null, false, 0).isEmpty(),
                "no diamond tier, no obsidian");
    }
}
