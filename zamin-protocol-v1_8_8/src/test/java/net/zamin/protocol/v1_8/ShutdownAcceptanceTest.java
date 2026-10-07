package net.zamin.protocol.v1_8;

import net.zamin.api.ServerState;
import net.zamin.engine.EngineServer;
import net.zamin.engine.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shutdown acceptance (§121, §302): shutdown under load — a client connected
 * mid-play — must not deadlock, must disconnect the client, and must leave no
 * leaked lifecycle state behind.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShutdownAcceptanceTest {

    @Test
    void shutdownWithConnectedClientCompletesCleanly() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "world", "shutdown test", 20, 2, 20);
        EngineServer server = new EngineServer(config);
        server.start();
        V18ProtocolServer adapter = new V18ProtocolServer(server, 250);
        adapter.start(server);

        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("ShutdownProbe");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            // Server stopping while the client is actively in play state.
            Thread stopper = new Thread(() -> server.shutdown(adapter::shutdown), "test-stopper");
            stopper.start();

            // The client receives a disconnect (play-state 0x40) or the connection closes.
            boolean disconnected = false;
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && !disconnected) {
                try {
                    byte[] packet = client.readPacket();
                    if (client.readPacketId(packet) == Protocol18.S2C_DISCONNECT) {
                        disconnected = true;
                    }
                } catch (java.io.IOException closed) {
                    disconnected = true; // socket closed without a disconnect frame is also valid
                }
            }
            assertTrue(disconnected, "client must be disconnected during shutdown");

            stopper.join(10_000);
            assertFalse(stopper.isAlive(), "shutdown must not deadlock");

            awaitCondition(() -> server.state() == ServerState.STOPPED,
                    "server reached STOPPED");
            assertEquals(0, server.players().size(), "no ghost players after shutdown");
            assertFalse(adapter.isRunning(), "adapter released");
        }
    }

    @Test
    void shutdownWithoutClientsIsImmediate() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "world2", "shutdown test 2", 20, 2, 20);
        EngineServer server = new EngineServer(config);
        server.start();
        V18ProtocolServer adapter = new V18ProtocolServer(server, 250);
        adapter.start(server);

        long start = System.currentTimeMillis();
        server.shutdown(adapter::shutdown);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(ServerState.STOPPED, server.state());
        assertTrue(elapsed < 5_000, "idle shutdown took " + elapsed + "ms");
    }

    private void awaitCondition(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
