package net.zamin.engine.furnace;

import net.zamin.api.BlockPosition;
import net.zamin.api.ItemStack;
import net.zamin.engine.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manager semantics and ZFD v1 persistence: break spills, state restore, and
 * a full save/load round trip preserving slots and burn state.
 */
class FurnaceManagerTest {

    private static final BlockPosition POS = new BlockPosition(7, 4, -3);

    @Test
    void getOrCreateIsStablePerPosition() {
        FurnaceManager manager = new FurnaceManager();
        FurnaceBlockEntity a = manager.getOrCreate(POS);
        FurnaceBlockEntity b = manager.getOrCreate(POS);
        assertTrue(a == b, "the same block is one furnace");
        assertNull(manager.peek(new BlockPosition(0, 0, 0)));
    }

    @Test
    void persistenceRoundTripPreservesSlotsAndBurnState(@TempDir Path dir) {
        FurnaceDataStore store = new FurnaceDataStore(dir.resolve("furnaces.bin"));
        FurnaceBlockEntity original = new FurnaceBlockEntity();
        original.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 5));
        original.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.COAL, 2));
        original.setSlot(FurnaceBlockEntity.SLOT_OUTPUT,
                new ItemStack(BuiltinItems.IRON_INGOT, 3, 0));
        original.restore(1370, 1600, 88);

        store.save(Map.of(POS, original));
        Map<BlockPosition, FurnaceBlockEntity> loaded = store.load();
        assertEquals(1, loaded.size());
        FurnaceBlockEntity restored = loaded.get(POS);
        assertEquals(5, restored.input().count());
        assertEquals(2, restored.fuel().count());
        assertEquals(3, restored.output().count());
        assertEquals(1370, restored.burnTimeRemaining());
        assertEquals(1600, restored.burnTimeTotal());
        assertEquals(88, restored.cookTime());
    }

    @Test
    void corruptedStoreQuarantinesAndLoadsEmpty(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("furnaces.bin");
        java.nio.file.Files.writeString(file, "ZFD1garbage-not-a-real-payload");
        FurnaceDataStore store = new FurnaceDataStore(file);
        assertTrue(store.load().isEmpty());
        assertTrue(java.nio.file.Files.exists(file.resolveSibling("furnaces.bin.corrupt")),
                "the corrupt file is preserved beside the storage path");
    }

    @Test
    void unknownSavedItemsAreDroppedNotFatal(@TempDir Path dir) {
        FurnaceDataStore store = new FurnaceDataStore(dir.resolve("furnaces.bin"));
        FurnaceBlockEntity original = new FurnaceBlockEntity();
        original.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 1));
        store.save(Map.of(POS, original));
        // A registry that forgot iron ore still loads the furnace.
        Map<BlockPosition, FurnaceBlockEntity> loaded = store.load();
        assertEquals(1, loaded.size());
        assertEquals(1, loaded.get(POS).input().count());
    }
}
