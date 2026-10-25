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
 * @param regionSizeChunks the spatial ownership granularity (the permanent
 *                        architecture's Phase 4 — ADR-6): chunks per axis
 *                        per ownership region. The default is deliberately
 *                        huge (the whole world one region, Folia's own
 *                        regionShift=31 conservatism) so the spatial
 *                        activation changes nothing until configured; the
 *                        boundary protocols and their tests carry the
 *                        multi-region semantics.
 * @param enableCommandBlocks the {@code enable-command-block} property
 *                        (DedicatedServer line 378: {@code getBoolean(
 *                        "enable-command-block", false)}): the MC|AdvCdm
 *                        edit gate — command-block edits answer
 *                        {@code advMode.notEnabled} while off (the
 *                        execution itself is ungated, the reference's own
 *                        shape — a block that exists runs).
 * @param spawnProtectionRadius the {@code spawn-protection} property
 *                        (DedicatedServer.getSpawnProtectionRadius:
 *                        {@code getInt("spawn-protection", 16)}): the
 *                        chebyshev radius around spawn where non-operators
 *                        may not use blocks — armed only while at least
 *                        one operator exists (the reference's own
 *                        {@code getOps().isEmpty() -> false}); 0 disables.
 * @param enableRcon     the {@code enable-rcon} property (DedicatedServer
 *                        line 210: {@code getBoolean("enable-rcon", false)})
 *                        — whether the remote-control TCP listener boots.
 * @param rconPassword   the {@code rcon.password} property (RconServer line
 *                        26: {@code getString("rcon.password", "")}); an
 *                        empty password is the listener's own refusal law
 *                        (RconServer.start lines 91-92: "No rcon password
 *                        set ..., rcon disabled!").
 * @param rconPort       the {@code rcon.port} property; the compact
 *                        constructor resolves the reference's default law
 *                        (RconServer lines 29-31: {@code 0 -> serverPort +
 *                        10}). Out-of-range values are carried as-is — the
 *                        listener's start() warn-and-disable law owns them
 *                        (RconServer.start lines 93-94), never a boot
 *                        failure.
 * @param enableQuery    the {@code enable-query} property (DedicatedServer
 *                        line 203: {@code getBoolean("enable-query", false)})
 *                        — whether the GS4 UDP responder boots.
 * @param queryPort      the {@code query.port} property; resolves the
 *                        reference's default law (QueryResponseHandler
 *                        lines 67-69: {@code 0 -> serverPort}). Range law
 *                        owned by start() (lines 244-249), like rconPort.
 * @param resourcePackUrl the {@code resource-pack} property (DedicatedServer
 *                        line 111: {@code getString("resource-pack", "")})
 *                        — the server resource pack's url. Empty (the
 *                        default) means no pack is ever offered; the
 *                        join-time gate is the reference's own (PlayerManager
 *                        line 148: {@code url.length() > 0}).
 * @param resourcePackHash the {@code resource-pack-hash} property
 *                        (DedicatedServer line 111:
 *                        {@code getString("resource-pack-hash", "")}) — the
 *                        pack's sha-1 digest, carried raw like the
 *                        reference's own PropertyManager row; the packet
 *                        ctor's 40-character law (ResourcePackS2CPacket
 *                        lines 18-20) owns the length at the send site,
 *                        never a boot failure — the rconPassword precedent.
 * @param maxTickTimeMillis the {@code max-tick-time} property
 *                        (DedicatedServer line 522: {@code getOrDefault(
 *                        "max-tick-time", TimeUnit.MINUTES.toMillis(1L))})
 *                        — the tick watchdog's stall threshold in
 *                        milliseconds. The default is the reference's own
 *                        one minute; {@code 0} disables the watchdog
 *                        entirely (the reference's boot gate
 *                        {@code getMaxTickTime() > 0L}, DedicatedServer
 *                        lines 216-221). A tick whose lane stays wedged
 *                        past the threshold dumps every thread, writes a
 *                        crash report under {@code crash-reports/} and
 *                        force-exits the JVM — the reference
 *                        ServerWatchdog's own law.
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
        boolean whiteList,
        int regionSizeChunks,
        boolean enableCommandBlocks,
        int spawnProtectionRadius,
        boolean enableRcon,
        String rconPassword,
        int rconPort,
        boolean enableQuery,
        int queryPort,
        String resourcePackUrl,
        String resourcePackHash,
        long maxTickTimeMillis
) {

    /**
     * The one-region granularity (ADR-6's default): every int-range chunk
     * coordinate floor-divides into region 0, so the whole world is one
     * ownership domain until the operator configures a real split.
     */
    public static final int WHOLE_WORLD_REGION_SIZE = 1 << 30;

    /**
     * The vanilla dedicated default ({@code getInt("spawn-protection", 16)}):
     * the chebyshev radius around spawn where non-operators may not use
     * blocks — armed only while at least one operator exists (the
     * reference's own {@code getOps().isEmpty() -> false}).
     */
    public static final int DEFAULT_SPAWN_PROTECTION = 16;

    /**
     * The unset marker every compatibility constructor passes for the two
     * remote-admin ports: both resolve through the reference's own default
     * laws in the compact constructor (never a boot failure).
     */
    public static final int UNSET_REMOTE_PORT = 0;

    /**
     * The tick watchdog's default threshold: the reference's own
     * {@code TimeUnit.MINUTES.toMillis(1L)} (DedicatedServer line 522).
     */
    public static final long DEFAULT_MAX_TICK_TIME_MILLIS = 60_000L;

    /**
     * The tick watchdog's disabled marker: the reference's boot gate
     * ({@code getMaxTickTime() > 0L}) never starts the watchdog thread at 0.
     */
    public static final long WATCHDOG_DISABLED = 0L;

    /** Compatibility constructor for callers that do not care about mode/type. */
    public EngineConfig(String host, int port, String worldName, String motd,
                        int maxPlayers, int viewDistance, int tickRateHz, String dataDir) {
        this(host, port, worldName, motd, maxPlayers, viewDistance, tickRateHz,
                dataDir, GameMode.SURVIVAL, true, "flat", false, WHOLE_WORLD_REGION_SIZE,
                false, DEFAULT_SPAWN_PROTECTION);
    }

    /** Compatibility constructor for callers that set only the mode. */
    public EngineConfig(String host, int port, String worldName, String motd,
                        int maxPlayers, int viewDistance, int tickRateHz, String dataDir,
                        GameMode gamemode) {
        this(host, port, worldName, motd, maxPlayers, viewDistance, tickRateHz,
                dataDir, gamemode, true, "flat", false, WHOLE_WORLD_REGION_SIZE,
                false, DEFAULT_SPAWN_PROTECTION);
    }

    /**
     * Compatibility constructor for the pre-Slice-72 shape (the command
     * block gate defaults OFF, the vanilla dedicated default).
     */
    public EngineConfig(String host, int port, String worldName, String motd,
                        int maxPlayers, int viewDistance, int tickRateHz, String dataDir,
                        GameMode gamemode, boolean pvp, String levelType, boolean whiteList,
                        int regionSizeChunks) {
        this(host, port, worldName, motd, maxPlayers, viewDistance, tickRateHz,
                dataDir, gamemode, pvp, levelType, whiteList, regionSizeChunks, false,
                DEFAULT_SPAWN_PROTECTION);
    }

    /**
     * Compatibility constructor for the pre-N4 shape (the spawn protection
     * keeps the vanilla dedicated default 16).
     */
    public EngineConfig(String host, int port, String worldName, String motd,
                        int maxPlayers, int viewDistance, int tickRateHz, String dataDir,
                        GameMode gamemode, boolean pvp, String levelType, boolean whiteList,
                        int regionSizeChunks, boolean enableCommandBlocks) {
        this(host, port, worldName, motd, maxPlayers, viewDistance, tickRateHz,
                dataDir, gamemode, pvp, levelType, whiteList, regionSizeChunks,
                enableCommandBlocks, DEFAULT_SPAWN_PROTECTION);
    }

    /**
     * Compatibility constructor for the pre-remote-admin canonical shape
     * (both listeners disabled; the ports resolve through their reference
     * default laws — port+10 for RCON, the server port for the query).
     */
    public EngineConfig(String host, int port, String worldName, String motd,
                        int maxPlayers, int viewDistance, int tickRateHz, String dataDir,
                        GameMode gamemode, boolean pvp, String levelType, boolean whiteList,
                        int regionSizeChunks, boolean enableCommandBlocks,
                        int spawnProtectionRadius) {
        this(host, port, worldName, motd, maxPlayers, viewDistance, tickRateHz, dataDir,
                gamemode, pvp, levelType, whiteList, regionSizeChunks, enableCommandBlocks,
                spawnProtectionRadius, false, "", UNSET_REMOTE_PORT, false, UNSET_REMOTE_PORT,
                "", "");
    }

    /**
     * Compatibility constructor for the pre-watchdog canonical shape (the
     * 22-arg form the existing callers compile against): the watchdog keeps
     * the reference's one-minute default (DedicatedServer line 522).
     */
    public EngineConfig(String host, int port, String worldName, String motd,
                        int maxPlayers, int viewDistance, int tickRateHz, String dataDir,
                        GameMode gamemode, boolean pvp, String levelType, boolean whiteList,
                        int regionSizeChunks, boolean enableCommandBlocks,
                        int spawnProtectionRadius, boolean enableRcon, String rconPassword,
                        int rconPort, boolean enableQuery, int queryPort,
                        String resourcePackUrl, String resourcePackHash) {
        this(host, port, worldName, motd, maxPlayers, viewDistance, tickRateHz, dataDir,
                gamemode, pvp, levelType, whiteList, regionSizeChunks, enableCommandBlocks,
                spawnProtectionRadius, enableRcon, rconPassword, rconPort, enableQuery,
                queryPort, resourcePackUrl, resourcePackHash, DEFAULT_MAX_TICK_TIME_MILLIS);
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
        if (regionSizeChunks < 1 || regionSizeChunks > WHOLE_WORLD_REGION_SIZE) {
            throw new IllegalArgumentException("regionSizeChunks out of range (1.."
                    + WHOLE_WORLD_REGION_SIZE + "): " + regionSizeChunks);
        }
        if (spawnProtectionRadius < 0) {
            throw new IllegalArgumentException("spawnProtectionRadius must not be negative: "
                    + spawnProtectionRadius);
        }
        if (maxTickTimeMillis < 0) {
            throw new IllegalArgumentException("max-tick-time must not be negative (0 disables the watchdog): "
                    + maxTickTimeMillis);
        }
        Objects.requireNonNull(dataDir, "dataDir");
        if (dataDir.isBlank()) {
            throw new IllegalArgumentException("dataDir must not be blank");
        }
        Objects.requireNonNull(rconPassword, "rconPassword");
        Objects.requireNonNull(resourcePackUrl, "resourcePackUrl");
        Objects.requireNonNull(resourcePackHash, "resourcePackHash");
        // The remote-admin ports' default laws, resolved where the reference
        // resolves them (RconServer lines 29-31, QueryResponseHandler lines
        // 67-69) — but at the engine's one validation site, because the
        // engine's config is an immutable load-once record: the reference's
        // setProperty/saveProperties write-back becomes ServerLayout's
        // first-boot rows (rcon.port = port+10, query.port = port). Out-of-
        // range values stay unvalidated here on purpose — the listeners'
        // start() warn-and-disable laws own them (never a boot failure).
        rconPort = rconPort <= 0 ? port + 10 : rconPort;
        queryPort = queryPort <= 0 ? port : queryPort;
    }

    public static EngineConfig defaults() {
        return new EngineConfig("0.0.0.0", 25565, "world",
                "A ZaminTorch Server", 20, 4, 20, ".", GameMode.SURVIVAL, true,
                "normal", false, WHOLE_WORLD_REGION_SIZE, false);
    }

    /**
     * A copy with a different bind port (ML-009a — the CLI override's
     * own shape; the dedicated server's precedence CLI &gt; properties
     * &gt; default). The compact constructor re-validates the range
     * (0-65535), so an out-of-range override fails boot loudly. The
     * remote-admin ports keep their already-resolved values: they
     * follow their OWN rows and the first-boot write-back law recorded
     * above — the same envelope as a properties-only port change.
     */
    public EngineConfig withPort(int port) {
        // The coordinator's merge repair: the copy carries EVERY loaded row
        // through — the resource-pack pair rides beside rcon/query exactly
        // as loaded (PR #43 branched before #39 added the pair; the 23-arg
        // form it was written against no longer compiles post-#39). The
        // watchdog's row rides the same law (the pre-loaded threshold
        // survives a --port override — never silently reset to the default).
        return new EngineConfig(host, port, worldName, motd, maxPlayers, viewDistance,
                tickRateHz, dataDir, gamemode, pvp, levelType, whiteList, regionSizeChunks,
                enableCommandBlocks, spawnProtectionRadius, enableRcon, rconPassword,
                rconPort, enableQuery, queryPort, resourcePackUrl, resourcePackHash,
                maxTickTimeMillis);
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
