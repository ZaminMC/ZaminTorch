package net.zaminmc.torch.server;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.ops.BanStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The console command path: bare console lines execute through the dispatcher. */
class ConsoleCommandTest {

    @TempDir
    Path dataDir;

    @Test
    void bareConsoleLinesExecuteAndPersist() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "console-world", "it",
                20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            server.consoleCommand("ban Griefer griefing the spawn");
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline
                    && BanStore.load(dataDir.resolve("banned-players.json")).entries().isEmpty()) {
                Thread.sleep(50);
            }
            assertTrue(Files.exists(dataDir.resolve("banned-players.json")), "the store file exists");
            assertTrue(!BanStore.load(dataDir.resolve("banned-players.json")).entries().isEmpty(),
                    "the console ban persisted");
        } finally {
            server.shutdown(null);
        }
    }
}
