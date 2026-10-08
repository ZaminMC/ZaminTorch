package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.ServerState;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The restart proof (§407): place a block, stop the server, start a fresh
 * server process on the same data, and the world must have survived.
 */
class PersistenceAcceptanceTest {

    @TempDir
    Path dataDir;

    @Test
    void worldSurvivesShutdownAndRestart() throws Exception {
        // --- first life: join, place a block, shut down -------------------------
        EngineConfig first = new EngineConfig("127.0.0.1", 0, "persisted", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer serverOne = new EngineServer(first);
        serverOne.start();
        var accepted = serverOne.joinRequest(link(), "Builder", UUID.randomUUID());
        PlayerSession builder = ((EngineBridge.Accepted) accepted).session();
        serverOne.blockInteraction().submitPlace(builder, new BlockPosition(3, 4, 3), 1, BuiltinBlocks.STONE);
        BlockPosition placed = new BlockPosition(3, 5, 3);
        await(() -> serverOne.world().getBlock(placed).equals(BuiltinBlocks.STONE), "stone placed");
        // Two deltas: the placed stone AND the grass it covers decaying to dirt
        // (the historical decay, driven by the scheduled block-update system).
        assertEquals(2, serverOne.world().deltaCount());

        serverOne.shutdown(null);
        awaitState(serverOne);

        // --- second life: fresh process state, same data ------------------------
        EngineConfig second = new EngineConfig("127.0.0.1", 0, "persisted", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer serverTwo = new EngineServer(second);
        serverTwo.start();

        await(() -> serverTwo.world().getBlock(placed).equals(BuiltinBlocks.STONE),
                "placed block survived the restart");
        assertEquals(2, serverTwo.world().deltaCount());
        serverTwo.shutdown(null);
        awaitState(serverTwo);
    }

    @Test
    void corruptDeltaFilePreservedAndWorldStillBoots() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "corrupt", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        server.shutdown(null);

        Path deltaFile = dataDir.resolve("corrupt").resolve("data").resolve("zamin-delta.bin");
        java.nio.file.Files.writeString(deltaFile, "this is not a ZWD file");

        EngineServer restarted = new EngineServer(config);
        restarted.start(); // must boot from generator, not crash (§286)
        assertEquals(0, restarted.world().deltaCount());
        assertTrue(java.nio.file.Files.exists(deltaFile.resolveSibling("zamin-delta.bin.corrupt")),
                "corrupt file must be preserved for diagnosis");
        restarted.shutdown(null);
    }

    @Test
    void saveOnIdleServerIsIdempotent() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "idle", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        server.saveAllNow();
        server.saveAllNow();
        server.shutdown(null);
        awaitState(server);
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private void awaitState(EngineServer server) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (server.state() == net.zaminmc.torch.ServerState.STOPPED) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Server did not reach STOPPED");
    }
}
