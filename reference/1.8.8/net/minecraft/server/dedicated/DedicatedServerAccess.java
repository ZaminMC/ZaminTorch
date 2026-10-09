package net.minecraft.server.dedicated;

public interface DedicatedServerAccess {
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

    String runRconCommand(String command);

    boolean isDebuggingEnabled();

    void info(String message);

    void warn(String message);

    void error(String message);

    void log(String message);
}
