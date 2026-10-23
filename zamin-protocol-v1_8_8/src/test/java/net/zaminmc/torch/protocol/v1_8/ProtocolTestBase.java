package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
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
        adapter = new V18ProtocolServer(server, keepAliveIntervalMs());
        adapter.start(server);
    }

    /**
     * Keep-alive cycle for the shared adapter. Subclasses running long scripted
     * flows override this: the scripted client only answers keep-alives when a
     * test explicitly drives the exchange, so slow suites must not be kicked.
     */
    protected long keepAliveIntervalMs() {
        return 250;
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

    /**
     * The engine-tick await (the run #375/#376 conversion — the old 5s wall
     * deadline expired by wall time on a loaded runner even while the engine
     * was alive and progressing; the census's own rule applies here too: a
     * tick budget never expires by wall time alone). The budget is 400
     * ticks — the established generous form (the core helpers' old 20s wall
     * equivalent), NOT the 5s form's nominal 100: the protocol suite's
     * conditions include the wire-interaction backlogs (the wheat ladder's
     * queued uses drain at the tick lane's own pace) that the tight 100-tick
     * boundary cut on the first converted run — the same boundary flake the
     * horse test documented, now sized honestly. The two-sided liveness
     * guard (stall window + patience cap) replaces the wall cutoff.
     */
    protected void awaitCondition(BooleanSupplier condition, String description) throws InterruptedException {
        net.zaminmc.torch.testing.EngineAwaits.awaitCondition(server.world(), condition,
                400, description);
    }
}
