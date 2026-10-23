package net.zaminmc.torch.launcher;

import net.zaminmc.torch.server.config.EngineConfig;

import java.util.OptionalInt;

/**
 * The command-line boot overrides (ML-009a — the audit's B-4 row): the
 * dedicated server's precedence law, <b>CLI &gt; properties &gt;
 * default</b>. The properties file ({@code server.properties} through
 * {@link net.zaminmc.torch.server.config.ConfigLoader}) keeps every
 * row's default and persistent form; a CLI flag overrides the loaded
 * value for THIS boot only — never written back — exactly like the
 * reference dedicated server's one-shot command-line surface.
 *
 * <p>Recognized: {@code --port <n>} and {@code --port=<n>} (the bind
 * port). A malformed form fails boot loudly with a clear message (the
 * config model's own law — a bad value must surface at boot, never at
 * runtime); the range law stays {@link EngineConfig}'s own (0-65535,
 * validated by the copy constructor). Unrecognized arguments are
 * ignored exactly as before (this lane adds no new surface for them).
 * A repeated flag answers the LAST occurrence.</p>
 */
record StartupArguments(OptionalInt portOverride) {

    /** No overrides. */
    static StartupArguments none() {
        return new StartupArguments(OptionalInt.empty());
    }

    /**
     * Parses the boot arguments. Throws {@link IllegalArgumentException}
     * on a malformed recognized flag — the boot path's loud-failure law.
     */
    static StartupArguments parse(String[] args) {
        OptionalInt port = OptionalInt.empty();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--port")) {
                continue;
            }
            String value;
            if (arg.length() > "--port".length() && arg.charAt("--port".length()) == '=') {
                value = arg.substring("--port=".length());
            } else if (arg.equals("--port")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(
                            "--port requires a port number: --port <1-65535>");
                }
                value = args[++i];
            } else {
                continue; // an unrelated --port* flag (e.g. --ports): not ours
            }
            try {
                port = OptionalInt.of(Integer.parseInt(value.trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "--port value is not a number: " + value, e);
            }
        }
        return new StartupArguments(port);
    }

    /**
     * Applies the overrides to a loaded config — the boot path's own
     * composition (CLI over the properties-loaded values, the defaults
     * already baked in beneath them). The remote-admin ports keep their
     * resolved values: they follow their OWN properties rows and the
     * reference's first-boot write-back (rcon.port = port+10,
     * query.port = port baked into the file at first boot, per
     * {@link EngineConfig}'s own record), and the listeners' own
     * start() laws own any mismatch — the same envelope as a
     * properties-only port change.
     */
    EngineConfig applyTo(EngineConfig config) {
        if (portOverride.isEmpty()) {
            return config;
        }
        int port = portOverride.getAsInt();
        LOGGER.info(() -> "Command-line port override: " + port
                + " (server.properties server-port=" + config.port() + " ignored for this boot)");
        return config.withPort(port);
    }

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(StartupArguments.class.getName());
}
