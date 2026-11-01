package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.chest.ChestBlockEntity;
import net.zaminmc.torch.server.chest.ChestDataStore;
import net.zaminmc.torch.server.furnace.FurnaceBlockEntity;
import net.zaminmc.torch.server.furnace.FurnaceDataStore;
import net.zaminmc.torch.server.hopper.HopperBlockEntity;
import net.zaminmc.torch.server.hopper.HopperDataStore;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE CONTAINER TRIO'S COMPUTE-THEN-PUBLISH SPLIT'S OWN EVIDENCE (slice
 * S2): the deep capture + the pure serializer + the file publication.
 * THE EQUIVALENCE (the Phase-5 adoption's proof obligation — the S1
 * shape): the split path's bytes ({@code serialize(capture(live))}) and
 * the HISTORICAL interleaved walk's bytes (the pre-S2 save, replicated
 * here as the reference) are BYTE-IDENTICAL — the same magic, the same
 * version, the same rows, the same slot encoding, the same order. The
 * synchronous {@code save} composes the same three halves, so the sync
 * file and the pipeline bytes are identical by construction; the load()
 * round-trip restores the same state.
 */
class ContainerSaveSplitTest {

    @TempDir
    Path dir;

    // ---- the historical walks (the pre-S2 save bodies, verbatim) ----

    private static byte[] legacyFurnaceBytes(Map<BlockPosition, FurnaceBlockEntity> furnaces)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('F');
        out.writeByte('D');
        out.writeByte(2);
        writeVarInt(out, furnaces.size());
        for (Map.Entry<BlockPosition, FurnaceBlockEntity> entry : furnaces.entrySet()) {
            BlockPosition position = entry.getKey();
            FurnaceBlockEntity furnace = entry.getValue();
            out.writeInt(position.x());
            out.writeInt(position.y());
            out.writeInt(position.z());
            writeVarInt(out, furnace.burnTimeRemaining());
            writeVarInt(out, furnace.burnTimeTotal());
            writeVarInt(out, furnace.cookTime());
            for (ItemStack stack : furnace.snapshotSlots()) {
                writeLegacySlot(out, stack);
            }
        }
        out.flush();
        return sink.toByteArray();
    }

    private static byte[] legacyChestBytes(Map<BlockPosition, ChestBlockEntity> chests)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('C');
        out.writeByte('D');
        out.writeByte(3);
        writeVarInt(out, chests.size());
        for (Map.Entry<BlockPosition, ChestBlockEntity> entry : chests.entrySet()) {
            BlockPosition position = entry.getKey();
            ChestBlockEntity chest = entry.getValue();
            out.writeInt(position.x());
            out.writeInt(position.y());
            out.writeInt(position.z());
            for (ItemStack stack : chest.snapshotSlots()) {
                writeLegacySlot(out, stack);
            }
            // Wave FL4-GAP2: the row's own world (the overworld stamp — the
            // seat's live map is the overworld family).
            out.writeByte(0);
        }
        out.flush();
        return sink.toByteArray();
    }

    private static byte[] legacyHopperBytes(Map<BlockPosition, HopperBlockEntity> hoppers)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('H');
        out.writeByte('D');
        out.writeByte(1);
        writeVarInt(out, hoppers.size());
        for (Map.Entry<BlockPosition, HopperBlockEntity> entry : hoppers.entrySet()) {
            BlockPosition position = entry.getKey();
            HopperBlockEntity hopper = entry.getValue();
            out.writeInt(position.x());
            out.writeInt(position.y());
            out.writeInt(position.z());
            for (ItemStack stack : hopper.snapshotSlots()) {
                writeLegacySlot(out, stack);
            }
        }
        out.flush();
        return sink.toByteArray();
    }

    /** The historical slot encoding: present(u8), id, count, damage, name. */
    private static void writeLegacySlot(DataOutputStream out, ItemStack stack) throws Exception {
        if (stack.isEmpty()) {
            out.writeByte(0);
        } else {
            out.writeByte(1);
            writeString(out, stack.type().identifier().toString());
            writeVarInt(out, stack.count());
            writeVarInt(out, stack.damage());
            writeOptionalString(out, stack.displayName());
        }
    }

    private static void writeOptionalString(DataOutputStream out, String value) throws Exception {
        if (value == null) {
            writeVarInt(out, 0);
            return;
        }
        writeString(out, value);
    }

    private static void writeString(DataOutputStream out, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws Exception {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    // ---- the equivalences ----

    @Test
    void furnaceSplitIsByteIdenticalToTheHistoricalWalk() throws Exception {
        Map<BlockPosition, FurnaceBlockEntity> live = new LinkedHashMap<>();
        FurnaceBlockEntity loaded = new FurnaceBlockEntity();
        loaded.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 5));
        loaded.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.COAL, 2));
        loaded.setSlot(FurnaceBlockEntity.SLOT_OUTPUT,
                new ItemStack(BuiltinItems.IRON_INGOT, 3, 0).withName("Smelted"));
        loaded.restore(1370, 1600, 88);
        live.put(new BlockPosition(7, 4, -3), loaded);
        // the second row: every slot empty
        live.put(new BlockPosition(0, 1, 2), new FurnaceBlockEntity());
        // the third row: an EMPTY stack in a real slot is the EMPTY encoding
        // (byte 0), not a present stack — the isEmpty arm the old walk owns
        FurnaceBlockEntity zeroed = new FurnaceBlockEntity();
        zeroed.setSlot(FurnaceBlockEntity.SLOT_INPUT, ItemStack.EMPTY);
        live.put(new BlockPosition(-4, 60, 100), zeroed);

        FurnaceDataStore store = new FurnaceDataStore(dir.resolve("furnaces.bin"));

        // the split path's bytes == the historical walk's bytes
        byte[] split = store.serialize(store.capture(live));
        assertArrayEquals(legacyFurnaceBytes(live), split,
                "the off-tick serialization and the historical walk are byte-identical");

        // the synchronous save composes the same halves — the file carries
        // the same bytes, and the round-trip restores the same state
        store.save(live);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("furnaces.bin")),
                "the sync save's file carries the split path's bytes");
        Map<BlockPosition, FurnaceBlockEntity> restored = store.load();
        assertEquals(3, restored.size());
        FurnaceBlockEntity first = restored.get(new BlockPosition(7, 4, -3));
        assertEquals(5, first.input().count());
        assertEquals(2, first.fuel().count());
        assertEquals(3, first.output().count());
        assertEquals("Smelted", first.output().displayName());
        assertEquals(1370, first.burnTimeRemaining());
        assertEquals(1600, first.burnTimeTotal());
        assertEquals(88, first.cookTime());
        // the zero-count stack loads as the EMPTY slot (nothing stored)
        FurnaceBlockEntity third = restored.get(new BlockPosition(-4, 60, 100));
        assertTrue(third.input().isEmpty(), "the zero-count stack stores empty");
    }

    @Test
    void chestSplitIsByteIdenticalToTheHistoricalWalk() throws Exception {
        Map<BlockPosition, ChestBlockEntity> live = new LinkedHashMap<>();
        ChestBlockEntity seeded = new ChestBlockEntity();
        seeded.setSlot(0, ItemStack.of(BuiltinItems.IRON_INGOT, 12));
        seeded.setSlot(26, new ItemStack(BuiltinItems.IRON_ORE, 1, 40).withName("Kept"));
        live.put(new BlockPosition(10, 5, 10), seeded);
        live.put(new BlockPosition(-1, 2, 3), new ChestBlockEntity());

        ChestDataStore store = new ChestDataStore(dir.resolve("chests.bin"));

        Map<Integer, Map<BlockPosition, ChestBlockEntity>> byDimension = Map.of(0, live);
        byte[] split = store.serialize(store.capture(byDimension));
        assertArrayEquals(legacyChestBytes(live), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(byDimension);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("chests.bin")),
                "the sync save's file carries the split path's bytes");
        Map<BlockPosition, ChestBlockEntity> restored = new LinkedHashMap<>();
        for (net.zaminmc.torch.server.chest.ChestDataStore.Loaded row : store.load()) {
            restored.put(row.position(), row.chest());
        }
        assertEquals(2, restored.size());
        ChestBlockEntity first = restored.get(new BlockPosition(10, 5, 10));
        assertEquals(12, first.snapshotSlots()[0].count());
        assertEquals(1, first.snapshotSlots()[26].count());
        assertEquals(40, first.snapshotSlots()[26].damage());
        assertEquals("Kept", first.snapshotSlots()[26].displayName());
        assertTrue(first.snapshotSlots()[1].isEmpty(), "the untouched slot stays empty");
    }

    @Test
    void hopperSplitIsByteIdenticalToTheHistoricalWalk() throws Exception {
        Map<BlockPosition, HopperBlockEntity> live = new LinkedHashMap<>();
        HopperBlockEntity seeded = new HopperBlockEntity();
        seeded.setItem(2, ItemStack.of(BuiltinItems.COAL, 7));
        live.put(new BlockPosition(3, 3, 3), seeded);
        live.put(new BlockPosition(8, 8, 8), new HopperBlockEntity());

        HopperDataStore store = new HopperDataStore(dir.resolve("hoppers.bin"));

        byte[] split = store.serialize(store.capture(live));
        assertArrayEquals(legacyHopperBytes(live), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(live);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("hoppers.bin")),
                "the sync save's file carries the split path's bytes");
        Map<BlockPosition, HopperBlockEntity> restored = store.load();
        assertEquals(2, restored.size());
        HopperBlockEntity first = restored.get(new BlockPosition(3, 3, 3));
        assertEquals(7, first.snapshotSlots()[2].count());
        assertTrue(first.snapshotSlots()[0].isEmpty(), "the untouched slot stays empty");
    }

    @Test
    void theEmptyMapsProduceTheHeaderOnlyBytes() throws Exception {
        FurnaceDataStore furnaces = new FurnaceDataStore(dir.resolve("empty-f.bin"));
        ChestDataStore chests = new ChestDataStore(dir.resolve("empty-c.bin"));
        HopperDataStore hoppers = new HopperDataStore(dir.resolve("empty-h.bin"));

        byte[] furnaceBytes = furnaces.serialize(furnaces.capture(Map.of()));
        assertArrayEquals(new byte[]{'Z', 'F', 'D', 2, 0}, furnaceBytes,
                "the empty furnace snapshot is the version-2 header alone");
        byte[] chestBytes = chests.serialize(chests.capture(Map.of()));
        assertArrayEquals(new byte[]{'Z', 'C', 'D', 3, 0}, chestBytes,
                "the empty chest snapshot is the version-3 header alone"
                        + " (Wave FL4-GAP2's dimension stamp)");
        byte[] hopperBytes = hoppers.serialize(hoppers.capture(Map.of()));
        assertArrayEquals(new byte[]{'Z', 'H', 'D', 1, 0}, hopperBytes,
                "the empty hopper snapshot is the version-1 header alone");

        // the empty maps still publish and load as absent-content maps
        furnaces.writeSerialized(furnaceBytes);
        assertTrue(furnaces.load().isEmpty(), "the header-only file loads as no furnaces");
        chests.writeSerialized(chestBytes);
        assertTrue(chests.load().isEmpty(), "the header-only file loads as no chests");
        hoppers.writeSerialized(hopperBytes);
        assertTrue(hoppers.load().isEmpty(), "the header-only file loads as no hoppers");
    }

    @Test
    void theCaptureDoesNotAliasTheLiveEntities() {
        // the deep-capture contract: a mutation AFTER the capture never
        // reaches the already-captured snapshot (the compute pool's own
        // immutable-input guarantee — §14)
        Map<BlockPosition, FurnaceBlockEntity> live = new LinkedHashMap<>();
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 5));
        live.put(new BlockPosition(1, 1, 1), furnace);
        FurnaceDataStore store = new FurnaceDataStore(dir.resolve("alias.bin"));

        FurnaceDataStore.Snapshot captured = store.capture(live);
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 32));
        furnace.restore(10, 20, 30);

        assertEquals(5, captured.rows().get(0).slots()
                        .get(FurnaceBlockEntity.SLOT_INPUT).count(),
                "the captured slot count is the capture-time state");
        assertEquals(0, captured.rows().get(0).burnRemaining(),
                "the captured burn state is the capture-time state");
        assertNull(captured.rows().get(0).slots().get(FurnaceBlockEntity.SLOT_FUEL),
                "the untouched slot captured as the empty row");
    }
}
