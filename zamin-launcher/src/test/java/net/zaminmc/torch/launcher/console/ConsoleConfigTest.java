package net.zaminmc.torch.launcher.console;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The console configuration (§30.3): defaults, parsing, and safe degradation. */
class ConsoleConfigTest {

    @TempDir
    Path dir;

    @Test
    void defaultsMatchTheDocumentedContract() {
        ConsoleConfig config = ConsoleConfig.defaults();
        assertEquals(ConsoleConfig.Interactive.AUTO, config.interactive());
        assertEquals(ConsoleConfig.Ansi.AUTO, config.ansi());
        assertEquals("> ", config.prompt());
        assertTrue(config.historyEnabled());
        assertEquals(Path.of("logs/console.history"), config.historyFile());
        assertEquals(500, config.historyLimit());
        assertTrue(config.sensitiveCommands().isEmpty());
        assertTrue(config.completionEnabled());
        assertEquals(Level.INFO, config.terminalThreshold());
        assertEquals(256, config.terminalQueueCapacity());
        assertTrue(config.plainFallbackEnabled());
    }

    @Test
    void firstBootWritesTheDocumentedDefaultFile() throws Exception {
        Path file = dir.resolve("console.properties");
        ConsoleConfig loaded = ConsoleConfig.loadOrDefault(file);
        assertTrue(Files.exists(file), "the default file is written for operators to discover");
        assertEquals(ConsoleConfig.defaults(), loaded);
        String text = Files.readString(file);
        assertTrue(text.contains("console.interactive=auto"));
        assertTrue(text.contains("console.terminal.log-threshold=INFO"),
                "the independent terminal threshold is documented in the file");
        assertTrue(text.contains("console.history.sensitive-commands="));
    }

    @Test
    void everyKnobParses() {
        Properties properties = new Properties();
        properties.setProperty("console.interactive", "off");
        properties.setProperty("console.ansi", "on");
        properties.setProperty("console.prompt", "$ ");
        properties.setProperty("console.history.enabled", "false");
        properties.setProperty("console.history.file", "var/h.txt");
        properties.setProperty("console.history.limit", "50");
        properties.setProperty("console.history.sensitive-commands", "login, Login ,SECRET");
        properties.setProperty("console.completion.enabled", "false");
        properties.setProperty("console.terminal.log-threshold", "WARNING");
        properties.setProperty("console.terminal.queue-capacity", "64");
        properties.setProperty("console.fallback.plain", "false");
        ConsoleConfig config = ConsoleConfig.apply(ConsoleConfig.defaults(), properties);
        assertEquals(ConsoleConfig.Interactive.OFF, config.interactive());
        assertEquals(ConsoleConfig.Ansi.ON, config.ansi());
        assertEquals("$ ", config.prompt());
        assertFalse(config.historyEnabled());
        assertEquals(Path.of("var/h.txt"), config.historyFile());
        assertEquals(50, config.historyLimit());
        assertEquals(List.of("login", "secret"), config.sensitiveCommands(),
                "the sensitive list is trimmed, lowercased, deduplicated");
        assertFalse(config.completionEnabled());
        assertEquals(Level.WARNING, config.terminalThreshold());
        assertEquals(64, config.terminalQueueCapacity());
        assertFalse(config.plainFallbackEnabled());
    }

    @Test
    void invalidValuesDegradeToDefaultsNeverFailBoot() {
        Properties properties = new Properties();
        properties.setProperty("console.interactive", "sideways");
        properties.setProperty("console.ansi", "maybe");
        properties.setProperty("console.prompt", "bad\nprompt");
        properties.setProperty("console.history.enabled", "yes");
        properties.setProperty("console.history.limit", "-3");
        properties.setProperty("console.terminal.log-threshold", "LOUDEST");
        properties.setProperty("console.terminal.queue-capacity", "0");
        ConsoleConfig fallback = ConsoleConfig.defaults();
        ConsoleConfig config = ConsoleConfig.apply(fallback, properties);
        assertEquals(fallback.interactive(), config.interactive());
        assertEquals(fallback.ansi(), config.ansi());
        assertEquals(fallback.prompt(), config.prompt());
        assertEquals(fallback.historyEnabled(), config.historyEnabled());
        assertEquals(fallback.historyLimit(), config.historyLimit());
        assertEquals(fallback.terminalThreshold(), config.terminalThreshold());
        assertEquals(fallback.terminalQueueCapacity(), config.terminalQueueCapacity());
    }

    @Test
    void unreadableFileFallsBackToDefaults() throws Exception {
        Path file = dir.resolve("console.properties");
        Files.createDirectories(file); // a directory: unreadable as a properties file
        ConsoleConfig config = ConsoleConfig.loadOrDefault(file);
        assertEquals(ConsoleConfig.defaults(), config);
    }
}
