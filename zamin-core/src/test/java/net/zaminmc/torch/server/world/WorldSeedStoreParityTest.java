package net.zaminmc.torch.server.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world seed store's law (T-SEED — the terrain family audit's first
 * fix): the reference's own seed derivation (DedicatedServer lines
 * 170-183) with the per-world persistence the engine's delta-overlay
 * model requires, and the pre-fix migration that keeps a name-seeded
 * world's continuity.
 */
class WorldSeedStoreParityTest {

    @TempDir
    Path dataDir;

    @Test
    void theNumericSeedKeepsTheParsedValue() {
        // DedicatedServer line 174-176: parseLong, kept when != 0.
        assertEquals(12345L, WorldSeedStore.derive("12345"));
        assertEquals(-7L, WorldSeedStore.derive("-7"));
    }

    @Test
    void theStringSeedKeepsItsHashCode() {
        // DedicatedServer lines 178-180: the NumberFormatException arm.
        assertEquals("hello".hashCode(), WorldSeedStore.derive("hello"));
        assertEquals("my seed".hashCode(), WorldSeedStore.derive("my seed"));
    }

    @Test
    void theZeroSeedQuirkRollsTheRandomValue() {
        // DedicatedServer line 175: l != 0L — the "0" property keeps the
        // random roll (the reference's own quirk). Both arms must answer
        // SOME value; the two calls must differ (a random roll).
        assertNotEquals(WorldSeedStore.derive("0"), WorldSeedStore.derive("0"));
        assertNotEquals(WorldSeedStore.derive(""), WorldSeedStore.derive(""));
    }

    @Test
    void theFreshWorldPersistsAndTheSecondBootReuses() {
        // The fresh world rolls and persists; the second resolve (the
        // second boot of the same world) reuses the stored record — the
        // delta-overlay model's cross-reboot stability, the reference's
        // level.dat law.
        long first = WorldSeedStore.resolve(dataDir, "world", "");
        assertTrue(Files.exists(dataDir.resolve(WorldSeedStore.FILE_NAME)),
                "the fresh world persists its seed record");
        long second = WorldSeedStore.resolve(dataDir, "world", "");
        assertEquals(first, second, "the stored record wins on every later boot");
    }

    @Test
    void theStoredRecordWinsOverEveryDerivation() {
        // Even a conflicting level-seed: the record is authoritative.
        long first = WorldSeedStore.resolve(dataDir, "world", "");
        assertEquals(first, WorldSeedStore.resolve(dataDir, "world", "99999"),
                "a later boot's level-seed never re-seeds a stored world");
        assertEquals(first, WorldSeedStore.resolve(dataDir, "world", ""),
                "the record stays the answer");
    }

    @Test
    void theLevelSeedDrivesTheFreshWorld() throws Exception {
        // One fresh world per row: the record-wins law means a world
        // resolved once never re-derives (the seat's own subdirectories).
        Path numeric = java.nio.file.Files.createDirectories(dataDir.resolve("numeric"));
        assertEquals(424242L, WorldSeedStore.resolve(numeric, "world", "424242"));
        Path named = java.nio.file.Files.createDirectories(dataDir.resolve("named"));
        assertEquals("named".hashCode(), WorldSeedStore.resolve(named, "world", "named"));
    }

    @Test
    void thePreFixWorldAdoptsItsHistoricalNameSeed() {
        // A world with engine-touched state (the storage manifest or the
        // delta file) and no seed record: the historical name law answers
        // (the saved deltas ride that seed — the migration keeps the
        // world's continuity), and the record persists.
        try {
            Files.createDirectories(dataDir);
            Files.writeString(dataDir.resolve(StorageManifest.FILE_NAME), "marker");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        long resolved = WorldSeedStore.resolve(dataDir, "world", "");
        assertEquals(WorldSeedStore.historicalNameSeed("world"), resolved,
                "a pre-fix world keeps its name-seeded continuity");
        assertEquals(resolved, WorldSeedStore.resolve(dataDir, "world", ""),
                "the adopted seed persists for every later boot");
    }

    @Test
    void theHistoricalNameLawMatchesThePreFixEngine() {
        // The pre-fix engine's own arithmetic, kept verbatim for the
        // migration path (NormalWorldGenerator's pre-T-SEED constructor).
        long expected = 0x5A4D494E5C00L ^ (long) "world".hashCode() * 0x9E3779B97F4L;
        assertEquals(expected, WorldSeedStore.historicalNameSeed("world"));
    }
}
