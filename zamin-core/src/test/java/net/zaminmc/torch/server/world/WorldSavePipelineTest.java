package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE PHASE-5 PERSISTENCE-PREPARATION ADOPTION'S OWN EVIDENCE (slice
 * S1): the world-delta save's compute-then-publish split. THE
 * EQUIVALENCE (the lane's own proof obligation — the P6-lane shape):
 * the pure {@link WorldDeltaSerializer}'s bytes and the synchronous
 * {@code DeltaWorldStorage.save} file are BYTE-IDENTICAL, and the
 * round-trip load restores the same deltas. THE PIPELINE: a submit
 * through the real compute subsystem + I/O lane lands the file with
 * the telemetry recorded (the off-tick engagement + the serialization
 * measurement). THE GATE (§15): a save whose sequence is not the
 * newest seen NEVER lands — the older bytes are discarded, the newer
 * snapshot owns the file.
 */
class WorldSavePipelineTest {

    @TempDir
    Path dir;

    private static BlockRegistry registry() {
        // The full boot chain (EngineServer's freeze order — BiomeWorldTest's
        // own registry()).
        return net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                        net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(new net.zaminmc.torch.server.block.BlockRegistryBuilder()))))))))
                .freeze();
    }

    private static WorldDeltaSnapshot snapshot(BlockRegistry registry, long ticks, long time,
                                               String... identifiers) {
        Map<Integer, net.zaminmc.torch.block.BlockType> entries = new HashMap<>();
        for (int i = 0; i < identifiers.length; i++) {
            entries.put(i, registry.require(net.zaminmc.torch.util.Identifier.parse(identifiers[i])));
        }
        Map<Long, Map<Integer, net.zaminmc.torch.block.BlockType>> deltas = new HashMap<>();
        deltas.put(0L, entries);
        return new WorldDeltaSnapshot(ticks, time, deltas);
    }

    private static java.util.function.Function<net.zaminmc.torch.util.Identifier, net.zaminmc.torch.block.BlockType> resolver(BlockRegistry registry) {
        return registry::require;
    }

    /**
     * THE EQUIVALENCE: the pure serializer's bytes == the synchronous
     * save's file, byte for byte; the load() round-trip restores the
     * same deltas.
     */
    @Test
    void thePureSerializerProducesTheSameBytesAsTheSynchronousSave() throws Exception {
        BlockRegistry registry = registry();
        WorldDeltaSnapshot snap = snapshot(registry, 1234L, 6000L,
                "minecraft:stone", "minecraft:oak_planks", "minecraft:stone");

        Path syncFile = dir.resolve("sync.bin");
        DeltaWorldStorage sync = new DeltaWorldStorage(syncFile, resolver(registry));
        sync.save(snap);
        byte[] syncBytes = Files.readAllBytes(syncFile);

        byte[] pure = WorldDeltaSerializer.serialize(snap);
        assertArrayEquals(syncBytes, pure,
                "the off-tick serialization and the on-tick save are byte-identical");

        Path pipelineFile = dir.resolve("pipeline.bin");
        DeltaWorldStorage pipelined = new DeltaWorldStorage(pipelineFile, resolver(registry));
        pipelined.writeSerialized(pure);
        assertArrayEquals(syncBytes, Files.readAllBytes(pipelineFile),
                "the published file carries the same bytes");

        // the round-trip: the load restores the same content
        WorldDeltaSnapshot loaded = pipelined.load().orElseThrow();
        assertEquals(snap.totalTicks(), loaded.totalTicks());
        assertEquals(snap.timeOfDay(), loaded.timeOfDay());
        assertEquals(snap.deltas(), loaded.deltas());
    }

    /**
     * THE PIPELINE: a real submit through the compute subsystem + the
     * I/O lane lands the file; the telemetry records the engagement and
     * the off-tick serialization measurement.
     */
    @Test
    void thePipelineLandsTheSaveThroughTheLanesWithTelemetry() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("save:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            BlockRegistry registry = registry();
            Path file = dir.resolve("live.bin");
            DeltaWorldStorage storage = new DeltaWorldStorage(file, resolver(registry));
            WorldDeltaSnapshot snap = snapshot(registry, 42L, 1000L, "minecraft:dirt");

            CountDownLatch done = pipeline.submitSave(storage, snap);
            // the owner drains (the ticker's own rhythm): compute first,
            // then the I/O completions — the mailbox contract's walk
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
            assertTrue(done.await(0, TimeUnit.MILLISECONDS), "the save landed");

            assertTrue(pipeline.submitted() >= 1, "the submit recorded");
            assertTrue(pipeline.serialized() >= 1, "the serialization recorded");
            assertEquals(1, pipeline.landed(), "the publication landed");
            assertEquals(0, pipeline.staleDropped(), "nothing dropped");
            assertTrue(pipeline.serializeNanos() > 0,
                    "the off-tick serialization measurement recorded");

            WorldDeltaSnapshot loaded = storage.load().orElseThrow();
            assertEquals(snap, loaded, "the landed file round-trips");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }

    /**
     * THE GATE (§15's "persistence must not publish an older snapshot
     * over newer state"): a save whose sequence is not the newest seen
     * NEVER lands — the older bytes are discarded and the newer
     * snapshot owns the file.
     */
    @Test
    void theGateNeverPublishesAnOlderSnapshotOverANewerOne() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("gate:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            BlockRegistry registry = registry();
            Path file = dir.resolve("gate.bin");
            DeltaWorldStorage storage = new DeltaWorldStorage(file, resolver(registry));
            byte[] newer = WorldDeltaSerializer.serialize(
                    snapshot(registry, 200L, 0L, "minecraft:stone"));
            byte[] older = WorldDeltaSerializer.serialize(
                    snapshot(registry, 100L, 0L, "minecraft:dirt"));

            assertTrue(pipeline.publish(storage, 5, newer), "the first publication lands");
            assertFalse(pipeline.publish(storage, 4, older),
                    "the older sequence drops at the gate");
            assertFalse(pipeline.publish(storage, 5, newer),
                    "the same sequence drops (already landed)");

            WorldDeltaSnapshot loaded = storage.load().orElseThrow();
            assertEquals(200L, loaded.totalTicks(),
                    "the file holds the NEWER snapshot's content");
            assertTrue(pipeline.publish(storage, 6, older),
                    "a NEWER sequence lands even when its content differs");
            // (seq 6 carrying the 'older' content is still the newest
            // SEQUENCE — the gate orders by sequence, not by content)
            assertEquals(100L, storage.load().orElseThrow().totalTicks(),
                    "the newest sequence's own content owns the file");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }

    /**
     * THE SUPERSESSION: a save whose publication loses the gate still
     * releases its latch — the durability demand is satisfied by the
     * newer content on disk (the caller's walk never hangs).
     */
    @Test
    void theSupersededSaveStillReleasesItsDurabilityDemand() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("supersede:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            BlockRegistry registry = registry();
            Path file = dir.resolve("supersede.bin");
            DeltaWorldStorage storage = new DeltaWorldStorage(file, resolver(registry));

            // force the gate: land seq 2 first, then submit seq 1 through
            // the real pipeline — the publication loses, the latch releases
            byte[] newer = WorldDeltaSerializer.serialize(
                    snapshot(registry, 2L, 0L, "minecraft:stone"));
            assertTrue(pipeline.publish(storage, 2, newer), "the newer save pre-landed");

            CountDownLatch done = pipeline.submitSave(storage,
                    snapshot(registry, 1L, 0L, "minecraft:dirt"));
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
            assertTrue(done.await(0, TimeUnit.MILLISECONDS),
                    "the superseded save's latch released");
            assertEquals(1, pipeline.staleDropped(), "the supersession recorded");
            assertEquals(2L, storage.load().orElseThrow().totalTicks(),
                    "the newer content still owns the file");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }
}
