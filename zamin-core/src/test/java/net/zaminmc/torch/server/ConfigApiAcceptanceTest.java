package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.config.ServerConfig;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The configuration read surface (§27.3's server-lifecycle wave — the
 * former NOT AUDITED row): {@link Server#config()} — the frozen
 * boot-time settings as an immutable value snapshot, readable from any
 * thread (the pure-read contract), carrying exactly what the operator's
 * file validated at boot.
 */
class ConfigApiAcceptanceTest {

    @TempDir
    Path dataDir;

    @Test
    void theConfigurationReadsBackThroughThePublicSurface() throws Exception {
        EngineConfig engineConfig = new EngineConfig("127.0.0.1", 0, "cfgworld",
                "A Config Test Server", 30, 6, 20, dataDir.toString(),
                GameMode.CREATIVE, false, "flat", true, 64);
        EngineServer engine = new EngineServer(engineConfig);
        engine.start();
        try {
            Server server = engine; // the public surface under test
            ServerConfig config = server.config();

            // Every field reads back exactly what the operator validated.
            assertEquals("127.0.0.1", config.host(), "the bind address");
            assertEquals(0, config.port(), "the bind port (the ephemeral test port)");
            assertEquals("cfgworld", config.worldName(), "the level name");
            assertEquals("A Config Test Server", config.motd(), "the status line");
            assertEquals(30, config.maxPlayers(), "the player ceiling");
            assertEquals(6, config.viewDistance(), "the view distance");
            assertEquals(20, config.tickRateHz(), "the 1.8.8 tick rate");
            assertEquals(dataDir.toString(), config.dataDir(), "the server root");
            assertEquals(GameMode.CREATIVE, config.gamemode(), "the default mode");
            assertEquals(false, config.pvp(), "the pvp flag");
            assertEquals("flat", config.levelType(), "the terrain generator");
            assertEquals(true, config.whiteList(), "the whitelist flag");
            assertEquals(64, config.regionSizeChunks(), "the ownership granularity");

            // The snapshot is immutable-by-value: two reads agree (the
            // frozen contract — no live setter surface exists).
            ServerConfig again = server.config();
            assertEquals(config, again, "the frozen configuration is stable");

            // The off-lane read (the §27.6 any-thread contract).
            AtomicReference<ServerConfig> foreign = new AtomicReference<>();
            Thread stranger = new Thread(() -> foreign.set(server.config()));
            stranger.start();
            stranger.join();
            assertEquals(config, foreign.get(), "the off-lane read answers the same value");

            // The whole-world sentinel stays the documented constant.
            assertTrue(ServerConfig.WHOLE_WORLD_REGION_SIZE == 1 << 30,
                    "the ADR-6 sentinel");
        } finally {
            engine.shutdown(null);
        }
    }

    @Test
    void theDefaultConfigurationCarriesTheHistoricalShape() throws Exception {
        // The defaults a bare operator file produces: survival, pvp on,
        // normal terrain, no whitelist, the whole world one region.
        EngineConfig engineConfig = EngineConfig.defaults();
        EngineServer engine = new EngineServer(new EngineConfig(
                engineConfig.host(), 0, engineConfig.worldName(), engineConfig.motd(),
                engineConfig.maxPlayers(), engineConfig.viewDistance(), engineConfig.tickRateHz(),
                dataDir.toString(), engineConfig.gamemode(), engineConfig.pvp(),
                engineConfig.levelType(), engineConfig.whiteList(),
                engineConfig.regionSizeChunks()));
        engine.start();
        try {
            ServerConfig config = engine.config();
            assertEquals(GameMode.SURVIVAL, config.gamemode(), "the default mode");
            assertTrue(config.pvp(), "the default pvp");
            assertEquals("normal", config.levelType(), "the default terrain");
            assertTrue(!config.whiteList(), "the default whitelist");
            assertEquals(ServerConfig.WHOLE_WORLD_REGION_SIZE, config.regionSizeChunks(),
                    "the whole-world default region");
        } finally {
            engine.shutdown(null);
        }
    }
}
