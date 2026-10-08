package net.zamin.engine.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ZMD v1 round trips: the population snapshot survives the file, corrupt
 * files quarantine and load as absent, and unknown saved kinds drop loudly
 * while everything else restores.
 */
class MobDataStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void snapshotRoundTripsThroughTheFile() {
        Path file = tempDir.resolve("worlds/w/mobs.bin");
        MobDataStore store = new MobDataStore(file);
        List<MobManager.MobSnapshot> saved = List.of(
                new MobManager.MobSnapshot("PIG", 1.5, 5.0, -2.25, 87.5f, 10.0f),
                new MobManager.MobSnapshot("ZOMBIE", 30.0, 6.0, 11.0, 180.0f, 12.5f));
        store.save(saved);
        assertEquals(saved, store.load(), "the full population round-trips");
    }

    @Test
    void emptyFileMeansNoMobsOnBothSides() {
        MobDataStore store = new MobDataStore(tempDir.resolve("fresh.bin"));
        assertEquals(List.of(), store.load(), "an absent file loads as a fresh world");
        store.save(List.of());
        assertEquals(List.of(), store.load(), "an explicitly empty population persists");
    }

    @Test
    void corruptFilesQuarantineAndLoadAsAbsent() throws Exception {
        Path file = tempDir.resolve("corrupt.bin");
        MobDataStore store = new MobDataStore(file);
        java.nio.file.Files.write(file, new byte[] {'Z', 'M', 'D', 1, 9, 9, 9});
        assertEquals(List.of(), store.load(), "corruption loads as absent");
        assertTrue(java.nio.file.Files.exists(file.resolveSibling("corrupt.bin.corrupt")),
                "the unreadable file is preserved beside the storage path");
    }

    @Test
    void unknownSavedKindsDropWithoutKillingTheRest() {
        // Manager-level: restoreAll skips unknown kinds and restores the rest.
        MobManager manager = new MobManager(
                new MobEntity.WorldQuery() {
                    @Override
                    public boolean isSolid(double x, double y, double z) {
                        return false;
                    }

                    @Override
                    public net.zamin.api.Position nearestPlayer(double x, double y, double z,
                                                                double range) {
                        return null;
                    }
                },
                new java.util.Random(7), (position, stack) -> { }, 3000,
                (x, z) -> 4);
        List<MobManager.MobSnapshot> saved = List.of(
                new MobManager.MobSnapshot("ENDER_DRAGON", 0, 100, 0, 0f, 200f),
                new MobManager.MobSnapshot("COW", 2.0, 5.0, 3.0, 45f, 3.5f));
        assertTrue(manager.restoreAll(saved), "the known kind restored");
        assertEquals(1, manager.size());
        MobEntity cow = manager.all().get(0);
        assertEquals(MobType.COW, cow.type());
        assertEquals(3.5f, cow.health(), "the saved body restores");
        assertEquals(45.0f, cow.yaw(), "the saved facing restores");
    }
}
