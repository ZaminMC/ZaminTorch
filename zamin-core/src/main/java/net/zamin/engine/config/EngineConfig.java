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
 * @param dataDir          directory for world persistence
 * @param gamemode         the server-wide player game mode (survival/creative)
 * @param pvp              whether player melee may damage other players
 */
public record EngineConfig(
        String host,
        int port,
        String worldName,
        String motd,
        int maxPlayers,
        int viewDistance,
        int tickRateHz,
        String dataDir,
        GameMode gamemode,
        boolean pvp
) {

    /** Compatibility constructor for callers that do not care about the mode. */
    public EngineConfig(String host, int port, String worldName, String motd,
                        int maxPlayers, int viewDistance, int tickRateHz, String dataDir) {
        this(host, port, worldName, motd, maxPlayers, viewDistance, tickRateHz,
                dataDir, GameMode.SURVIVAL, true);
    }

    /** Compatibility constructor for callers that do not care about the mode. */
    public EngineConfig(String host, int port, String worldName, String motd,
                        int maxPlayers, int viewDistance, int tickRateHz, String dataDir,
                        GameMode gamemode) {
        this(host, port, worldName, motd, maxPlayers, viewDistance, tickRateHz,
                dataDir, gamemode, true);
    }

    public EngineConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(gamemode, "gamemode");
        Objects.requireNonNull(motd, "motd");
        if (port < 0 || port > 65535) {
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
        Objects.requireNonNull(dataDir, "dataDir");
        if (dataDir.isBlank()) {
            throw new IllegalArgumentException("dataDir must not be blank");
        }
    }

    public static EngineConfig defaults() {
        return new EngineConfig("0.0.0.0", 25565, "world",
                "A ZaminTorch server", 20, 4, 20, ".", GameMode.SURVIVAL, true);
    }

    /** The file operators edit. Subsystems never read this file directly. */
    public static final Path DEFAULT_FILE = Path.of("zamin.properties");
}
