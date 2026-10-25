package net.zaminmc.torch.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-013's config surface: the {@code max-tick-time} row (DedicatedServer
 * line 522's own spelling) loads through ConfigLoader with the reference's
 * one-minute default, 0 carries as the boot gate's disabled marker, the
 * first-boot template carries the row for operators to discover, and the
 * compatibility constructors keep the reference's default (the pre-ML-013
 * shapes never saw the row).
 */
class MaxTickTimeConfigTest {

    @Test
    void theReferenceRowLoadsWithTheMinuteDefault() {
        Properties empty = new Properties();
        EngineConfig config = ConfigLoader.fromProperties(empty);
        assertEquals(60_000L, config.maxTickTimeMillis(),
                "the reference's own default: getOrDefault(\"max-tick-time\", MINUTES.toMillis(1))");
        assertEquals(EngineConfig.DEFAULT_MAX_TICK_TIME_MILLIS, config.maxTickTimeMillis(),
                "the model's constant IS the reference's default");

        Properties configured = new Properties();
        configured.setProperty("max-tick-time", "30000");
        assertEquals(30_000L, ConfigLoader.fromProperties(configured).maxTickTimeMillis(),
                "an explicit row carries through");

        Properties disabled = new Properties();
        disabled.setProperty("max-tick-time", "0");
        assertEquals(0L, ConfigLoader.fromProperties(disabled).maxTickTimeMillis(),
                "0 carries as 0 — the boot gate's disabled marker, never a default");
    }

    @Test
    void aBlankRowReadsAsTheDefault() {
        // The loader's row convention: a blank row is the fallback — the
        // reference's PropertyManager reads a missing row the same way.
        Properties blank = new Properties();
        blank.setProperty("max-tick-time", "");
        assertEquals(EngineConfig.DEFAULT_MAX_TICK_TIME_MILLIS,
                ConfigLoader.fromProperties(blank).maxTickTimeMillis(),
                "a blank row is unconfigured");
    }

    @Test
    void aMalformedRowFailsBootLoudly() {
        Properties malformed = new Properties();
        malformed.setProperty("max-tick-time", "sixty seconds");
        assertThrows(IllegalArgumentException.class,
                () -> ConfigLoader.fromProperties(malformed),
                "a non-number row fails at boot with a clear message, never at runtime");
    }

    @Test
    void aNegativeRowFailsValidation() {
        assertThrows(IllegalArgumentException.class, () -> new EngineConfig(
                "0.0.0.0", 25565, "world", "motd", 20, 4, 20, ".",
                net.zaminmc.torch.GameMode.SURVIVAL, true, "normal", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE, false, 16,
                false, "", 0, false, 0, "", "", -1L),
                "a negative threshold is a config bug, not a fire-on-boot watchdog");
    }

    @Test
    void theFirstBootTemplateCarriesTheRow(@TempDir Path root) throws Exception {
        ServerLayout.ensureServerProperties(root);
        String text = Files.readString(root.resolve("server.properties"));
        assertTrue(text.contains("max-tick-time=60000"),
                "the template carries the reference's resolved default: " + text);
        // The generated defaults parse back through the loader.
        EngineConfig loaded = ConfigLoader.fromProperties(new Properties() {{
            load(Files.newInputStream(root.resolve("server.properties")));
        }});
        assertEquals(60_000L, loaded.maxTickTimeMillis(),
                "the template's row parses back as the default");
    }

    @Test
    void theCompatibilityShapesKeepTheReferenceDefault() {
        // The 22-arg pre-watchdog canonical shape: the one-minute default.
        EngineConfig legacy = new EngineConfig("0.0.0.0", 25565, "world", "motd",
                20, 4, 20, ".", net.zaminmc.torch.GameMode.SURVIVAL, true, "normal",
                false, EngineConfig.WHOLE_WORLD_REGION_SIZE, false, 16,
                false, "", 0, false, 0, "", "");
        assertEquals(EngineConfig.DEFAULT_MAX_TICK_TIME_MILLIS, legacy.maxTickTimeMillis(),
                "the pre-ML-013 shape keeps the reference's default");

        // The port override carries the LOADED row through, never a reset.
        EngineConfig overridden = legacy.withPort(25566);
        assertEquals(legacy.maxTickTimeMillis(), overridden.maxTickTimeMillis(),
                "a --port override never touches the watchdog row");
    }
}
