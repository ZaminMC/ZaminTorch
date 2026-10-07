package net.zamin.launcher;

import net.zamin.api.ServerState;
import net.zamin.engine.EngineServer;
import net.zamin.engine.config.ConfigLoader;
import net.zamin.engine.config.EngineConfig;
import net.zamin.engine.net.ProtocolAdapter;
import net.zamin.protocol.v1_8.V18ProtocolServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Composition root: config -> engine -> protocol adapter -> console.
 * Contains no engine logic; it only wires and supervises the process.
 */
final class LauncherRuntime {

    private static final Logger LOGGER = Logger.getLogger(LauncherRuntime.class.getName());

    private final String[] args;

    LauncherRuntime(String[] args) {
        this.args = Arrays.copyOf(args, args.length);
    }

    void run() throws Exception {
        EngineConfig config = loadConfig();
        EngineServer server = new EngineServer(config);
        ProtocolAdapter adapter = new V18ProtocolServer(server);

        // Shutdown must work from signals as well as the console command (§121).
        Thread shutdownHook = new Thread(() -> server.shutdown(adapter::shutdown), "zamin-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        server.start();
        adapter.start(server);
        LOGGER.info(() -> "Server ready: " + adapter.protocolName() + " on port " + config.port());

        runConsole(server, adapter);
    }

    private EngineConfig loadConfig() throws IOException {
        Path file = EngineConfig.DEFAULT_FILE;
        if (!Files.exists(file)) {
            ConfigLoader.writeDefault(file);
            LOGGER.info("Wrote default configuration to " + file.toAbsolutePath());
        }
        try {
            return ConfigLoader.loadOrDefault(file);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Configuration invalid: " + e.getMessage(), e);
            throw e;
        }
    }

    /** Minimal console: reads lines, supports the first administrative commands (§305). */
    private void runConsole(EngineServer server, ProtocolAdapter adapter) throws IOException {
        LOGGER.info("Console ready. Type 'help' for commands.");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (server.state() == ServerState.RUNNING && (line = reader.readLine()) != null) {
                String command = line.trim().toLowerCase();
                switch (command) {
                    case "", "help" -> printHelp();
                    case "stop", "shutdown" -> {
                        LOGGER.info("Stop requested from console");
                        server.shutdown(adapter::shutdown);
                        return;
                    }
                    case "state" -> LOGGER.info(() -> "Server state: " + server.state()
                            + ", players=" + server.players().size());
                    default -> LOGGER.warning(() -> "Unknown command: " + command + " (try 'help')");
                }
            }
        } catch (IOException ignored) {
            // Console unavailable (e.g. daemonized process): fall through to latch wait.
        }
        // Console EOF or closure: the server keeps serving until stopped by signal
        // or shutdown call. A closed console must never stop a live server.
        server.awaitShutdown();
    }

    private void printHelp() {
        LOGGER.info("Commands: help, state, stop");
    }
}
