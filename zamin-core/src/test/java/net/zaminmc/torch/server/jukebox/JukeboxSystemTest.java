package net.zaminmc.torch.server.jukebox;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The jukebox family on the live engine (Slice 27b) — the reference port of
 * JukeboxBlock + JukeboxBlockEntity + MusicDiscItem: the disc's insert arm
 * (the slot copy, the HAS_RECORD flip, the event 1005 carrying the disc's
 * item id), the eject arm (the stop event, the drop, the flip back), the
 * break arm (the record ejects with the stop event), the useOn/use gates
 * (a playing jukebox never takes a second disc, a record-less one never
 * consumes the click), the comparator's disc-index read, and the ZJD record
 * persistence round trip.
 */
class JukeboxSystemTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The disc table (the reference's Item.java lines 735-746)
    // ------------------------------------------------------------------

    @Test
    void theDiscTableCarriesTheReferenceOrder() {
        // The twelve discs in the registration order — the analog signal is
        // the index + 1 (13 -> 1 ... wait -> 12).
        assertEquals(12, JukeboxBlockSystem.DISC_ORDER.size());
        assertEquals(Identifier.parse("minecraft:record_13"), JukeboxBlockSystem.DISC_ORDER.get(0));
        assertEquals(Identifier.parse("minecraft:record_cat"), JukeboxBlockSystem.DISC_ORDER.get(1));
        assertEquals(Identifier.parse("minecraft:record_wait"),
                JukeboxBlockSystem.DISC_ORDER.get(11));
        // The family predicates.
        assertTrue(JukeboxBlocks.isFamily(BuiltinBlocks.JUKEBOX));
        assertTrue(JukeboxBlocks.isFamily(BuiltinBlocks.JUKEBOX_RECORD));
        assertFalse(JukeboxBlocks.isFamily(BuiltinBlocks.NOTE_BLOCK));
        assertTrue(JukeboxBlocks.isRecordVariant(BuiltinBlocks.JUKEBOX_RECORD));
        assertFalse(JukeboxBlocks.isRecordVariant(BuiltinBlocks.JUKEBOX));
        assertTrue(JukeboxBlocks.isMusicDisc(Identifier.parse("minecraft:record_13")));
        assertTrue(JukeboxBlocks.isMusicDisc(Identifier.parse("minecraft:record_wait")));
        assertFalse(JukeboxBlocks.isMusicDisc(Identifier.parse("minecraft:jukebox")));
        assertFalse(JukeboxBlocks.isMusicDisc(Identifier.parse("minecraft:diamond")));
    }

    // ------------------------------------------------------------------
    // The insert arm (MusicDiscItem.useOn lines 28-43)
    // ------------------------------------------------------------------

    @Test
    void theInsertArmStoresCopiesFlipsAndPlays() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            place(server, x, y, z, BuiltinBlocks.STONE, x, y + 1, z, BuiltinBlocks.JUKEBOX);
            BlockPosition jukeboxAt = new BlockPosition(x, y + 1, z);
            await(server, () -> server.world().getBlock(jukeboxAt)
                    .identifier().value().equals("jukebox"), "the jukebox placed");

            ConcurrentLinkedQueue<Identifier> plays = new ConcurrentLinkedQueue<>();
            AtomicInteger stops = new AtomicInteger();
            server.jukeboxes().addRecordListener((position, disc) -> {
                if (disc == null) {
                    stops.incrementAndGet();
                } else {
                    plays.add(disc);
                }
            });

            // The insert: a disc against the record-less jukebox — the slot
            // takes the COPY (count 1), the variant flips, the event carries
            // the disc's id (MusicDiscItem's own maxStackSize 1 — the held
            // stack is always a single disc).
            ItemStack held = ItemStack.of(BuiltinItems.RECORD_CAT, 1);
            server.ticker().submit(() -> server.jukeboxes().insertFromTick(jukeboxAt, held));
            await(server, () -> !plays.isEmpty(), "the insert played over the wire");
            assertEquals(Identifier.parse("minecraft:record_cat"), plays.peek(),
                    "the event carried the disc's identifier");
            await(server, () -> server.world().getBlock(jukeboxAt)
                    .identifier().value().equals("jukebox_record"), "the HAS_RECORD flip");
            ItemStack stored = server.jukeboxes().recordAt(jukeboxAt);
            assertEquals(BuiltinItems.RECORD_CAT, stored.type(), "the slot holds the disc");
            assertEquals(1, stored.count(), "the slot holds the count-1 copy");
            // The held stack itself was never touched (the caller's consume
            // arm owns the hand — MusicDiscItem's own stack.size--).
            assertEquals(1, held.count());

            // A second disc never inserts (the useOn gate: HAS_RECORD).
            AtomicReference<Boolean> secondInsert = new AtomicReference<>();
            server.ticker().submit(() -> secondInsert.set(server.jukeboxes().insertFromTick(
                    jukeboxAt, ItemStack.of(BuiltinItems.RECORD_13, 1))));
            await(server, () -> secondInsert.get() != null, "the gate answered");
            assertFalse(secondInsert.get(), "a playing jukebox never takes a second disc");
            assertEquals(1, plays.size(), "the gate refused the second disc silently");
            assertEquals(0, stops.get(), "the gate never stopped anything");
            assertEquals(BuiltinItems.RECORD_CAT, server.jukeboxes().recordAt(jukeboxAt).type(),
                    "the first disc stays");

            // A disc against a non-jukebox never inserts either.
            AtomicReference<Boolean> offTarget = new AtomicReference<>();
            server.ticker().submit(() -> offTarget.set(server.jukeboxes().insertFromTick(
                    new BlockPosition(x, y, z), ItemStack.of(BuiltinItems.RECORD_13, 1))));
            await(server, () -> offTarget.get() != null, "the off-target answered");
            assertFalse(offTarget.get(), "a disc against the floor never inserts");
            assertEquals(1, plays.size());
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The eject arm (JukeboxBlock.use lines 30-38 over removeRecord)
    // ------------------------------------------------------------------

    @Test
    void theEjectArmStopsDropsAndFlipsBack() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            place(server, x, y, z, BuiltinBlocks.STONE, x, y + 1, z, BuiltinBlocks.JUKEBOX);
            BlockPosition jukeboxAt = new BlockPosition(x, y + 1, z);
            await(server, () -> server.world().getBlock(jukeboxAt)
                    .identifier().value().equals("jukebox"), "the jukebox placed");

            ConcurrentLinkedQueue<Identifier> plays = new ConcurrentLinkedQueue<>();
            AtomicInteger stops = new AtomicInteger();
            server.jukeboxes().addRecordListener((position, disc) -> {
                if (disc == null) {
                    stops.incrementAndGet();
                } else {
                    plays.add(disc);
                }
            });
            server.ticker().submit(() -> server.jukeboxes().insertFromTick(jukeboxAt,
                    ItemStack.of(BuiltinItems.RECORD_MELLOHI, 1)));
            await(server, () -> !plays.isEmpty(), "the insert played");

            // The use on the playing jukebox: the stop event, the flip
            // back, the drop (the reference's use -> removeRecord walk).
            AtomicReference<Boolean> ejected = new AtomicReference<>();
            server.ticker().submit(() -> ejected.set(server.jukeboxes().useFromTick(jukeboxAt)));
            await(server, () -> Boolean.TRUE.equals(ejected.get()), "the eject consumed the use");
            assertEquals(1, plays.size(), "one insert's play");
            assertEquals(1, stops.get(), "the stop event carried the null disc");
            await(server, () -> server.world().getBlock(jukeboxAt)
                    .identifier().value().equals("jukebox"), "the flip back");
            assertNull(server.jukeboxes().recordAt(jukeboxAt), "the slot cleared");
            // The record dropped as an item entity at the block.
            await(server, () -> server.itemEntities().all().stream().anyMatch(entity -> entity.stack() != null
                    && entity.stack().type().equals(BuiltinItems.RECORD_MELLOHI)),
                    "the record dropped into the world");

            // The next use on the now record-less jukebox answers false.
            AtomicReference<Boolean> fellThrough = new AtomicReference<>();
            server.ticker().submit(() -> fellThrough.set(
                    server.jukeboxes().useFromTick(jukeboxAt)));
            await(server, () -> fellThrough.get() != null, "the second use answered");
            assertFalse(fellThrough.get(), "a record-less jukebox never consumes the click");
            assertEquals(1, stops.get(), "no second stop event");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The break arm (onRemoved lines 74-78 -> removeRecord)
    // ------------------------------------------------------------------

    @Test
    void theBreakArmEjectsTheRecordFirst() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            place(server, x, y, z, BuiltinBlocks.STONE, x, y + 1, z, BuiltinBlocks.JUKEBOX);
            BlockPosition jukeboxAt = new BlockPosition(x, y + 1, z);
            await(server, () -> server.world().getBlock(jukeboxAt)
                    .identifier().value().equals("jukebox"), "the jukebox placed");

            ConcurrentLinkedQueue<Identifier> plays = new ConcurrentLinkedQueue<>();
            AtomicInteger stops = new AtomicInteger();
            server.jukeboxes().addRecordListener((position, disc) -> {
                if (disc == null) {
                    stops.incrementAndGet();
                } else {
                    plays.add(disc);
                }
            });
            server.ticker().submit(() -> server.jukeboxes().insertFromTick(jukeboxAt,
                    ItemStack.of(BuiltinItems.RECORD_STAL, 1)));
            await(server, () -> !plays.isEmpty(), "the insert played");

            // The break: the block leaves the world, then the hook ejects.
            server.ticker().submit(() -> {
                server.world().setBlock(jukeboxAt, server.world().airType());
                server.jukeboxes().onBlockBroken(jukeboxAt);
            });
            await(server, () -> server.world().getBlock(jukeboxAt)
                    .identifier().value().equals("air"), "the block left");
            assertEquals(1, plays.size(), "one insert's play");
            assertEquals(1, stops.get(), "the break fired the stop event");
            assertNull(server.jukeboxes().recordAt(jukeboxAt), "the slot died with the block");
            await(server, () -> server.itemEntities().all().stream().anyMatch(entity -> entity.stack() != null
                    && entity.stack().type().equals(BuiltinItems.RECORD_STAL)),
                    "the record dropped into the world");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The comparator read (getAnalogSignal lines 97-108)
    // ------------------------------------------------------------------

    @Test
    void theComparatorReadsTheDiscIndex() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            place(server, x, y, z, BuiltinBlocks.STONE, x, y + 1, z, BuiltinBlocks.JUKEBOX);
            BlockPosition jukeboxAt = new BlockPosition(x, y + 1, z);
            await(server, () -> server.world().getBlock(jukeboxAt)
                    .identifier().value().equals("jukebox"), "the jukebox placed");

            // No record: 0.
            assertEquals(0, server.jukeboxes().analogSignal(jukeboxAt));

            // 13 is 1, cat is 2 (Item.getId(record) + 1 - RECORD_13's id).
            server.ticker().submit(() -> server.jukeboxes().insertFromTick(jukeboxAt,
                    ItemStack.of(BuiltinItems.RECORD_13, 1)));
            await(server, () -> server.jukeboxes().recordAt(jukeboxAt) != null, "the 13 disc in");
            assertEquals(1, server.jukeboxes().analogSignal(jukeboxAt));
            server.ticker().submit(() -> server.jukeboxes().useFromTick(jukeboxAt));
            await(server, () -> server.jukeboxes().recordAt(jukeboxAt) == null, "the 13 disc out");

            server.ticker().submit(() -> server.jukeboxes().insertFromTick(jukeboxAt,
                    ItemStack.of(BuiltinItems.RECORD_CAT, 1)));
            await(server, () -> server.jukeboxes().recordAt(jukeboxAt) != null, "the cat disc in");
            assertEquals(2, server.jukeboxes().analogSignal(jukeboxAt));
            server.ticker().submit(() -> server.jukeboxes().useFromTick(jukeboxAt));
            await(server, () -> server.jukeboxes().recordAt(jukeboxAt) == null, "the cat disc out");

            // wait is 12 (the table's tail).
            server.ticker().submit(() -> server.jukeboxes().insertFromTick(jukeboxAt,
                    ItemStack.of(BuiltinItems.RECORD_WAIT, 1)));
            await(server, () -> server.jukeboxes().recordAt(jukeboxAt) != null, "the wait disc in");
            assertEquals(12, server.jukeboxes().analogSignal(jukeboxAt));
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The ZJD persistence round trip
    // ------------------------------------------------------------------

    @Test
    void theZjdRoundTripAndTheRestoreFlip() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            place(server, x, y, z, BuiltinBlocks.STONE, x, y + 1, z, BuiltinBlocks.JUKEBOX);
            BlockPosition jukeboxAt = new BlockPosition(x, y + 1, z);
            await(server, () -> server.world().getBlock(jukeboxAt)
                    .identifier().value().equals("jukebox"), "the jukebox placed");

            server.ticker().submit(() -> server.jukeboxes().insertFromTick(jukeboxAt,
                    ItemStack.of(BuiltinItems.RECORD_FAR, 1)));
            await(server, () -> server.jukeboxes().recordAt(jukeboxAt) != null, "the disc in");

            // The round trip through a real file (Wave FL4-GAP2 Slice C:
            // the union save, each row stamped with its world).
            Path zjd = dataDir.resolve("jukeboxes.bin");
            JukeboxDataStore store = new JukeboxDataStore(zjd);
            store.save(Map.of(0, server.jukeboxes().snapshotRecords()));
            assertTrue(Files.exists(zjd), "the store wrote the file");
            JukeboxDataStore reloaded = new JukeboxDataStore(zjd);
            java.util.List<JukeboxDataStore.Loaded> loaded = reloaded.load();
            assertEquals(1, loaded.size());
            JukeboxDataStore.Loaded row = loaded.get(0);
            assertEquals(0, row.dimension(), "the row stamps the overworld");
            ItemStack record = row.record();
            assertEquals(BuiltinItems.RECORD_FAR, record.type(), "the disc survived");
            assertEquals(1, record.count());

            // The restore flip: a plain jukebox at the position + the stored
            // record -> the HAS_RECORD variant (the restored world's own
            // walk — the reference's readNbt RecordItem + the metadata bit).
            server.ticker().submit(() -> {
                server.jukeboxes().onBlockBroken(jukeboxAt); // clear the live slot
                server.world().setBlock(jukeboxAt, BuiltinBlocks.JUKEBOX); // the plain block
            });
            await(server, () -> server.world().getBlock(jukeboxAt)
                    .identifier().value().equals("jukebox")
                    && server.jukeboxes().recordAt(jukeboxAt) == null, "the plain state");
            AtomicReference<Boolean> flipped = new AtomicReference<>();
            server.ticker().submit(() -> {
                // Wave FL4-GAP2 Slice C: the restore takes the position-keyed
                // map (the row's own family restores it).
                java.util.Map<BlockPosition, ItemStack> restored = new java.util.LinkedHashMap<>();
                for (JukeboxDataStore.Loaded recordRow : loaded) {
                    restored.put(recordRow.position(), recordRow.record());
                }
                server.jukeboxes().restoreAll(restored);
                flipped.set(true);
            });
            await(server, () -> Boolean.TRUE.equals(flipped.get()), "the restore ran");
            await(server, () -> server.world().getBlock(jukeboxAt)
                    .identifier().value().equals("jukebox_record"),
                    "the restore flipped the variant");
            assertEquals(BuiltinItems.RECORD_FAR, server.jukeboxes().recordAt(jukeboxAt).type());

            // A corrupt ZJD loads empty (preserved, not destroyed).
            Path corrupt = dataDir.resolve("corrupt.bin");
            Files.writeString(corrupt, "not a zjd file");
            JukeboxDataStore corruptStore = new JukeboxDataStore(corrupt);
            assertTrue(corruptStore.load().isEmpty());
            assertTrue(Files.exists(corrupt), "the corrupt file is preserved");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /** Places the floor then the jukebox above it, on the tick thread. */
    private static void place(EngineServer server, int fx, int fy, int fz,
                              net.zaminmc.torch.block.BlockType floor,
                              int jx, int jy, int jz,
                              net.zaminmc.torch.block.BlockType jukebox) {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(fx, fy, fz), floor);
            server.world().setBlock(new BlockPosition(jx, jy, jz), jukebox);
        });
    }

    private static void await(EngineServer server, BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "jkbx" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
