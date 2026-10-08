package net.zaminmc.torch.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads {@link EngineConfig} from a Java properties file. Unknown keys are ignored
 * (forward compatibility); every recognized key is validated by the config model
 * itself, so a bad value fails at startup with a clear message instead of at runtime.
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static EngineConfig loadOrDefault(Path file) throws IOException {
        if (!Files.exists(file)) {
            return EngineConfig.defaults();
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        }
        return fromProperties(properties);
    }

    /** Visible for tests and for writing the default file. */
    public static EngineConfig fromProperties(Properties properties) {
        return new EngineConfig(
                string(properties, "host", EngineConfig.defaults().host()),
                intOf(properties, "port", 25565),
                string(properties, "world-name", EngineConfig.defaults().worldName()),
                string(properties, "motd", EngineConfig.defaults().motd()),
                intOf(properties, "max-players", 20),
                intOf(properties, "view-distance", 4),
                intOf(properties, "tick-rate", 20),
                string(properties, "data-dir", "."),
                GameMode.parse(string(properties, "gamemode", "survival")),
                boolOf(properties, "pvp", true));
    }

    public static void writeDefault(Path file) throws IOException {
        Properties properties = new Properties();
        EngineConfig defaults = EngineConfig.defaults();
        properties.setProperty("host", defaults.host());
        properties.setProperty("port", String.valueOf(defaults.port()));
        properties.setProperty("world-name", defaults.worldName());
        properties.setProperty("motd", defaults.motd());
        properties.setProperty("max-players", String.valueOf(defaults.maxPlayers()));
        properties.setProperty("view-distance", String.valueOf(defaults.viewDistance()));
        properties.setProperty("tick-rate", String.valueOf(defaults.tickRateHz()));
        properties.setProperty("data-dir", defaults.dataDir());
        properties.setProperty("gamemode", defaults.gamemode().name().toLowerCase());
        properties.setProperty("pvp", String.valueOf(defaults.pvp()));
        try (var out = Files.newOutputStream(file)) {
            properties.store(out, "ZaminTorch server configuration");
        }
    }

    private static String string(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int intOf(Properties properties, String key, int fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Configuration key '" + key + "' is not a number: " + raw, e);
        }
    }

    private static boolean boolOf(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase();
        if ("true".equals(value) || "false".equals(value)) {
            return Boolean.parseBoolean(value);
        }
        throw new IllegalArgumentException(
                "Configuration key '" + key + "' is not a boolean: " + raw);
    }
}
