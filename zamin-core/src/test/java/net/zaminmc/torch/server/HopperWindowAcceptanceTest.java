package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The hopper GUI window (the reference's HopperBlock.use + HopperMenu —
 * Slice 22i): the right-click open over the placed hopper block, the
 * click semantics through the window's wire layout (5 container slots,
 * the player tail), and the hopper-contents ZHD persistence across a
 * restart.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HopperWindowAcceptanceTest {

    @org.junit.jupiter.api.io.TempDir
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
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    @Test
    void theHopperWindowOpensAndServesItsClicks() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession session = join(server, "Hana");
            BlockPosition at = new BlockPosition(90, 60, 90);
            server.ticker().submit(() ->
                    server.world().setBlock(at, HopperBlocks.hopperOf(0, true)));
            // The handler's 8-block body gate (Slice N2): the player
            // stands at the hopper before the use.
            server.ticker().submit(() -> session.applyMovement(
                    new Position(90.5, 61.5, 91.5),
                    net.zaminmc.torch.util.Rotation.ZERO, true));
            awaitSettled(server);

            // The right-click open (HopperBlock.use): the window id assigns,
            // the kind records, the entity lazily creates.
            AtomicInteger opened = new AtomicInteger(-1);
            server.useItemOnBlock(session, at, 1, java.util.Optional.empty(), 0,
                    opened::set);
            awaitSettled(server);
            assertTrue(opened.get() > 0, "the hopper window opened (a container id)");
            assertEquals(PlayerSession.ContainerKind.HOPPER, session.openContainerKind(),
                    "the session records the hopper kind");

            // A click through the window: the player holds diamonds (creative
            // inventory path), picks them up from the hotbar (wire slot 32 =
            // hotbar 0) onto the cursor, then clicks hopper slot 2 — the
            // stack lands in the hopper entity.
            server.ticker().submit(() -> session.inventory().setSlot(
                    session.inventory().heldSlot(),
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.DIAMOND, 7)));
            awaitSettled(server);
            // Pick up from the hotbar (the cursor take).
            server.windowClick(session, opened.get(),
                    32 + session.inventory().heldSlot(), 0, 0, ok -> { });
            awaitSettled(server);
            // Place into the hopper's slot 2.
            server.windowClick(session, opened.get(), 2, 0, 0, ok -> { });
            awaitSettled(server);
            var hopper = server.hopper().peek(at);
            assertTrue(hopper != null, "the entity exists");
            assertEquals(7, hopper.getItem(2).count(), "the clicked stack landed");
            assertEquals(BuiltinItems.DIAMOND, hopper.getItem(2).type());

            // The shift-click out: the stack returns to the inventory.
            server.windowClick(session, opened.get(), 2, 0, 1, ok -> { });
            awaitSettled(server);
            assertTrue(hopper.getItem(2).isEmpty(), "the quick-move emptied the slot");
            assertEquals(7, session.inventory().countOf(BuiltinItems.DIAMOND),
                    "the inventory received the stack back");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theHopperContentsSurviveTheRestart() throws Exception {
        // Boot, place a hopper with contents, save, shut down.
        EngineServer first = boot();
        BlockPosition at = new BlockPosition(94, 60, 94);
        server_ticker_submit(first, at);
        awaitCondition(() -> first.hopper().peek(at) != null,
                "the placed hopper's entity exists");
        first.ticker().submit(() ->
                first.hopper().getOrCreate(at).setItem(1,
                        net.zaminmc.torch.item.ItemStack.of(BuiltinItems.EMERALD, 12)));
        awaitCondition(() -> {
            var hopper = first.hopper().peek(at);
            return hopper != null && hopper.getItem(1).count() == 12;
        }, "the emerald stack landed");
        first.shutdown(null);

        // Boot again over the same data dir: the contents restore.
        EngineServer second = boot();
        try {
            var hopper = second.hopper().peek(at);
            assertTrue(hopper != null, "the hopper state restored");
            assertEquals(12, hopper.getItem(1).count(), "the saved stack came back");
            assertEquals(BuiltinItems.EMERALD, hopper.getItem(1).type());
        } finally {
            second.shutdown(null);
        }
    }

    /** Polls a condition up to five seconds (robust under load). */
    private static void awaitCondition(java.util.function.BooleanSupplier condition,
                                       String description) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for " + description, e);
            }
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static void server_ticker_submit(EngineServer server, BlockPosition at) {
        server.ticker().submit(() ->
                server.world().setBlock(at, HopperBlocks.hopperOf(0, true)));
    }

    /** The submit drains at the head of the next tick (<= 100 ms). */
    private void awaitSettled(EngineServer server) throws InterruptedException {
        EngineAwaits.awaitTicks(server.world(), 6, "the submit drains at the head of the next tick");
    }
}
