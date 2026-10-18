package net.zaminmc.torch.launcher;

import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.launcher.console.ConsoleConfig;
import net.zaminmc.torch.launcher.console.EngineConsoleTarget;
import net.zaminmc.torch.launcher.console.LogFileFormatter;
import net.zaminmc.torch.launcher.console.TorchConsole;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.ConfigLoader;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.config.ServerLayout;
import net.zaminmc.torch.server.net.ProtocolAdapter;
import net.zaminmc.torch.protocol.v1_8.V18ProtocolServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Composition root: layout -> eula -> config -> engine -> protocol -> console.
 * Contains no engine logic; it only wires and supervises the process, with the
 * runtime home shaped exactly like the historical Paper server.
 */
final class LauncherRuntime {

    private static final Logger LOGGER = Logger.getLogger(LauncherRuntime.class.getName());

    private final String[] args;

    LauncherRuntime(String[] args) {
        this.args = Arrays.copyOf(args, args.length);
    }

    void run() throws Exception {
        // The update prompt fires in the background: boot never waits for it.
        new UpdateChecker().checkAsync();

        Path root = Path.of(".");
        // First boot writes eula.txt with eula=false and stops, exactly like
        // the historical server; operators accept by editing the file.
        if (!ServerLayout.ensureEula(root)) {
            LOGGER.severe("You need to agree to the EULA in order to run the server.");
            LOGGER.severe("Go to eula.txt for more info.");
            return;
        }

        EngineConfig config = loadConfig();
        // The Paper home: world folders, JSON stores, YAML files, logs.
        ServerLayout.ensureDirectories(root, config.worldName());
        ServerLayout.ensureJsonStores(root);
        ServerLayout.ensureYamlStores(root);
        attachFileLogging(root);

        EngineServer server = new EngineServer(config);
        ProtocolAdapter adapter = new V18ProtocolServer(server);

        // Shutdown must work from signals as well as the console command.
        Thread shutdownHook = new Thread(() -> server.shutdown(adapter::shutdown), "zamin-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        server.start();
        adapter.start(server);
        LOGGER.info(() -> "Server ready: " + adapter.protocolName() + " on port " + config.port()
                + " (build " + UpdateChecker.BUILD_VERSION + ")");

        runConsole(server, adapter);
    }

    private EngineConfig loadConfig() throws IOException {
        try {
            return ConfigLoader.loadOrDefault(EngineConfig.DEFAULT_FILE);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Configuration invalid: " + e.getMessage(), e);
            throw e;
        }
    }

    /** Duplicates the console stream into logs/latest.log (rotating), the
     *  file sink of the independent-sinks policy (§30.3): every record lands
     *  here regardless of the terminal sink's threshold or backpressure. */
    private void attachFileLogging(Path root) throws IOException {
        Files.createDirectories(root.resolve("logs"));
        FileHandler fileHandler = new FileHandler("logs/latest.log", 5_000_000, 5, true);
        fileHandler.setFormatter(new LogFileFormatter());
        fileHandler.setEncoding(StandardCharsets.UTF_8.name());
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(fileHandler);
    }

    /**
     * The terminal console (architecture §30): the interactive JLine reader
     * with the log bridge, or the plain line fallback — both over the ONE
     * command path. Console EOF or failure never stops a live server; the
     * 'stop' built-in, Ctrl+C, and signals converge on the same idempotent
     * shutdown.
     */
    private void runConsole(EngineServer server, ProtocolAdapter adapter) {
        ConsoleConfig consoleConfig = ConsoleConfig.loadOrDefault(
                Path.of(ConsoleConfig.DEFAULT_FILE));
        TorchConsole console = new TorchConsole(consoleConfig,
                new EngineConsoleTarget(server, adapter::shutdown));
        console.run();
        // Console EOF, terminal failure, or a completed stop: the server
        // keeps serving until the shutdown actually completes.
        server.awaitShutdown();
    }
}
