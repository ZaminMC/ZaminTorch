package net.zaminmc.torch.server.config;

import net.zaminmc.torch.GameMode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads {@link EngineConfig} from the historical {@code server.properties}.
 * Vanilla key names are read as-is (server-port, level-name, gamemode, ...);
 * unknown keys are ignored for forward compatibility. Every recognized value
 * is validated by the config model, so a bad value fails at boot with a clear
 * message instead of surfacing at runtime.
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static EngineConfig loadOrDefault(Path file) throws IOException {
        if (!Files.exists(file)) {
            ServerLayout.ensureServerProperties(file.toAbsolutePath().getParent() == null
                    ? Path.of(".") : file.toAbsolutePath().getParent());
            return EngineConfig.defaults();
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        }
        return fromProperties(properties);
    }

    /** Visible for tests and for boot-time validation messages. */
    public static EngineConfig fromProperties(Properties properties) {
        return new EngineConfig(
                host(properties),
                intOf(properties, "server-port", 25565),
                string(properties, "level-name", "world"),
                string(properties, "motd", "A ZaminTorch Server"),
                intOf(properties, "max-players", 20),
                intOf(properties, "view-distance", 4),
                intOf(properties, "tick-rate", 20),
                string(properties, "data-dir", "."),
                GameMode.parse(string(properties, "gamemode", "survival")),
                boolOf(properties, "pvp", true),
                levelType(properties),
                boolOf(properties, "white-list", false));
    }

    /** The bind address: empty means the wildcard (the vanilla convention). */
    private static String host(Properties properties) {
        String raw = string(properties, "server-ip", "");
        return raw.isBlank() ? "0.0.0.0" : raw;
    }

    /** Accepts the vanilla names and the historical flat/normal ids. */
    private static String levelType(Properties properties) {
        String raw = string(properties, "level-type", "normal").toLowerCase();
        return switch (raw) {
            case "flat", "default", "normal" -> raw.equals("default") ? "normal" : raw;
            case "0" -> "normal";
            case "1" -> "flat";
            default -> throw new IllegalArgumentException(
                    "level-type '" + raw + "' is not supported (normal or flat)");
        };
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
                    "server.properties key '" + key + "' is not a number: " + raw, e);
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
                "server.properties key '" + key + "' is not a boolean: " + raw);
    }
}
