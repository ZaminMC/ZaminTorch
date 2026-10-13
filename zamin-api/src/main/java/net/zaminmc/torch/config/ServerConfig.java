package net.zaminmc.torch.config;

import net.zaminmc.torch.GameMode;

/**
 * The server's validated configuration (§27.3's server-lifecycle read
 * surface): the boot-time settings the engine runs under — an immutable
 * value snapshot of the operator's file, readable from any thread.
 *
 * <p>The configuration is frozen at boot (the operator's
 * {@code server.properties}); there is no live setter surface. A change
 * requires a restart — the historical rule the engine keeps.</p>
 *
 * @param host             the bind address
 * @param port             the bind port
 * @param worldName        the primary world's name (the {@code level-name})
 * @param motd             the status response's description line
 * @param maxPlayers       the simultaneous-player ceiling
 * @param viewDistance     the server-enforced view distance in chunks
 * @param tickRateHz       the simulation rate (20 for the 1.8.8 target)
 * @param dataDir          the server root directory (where the property
 *                         file sits — the plugin data folders' parent
 *                         context)
 * @param gamemode         the default player game mode
 * @param pvp              whether player melee may damage other players
 * @param levelType        the terrain generator: {@code normal} or {@code flat}
 * @param whiteList        whether only whitelisted names may join
 * @param regionSizeChunks the spatial ownership granularity in chunks per
 *                         axis per region (the permanent architecture's
 *                         ADR-6 — the whole-world default is one region)
 */
public record ServerConfig(
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
        boolean whiteList,
        int regionSizeChunks) {

    /** The whole-world granularity sentinel (ADR-6's default). */
    public static final int WHOLE_WORLD_REGION_SIZE = 1 << 30;
}
