package net.zamin.launcher;

import net.zamin.api.ServerState;
import net.zamin.engine.EngineServer;
import net.zamin.engine.config.ConfigLoader;
import net.zamin.engine.config.EngineConfig;

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

        // Shutdown must work from signals as well as the console command (§121).
        Thread shutdownHook = new Thread(() -> server.shutdown(null), "zamin-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        server.start();
        LOGGER.info(() -> "Engine running (" + server.state() + "); waiting for protocol adapter wiring");

        runConsole(server);
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
    private void runConsole(EngineServer server) throws IOException {
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
                        server.shutdown(null);
                        return;
                    }
                    case "state" -> LOGGER.info(() -> "Server state: " + server.state()
                            + ", players=" + server.players().size());
                    default -> LOGGER.warning(() -> "Unknown command: " + command + " (try 'help')");
                }
            }
        }
    }

    private void printHelp() {
        LOGGER.info("Commands: help, state, stop");
    }
}
