package net.zaminmc.torch.server.rcon;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.config.ConfigLoader;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.config.ServerLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remote-admin configuration rows: the five keys the reference reads
 * (DedicatedServer line 210 {@code enable-rcon}, RconServer line 25-26
 * {@code rcon.port}/{@code rcon.password}, DedicatedServer line 203
 * {@code enable-query}, QueryResponseHandler line 41 {@code query.port})
 * flow through the engine's established config path — the ServerLayout
 * first-boot template, the ConfigLoader reads, and EngineConfig's compact
 * constructor resolutions.
 */
class RconQueryConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void theRemoteAdminRowsRoundTripThroughTheLoader() {
        Properties properties = new Properties();
        properties.setProperty("server-port", "25599");
        properties.setProperty("enable-rcon", "true");
        properties.setProperty("rcon.password", "secret-of-the-torch");
        properties.setProperty("rcon.port", "25609");
        properties.setProperty("enable-query", "true");
        properties.setProperty("query.port", "25598");
        EngineConfig config = ConfigLoader.fromProperties(properties);
        assertTrue(config.enableRcon(), "enable-rcon parsed");
        assertEquals("secret-of-the-torch", config.rconPassword(), "rcon.password parsed");
        assertEquals(25609, config.rconPort(), "an explicit rcon.port is respected");
        assertTrue(config.enableQuery(), "enable-query parsed");
        assertEquals(25598, config.queryPort(), "an explicit query.port is respected");
    }

    @Test
    void missingRowsKeepBothListenersDisabled() {
        Properties properties = new Properties();
        properties.setProperty("server-port", "25565");
        EngineConfig config = ConfigLoader.fromProperties(properties);
        assertFalse(config.enableRcon(), "enable-rcon defaults off (DedicatedServer line 210)");
        assertFalse(config.enableQuery(), "enable-query defaults off (DedicatedServer line 203)");
        assertEquals("", config.rconPassword(), "rcon.password defaults empty (RconServer line 26)");
    }

    /**
     * The reference's own default-port laws: an unset {@code rcon.port}
     * resolves to the server port + 10 (RconServer lines 29-31) and an unset
     * {@code query.port} resolves to the server port (QueryResponseHandler
     * lines 67-69).
     */
    @Test
    void unsetPortsResolveThroughTheReferenceDefaultLaws() {
        EngineConfig config = new EngineConfig("127.0.0.1", 25577, "world", "it",
                20, 4, 20, tempDir.toString(), GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE, false,
                EngineConfig.DEFAULT_SPAWN_PROTECTION,
                true, "pw", EngineConfig.UNSET_REMOTE_PORT,
                true, EngineConfig.UNSET_REMOTE_PORT);
        assertEquals(25587, config.rconPort(), "rcon.port 0 -> serverPort + 10");
        assertEquals(25577, config.queryPort(), "query.port 0 -> serverPort");
        // An explicit zero row carries the same law (the reference reads the
        // row through the same 0-check).
        EngineConfig zeroed = new EngineConfig("127.0.0.1", 25577, "world", "it",
                20, 4, 20, tempDir.toString(), GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE, false,
                EngineConfig.DEFAULT_SPAWN_PROTECTION,
                true, "pw", 0, true, 0);
        assertEquals(25587, zeroed.rconPort());
        assertEquals(25577, zeroed.queryPort());
    }

    /**
     * The compatibility constructors keep the listeners off, and both ports
     * still resolve — no existing caller changed behavior.
     */
    @Test
    void theLegacyConstructorsKeepTheListenersOff() {
        EngineConfig config = new EngineConfig("127.0.0.1", 25565, "world", "it",
                20, 4, 20, tempDir.toString());
        assertFalse(config.enableRcon());
        assertFalse(config.enableQuery());
        assertEquals(25575, config.rconPort(), "the port+10 law rides the legacy shape too");
        assertEquals(25565, config.queryPort());
    }

    @Test
    void theFirstBootTemplateCarriesTheReferenceResolvedRows() throws Exception {
        ServerLayout.ensureServerProperties(tempDir);
        Path file = tempDir.resolve("server.properties");
        assertTrue(Files.exists(file));
        String text = Files.readString(file);
        assertTrue(text.contains("enable-rcon=false"), "the enable-rcon row exists");
        assertTrue(text.contains("rcon.password="), "the rcon.password row exists");
        assertTrue(text.contains("rcon.port=25575"), "the +10 default row is written resolved");
        assertTrue(text.contains("enable-query=false"), "the enable-query row exists");
        assertTrue(text.contains("query.port=25565"), "the query default row is the server port");
        // And the written template still equals the model defaults exactly
        // (the established layout/loader round-trip law).
        assertEquals(EngineConfig.defaults(), ConfigLoader.loadOrDefault(file));
    }
}
