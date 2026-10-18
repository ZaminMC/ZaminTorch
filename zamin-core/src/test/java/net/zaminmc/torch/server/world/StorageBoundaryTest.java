package net.zaminmc.torch.server.world;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The §31 immediate storage boundary: the session lock (one live writer per
 * world root, released at shutdown, no stale-marker story) and the storage
 * identity manifest (first boot writes it, wrong family/newer layout refuse
 * to touch the world — never an implicit conversion).
 */
class StorageBoundaryTest {

    @TempDir
    Path dir;

    // ---------------------------------------------------------- session lock

    @Test
    void theFirstWriterAcquiresAndTheSecondLiveWriterIsRefused() {
        Path worldRoot = dir.resolve("world");
        try (WorldSessionLock first = WorldSessionLock.acquire(worldRoot)) {
            assertTrue(first.held());
            assertTrue(Files.exists(worldRoot.resolve(WorldSessionLock.LOCK_FILE)));
            // A second LIVE writer — same process, same world — is refused
            // loudly: two authoritative writers are the §3 corruption.
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> WorldSessionLock.acquire(worldRoot));
            assertTrue(refused.getMessage().contains("already held"),
                    "the refusal names the live-writer rule: " + refused.getMessage());
            assertTrue(refused.getMessage().contains("session.lock"),
                    "the refusal names the marker for the operator");
        }
    }

    @Test
    void theLockReleasesAtCloseAndASecondWriterMayThenAcquire() {
        Path worldRoot = dir.resolve("world");
        WorldSessionLock first = WorldSessionLock.acquire(worldRoot);
        first.close();
        assertFalse(first.held());
        // The restart acquires cleanly — this is the WeatherSystemTest
        // restart walk's guarantee at the lock's own level.
        try (WorldSessionLock second = WorldSessionLock.acquire(worldRoot)) {
            assertTrue(second.held());
        }
    }

    @Test
    void aCrashedProcessLeavesNoLockToRecoverFrom() {
        // The OS lock is the authority, not the marker file: after the
        // "crash" (close without cleanup), the marker file may well remain
        // on disk — and the next boot still acquires, because a leftover
        // file without a live OS lock is meaningless noise (§31.6's exact
        // "never rely on presence or timestamp" rule).
        Path worldRoot = dir.resolve("world");
        WorldSessionLock acquired = WorldSessionLock.acquire(worldRoot);
        acquired.close();
        assertTrue(Files.exists(worldRoot.resolve(WorldSessionLock.LOCK_FILE)),
                "the marker file itself may remain");
        try (WorldSessionLock restart = WorldSessionLock.acquire(worldRoot)) {
            assertTrue(restart.held(), "a stale marker never blocks a restart");
        }
    }

    @Test
    void differentWorldRootsNeverContend() {
        try (WorldSessionLock overworld = WorldSessionLock.acquire(dir.resolve("world"));
             WorldSessionLock otherRoot = WorldSessionLock.acquire(dir.resolve("other"))) {
            assertTrue(overworld.held() && otherRoot.held());
        }
    }

    // ---------------------------------------------------------- the manifest

    @Test
    void firstBootWritesTheCurrentIdentityAndLaterBootsVerifyIt() throws Exception {
        Path dataDir = dir.resolve("world/data");
        StorageManifest first = StorageManifest.open(dataDir);
        assertEquals(StorageManifest.FORMAT_FAMILY, first.formatFamily());
        assertEquals(1, first.storageLayoutVersion());
        assertTrue(Files.exists(dataDir.resolve(StorageManifest.FILE_NAME)));
        String text = Files.readString(dataDir.resolve(StorageManifest.FILE_NAME));
        assertTrue(text.contains("format-family=zamin-delta"));
        assertTrue(text.contains("storage-layout=1"),
                "the layout version is recorded independent of the game version");
        // The restart reads the same identity back.
        StorageManifest second = StorageManifest.open(dataDir);
        assertEquals(first, second);
    }

    @Test
    void aDifferentFormatFamilyIsRefusedNeverConverted() throws Exception {
        Path dataDir = dir.resolve("world/data");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve(StorageManifest.FILE_NAME),
                "format-family=vanilla-26.1\nstorage-layout=1\n");
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> StorageManifest.open(dataDir));
        assertTrue(refused.getMessage().contains("vanilla-26.1"),
                "the refusal names the family it found");
        assertTrue(refused.getMessage().contains("never a compatibility claim")
                        || refused.getMessage().contains("compatibility claim"),
                "the refusal states the rule: directory shape is not compatibility");
    }

    @Test
    void aNewerLayoutFailsSafeRefusingToWrite() throws Exception {
        Path dataDir = dir.resolve("world/data");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve(StorageManifest.FILE_NAME),
                "format-family=zamin-delta\nstorage-layout=99\n");
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> StorageManifest.open(dataDir));
        assertTrue(refused.getMessage().contains("NEWER"),
                "the refusal names the newer-layout rule");
        assertTrue(refused.getMessage().contains("v99"));
    }

    @Test
    void aBlankOrGarbageManifestIsRefusedNotGuessed() throws Exception {
        Path dataDir = dir.resolve("world/data");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve(StorageManifest.FILE_NAME),
                "# empty comment only\n");
        assertThrows(IllegalStateException.class, () -> StorageManifest.open(dataDir));
        Files.writeString(dataDir.resolve(StorageManifest.FILE_NAME),
                "format-family=zamin-delta\nstorage-layout=banana\n");
        assertThrows(IllegalStateException.class, () -> StorageManifest.open(dataDir));
    }

    @Test
    void anUnreadableManifestRefusesRatherThanRegenerating() throws Exception {
        Path dataDir = Files.createDirectories(dir.resolve("locked/data"));
        // A directory where the manifest should be: every read fails.
        Files.createDirectories(dataDir.resolve(StorageManifest.FILE_NAME));
        assertThrows(IllegalStateException.class, () -> StorageManifest.open(dataDir));
    }

    // ---------------------------------------------------------- end to end

    @Test
    void aRealBootWritesTheManifestAndTheRestartWalksThroughIt() throws Exception {
        // Two sequential boots through the real EngineServer: the first
        // writes the manifest and takes the lock; the shutdown releases;
        // the second verifies identity and takes the lock again (the
        // PersistenceAcceptanceTest restart walk now rides both guards).
        net.zaminmc.torch.server.config.EngineConfig config =
                new net.zaminmc.torch.server.config.EngineConfig(
                        "127.0.0.1", 0, "lock-world", "it", 20, 2, 20, dir.toString());
        net.zaminmc.torch.server.EngineServer first =
                new net.zaminmc.torch.server.EngineServer(config);
        first.start();
        first.saveAllNow();
        first.shutdown(null);
        assertTrue(Files.exists(dir.resolve("lock-world/data/storage.properties")),
                "the real boot wrote the manifest beside the delta store");
        net.zaminmc.torch.server.EngineServer second =
                new net.zaminmc.torch.server.EngineServer(config);
        second.start();
        second.shutdown(null);
        // The lock file exists but no live holder remains.
        assertTrue(Files.exists(dir.resolve("lock-world/session.lock")));
    }
}
