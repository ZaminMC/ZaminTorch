package net.zaminmc.torch.launcher;

import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.ConfigLoader;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.config.ServerLayout;
import net.zaminmc.torch.server.net.ProtocolAdapter;
import net.zaminmc.torch.protocol.v1_8.V18ProtocolServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    /** Duplicates the console stream into logs/latest.log (rotating). */
    private void attachFileLogging(Path root) throws IOException {
        Files.createDirectories(root.resolve("logs"));
        FileHandler fileHandler = new FileHandler("logs/latest.log", 5_000_000, 5, true);
        fileHandler.setFormatter(new SimpleFormatter());
        fileHandler.setEncoding(StandardCharsets.UTF_8.name());
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(fileHandler);
    }

    /** Minimal console: reads lines, supports the first administrative commands. */
    private void runConsole(EngineServer server, ProtocolAdapter adapter) throws IOException {
        LOGGER.info("Console ready. Type 'help' for commands.");
        System.out.print("> ");
        System.out.flush();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (server.state() == ServerState.RUNNING && (line = reader.readLine()) != null) {
                String command = line.trim().toLowerCase();
                if (!command.isEmpty()) {
                    // Acknowledge what the operator typed so interleaved log
                    // lines never leave a command's fate ambiguous.
                    LOGGER.info(() -> "Console: " + command);
                }
                switch (command) {
                    case "" -> { /* the bare enter: prompt redraw only */ }
                    case "stop", "shutdown" -> {
                        LOGGER.info("Stop requested from console");
                        // Direct path: stop must work even if the tick loop is wedged.
                        server.shutdown(adapter::shutdown);
                        return;
                    }
                    case "save" -> {
                        server.saveAllNow();
                        LOGGER.info("World saved");
                    }
                    case "state" -> LOGGER.info(() -> "Server state: " + server.state()
                            + ", players=" + server.players().size());
                    default -> {
                        // Everything else rides the same dispatcher the chat
                        // slash commands use (help, gamemode, op, say, ...).
                        server.consoleCommand(command);
                    }
                }
                System.out.print("> ");
                System.out.flush();
            }
        } catch (IOException ignored) {
            // Console unavailable (e.g. daemonized process): fall through to latch wait.
        }
        // Console EOF or closure: the server keeps serving until stopped by signal
        // or shutdown call. A closed console must never stop a live server.
        server.awaitShutdown();
    }
}
