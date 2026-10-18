package net.zaminmc.torch.launcher.console;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.ConsoleHandler;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * The Torch terminal adapter (architecture §30.3): one JLine Terminal and
 * LineReader for the interactive session, a JUL bridge that prints log
 * output ABOVE the edited line, and the plain line-oriented fallback — over
 * the ONE command path ({@link ConsoleCommands}).
 *
 * <p>Boundaries (as specified): the adapter owns terminal I/O, rendering,
 * history, and completion presentation only. It never owns server
 * lifecycle state (the target's stop route does), never parses or executes
 * commands itself, and never receives live mutable world state. The
 * reading loop runs on the CALLER's thread (the launcher main thread —
 * never a simulation thread); the bridge's writer is a daemon thread; Ctrl+C
 * during editing surfaces as {@link UserInterruptException} and converges
 * on the same idempotent stop as the 'stop' command; EOF ({@link
 * EndOfFileException}) ends the console but NEVER the server.</p>
 *
 * <p>Lifecycle: {@link #requestShutdown()} is idempotent and safe from any
 * thread — it flags the loop and closes the terminal to unblock a pending
 * read; the loop then drains, detaches the bridge (restoring the default
 * console handler so a post-EOF server still logs to the screen), closes
 * history, and returns.</p>
 */
public final class TorchConsole {

    private static final Logger LOGGER = Logger.getLogger(TorchConsole.class.getName());

    private final ConsoleConfig config;
    private final ConsoleTarget target;
    private final ConsoleCommands dispatcher;
    /** Set once shutdown has been requested; the loop checks between lines. */
    private volatile boolean shutdownRequested;
    /** The live terminal, for unblocking a blocked read from another thread. */
    private volatile Terminal activeTerminal;

    public TorchConsole(ConsoleConfig config, ConsoleTarget target) {
        this.config = config;
        this.target = target;
        this.dispatcher = new ConsoleCommands(target);
    }

    /** Runs the console loop on the calling thread; returns when it ends. */
    public void run() {
        Terminal terminal = buildTerminal();
        if (terminal != null) {
            interactiveLoop(terminal);
        } else {
            plainLoop();
        }
    }

    /**
     * Requests console shutdown from any thread: idempotent, and it unblocks
     * a pending read so the loop can end within the bounded close deadline.
     * The SERVER's stop is the target's own business (the dispatcher's stop
     * route or an external signal calls it separately).
     */
    public void requestShutdown() {
        shutdownRequested = true;
        Terminal terminal = this.activeTerminal;
        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception ignored) {
                // already closed or a provider quirk: the loop's read failure covers it
            }
        }
    }

    /** Whether the loop saw a shutdown request (telemetry/lifecycle tests). */
    public boolean isShutdownRequested() {
        return shutdownRequested;
    }

    // ------------------------------------------------------------------
    // terminal setup
    // ------------------------------------------------------------------

    /**
     * Builds the terminal per the interactive policy. AUTO: a real system
     * terminal or nothing (a redirected/headless environment falls back to
     * the plain loop — line editing would be a lie there). ON: the system
     * terminal, or a DUMB terminal with a loud warning when no TTY exists
     * (forced-on degrades safely, never printing escape sequences blindly).
     */
    private Terminal buildTerminal() {
        if (config.interactive() == ConsoleConfig.Interactive.OFF) {
            return null;
        }
        try {
            return TerminalBuilder.builder()
                    .name("ZaminTorch")
                    .system(true)
                    .dumb(false)
                    .build();
        } catch (Exception noTty) {
            if (config.interactive() == ConsoleConfig.Interactive.ON) {
                LOGGER.warning("[console] console.interactive=on but no interactive terminal"
                        + " is available; using a dumb terminal without line editing");
                try {
                    return TerminalBuilder.builder()
                            .name("ZaminTorch")
                            .system(true)
                            .dumb(true)
                            .build();
                } catch (Exception fatal) {
                    LOGGER.warning("[console] dumb terminal also unavailable; plain fallback: "
                            + fatal);
                    return null;
                }
            }
            // AUTO: a non-interactive environment uses the plain line console
            LOGGER.fine(() -> "[console] no interactive terminal ("
                    + noTty.getClass().getSimpleName() + "); plain console");
            return null;
        }
    }

    /** The ANSI verdict: honored only when the terminal type can carry color. */
    private boolean resolveAnsi(Terminal terminal) {
        String type = terminal.getType();
        boolean capable = Terminal.TYPE_DUMB_COLOR.equals(type)
                || (!Terminal.TYPE_DUMB.equals(type) && type != null && !type.isBlank());
        return switch (config.ansi()) {
            case OFF -> false;
            case ON -> {
                if (!capable) {
                    LOGGER.warning("[console] console.ansi=on cannot be honored on terminal"
                            + " type '" + type + "'; colors disabled");
                    yield false;
                }
                yield true;
            }
            case AUTO -> capable;
        };
    }

    // ------------------------------------------------------------------
    // the interactive (JLine) loop
    // ------------------------------------------------------------------

    private void interactiveLoop(Terminal terminal) {
        activeTerminal = terminal;
        boolean ansi = resolveAnsi(terminal);
        LineReader reader = buildReader(terminal, ansi);
        TerminalLogBridge bridge = new TerminalLogBridge(config,
                line -> printAboveSafe(reader, line), ansi);
        Handler[] detached = attachBridge(bridge);
        bridge.start();
        try {
            LOGGER.info("Console ready. Type 'help' for commands.");
            while (!shutdownRequested) {
                String line;
                try {
                    line = reader.readLine(config.prompt());
                } catch (UserInterruptException e) {
                    // Ctrl+C during editing: the same idempotent stop as 'stop'.
                    requestShutdown();
                    target.requestStop();
                    break;
                } catch (EndOfFileException e) {
                    break; // EOF: the console ends, the server keeps serving
                } catch (RuntimeException e) {
                    LOGGER.warning("[console] terminal read failed: " + e);
                    break; // terminal failure: end the console, keep the server
                }
                if (line == null || shutdownRequested) {
                    break;
                }
                ConsoleCommands.Result result = dispatcher.dispatch(line);
                persistHistory(reader);
                if (result == ConsoleCommands.Result.SHUTDOWN_REQUESTED) {
                    shutdownRequested = true;
                    break;
                }
            }
        } finally {
            detachBridge(bridge, detached);
            try {
                if (reader.getHistory() instanceof FilteredHistory history) {
                    history.save();
                }
            } catch (Exception e) {
                LOGGER.warning("[console] history save failed: " + e);
            }
            try {
                terminal.close();
            } catch (Exception ignored) {
                // closing an already-closed terminal is fine
            }
            activeTerminal = null;
        }
    }

    private LineReader buildReader(Terminal terminal, boolean ansi) {
        LineReaderBuilder builder = LineReaderBuilder.builder()
                .terminal(terminal);
        FilteredHistory history = new FilteredHistory(config.sensitiveCommands());
        if (config.historyEnabled()) {
            builder.variable(LineReader.HISTORY_FILE, config.historyFile().toString());
            builder.variable(LineReader.HISTORY_SIZE, config.historyLimit());
            // The file keeps a margin above the in-memory bound so the tail
            // trimming never loses the live window.
            builder.variable(LineReader.HISTORY_FILE_SIZE, config.historyLimit() * 2);
        }
        builder.history(history);
        if (config.completionEnabled()) {
            builder.completer(new CommandCompleter(target::commandNames));
        }
        try {
            return builder.build();
        } catch (RuntimeException e) {
            // An unreadable/locked history file must not kill the console:
            // rebuild without persistence and warn (30.4.3).
            LOGGER.warning("[console] history could not be attached (" + e.getMessage()
                    + "); continuing without it");
            return LineReaderBuilder.builder()
                    .terminal(terminal)
                    .history(new FilteredHistory(config.sensitiveCommands()))
                    .completer(config.completionEnabled()
                            ? new CommandCompleter(target::commandNames) : null)
                    .build();
        }
    }

    /** Persists the just-accepted line's history entry; failures are isolated. */
    private void persistHistory(LineReader reader) {
        if (!config.historyEnabled()) {
            return;
        }
        try {
            if (reader.getHistory() instanceof FilteredHistory history) {
                history.save();
            }
        } catch (Exception e) {
            LOGGER.warning("[console] history persist failed: " + e.getMessage());
        }
    }

    /** printAbove with the dumb-terminal degradation: plain write, no crash. */
    private void printAboveSafe(LineReader reader, String line) {
        try {
            reader.printAbove(line);
        } catch (RuntimeException e) {
            // A terminal that cannot reposition still gets the line — this
            // is exactly the plain-path behavior; never lose the record.
            try {
                reader.getTerminal().writer().println(line);
                reader.getTerminal().writer().flush();
            } catch (RuntimeException fatal) {
                // the bridge's writer loop handles the death of the sink
            }
        }
    }

    // ------------------------------------------------------------------
    // the plain fallback loop (the historical console, byte-for-byte in shape)
    // ------------------------------------------------------------------

    private void plainLoop() {
        LOGGER.info("Console ready. Type 'help' for commands.");
        System.out.print(config.prompt());
        System.out.flush();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (!shutdownRequested && (line = reader.readLine()) != null) {
                ConsoleCommands.Result result = dispatcher.dispatch(line);
                if (result == ConsoleCommands.Result.SHUTDOWN_REQUESTED) {
                    shutdownRequested = true;
                    return;
                }
                System.out.print(config.prompt());
                System.out.flush();
            }
        } catch (java.io.IOException ignored) {
            // Console unavailable (e.g. daemonized process): fall through.
        }
        // Console EOF or closure: the caller keeps the server running.
    }

    // ------------------------------------------------------------------
    // JUL bridge attachment (the independent terminal sink)
    // ------------------------------------------------------------------

    /**
     * Attaches the terminal bridge and removes the default console handler
     * (else every record would print twice); the FILE handler is untouched.
     *
     * @return the detached handlers, for restoration when the console ends
     */
    private static Handler[] attachBridge(TerminalLogBridge bridge) {
        Logger root = Logger.getLogger("");
        Handler[] removed = Arrays.stream(root.getHandlers())
                .filter(h -> h instanceof ConsoleHandler)
                .toArray(Handler[]::new);
        for (Handler handler : removed) {
            root.removeHandler(handler);
        }
        root.addHandler(bridge);
        return removed;
    }

    /** Detaches the bridge and restores whatever console handlers existed. */
    private static void detachBridge(TerminalLogBridge bridge, Handler[] restore) {
        Logger root = Logger.getLogger("");
        root.removeHandler(bridge);
        bridge.close();
        for (Handler handler : restore) {
            root.addHandler(handler);
        }
    }
}
