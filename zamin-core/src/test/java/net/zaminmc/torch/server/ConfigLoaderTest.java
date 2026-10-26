package net.zaminmc.torch.server;

import net.zaminmc.torch.server.config.ConfigLoader;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.config.ServerLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void missingServerPropertiesBootstrapsDefaults() throws Exception {
        EngineConfig config = ConfigLoader.loadOrDefault(tempDir.resolve("server.properties"));
        assertEquals(25565, config.port());
        assertEquals(20, config.maxPlayers());
        assertEquals(4, config.viewDistance());
        assertEquals("world", config.worldName());
        // The default property file is written beside itself (vanilla first boot).
        assertTrue(Files.exists(tempDir.resolve("server.properties")));
    }

    @Test
    void vanillaKeysAreParsedAndValidated() {
        Properties properties = new Properties();
        properties.setProperty("server-port", "25599");
        properties.setProperty("max-players", "50");
        properties.setProperty("view-distance", "8");
        properties.setProperty("motd", "A test server");
        properties.setProperty("level-name", "overworld");
        properties.setProperty("gamemode", "1");
        properties.setProperty("level-type", "flat");
        EngineConfig config = ConfigLoader.fromProperties(properties);
        assertEquals(25599, config.port());
        assertEquals(50, config.maxPlayers());
        assertEquals(8, config.viewDistance());
        assertEquals("A test server", config.motd());
        assertEquals("overworld", config.worldName());
        assertEquals(net.zaminmc.torch.GameMode.CREATIVE, config.gamemode());
        assertEquals("flat", config.levelType());
    }

    /**
     * Wave ON-1's two rows: the online-mode gate (DedicatedServer line
     * 103's {@code getBoolean("online-mode", true)} — the reference's own
     * default is TRUE, the missing-row law this pins) and the compression
     * threshold (DedicatedServer line 451 over MinecraftServer line
     * 1122's 256 — a negative value disables the exchange, the reference's
     * own {@code threshold >= 0} gate carried unvalidated).
     */
    @Test
    void onlineModeAndCompressionRowsParseWithTheReferenceDefaults() {
        Properties properties = new Properties();
        // Neither row present: the reference's own defaults.
        EngineConfig absent = ConfigLoader.fromProperties(properties);
        assertTrue(absent.onlineMode(),
                "a missing online-mode row defaults TRUE (DedicatedServer line 103)");
        assertEquals(256, absent.networkCompressionThreshold(),
                "a missing threshold row defaults 256 (MinecraftServer line 1122)");

        properties.setProperty("online-mode", "false");
        properties.setProperty("network-compression-threshold", "512");
        EngineConfig present = ConfigLoader.fromProperties(properties);
        assertEquals(false, present.onlineMode());
        assertEquals(512, present.networkCompressionThreshold());

        // The disabled spelling: any negative value (the reference's own
        // law — -1 and -5 behave identically, never a boot failure).
        properties.setProperty("network-compression-threshold", "-1");
        assertEquals(-1, ConfigLoader.fromProperties(properties).networkCompressionThreshold());

        // The first-boot template carries the reference's resolved rows.
        assertEquals("online-mode=true", templateRow("online-mode"),
                "the template writes the vanilla TRUE default (first boot's own write)");
        assertEquals("network-compression-threshold=256", templateRow("network-compression-threshold"));
    }

    private String templateRow(String key) {
        try {
            Path file = tempDir.resolve("server.properties");
            ServerLayout.ensureServerProperties(tempDir);
            for (String line : Files.readAllLines(file)) {
                if (line.startsWith(key + "=")) {
                    return line;
                }
            }
            return "<absent>";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void emptyServerIpMeansWildcard() {
        Properties properties = new Properties();
        properties.setProperty("server-ip", "");
        assertEquals("0.0.0.0", ConfigLoader.fromProperties(properties).host());
    }

    @Test
    void invalidValueFailsWithClearMessage() {
        Properties properties = new Properties();
        properties.setProperty("server-port", "not-a-number");
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> ConfigLoader.fromProperties(properties));
        assertTrue(error.getMessage().contains("server-port"));
    }

    @Test
    void outOfRangeValueIsRejected() {
        Properties properties = new Properties();
        properties.setProperty("view-distance", "99");
        assertThrows(IllegalArgumentException.class, () -> ConfigLoader.fromProperties(properties));
    }

    @Test
    void layoutDefaultFileRoundTripsThroughTheLoader() throws Exception {
        ServerLayout.ensureServerProperties(tempDir);
        Path file = tempDir.resolve("server.properties");
        assertTrue(Files.exists(file));
        EngineConfig config = ConfigLoader.loadOrDefault(file);
        assertEquals(EngineConfig.defaults(), config);
    }
}
