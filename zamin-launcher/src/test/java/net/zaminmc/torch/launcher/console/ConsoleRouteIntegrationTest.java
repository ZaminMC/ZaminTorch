package net.zaminmc.torch.launcher.console;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.ops.BanStore;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The end-to-end route proof (§30.4.1): a command typed through the
 * interactive reader takes the EXISTING engine command route — the same
 * dispatcher the chat slash commands use — with persistence intact, exactly
 * what the bare console line always did (the core's ConsoleCommandTest
 * contract, now proven through the terminal adapter).
 */
class ConsoleRouteIntegrationTest {

    @TempDir
    Path dataDir;

    @Test
    void aRealCommandThroughTheInteractiveReaderReachesTheEngineRoute() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "console-world", "it",
                20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            PipedOutputStream feed = new PipedOutputStream();
            DumbTerminal terminal = new DumbTerminal(new PipedInputStream(feed),
                    new ByteArrayOutputStream());
            ConsoleTarget target = new EngineConsoleTarget(server, () -> { });
            TorchConsole console = new TorchConsole(
                    ConsoleConfig.defaults().withHistory(dataDir.resolve("h")), target, terminal);
            Thread consoleThread = new Thread(console::run, "it-console");
            consoleThread.setDaemon(true);
            consoleThread.start();

            feed.write("ban Griefer griefing the spawn\n".getBytes(StandardCharsets.UTF_8));
            feed.flush();

            // The dispatch rides the tick thread; poll for the persisted
            // effect exactly like the core's ConsoleCommandTest.
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline
                    && BanStore.load(dataDir.resolve("banned-players.json")).entries().isEmpty()) {
                Thread.sleep(50);
            }
            assertTrue(!BanStore.load(dataDir.resolve("banned-players.json")).entries().isEmpty(),
                    "the console ban persisted through the existing route");

            // The completion snapshot sees the engine's registered commands.
            List<String> names = target.commandNames();
            assertTrue(names.contains("ban"), "the registry snapshot lists the engine commands");
            assertTrue(names.contains("help"));

            feed.close();
            console.requestShutdown();
            consoleThread.join(5_000);
        } finally {
            server.shutdown(null);
        }
    }
}
