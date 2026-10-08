package net.zaminmc.torch.server.config;

import net.zaminmc.torch.GameMode;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Validated, typed server configuration. Subsystems receive this model, never the
 * raw property file, so the source of configuration stays an implementation detail.
 *
 * <p>The runtime home mirrors the historical Paper layout: {@code dataDir} is the
 * server root (where {@code server.properties} sits), the world folder is
 * {@code dataDir/<worldName>}, player bodies live under {@code world/playerdata},
 * and world-bound stores under {@code world/data}.</p>
 *
 * @param host         bind address
 * @param port         bind port
 * @param worldName    primary world name (the {@code level-name} property)
 * @param motd         status response description
 * @param maxPlayers   maximum simultaneously active players
 * @param viewDistance view distance in chunks (server-enforced, not client)
 * @param tickRateHz   simulation ticks per second (20 for the 1.8.8 target)
 * @param dataDir      the server root directory (the Paper-style home)
 * @param gamemode     the default player game mode (per-player modes override)
 * @param pvp          whether player melee may damage other players
 * @param levelType    terrain generator: normal (full world) or flat
 * @param whiteList    whether only whitelisted names may join
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
        boolean pvp,
        String levelType,
        boolean whiteList
) {

    /** Compatibility constructor for callers that do not care about mode/type. */
    public EngineConfig(String host, int port, String worldName, String motd,
                        int maxPlayers, int viewDistance, int tickRateHz, String dataDir) {
        this(host, port, worldName, motd, maxPlayers, viewDistance, tickRateHz,
                dataDir, GameMode.SURVIVAL, true, "normal", false);
    }

    /** Compatibility constructor for callers that set only the mode. */
    public EngineConfig(String host, int port, String worldName, String motd,
                        int maxPlayers, int viewDistance, int tickRateHz, String dataDir,
                        GameMode gamemode) {
        this(host, port, worldName, motd, maxPlayers, viewDistance, tickRateHz,
                dataDir, gamemode, true, "normal", false);
    }

    public EngineConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(gamemode, "gamemode");
        Objects.requireNonNull(motd, "motd");
        Objects.requireNonNull(levelType, "levelType");
        if (!levelType.equals("normal") && !levelType.equals("flat")) {
            throw new IllegalArgumentException("level-type must be normal or flat: " + levelType);
        }
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
                "A ZaminTorch Server", 20, 4, 20, ".", GameMode.SURVIVAL, true,
                "normal", false);
    }

    /** The world folder (the historical {@code world/} of the Paper layout). */
    public Path worldDir() {
        return Path.of(dataDir, worldName);
    }

    /** World-bound simulation stores (deltas, furnaces, chests, mobs). */
    public Path worldDataDir() {
        return worldDir().resolve("data");
    }

    /** Per-player body persistence (the historical world/playerdata). */
    public Path playerDataDir() {
        return worldDir().resolve("playerdata");
    }

    /** The operator's property file at the server root. */
    public static final Path DEFAULT_FILE = Path.of("server.properties");
}
