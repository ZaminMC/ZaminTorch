package net.zaminmc.torch.server.rcon;

import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The engine's implementation of {@link RconServerAccess} (the reference's
 * implementation was the DedicatedServer itself): the immutable
 * {@link EngineConfig} answers the property rows, the engine's established
 * public surfaces answer the status reads, and the RCON command route is
 * the engine's synchronous console path
 * ({@link EngineServer#runRconCommand(String)}).
 *
 * <p>Property rows: the engine's config is an immutable load-once record,
 * so the reference's runtime {@code setProperty/saveProperties} write-back
 * is a no-op here — the resolved rows were written at first boot by
 * ServerLayout instead (the named adaptation shared with the
 * listeners' constructors).</p>
 */
final class EngineServerAccess implements RconServerAccess {

    private static final Logger LOGGER = Logger.getLogger(EngineServerAccess.class.getName());

    /**
     * The game version the GS4 full-stats reply advertises (the reference
     * DedicatedServer.getGameVersion) — the engine's 1.8.8 identity, the
     * same string the protocol adapter's status response carries.
     */
    private static final String GAME_VERSION = "1.8.8";

    private final EngineServer engine;

    EngineServerAccess(EngineServer engine) {
        this.engine = engine;
    }

    private EngineConfig config() {
        return this.engine.engineConfig();
    }

    @Override
    public int getIntProperty(String key, int defaultValue) {
        return switch (key) {
            case "rcon.port" -> config().rconPort();
            case "query.port" -> config().queryPort();
            default -> defaultValue;
        };
    }

    @Override
    public String getStringProperty(String key, String defaultValue) {
        return switch (key) {
            case "rcon.password" -> config().rconPassword();
            default -> defaultValue;
        };
    }

    @Override
    public void setProperty(String key, Object value) {
        // No-op (the immutable-record adaptation): the resolved default rows
        // land through ServerLayout's first-boot template instead.
    }

    @Override
    public void saveProperties() {
        // No-op, the same adaptation.
    }

    @Override
    public String getPropertiesFilePath() {
        return Path.of(config().dataDir(), "server.properties").toString();
    }

    @Override
    public String getIp() {
        return config().host();
    }

    @Override
    public int getPort() {
        return config().port();
    }

    @Override
    public String getMotd() {
        return config().motd();
    }

    @Override
    public String getGameVersion() {
        return GAME_VERSION;
    }

    /**
     * The live census read the reference answers with the player manager's
     * count — the same concurrent-registry read the server-list ping already
     * performs from its Netty thread ({@code engine.players().size()}).
     */
    @Override
    public int getPlayerCount() {
        return this.engine.players().size();
    }

    @Override
    public int getMaxPlayerCount() {
        return config().maxPlayers();
    }

    /** The names snapshot (the reference's getPlayerNames), newest-first map order. */
    @Override
    public String[] getPlayerNames() {
        return this.engine.players().stream()
                .map(Player::name)
                .toArray(String[]::new);
    }

    @Override
    public String getWorldSaveName() {
        return config().worldName();
    }

    /** The reference DedicatedServer line 523: the dedicated server has no plugin line. */
    @Override
    public String getPlugins() {
        return "";
    }

    @Override
    public String runRconCommand(String command) throws Exception {
        return this.engine.runRconCommand(command);
    }

    /** The engine's key set has no debug row (the reference writes {@code debug=false}). */
    @Override
    public boolean isDebuggingEnabled() {
        return false;
    }

    @Override
    public void info(String message) {
        LOGGER.info(message);
    }

    @Override
    public void warn(String message) {
        LOGGER.warning(message);
    }

    @Override
    public void error(String message) {
        LOGGER.log(Level.SEVERE, message);
    }

    /** The reference's fine-grained per-packet verb — the diagnostic sink, §30's file record. */
    @Override
    public void log(String message) {
        LOGGER.log(Level.FINE, message);
    }
}
