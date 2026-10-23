package net.zaminmc.torch.launcher;

import net.zaminmc.torch.server.config.ConfigLoader;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE --PORT PRECEDENCE (ML-009a — the audit's B-4 row): the dedicated
 * server's boot law, <b>CLI &gt; properties &gt; default</b>, proven
 * through the exact composition the boot path runs
 * ({@code StartupArguments.parse(args).applyTo(ConfigLoader...)}): a
 * CLI flag overrides the properties-loaded value for THIS boot only —
 * the file is never rewritten — the properties value overrides the
 * default, and a malformed or out-of-range flag fails boot loudly (the
 * config model's own law: a bad value surfaces at boot, never at
 * runtime).
 */
class StartupArgumentsTest {

    @TempDir
    Path dir;

    private Path properties(String rows) throws Exception {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, rows);
        return file;
    }

    /** The boot path's own composition, over the given file and flags. */
    private EngineConfig bootConfig(Path file, String... args) throws Exception {
        return StartupArguments.parse(args).applyTo(ConfigLoader.loadOrDefault(file));
    }

    @Test
    void theCommandLinePortBeatsThePropertiesFile() throws Exception {
        Path file = properties("server-port=25599\nlevel-name=world\n");
        EngineConfig config = bootConfig(file, "--port", "25566");
        assertEquals(25566, config.port(), "the CLI override won");
        // Boot-only: the properties file keeps its row for the next boot.
        assertTrue(Files.readString(file).contains("server-port=25599"),
                "the override was never written back");
    }

    @Test
    void thePropertiesFileBeatsTheDefault() throws Exception {
        Path file = properties("server-port=25599\n");
        assertEquals(25599, bootConfig(file).port(), "the properties value held");
        assertEquals(25599, bootConfig(file, "--unrelated").port(),
                "an unrecognized flag changes nothing");
    }

    @Test
    void theDefaultHoldsWithoutEither() throws Exception {
        Path file = dir.resolve("server.properties");
        assertEquals(25565, bootConfig(file).port(),
                "the absent file's written defaults hold");
    }

    @Test
    void bothFlagFormsParseAndTheLastOccurrenceWins() {
        assertEquals(OptionalInt.of(25501), StartupArguments.parse(new String[]{"--port=25501"})
                .portOverride());
        assertEquals(OptionalInt.of(25502), StartupArguments.parse(
                        new String[]{"--port", "1", "--port", "25502"}).portOverride());
        // A --port-prefixed flag that is not ours stays untouched.
        assertTrue(StartupArguments.parse(new String[]{"--ports", "7"}).portOverride().isEmpty());
        assertTrue(StartupArguments.parse(new String[0]).portOverride().isEmpty());
    }

    @Test
    void malformedFlagsFailLoudly() {
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> StartupArguments.parse(new String[]{"--port"}));
        assertTrue(missing.getMessage().contains("requires a port number"),
                "the missing value names its law: " + missing);
        IllegalArgumentException notANumber = assertThrows(IllegalArgumentException.class,
                () -> StartupArguments.parse(new String[]{"--port", "abc"}));
        assertTrue(notANumber.getMessage().contains("not a number"),
                "the non-numeric value names its law: " + notANumber);
    }

    @Test
    void anOutOfRangePortFailsTheConfigModel() throws Exception {
        Path file = properties("server-port=25599\n");
        IllegalArgumentException outOfRange = assertThrows(IllegalArgumentException.class,
                () -> bootConfig(file, "--port", "70000"));
        assertTrue(outOfRange.getMessage().contains("port out of range"),
                "the engine config's range law answered: " + outOfRange);
        assertFalse(Files.readString(file).contains("70000"),
                "the refused value was never written");
    }
}
