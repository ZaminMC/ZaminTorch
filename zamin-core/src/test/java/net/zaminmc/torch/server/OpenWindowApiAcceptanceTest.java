package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.entity.WindowKind;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The §27 window family's public read surface over the live engine: the
 * open-window view through {@link Player} — the session IS the handle
 * (§27.5). The kind/window-id/position/mount-id quadruple follows the
 * engine's own window state through every open family and the close.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenWindowApiAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.SURVIVAL);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for: " + description);
    }

    @Test
    void theOpenWindowReadCarriesTheEngineState() throws Exception {
        server = boot();
        try {
            PlayerSession session = join("Windower");
            Player.OpenWindow view = session.openWindow();

            // nothing open: the neutral quadruple
            assertEquals(WindowKind.NONE, view.kind());
            assertEquals(-1, view.windowId());
            assertTrue(view.position().isEmpty(), "no position while nothing is open");
            assertEquals(-1, view.mountId());

            // the position-keyed family: the chest
            BlockPosition chestAt = new BlockPosition(4, 40, 4);
            session.openContainerWindow(7, PlayerSession.ContainerKind.CHEST, chestAt);
            assertEquals(WindowKind.CHEST, view.kind());
            assertEquals(7, view.windowId());
            assertEquals(chestAt, view.position().orElseThrow());
            assertEquals(-1, view.mountId(), "the chest window is position-keyed, never mount-keyed");

            // the private/session family: the crafting table carries no position
            session.openContainerWindow(8, PlayerSession.ContainerKind.CRAFTING_TABLE, null);
            assertEquals(WindowKind.CRAFTING_TABLE, view.kind());
            assertEquals(8, view.windowId());
            assertTrue(view.position().isEmpty(), "the crafting table is session-private");
            assertEquals(-1, view.mountId());

            // the mount-keyed family: the chest minecart
            session.openChestMinecartWindow(9, 42);
            assertEquals(WindowKind.CHEST_MINECART, view.kind());
            assertEquals(9, view.windowId());
            assertTrue(view.position().isEmpty(), "the cart window is mount-keyed, never position-keyed");
            assertEquals(42, view.mountId());

            // the close: back to the neutral quadruple
            session.closeContainerWindow();
            assertEquals(WindowKind.NONE, view.kind());
            assertEquals(-1, view.windowId());
            assertTrue(view.position().isEmpty());
            assertEquals(-1, view.mountId());

            // the same view object stays live across the transitions (the
            // engine object IS the handle — no per-read wrapper)
            session.openContainerWindow(11, PlayerSession.ContainerKind.FURNACE, chestAt);
            assertEquals(WindowKind.FURNACE, view.kind());
            assertEquals(11, view.windowId());
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theOffLaneReadIsBestEffort() throws Exception {
        server = boot();
        try {
            PlayerSession session = join("OffLaneWindower");
            session.openContainerWindow(5, PlayerSession.ContainerKind.HOPPER,
                    new BlockPosition(6, 40, 6));
            // the adapter's cross-thread sync contract: a foreign thread
            // reads the same live view (the volatile publish)
            AtomicReference<WindowKind> seen = new AtomicReference<>(WindowKind.NONE);
            Thread offLane = new Thread(() -> seen.set(session.openWindow().kind()));
            offLane.start();
            offLane.join();
            assertEquals(WindowKind.HOPPER, seen.get(), "the off-lane read sees the open hopper");
        } finally {
            server.shutdown(null);
        }
    }
}
