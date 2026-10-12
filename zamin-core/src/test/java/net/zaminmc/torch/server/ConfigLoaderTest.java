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
