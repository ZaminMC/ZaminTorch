package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.beacon.BeaconBlockEntity;
import net.zaminmc.torch.server.brewing.BrewingStandBlockEntity;
import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.dispenser.DispenserBlockEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE EFFECT-TILE TRIO'S PIPELINE ADOPTION'S OWN EVIDENCE (slice S4,
 * part five): the dispenser's slots, the brewing stand's timer + slots,
 * the beacon's levels/effects/payment — the deep captures, the pure
 * serializers, the file halves, and the named pipeline path through the
 * real lanes. THE EQUIVALENCE: the sync save's file carries exactly the
 * pure serializer's bytes (one serializer, one file half — the
 * construction's own proof); the loads round-trip the state.
 */
class EffectTileSaveSplitTest {

    @TempDir
    Path dir;

    private static void drainUntil(OwnershipDomain domain, ComputeSubsystem compute,
                                   BlockingIoLane io, CountDownLatch done) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!done.await(10, TimeUnit.MILLISECONDS)
                && System.currentTimeMillis() < deadline) {
            domain.enter();
            try {
                compute.drainResults(domain);
                io.drainCompletions(domain);
            } finally {
                domain.exit();
            }
        }
    }

    @Test
    void dispenserSplitRoundTripsThroughTheSyncSaveAndThePipeline() throws Exception {
        Map<Integer, Map<BlockPosition, DispenserBlockEntity>> live = new LinkedHashMap<>();
        Map<BlockPosition, DispenserBlockEntity> overworld = new LinkedHashMap<>();
        DispenserBlockEntity seeded = new DispenserBlockEntity();
        seeded.setItem(0, ItemStack.of(BuiltinItems.IRON_INGOT, 4));
        seeded.setItem(8, new ItemStack(BuiltinItems.COAL, 1, 0).withName("Cannonball"));
        overworld.put(new BlockPosition(3, 21, -6), seeded);
        overworld.put(new BlockPosition(7, 7, 7), new DispenserBlockEntity());
        // Wave FL4-GAP2 Slice B: a nether row rides the same file, stamped
        // with its own world.
        DispenserBlockEntity netherSeeded = new DispenserBlockEntity();
        netherSeeded.setItem(0, ItemStack.of(BuiltinItems.DIAMOND, 2));
        live.put(0, overworld);
        live.put(-1, Map.of(new BlockPosition(3, 21, -6), netherSeeded));

        var store = new net.zaminmc.torch.server.dispenser.DispenserDataStore(
                dir.resolve("dispensers.bin"));
        byte[] pure = store.serialize(store.capture(live));
        store.save(live);
        assertArrayEquals(pure, Files.readAllBytes(dir.resolve("dispensers.bin")),
                "the sync save's file carries the serializer's bytes");
        var restored = store.load();
        assertEquals(3, restored.size(), "the overworld pair + the nether row");
        var overworldRow = restored.stream()
                .filter(row -> row.dimension() == 0 && row.position().equals(
                        new BlockPosition(3, 21, -6))).findFirst().orElseThrow();
        assertEquals(4, overworldRow.container().snapshotSlots()[0].count());
        assertEquals("Cannonball",
                overworldRow.container().snapshotSlots()[8].displayName());
        var emptyRow = restored.stream()
                .filter(row -> row.dimension() == 0 && row.position().equals(
                        new BlockPosition(7, 7, 7))).findFirst().orElseThrow();
        assertTrue(emptyRow.container().snapshotSlots()[0].isEmpty());
        var netherRow = restored.stream()
                .filter(row -> row.dimension() == -1).findFirst().orElseThrow();
        assertEquals(2, netherRow.container().snapshotSlots()[0].count(),
                "the same-coordinate nether tile is its own row");

        // the pipeline ride
        OwnershipDomain domain = OwnershipDomain.create("s4disp:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            var snapshot = store.capture(live);
            CountDownLatch done = pipeline.submitSave("dispenser", snapshot,
                    store::serialize, store::writeSerialized);
            drainUntil(domain, compute, io, done);
            assertTrue(done.await(0, TimeUnit.MILLISECONDS), "the save landed");
            assertArrayEquals(pure, Files.readAllBytes(dir.resolve("dispensers.bin")),
                    "the landed file carries exactly the serializer's bytes");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }

    @Test
    void brewingSplitRoundTripsThroughTheSyncSave() throws Exception {
        Map<Integer, Map<BlockPosition, BrewingStandBlockEntity>> live = new LinkedHashMap<>();
        Map<BlockPosition, BrewingStandBlockEntity> overworld = new LinkedHashMap<>();
        BrewingStandBlockEntity seeded = new BrewingStandBlockEntity();
        seeded.setItem(0, ItemStack.of(BuiltinItems.IRON_ORE, 1));
        seeded.setItem(3, ItemStack.of(BuiltinItems.COAL, 2));
        overworld.put(new BlockPosition(-2, 44, 9), seeded);
        live.put(0, overworld);

        var store = new net.zaminmc.torch.server.brewing.BrewingStandDataStore(
                dir.resolve("brewing.bin"));
        byte[] pure = store.serialize(store.capture(live));
        store.save(live);
        assertArrayEquals(pure, Files.readAllBytes(dir.resolve("brewing.bin")),
                "the sync save's file carries the serializer's bytes");
        var restored = store.load();
        var row = restored.get(0);
        assertEquals(new BlockPosition(-2, 44, 9), row.position());
        assertEquals(0, row.dimension());
        assertEquals(1, row.stand().snapshotSlots()[0].count());
        assertEquals(2, row.stand().snapshotSlots()[3].count());
    }

    @Test
    void beaconSplitRoundTripsThroughTheSyncSave() throws Exception {
        Map<BlockPosition, BeaconBlockEntity> live = new LinkedHashMap<>();
        BeaconBlockEntity seeded = new BeaconBlockEntity();
        seeded.setLevels(3);
        seeded.setPrimary(1);
        seeded.setSecondary(5);
        seeded.setPayment(ItemStack.of(BuiltinItems.IRON_INGOT, 1));
        live.put(new BlockPosition(0, 90, 0), seeded);

        BeaconBlockEntity empty = new BeaconBlockEntity();
        live.put(new BlockPosition(1, 91, 1), empty);

        var store = new net.zaminmc.torch.server.beacon.BeaconDataStore(
                dir.resolve("beacons.bin"));
        byte[] pure = store.serialize(store.capture(live));
        store.save(live);
        assertArrayEquals(pure, Files.readAllBytes(dir.resolve("beacons.bin")),
                "the sync save's file carries the serializer's bytes");
        var restored = store.load();
        BeaconBlockEntity first = restored.get(new BlockPosition(0, 90, 0));
        assertEquals(3, first.levels());
        assertEquals(1, first.primary());
        assertEquals(5, first.secondary());
        assertEquals(1, first.payment().count());
    }
}
