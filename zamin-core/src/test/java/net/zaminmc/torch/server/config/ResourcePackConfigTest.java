package net.zaminmc.torch.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-017's config surface: the {@code resource-pack} and
 * {@code resource-pack-hash} rows (DedicatedServer line 111's own spellings)
 * load through ConfigLoader with the reference's empty defaults, the
 * first-boot template carries them for operators to discover, and the
 * compatibility constructors keep the pair empty (the pre-ML-017 shapes).
 */
class ResourcePackConfigTest {

    @Test
    void theReferenceRowsLoadWithEmptyDefaults() {
        Properties empty = new Properties();
        EngineConfig config = ConfigLoader.fromProperties(empty);
        assertEquals("", config.resourcePackUrl(),
                "the reference's own default: getString(\"resource-pack\", \"\")");
        assertEquals("", config.resourcePackHash(),
                "the reference's own default: getString(\"resource-pack-hash\", \"\")");

        Properties configured = new Properties();
        configured.setProperty("resource-pack", "https://example.com/pack.zip");
        configured.setProperty("resource-pack-hash", "0123456789abcdef0123456789abcdef01234567");
        EngineConfig loaded = ConfigLoader.fromProperties(configured);
        assertEquals("https://example.com/pack.zip", loaded.resourcePackUrl());
        assertEquals("0123456789abcdef0123456789abcdef01234567", loaded.resourcePackHash());
    }

    @Test
    void blankRowsReadAsUnconfigured() {
        // The loader's string() convention: a blank row is the fallback —
        // the historical server.properties ships the rows EMPTY.
        Properties blank = new Properties();
        blank.setProperty("resource-pack", "");
        blank.setProperty("resource-pack-hash", "");
        EngineConfig config = ConfigLoader.fromProperties(blank);
        assertEquals("", config.resourcePackUrl(), "a blank url row is unconfigured");
        assertEquals("", config.resourcePackHash(), "a blank hash row is unconfigured");
    }

    @Test
    void theFirstBootTemplateCarriesBothRows(@TempDir Path root) throws Exception {
        ServerLayout.ensureServerProperties(root);
        String text = Files.readString(root.resolve("server.properties"));
        assertTrue(text.contains("resource-pack="),
                "the template carries the url row (DedicatedServer's own spelling): " + text);
        assertTrue(text.contains("resource-pack-hash="),
                "the template carries the hash row: " + text);
        // The generated defaults parse back through the loader unconfigured.
        EngineConfig config = ConfigLoader.loadOrDefault(root.resolve("server.properties"));
        assertEquals("", config.resourcePackUrl());
        assertEquals("", config.resourcePackHash());
    }

    @Test
    void theCompatibilityConstructorsKeepThePairEmpty() {
        EngineConfig compat = new EngineConfig("127.0.0.1", 0, "world", "it",
                20, 4, 20, ".", net.zaminmc.torch.GameMode.SURVIVAL);
        assertEquals("", compat.resourcePackUrl(),
                "the pre-ML-017 shapes default to no pack");
        assertEquals("", compat.resourcePackHash());
        assertFalse(compat.resourcePackUrl().length() > 0,
                "the engine's join gate reads the same emptiness the reference does");
        assertEquals("", EngineConfig.defaults().resourcePackUrl(),
                "the boot defaults carry no pack");
    }
}
