package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.furnace.FurnaceBlockEntity;
import net.zaminmc.torch.server.furnace.FurnaceDataStore;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE CONTAINER TRIO'S PIPELINE ADOPTION'S OWN EVIDENCE (slice S2, part
 * two): the named submission path — the deep-captured snapshot IN (the
 * capture ran on the caller's owning thread, the test's own stand-in for
 * the tick lane), the pure serializer on the compute pool, the store's
 * file half on the blocking-I/O lane, the named sequence gate at the
 * file. THE EQUIVALENCE: the pipeline-landed file carries exactly the
 * pure serializer's bytes. THE GATE: the named storages gate
 * independently — an older sequence never lands, and one storage's
 * sequence never blocks another's. THE FAILURE ARM: a publication that
 * fails loudly still releases the durability latch — the caller's walk
 * never hangs on the failure.
 */
class ContainerSavePipelineTest {

    @TempDir
    Path dir;

    private static FurnaceDataStore.Snapshot oneFurnaceSnapshot(FurnaceDataStore store) {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 5));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.COAL, 2));
        furnace.restore(1370, 1600, 88);
        return store.capture(Map.of(0, Map.of(new BlockPosition(7, 4, -3), furnace)));
    }

    /** The owner's drain loop (the ticker's own rhythm), bounded. */
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
    void theNamedPathLandsTheTrioThroughTheLanesWithTelemetry() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s2:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            FurnaceDataStore store = new FurnaceDataStore(dir.resolve("furnaces.bin"));
            FurnaceDataStore.Snapshot snapshot = oneFurnaceSnapshot(store);
            byte[] pure = store.serialize(snapshot);

            CountDownLatch done = pipeline.submitSave("furnace", snapshot,
                    store::serialize, store::writeSerialized);
            drainUntil(domain, compute, io, done);
            assertTrue(done.await(0, TimeUnit.MILLISECONDS), "the save landed");

            assertArrayEquals(pure, Files.readAllBytes(dir.resolve("furnaces.bin")),
                    "the landed file carries exactly the pure serializer's bytes");
            assertTrue(pipeline.submitted() >= 1, "the submit recorded");
            assertTrue(pipeline.serialized() >= 1, "the serialization recorded");
            assertEquals(1, pipeline.landed(), "the publication landed");
            assertEquals(0, pipeline.staleDropped(), "nothing dropped");
            assertTrue(pipeline.serializeNanos() > 0,
                    "the off-tick serialization measurement recorded");

            // the round-trip: the landed file restores the same furnace
            FurnaceBlockEntity restored = store.load().get(0).furnace();
            assertEquals(5, restored.input().count());
            assertEquals(1370, restored.burnTimeRemaining());
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }

    @Test
    void theNamedGateNeverPublishesAnOlderSnapshotOverANewerOne() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s2gate:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            FurnaceDataStore store = new FurnaceDataStore(dir.resolve("gate.bin"));
            FurnaceDataStore.Snapshot newer = oneFurnaceSnapshot(store);
            byte[] newerBytes = store.serialize(newer);
            byte[] olderBytes = new byte[]{'Z', 'F', 'D', 2, 0}; // the empty save

            assertTrue(pipeline.publishNamed("furnace", 5, newerBytes, store::writeSerialized),
                    "the first publication lands");
            assertFalse(pipeline.publishNamed("furnace", 4, olderBytes, store::writeSerialized),
                    "the older sequence drops at the named gate");
            assertFalse(pipeline.publishNamed("furnace", 5, newerBytes, store::writeSerialized),
                    "the same sequence drops (already landed)");
            assertArrayEquals(newerBytes, Files.readAllBytes(dir.resolve("gate.bin")),
                    "the file holds the NEWER snapshot's content");

            // the named storages gate INDEPENDENTLY — the chest's own
            // sequence space never sees the furnace's landed sequence
            ChestSink sink = new ChestSink();
            assertTrue(pipeline.publishNamed("chest", 1, new byte[]{1}, sink),
                    "another named storage's sequence space is its own");
            assertEquals(1, sink.written.get(), "the independent gate let it land");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }

    @Test
    void theSupersededNamedSaveStillReleasesItsDurabilityDemand() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s2supersede:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            FurnaceDataStore store = new FurnaceDataStore(dir.resolve("supersede.bin"));

            // force the gate: land seq 2 first, then submit seq 1 through
            // the real pipeline — the publication loses, the latch releases
            assertTrue(pipeline.publishNamed("furnace", 2,
                    store.serialize(oneFurnaceSnapshot(store)), store::writeSerialized),
                    "the newer save pre-landed");

            CountDownLatch done = pipeline.submitSave("furnace",
                    store.capture(Map.of()), store::serialize, store::writeSerialized);
            drainUntil(domain, compute, io, done);
            assertTrue(done.await(0, TimeUnit.MILLISECONDS),
                    "the superseded save's latch released");
            assertEquals(1, pipeline.staleDropped(), "the supersession recorded");
            assertEquals(1, store.load().size(),
                    "the newer content still owns the file");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }

    @Test
    void theFailedPublicationStillReleasesItsDurabilityDemand() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s2fail:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            FurnaceDataStore store = new FurnaceDataStore(dir.resolve("never.bin"));
            long failedBefore = io.failedCount();

            CountDownLatch done = pipeline.submitSave("furnace", oneFurnaceSnapshot(store),
                    store::serialize,
                    bytes -> {
                        throw new IllegalStateException("the disk is gone (the loud failure)");
                    });
            drainUntil(domain, compute, io, done);
            assertTrue(done.await(0, TimeUnit.MILLISECONDS),
                    "the failed save's latch released — the caller's walk never hangs");
            assertEquals(0, pipeline.landed(), "the failed save never landed");
            assertFalse(Files.exists(dir.resolve("never.bin")),
                    "the failed save wrote nothing");
            // the failure stays loud at the lane: markFailed runs on the
            // worker after the latch released — poll briefly
            long deadline = System.currentTimeMillis() + 2_000;
            while (io.failedCount() <= failedBefore
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(io.failedCount() > failedBefore,
                    "the failure stayed loud at the lane");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }

    /** The publication sink for the independent-gate case. */
    private static final class ChestSink implements java.util.function.Consumer<byte[]> {
        final AtomicInteger written = new AtomicInteger();

        @Override
        public void accept(byte[] bytes) {
            written.incrementAndGet();
        }
    }
}
