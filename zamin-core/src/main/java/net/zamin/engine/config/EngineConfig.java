package net.zamin.engine.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Validated, typed server configuration. Subsystems receive this model, never the
 * raw property file, so the source of configuration stays an implementation detail.
 *
 * @param host             bind address
 * @param port             bind port
 * @param worldName        primary world name
 * @param motd             status response description
 * @param maxPlayers       maximum simultaneously active players
 * @param viewDistance     view distance in chunks (server-enforced, not client)
 * @param tickRateHz       simulation ticks per second (20 for the 1.8.8 target)
 */
public record EngineConfig(
        String host,
        int port,
        String worldName,
        String motd,
        int maxPlayers,
        int viewDistance,
        int tickRateHz
) {

    public EngineConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(motd, "motd");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        if (maxPlayers < 1 || maxPlayers > 100_000) {
            throw new IllegalArgumentException("maxPlayers out of range: " + maxPlayers);
        }
        if (viewDistance < 1 || viewDistance > 32) {
            throw new IllegalArgumentException("viewDistance out of range: " + viewDistance);
        }
        if (tickRateHz < 1 || tickRateHz > 100) {
            throw new IllegalArgumentException("tickRateHz out of range: " + tickRateHz);
        }
    }

    public static EngineConfig defaults() {
        return new EngineConfig("0.0.0.0", 25565, "world",
                "A ZaminTorch server", 20, 4, 20);
    }

    /** The file operators edit. Subsystems never read this file directly. */
    public static final Path DEFAULT_FILE = Path.of("zamin.properties");
}
