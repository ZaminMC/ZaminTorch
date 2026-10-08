package net.zaminmc.torch.server;

import net.zaminmc.torch.server.config.ConfigLoader;
import net.zaminmc.torch.server.config.EngineConfig;
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
    void missingFileYieldsDefaults() throws Exception {
        EngineConfig config = ConfigLoader.loadOrDefault(tempDir.resolve("absent.properties"));
        assertEquals(25565, config.port());
        assertEquals(20, config.maxPlayers());
        assertEquals(4, config.viewDistance());
        assertEquals("world", config.worldName());
    }

    @Test
    void valuesAreParsedAndValidated() {
        Properties properties = new Properties();
        properties.setProperty("port", "25599");
        properties.setProperty("max-players", "50");
        properties.setProperty("view-distance", "8");
        properties.setProperty("motd", "A test server");
        EngineConfig config = ConfigLoader.fromProperties(properties);
        assertEquals(25599, config.port());
        assertEquals(50, config.maxPlayers());
        assertEquals(8, config.viewDistance());
        assertEquals("A test server", config.motd());
    }

    @Test
    void invalidValueFailsWithClearMessage() {
        Properties properties = new Properties();
        properties.setProperty("port", "not-a-number");
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> ConfigLoader.fromProperties(properties));
        assertTrue(error.getMessage().contains("port"));
    }

    @Test
    void outOfRangeValueIsRejected() {
        Properties properties = new Properties();
        properties.setProperty("view-distance", "99");
        assertThrows(IllegalArgumentException.class, () -> ConfigLoader.fromProperties(properties));
    }

    @Test
    void roundTripThroughFileKeepsMeaning() throws Exception {
        Path file = tempDir.resolve("zamin.properties");
        ConfigLoader.writeDefault(file);
        assertTrue(Files.exists(file));
        EngineConfig config = ConfigLoader.loadOrDefault(file);
        assertEquals(EngineConfig.defaults(), config);
    }
}
