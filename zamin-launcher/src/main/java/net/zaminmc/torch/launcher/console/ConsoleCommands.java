package net.zaminmc.torch.launcher.console;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * The console's command dispatch (architecture §30.3 "one command path"):
 * the SAME switch the plain console always had — the administrative
 * built-ins (stop/shutdown, save, state) handled here, everything else
 * handed to the engine's existing {@code EngineServer.consoleCommand} route
 * (the dispatcher the chat slash commands use, with aliases, operator
 * levels, result recording, and exception handling intact). The interactive
 * JLine reader and the plain fallback both feed this one dispatcher; there
 * is no console-only registry and no permission bypass.
 */
public final class ConsoleCommands {

    private static final Logger LOGGER = Logger.getLogger(ConsoleCommands.class.getName());

    /** The dispatcher's verdict for the reading loop. */
    public enum Result { CONTINUE, SHUTDOWN_REQUESTED }

    private final ConsoleTarget target;

    public ConsoleCommands(ConsoleTarget target) {
        this.target = target;
    }

    /**
     * Dispatches one accepted console line.
     *
     * @param rawLine the line exactly as accepted (case preserved for arguments)
     */
    public Result dispatch(String rawLine) {
        String raw = rawLine == null ? "" : rawLine.trim();
        // Only the command word routes the switch; arguments keep their case
        // (player names and ban reasons are case-bearing).
        String command = raw.toLowerCase(Locale.ROOT);
        if (!command.isEmpty()) {
            // Acknowledge what the operator typed so interleaved log lines
            // never leave a command's fate ambiguous.
            LOGGER.info(() -> "Console: " + command);
        }
        switch (command) {
            case "" -> {
                return Result.CONTINUE; // the bare enter: prompt redraw only
            }
            case "stop", "shutdown" -> {
                LOGGER.info("Stop requested from console");
                // Direct path: stop must work even if the tick loop is wedged.
                target.requestStop();
                return Result.SHUTDOWN_REQUESTED;
            }
            case "save" -> {
                target.saveAllNow();
                LOGGER.info("World saved");
                return Result.CONTINUE;
            }
            case "state" -> {
                String line = target.stateLine();
                LOGGER.info(() -> line);
                return Result.CONTINUE;
            }
            default -> {
                // Everything else rides the same dispatcher the chat slash
                // commands use (help, gamemode, op, say, ...). A throwing
                // route is isolated from the reading loop (§30.3 errors): the
                // engine's own dispatcher reports failures to the sender.
                try {
                    target.consoleCommand(raw);
                } catch (RuntimeException e) {
                    LOGGER.warning(() -> "Console command route failed (isolated): " + e);
                }
                return Result.CONTINUE;
            }
        }
    }
}
