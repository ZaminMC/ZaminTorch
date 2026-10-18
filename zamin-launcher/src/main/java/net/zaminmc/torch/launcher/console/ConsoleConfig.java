package net.zaminmc.torch.launcher.console;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;

/**
 * The console configuration (architecture §30.3): interactive-mode selection,
 * ANSI policy, prompt, history enablement/path/limit/sensitive-command policy,
 * completion mode, terminal log threshold, and fallback behavior.
 *
 * <p>File form: {@code console.properties} at the server root, written with
 * documented defaults on first boot. The architecture's proposal named
 * {@code tweaks/console.yml}; the engine's whole configuration surface is
 * properties-based ({@code zamin.properties}), so the console keeps the same
 * convention — a deliberate Torch naming decision recorded with ADR-21, not
 * a silent deviation.</p>
 *
 * <p>Every value degrades safely: an unparseable or out-of-range value logs a
 * warning and falls back to its default rather than failing boot (the console
 * must never make a server unstartable).</p>
 */
public record ConsoleConfig(
        Interactive interactive,
        Ansi ansi,
        String prompt,
        boolean historyEnabled,
        Path historyFile,
        int historyLimit,
        List<String> sensitiveCommands,
        boolean completionEnabled,
        Level terminalThreshold,
        int terminalQueueCapacity,
        boolean plainFallbackEnabled) {

    /** {@code console.interactive}: auto (TTY detection), on (forced), off (plain). */
    public enum Interactive { AUTO, ON, OFF }

    /** {@code console.ansi}: auto (terminal capability), on (forced), off (never). */
    public enum Ansi { AUTO, ON, OFF }

    public static final String DEFAULT_FILE = "console.properties";
    public static final Path DEFAULT_HISTORY_FILE = Path.of("logs/console.history");
    public static final int DEFAULT_HISTORY_LIMIT = 500;
    public static final int DEFAULT_QUEUE_CAPACITY = 256;
    private static final String DEFAULT_PROMPT = "> ";

    public static ConsoleConfig defaults() {
        return new ConsoleConfig(Interactive.AUTO, Ansi.AUTO, DEFAULT_PROMPT,
                true, DEFAULT_HISTORY_FILE, DEFAULT_HISTORY_LIMIT, List.of(),
                true, Level.INFO, DEFAULT_QUEUE_CAPACITY, true);
    }

    /** A copy with a different history file (tests and custom bindings). */
    public ConsoleConfig withHistory(Path file) {
        return new ConsoleConfig(interactive, ansi, prompt, historyEnabled, file,
                historyLimit, sensitiveCommands, completionEnabled, terminalThreshold,
                terminalQueueCapacity, plainFallbackEnabled);
    }

    /**
     * Loads {@code console.properties} beside the server root, writing the
     * documented defaults first when absent. Missing or invalid values fall
     * back to their defaults individually (a warning per repair, boot never
     * fails).
     */
    public static ConsoleConfig loadOrDefault(Path file) {
        ConsoleConfig config = defaults();
        if (!Files.exists(file)) {
            writeDefaults(file);
            return config;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            warn("console.properties unreadable; using defaults: " + e.getMessage());
            return config;
        }
        return apply(config, properties);
    }

    static ConsoleConfig apply(ConsoleConfig base, Properties properties) {
        Interactive interactive = enumValue(properties, "console.interactive",
                Interactive.class, base.interactive());
        Ansi ansi = enumValue(properties, "console.ansi", Ansi.class, base.ansi());
        String prompt = properties.getProperty("console.prompt", base.prompt());
        if (prompt == null || prompt.contains("\n") || prompt.contains("\r")) {
            warn("console.prompt must be a single line; using default");
            prompt = base.prompt();
        }
        boolean history = boolValue(properties, "console.history.enabled", base.historyEnabled());
        Path historyFile = pathValue(properties, "console.history.file", base.historyFile());
        int historyLimit = intValue(properties, "console.history.limit",
                base.historyLimit(), 1, 100_000);
        List<String> sensitive = listValue(properties, "console.history.sensitive-commands");
        boolean completion = boolValue(properties, "console.completion.enabled",
                base.completionEnabled());
        Level threshold = levelValue(properties, "console.terminal.log-threshold",
                base.terminalThreshold());
        int queue = intValue(properties, "console.terminal.queue-capacity",
                base.terminalQueueCapacity(), 16, 65_536);
        boolean plainFallback = boolValue(properties, "console.fallback.plain",
                base.plainFallbackEnabled());
        return new ConsoleConfig(interactive, ansi, prompt, history, historyFile,
                historyLimit, sensitive, completion, threshold, queue, plainFallback);
    }

    /** Writes the documented defaults so operators can discover every knob. */
    public static void writeDefaults(Path file) {
        StringBuilder out = new StringBuilder();
        out.append("#ZaminTorch console configuration (architecture 30.3)\n");
        out.append("#interactive: auto | on | off. auto enables the JLine terminal when a\n");
        out.append("#  TTY is attached; off always uses the plain line console.\n");
        out.append("console.interactive=auto\n");
        out.append("#ansi: auto | on | off. on forces color; if the terminal cannot honor\n");
        out.append("#  it the console warns and degrades to plain output.\n");
        out.append("console.ansi=auto\n");
        out.append("#prompt: the interactive prompt (single line).\n");
        out.append("console.prompt=> \n");
        out.append("#history: persistent across restarts; sensitive commands are never stored.\n");
        out.append("console.history.enabled=true\n");
        out.append("console.history.file=logs/console.history\n");
        out.append("console.history.limit=500\n");
        out.append("#comma-separated command names excluded from history (e.g. login).\n");
        out.append("console.history.sensitive-commands=\n");
        out.append("#tab completion for command names (first word only).\n");
        out.append("console.completion.enabled=true\n");
        out.append("#terminal-only log threshold: records below this level are hidden from\n");
        out.append("#  the screen but ALWAYS kept in logs/latest.log (independent sinks).\n");
        out.append("console.terminal.log-threshold=INFO\n");
        out.append("#bounded handoff for terminal writes; excess lines are dropped and\n");
        out.append("#  counted loudly (the file sink keeps everything).\n");
        out.append("console.terminal.queue-capacity=256\n");
        out.append("#plain fallback when the JLine terminal cannot initialize.\n");
        out.append("console.fallback.plain=true\n");
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, out.toString());
        } catch (IOException e) {
            warn("could not write default console.properties: " + e.getMessage());
        }
    }

    // ---- parse helpers: every failure degrades to the default with a warning ----

    private static <E extends Enum<E>> E enumValue(Properties properties, String key,
                                                   Class<E> type, E fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn(key + "=" + raw + " is not valid; using " + fallback);
            return fallback;
        }
    }

    private static boolean boolValue(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        warn(key + "=" + raw + " is not a boolean; using " + fallback);
        return fallback;
    }

    private static int intValue(Properties properties, String key, int fallback,
                                int min, int max) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                warn(key + "=" + raw + " out of range " + min + ".." + max + "; using " + fallback);
                return fallback;
            }
            return value;
        } catch (NumberFormatException e) {
            warn(key + "=" + raw + " is not an integer; using " + fallback);
            return fallback;
        }
    }

    private static Path pathValue(Properties properties, String key, Path fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Path.of(raw.trim());
    }

    private static List<String> listValue(Properties properties, String key) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static Level levelValue(Properties properties, String key, Level fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Level.parse(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn(key + "=" + raw + " is not a log level; using " + fallback);
            return fallback;
        }
    }

    private static void warn(String message) {
        java.util.logging.Logger.getLogger(ConsoleConfig.class.getName())
                .warning("[console] " + message);
    }
}
