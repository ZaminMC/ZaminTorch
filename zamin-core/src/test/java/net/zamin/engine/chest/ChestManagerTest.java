package net.zamin.engine.chest;

import net.zamin.api.BlockPosition;
import net.zamin.api.ItemStack;
import net.zamin.engine.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manager semantics and ZCD v1 persistence: lazy state per position, the
 * save/load round trip preserving slots (variants included — charcoal rides
 * its metadata through), and corrupt-file quarantine.
 */
class ChestManagerTest {

    private static final BlockPosition POS = new BlockPosition(-4, 5, 9);

    @Test
    void getOrCreateIsStablePerPosition() {
        ChestManager manager = new ChestManager();
        ChestBlockEntity a = manager.getOrCreate(POS);
        ChestBlockEntity b = manager.getOrCreate(POS);
        assertTrue(a == b, "the same block is one chest");
        assertNull(manager.peek(new BlockPosition(0, 0, 0)));
    }

    @Test
    void persistenceRoundTripPreservesSlotsAndVariants(@TempDir Path dir) {
        ChestDataStore store = new ChestDataStore(dir.resolve("chests.bin"));
        ChestBlockEntity original = new ChestBlockEntity();
        original.setSlot(0, ItemStack.of(BuiltinItems.DIRT, 30));
        original.setSlot(1, new ItemStack(BuiltinItems.COAL, 4, 1)); // charcoal
        original.setSlot(26, ItemStack.of(BuiltinItems.SAND, 7));    // last slot

        store.save(Map.of(POS, original));
        Map<BlockPosition, ChestBlockEntity> loaded = store.load();
        assertEquals(1, loaded.size());
        ChestBlockEntity restored = loaded.get(POS);
        assertEquals(30, restored.snapshotSlots()[0].count());
        assertEquals(4, restored.snapshotSlots()[1].count());
        assertEquals(1, restored.snapshotSlots()[1].damage(),
                "the charcoal variant survives the round trip");
        assertEquals(7, restored.snapshotSlots()[26].count());
    }

    @Test
    void corruptedStoreQuarantinesAndLoadsEmpty(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("chests.bin");
        java.nio.file.Files.writeString(file, "ZCD1garbage-not-a-real-payload");
        ChestDataStore store = new ChestDataStore(file);
        assertTrue(store.load().isEmpty());
        assertTrue(java.nio.file.Files.exists(file.resolveSibling("chests.bin.corrupt")),
                "the corrupt file is preserved beside the storage path");
    }

    @Test
    void spillEmitsEveryFilledSlotAndForgetsTheState() {
        ChestManager manager = new ChestManager();
        ChestBlockEntity chest = manager.getOrCreate(POS);
        chest.setSlot(0, ItemStack.of(BuiltinItems.DIRT, 12));
        chest.setSlot(4, new ItemStack(BuiltinItems.COAL, 2, 1));

        java.util.List<net.zamin.engine.entity.ItemEntity> spawned = new java.util.ArrayList<>();
        net.zamin.engine.entity.ItemEntityManager items = new net.zamin.engine.entity.ItemEntityManager(
                (x, y, z) -> true, new java.util.Random(), 500_000);
        items.addListener(new net.zamin.engine.entity.ItemEntityManager.Listener() {
            @Override
            public void onItemSpawned(net.zamin.engine.entity.ItemEntity entity) {
                spawned.add(entity);
            }

            @Override
            public void onItemMoved(net.zamin.engine.entity.ItemEntity entity) {
            }

            @Override
            public void onItemCollected(net.zamin.engine.entity.ItemEntity entity,
                                        net.zamin.engine.player.PlayerSession collector,
                                        int collectedCount) {
            }

            @Override
            public void onItemStackChanged(net.zamin.engine.entity.ItemEntity entity) {
            }

            @Override
            public void onItemRemoved(net.zamin.engine.entity.ItemEntity entity, String reason) {
            }
        });
        manager.onBlockBroken(POS, items);
        assertEquals(2, spawned.size(), "both filled slots spilled");
        assertEquals(12, spawned.get(0).stack().count());
        assertEquals(1, spawned.get(1).stack().damage(), "the charcoal variant rides the spill");
        assertNull(manager.peek(POS), "the broken chest's state is gone");

        // Breaking an untracked position stays calm.
        manager.onBlockBroken(new BlockPosition(9, 9, 9), items);
        assertEquals(2, spawned.size());
    }
}
