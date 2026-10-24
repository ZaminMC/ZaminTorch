package net.zaminmc.torch.server.spawner;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.entity.MobType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spawners' ZSD persistence (the reference's MobSpawner.writeNbt
 * field set, lines 236-252): the typed row carries the delay + the six
 * cadence rows through the byte-identical round trip, the untyped row
 * never persists (the whole write sits inside the EntityId-non-empty
 * gate), and a corrupt file loads empty while preserved.
 */
class SpawnerDataStoreTest {

    @TempDir
    Path dataDir;

    private static SpawnerManager.MobSpawnerBlockEntity typed(int dimension, MobType type,
                                                              int delay, int min, int max,
                                                              int count, int nearby, int range,
                                                              int spawnRange) {
        SpawnerManager.MobSpawnerBlockEntity spawner = new SpawnerManager.MobSpawnerBlockEntity();
        spawner.setDimension(dimension);
        spawner.setType(type);
        spawner.setDelay(delay);
        spawner.setMinSpawnDelay(min);
        spawner.setMaxSpawnDelay(max);
        spawner.setSpawnCount(count);
        spawner.setMaxNearbyEntities(nearby);
        spawner.setRequiredPlayerRange(range);
        spawner.setSpawnRange(spawnRange);
        return spawner;
    }

    @Test
    void theTypedRowRoundTripsEveryField() throws Exception {
        Path file = dataDir.resolve("spawners.bin");
        SpawnerDataStore store = new SpawnerDataStore(file);
        Map<BlockPosition, SpawnerManager.MobSpawnerBlockEntity> live = new LinkedHashMap<>();
        // The overworld dungeon row (the reference defaults) and a
        // re-tuned nether row (the egg re-type + the cadence NBT knobs).
        live.put(new BlockPosition(10, 40, 10), typed(0, MobType.ZOMBIE,
                137, 200, 800, 4, 6, 16, 4));
        live.put(new BlockPosition(-30, 55, 42), typed(-1, MobType.BLAZE,
                99, 300, 600, 2, 12, 24, 6));
        store.save(live);
        assertTrue(Files.exists(file), "the store wrote the file");

        Map<BlockPosition, SpawnerManager.MobSpawnerBlockEntity> loaded = store.load();
        assertEquals(2, loaded.size(), "both typed rows returned");

        SpawnerManager.MobSpawnerBlockEntity zombie = loaded.get(new BlockPosition(10, 40, 10));
        assertEquals(MobType.ZOMBIE, zombie.type(), "the zombie type survived");
        assertEquals(0, zombie.dimension(), "the overworld dimension survived");
        assertEquals(137, zombie.delay(), "the live delay survived");
        assertEquals(200, zombie.minSpawnDelay());
        assertEquals(800, zombie.maxSpawnDelay());
        assertEquals(4, zombie.spawnCount());
        assertEquals(6, zombie.maxNearbyEntities());
        assertEquals(16, zombie.requiredPlayerRange());
        assertEquals(4, zombie.spawnRange());

        SpawnerManager.MobSpawnerBlockEntity blaze = loaded.get(new BlockPosition(-30, 55, 42));
        assertEquals(MobType.BLAZE, blaze.type(), "the blaze type survived");
        assertEquals(-1, blaze.dimension(), "the nether dimension survived");
        assertEquals(300, blaze.minSpawnDelay());
        assertEquals(24, blaze.requiredPlayerRange());
        assertEquals(6, blaze.spawnRange());
    }

    @Test
    void theUntypedRowNeverPersists() throws Exception {
        Path file = dataDir.resolve("spawners.bin");
        SpawnerDataStore store = new SpawnerDataStore(file);
        Map<BlockPosition, SpawnerManager.MobSpawnerBlockEntity> live = new LinkedHashMap<>();
        SpawnerManager.MobSpawnerBlockEntity untyped = new SpawnerManager.MobSpawnerBlockEntity();
        untyped.setDimension(0);
        live.put(new BlockPosition(0, 5, 0), untyped);
        live.put(new BlockPosition(1, 5, 1), typed(0, MobType.SPIDER,
                20, 200, 800, 4, 6, 16, 4));
        store.save(live);

        Map<BlockPosition, SpawnerManager.MobSpawnerBlockEntity> loaded = store.load();
        assertEquals(1, loaded.size(), "only the typed row persisted (the writeNbt EntityId gate)");
        assertTrue(loaded.containsKey(new BlockPosition(1, 5, 1)));
        assertFalse(loaded.containsKey(new BlockPosition(0, 5, 0)),
                "the untyped spawner never persists");
    }

    @Test
    void theCorruptFileLoadsEmptyAndIsPreserved() throws Exception {
        Path corrupt = dataDir.resolve("corrupt.bin");
        Files.writeString(corrupt, "not a zsd file");
        SpawnerDataStore store = new SpawnerDataStore(corrupt);
        assertTrue(store.load().isEmpty(), "the corrupt file loads empty");
        assertTrue(Files.exists(corrupt), "the corrupt file is preserved, not destroyed");
    }
}
