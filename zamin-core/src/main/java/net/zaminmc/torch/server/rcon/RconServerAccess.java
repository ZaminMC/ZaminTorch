package net.zaminmc.torch.server.rcon;

/**
 * The server-facing surface both remote-admin listeners consume, 1:1 the
 * reference's DedicatedServerAccess
 * (reference/1.8.8/net/minecraft/server/dedicated/DedicatedServerAccess.java,
 * 33 lines, every method in the reference's own order): the property reads,
 * the status reads the GS4 reply carries, the RCON command route, and the
 * four log verbs. The engine's implementation is
 * {@link EngineServerAccess} (the adapter over EngineServer + EngineConfig);
 * the reference's implementation was the DedicatedServer itself.
 */
public interface RconServerAccess {

    /**
     * The engine adapter (the rcon package's own boundary: EngineServer
     * constructs its access through here, the implementation class stays
     * package-private).
     */
    static RconServerAccess forEngine(net.zaminmc.torch.server.EngineServer engine) {
        return new EngineServerAccess(engine);
    }

    int getIntProperty(String key, int defaultValue);

    String getStringProperty(String key, String defaultValue);

    void setProperty(String key, Object value);

    void saveProperties();

    String getPropertiesFilePath();

    String getIp();

    int getPort();

    String getMotd();

    String getGameVersion();

    int getPlayerCount();

    int getMaxPlayerCount();

    String[] getPlayerNames();

    String getWorldSaveName();

    String getPlugins();

    /**
     * The RCON command route (the reference DedicatedServer line 531). The
     * calling client thread blocks inside until the command's captured
     * output is ready — the §6 ownership law: the remote thread never
     * touches world state, the dispatch runs on the tick lane.
     */
    String runRconCommand(String command) throws Exception;

    boolean isDebuggingEnabled();

    void info(String message);

    void warn(String message);

    void error(String message);

    /** The reference's fine-grained per-packet log verb. */
    void log(String message);
}
