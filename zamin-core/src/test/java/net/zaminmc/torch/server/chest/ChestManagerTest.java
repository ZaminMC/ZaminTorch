package net.zaminmc.torch.server.chest;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
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

        store.save(Map.of(0, Map.of(POS, original)));
        Map<BlockPosition, ChestBlockEntity> loaded = new java.util.LinkedHashMap<>();
        for (net.zaminmc.torch.server.chest.ChestDataStore.Loaded row : store.load()) {
            loaded.put(row.position(), row.chest());
        }
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

        java.util.List<net.zaminmc.torch.server.entity.ItemEntity> spawned = new java.util.ArrayList<>();
        net.zaminmc.torch.server.entity.ItemEntityManager items = new net.zaminmc.torch.server.entity.ItemEntityManager(
                (x, y, z) -> true, new java.util.Random(), 500_000);
        items.addListener(new net.zaminmc.torch.server.entity.ItemEntityManager.Listener() {
            @Override
            public void onItemSpawned(net.zaminmc.torch.server.entity.ItemEntity entity) {
                spawned.add(entity);
            }

            @Override
            public void onItemMoved(net.zaminmc.torch.server.entity.ItemEntity entity) {
            }

            @Override
            public void onItemCollected(net.zaminmc.torch.server.entity.ItemEntity entity,
                                        net.zaminmc.torch.server.player.PlayerSession collector,
                                        int collectedCount) {
            }

            @Override
            public void onItemStackChanged(net.zaminmc.torch.server.entity.ItemEntity entity) {
            }

            @Override
            public void onItemRemoved(net.zaminmc.torch.server.entity.ItemEntity entity, String reason) {
            }
        });
        manager.onBlockBroken(POS, items);
        // THE SPILL'S CHUNK-SPLIT WALK (Wave PE-6 — InventoryUtils.dropItem
        // lines 31-50): a 12-stack spills as {@code nextInt(21) + 10}
        // chunks capped at the remainder — one chunk when the roll pays
        // 12 whole, two when it splits — every chunk 1-30 with the TOTAL
        // preserved and the charcoal variant riding every chunk.
        int dirtTotal = 0;
        for (net.zaminmc.torch.server.entity.ItemEntity entity : spawned) {
            int count = entity.stack().count();
            assertTrue(count >= 1 && count <= 30,
                    "every spill chunk rides the 10-30 split band capped at the stack "
                            + "(observed " + count + ")");
            if (entity.stack().type().equals(BuiltinItems.DIRT)) {
                dirtTotal += count;
            } else {
                assertEquals(1, entity.stack().damage(),
                        "the charcoal variant rides every spill chunk");
            }
            assertTrue(entity.pickupDelayTicks() == 0,
                    "the spill form carries NO pickup delay (InventoryUtils never "
                            + "calls setDefaultPickUpDelay on this path)");
            assertTrue(entity.position().x() >= POS.x() + 0.1
                    && entity.position().x() <= POS.x() + 0.9,
                    "the spill band's x rides [cell+0.1, cell+0.9]");
        }
        assertEquals(12, dirtTotal, "the dirt stack's total preserved across the chunks");
        int coalChunks = countOf(spawned, BuiltinItems.COAL);
        assertEquals(1, coalChunks, "the 2-stack spills one chunk (the cap)");
        for (net.zaminmc.torch.server.entity.ItemEntity entity : spawned) {
            if (entity.stack().type().equals(BuiltinItems.COAL)) {
                assertEquals(2, entity.stack().count(), "the capped chunk carries the whole 2");
            }
        }
        assertNull(manager.peek(POS), "the broken chest's state is gone");

        // Breaking an untracked position stays calm.
        int before = spawned.size();
        manager.onBlockBroken(new BlockPosition(9, 9, 9), items);
        assertEquals(before, spawned.size());
    }

    private static int countOf(java.util.List<net.zaminmc.torch.server.entity.ItemEntity> spawned,
                               net.zaminmc.torch.item.ItemType type) {
        int n = 0;
        for (net.zaminmc.torch.server.entity.ItemEntity entity : spawned) {
            if (entity.stack().type().equals(type)) {
                n++;
            }
        }
        return n;
    }
}
