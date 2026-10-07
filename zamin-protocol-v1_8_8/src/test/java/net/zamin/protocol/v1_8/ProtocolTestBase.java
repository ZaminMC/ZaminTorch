package net.zamin.protocol.v1_8;

import net.zamin.engine.EngineServer;
import net.zamin.engine.config.EngineConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/**
 * Shared boot for protocol-level behavioral suites: engine + adapter on an
 * ephemeral port, one process per test class, temp-isolated world data.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ProtocolTestBase {

    protected EngineServer server;
    protected V18ProtocolServer adapter;

    @org.junit.jupiter.api.io.TempDir
    static Path dataDir;

    @BeforeAll
    void bootStack() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "world", "protocol tests", 20, 4, 20,
                dataDir.toString());
        server = new EngineServer(config);
        server.start();
        adapter = new V18ProtocolServer(server, 250);
        adapter.start(server);
    }

    @AfterAll
    void stopStack() {
        server.shutdown(adapter::shutdown);
    }

    protected void awaitPlayers(String... names) throws InterruptedException {
        awaitCondition(() -> {
            for (String name : names) {
                if (server.playerRegistry().byName(name).isEmpty()) {
                    return false;
                }
            }
            return true;
        }, "players present: " + String.join(", ", names));
    }

    protected void awaitEmptyServer() throws InterruptedException {
        awaitCondition(() -> server.players().isEmpty(), "all players left");
    }

    protected void awaitCondition(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
