package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.player.ContainerOpenEvent;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE SHARED CONTAINER-OPEN OBSERVATION (§27.7 — the standing row's
 * retirement): every container family's open fires the ONE event through
 * the session's own seam — the block containers and the entity forms —
 * the kind naming the family, the position carrying the block cell for
 * the block forms, the window id the wire's own counter. Post-commit
 * observation (the ChestOpenEvent convention): handlers observe, they do
 * not veto.
 */
class ContainerOpenEventTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private PlayerSession player;

    @AfterEach
    void stop() {
        if (server != null) {
            server.shutdown(null);
        }
    }

    private void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "contopen" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    private PlayerSession join(String name) {
        List<String> sink = new ArrayList<>();
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
            @Override public void blockResync(net.zaminmc.torch.block.BlockPosition position,
                    net.zaminmc.torch.block.BlockType type) {
                sink.add(position + "=" + type.identifier());
            }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void everyContainerFamilyFiresTheSharedObservation() throws Exception {
        boot();
        player = join("Opener");
        List<ContainerOpenEvent> seen = new CopyOnWriteArrayList<>();
        var sub = server.events().on(ContainerOpenEvent.class, seen::add);
        try {
            // The chest family: the plain chest at (4, 4, 4).
            BlockPosition chest = new BlockPosition(4, 4, 4);
            server.ticker().submit(() ->
                    server.world().setBlock(chest, BuiltinBlocks.CHEST));
            await(() -> server.world().getBlock(chest).equals(BuiltinBlocks.CHEST),
                    "the chest seeded");
            server.useItemOnBlock(player, chest, 1, java.util.Optional.empty(), id -> { });
            await(() -> !seen.isEmpty(), "the chest's open fired the shared event");
            ContainerOpenEvent first = seen.get(0);
            assertEquals("CHEST", first.kind(), "the kind names the family");
            assertEquals(chest, first.position(), "the position carries the block cell");
            assertEquals(player.uuid(), first.openerId(), "the opener rides the event");
            assertTrue(first.windowId() > 0, "the window id is the wire's own counter");

            // The furnace family: the same shared event, the family's own kind
            // (the chest window closes first — the engine's own one-window law).
            server.closeWindow(player, first.windowId());
            BlockPosition furnace = new BlockPosition(2, 4, 4);
            server.ticker().submit(() ->
                    server.world().setBlock(furnace, BuiltinBlocks.FURNACE));
            await(() -> server.world().getBlock(furnace).equals(BuiltinBlocks.FURNACE),
                    "the furnace seeded");
            server.useItemOnBlock(player, furnace, 1, java.util.Optional.empty(), id -> { });
            await(() -> seen.size() >= 2, "the furnace's open fired the shared event");
            assertEquals("FURNACE", seen.get(1).kind(), "the furnace names its own kind");
            assertEquals(furnace, seen.get(1).position(), "the furnace's cell rides the event");

            // The dispenser family: the third kind through the same seam.
            BlockPosition dispenser = new BlockPosition(4, 4, 2);
            var dispenserType = net.zaminmc.torch.server.dispenser.DispenserBlocks
                    .dispenserOf(5, false);
            server.ticker().submit(() -> server.world().setBlock(dispenser, dispenserType));
            await(() -> server.world().getBlock(dispenser).equals(dispenserType),
                    "the dispenser seeded");
            server.useItemOnBlock(player, dispenser, 1, java.util.Optional.empty(), id -> { });
            await(() -> seen.size() >= 3, "the dispenser's open fired the shared event");
            assertEquals("DISPENSER", seen.get(2).kind(), "the dispenser names its own kind");
        } finally {
            sub.unsubscribe();
        }
    }
}