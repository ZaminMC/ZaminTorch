package net.zaminmc.torch.server.world.anvil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The level.dat reader (the Anvil canvas' level leg): the gzip root's
 * Data compound answers the rows the engine's surfaces use — the seed,
 * the spawn triple, the two time rows, the name — and the used-vs-ignored
 * ledger names every other present row; a file without the Data compound
 * is a loud refusal, never a default world.
 */
class AnvilLevelDataTest {

    @TempDir
    Path dir;

    @Test
    void theRowsReadAndTheIgnoredLedgerNamesThem() throws Exception {
        Map<String, Object> data = AnvilFixture.compound();
        data.put("RandomSeed", 4242L);
        data.put("SpawnX", 129);
        data.put("SpawnY", 70);
        data.put("SpawnZ", 351);
        data.put("Time", 100_000L);
        data.put("DayTime", 6000L);
        data.put("LevelName", "Gold Mine");
        data.put("generatorName", "default");
        data.put("GameType", 1);
        data.put("raining", (byte) 0);
        data.put("rainTime", 0);
        data.put("LastPlayed", 1_700_000_000L);
        AnvilFixture.writeLevelDat(dir, data);

        AnvilLevelData level = AnvilLevelData.read(dir.resolve("level.dat"));
        assertEquals(4242L, level.randomSeed());
        assertEquals(129, level.spawnX());
        assertEquals(70, level.spawnY());
        assertEquals(351, level.spawnZ());
        assertEquals(100_000L, level.time());
        assertEquals(6000L, level.dayTime());
        assertEquals("Gold Mine", level.levelName());
        // The ignored ledger names every present row the engine's
        // configuration surface does not hold.
        for (String row : new String[] {"generatorName", "GameType", "raining",
                "rainTime", "LastPlayed"}) {
            assertTrue(level.ignoredReport().contains(row),
                    "the ledger names " + row + ": " + level.ignoredReport());
        }
        assertTrue(!level.ignoredReport().contains("RandomSeed"),
                "the used rows are not in the ignored ledger");
        assertTrue(!level.ignoredReport().contains("SpawnX"));
    }

    @Test
    void aMissingDayTimeFallsBackToTimeLikeTheReference() throws Exception {
        // WorldData's own arm: DayTime absent -> timeOfDay = Time.
        Map<String, Object> data = AnvilFixture.compound();
        data.put("RandomSeed", 1L);
        data.put("SpawnX", 0);
        data.put("SpawnY", 64);
        data.put("SpawnZ", 0);
        data.put("Time", 12_345L);
        AnvilFixture.writeLevelDat(dir, data);
        AnvilLevelData level = AnvilLevelData.read(dir.resolve("level.dat"));
        assertEquals(12_345L, level.dayTime(), "the reference's own fallback");
        assertEquals("", level.ignoredReport(), "the absent rows are not named");
    }

    @Test
    void aFileWithoutTheDataCompoundIsRefused() throws Exception {
        Map<String, Object> root = AnvilFixture.compound();
        root.put("NotData", AnvilFixture.compound());
        Files.write(dir.resolve("level.dat"),
                AnvilFixture.gzipNbtDocument(root));
        java.io.IOException refused = assertThrows(java.io.IOException.class,
                () -> AnvilLevelData.read(dir.resolve("level.dat")));
        assertTrue(refused.getMessage().contains("no Data compound"),
                "the refusal names the rule: " + refused.getMessage());
    }

    @Test
    void aMissingFileIsRefusedNotDefaulted() {
        assertThrows(java.io.IOException.class,
                () -> AnvilLevelData.read(dir.resolve("level.dat")),
                "a missing level.dat is a loud refusal (§31.6), never a default world");
    }
}
