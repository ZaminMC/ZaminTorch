package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.dispenser.DispenserSystem;
import net.zaminmc.torch.server.hopper.HopperSystem;
import net.zaminmc.torch.server.piston.PistonSystem;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.server.redstone.TripwireBlocks;
import net.zaminmc.torch.server.brewing.BrewingStandBlocks;
import net.zaminmc.torch.util.Identifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE RC — THE REGION-COMPRESSION PAIR'S SEATS (the KigPaper 0214/0215
 * port, the census's heaviest unclaimed engine-mappable row): the
 * compression envelope over the ZWD store, every law pinned against the
 * patch's own shape.
 *
 * <p>THE LAWS: (1) the default keeps the historical raw bytes whole — no
 * envelope byte, byte-identical files, every existing save loads; (2) every
 * armed algorithm writes {@code [ordinal][compressed]} and the load
 * round-trips the snapshot exactly; (3) the reader dispatches on the FILE's
 * own byte, never the operator's flag — a ZSTD file loads under a NONE
 * flag, a raw historical file loads under an armed flag; (4) an unknown
 * type byte quarantines (the corruption law never weakens); (5) the async
 * pipeline path wraps through the same choke point (the two save paths'
 * equivalence over the envelope).</p>
 */
class RegionCompressionParityTest {

    @TempDir
    Path dir;

    private static BlockRegistry registry() {
        // The full boot chain (EngineServer's freeze order — the pipeline
        // test's own registry()).
        return BrewingStandBlocks.registerAll(
                DispenserSystem.registerBlocks(
                HopperSystem.registerBlocks(
                PistonSystem.registerBlocks(
                TripwireBlocks.registerAll(
                RedstoneBlocks.registerAll(
                        FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(new BlockRegistryBuilder()))))))))
                .freeze();
    }

    private static java.util.function.Function<Identifier, net.zaminmc.torch.block.BlockType> resolver(
            BlockRegistry registry) {
        return registry::require;
    }

    /**
     * A real worked-world shape: many chunks, many distinct palette rows,
     * inhabited tail rows — a payload with actual compressible structure
     * (the delta table's repeated palette indices).
     */
    private static WorldDeltaSnapshot workedSnapshot(BlockRegistry registry) {
        Map<Long, Map<Integer, net.zaminmc.torch.block.BlockType>> deltas = new HashMap<>();
        Map<Long, Long> inhabited = new HashMap<>();
        String[] family = {
                "minecraft:stone", "minecraft:oak_planks", "minecraft:dirt",
                "minecraft:cobblestone", "minecraft:sand", "minecraft:gravel",
                "minecraft:iron_ore", "minecraft:coal_ore", "minecraft:gold_ore",
                "minecraft:diamond_ore", "minecraft:redstone_ore", "minecraft:lapis_ore",
        };
        for (int chunk = 0; chunk < 48; chunk++) {
            Map<Integer, net.zaminmc.torch.block.BlockType> entries = new HashMap<>();
            for (int local = 0; local < 96; local++) {
                entries.put(local, registry.require(Identifier.parse(family[(chunk + local) % family.length])));
            }
            deltas.put((long) chunk, entries);
            if (chunk % 4 == 0) {
                inhabited.put((long) chunk, (long) chunk * 1000 + 7);
            }
        }
        return new WorldDeltaSnapshot(987_654_321L, 13_799L, 999_999L, inhabited, deltas);
    }

    private static void assertRoundTrips(DeltaWorldStorage storage, WorldDeltaSnapshot expected) {
        WorldDeltaSnapshot loaded = storage.load().orElseThrow();
        assertEquals(expected.totalTicks(), loaded.totalTicks(), "totalTicks");
        assertEquals(expected.timeOfDay(), loaded.timeOfDay(), "timeOfDay");
        assertEquals(expected.rawTimeOfDay(), loaded.rawTimeOfDay(), "rawTimeOfDay");
        assertEquals(expected.inhabitedTimes(), loaded.inhabitedTimes(), "the inhabited tail");
        assertEquals(expected.deltas(), loaded.deltas(), "the delta table whole");
    }

    /**
     * SEAT 1 — THE DEFAULT LAW: {@code fromFlag()} answers NONE unset; a
     * save through the default storage answers the historical raw bytes
     * BYTE-IDENTICAL (the pure serializer's own output — no envelope byte
     * ever written); an unknown flag name fails loudly (the patch's own
     * valueOf shape — never a silent fallback).
     */
    @Test
    void theDefaultLawKeepsTheHistoricalRawBytes() throws Exception {
        BlockRegistry registry = registry();
        assertEquals(RegionCompression.Algorithm.NONE, RegionCompression.fromFlag(),
                "the flag's default answers NONE (the never-a-default-change law)");

        // The case-insensitive read (0215's toUpperCase(Locale.ROOT) law)
        String held = System.getProperty(RegionCompression.FLAG_PROPERTY);
        try {
            System.setProperty(RegionCompression.FLAG_PROPERTY, "zstd");
            assertEquals(RegionCompression.Algorithm.ZSTD, RegionCompression.fromFlag(),
                    "the flag reads case-insensitively (the 0215 read)");
            System.setProperty(RegionCompression.FLAG_PROPERTY, "LZ4");
            assertEquals(RegionCompression.Algorithm.LZ4, RegionCompression.fromFlag());
        } finally {
            if (held == null) {
                System.clearProperty(RegionCompression.FLAG_PROPERTY);
            } else {
                System.setProperty(RegionCompression.FLAG_PROPERTY, held);
            }
        }

        // The loud unknown (the patch's valueOf shape)
        try {
            System.setProperty(RegionCompression.FLAG_PROPERTY, "lzma");
            assertThrows(IllegalStateException.class, RegionCompression::fromFlag,
                    "an unknown algorithm name fails loudly");
        } finally {
            if (held == null) {
                System.clearProperty(RegionCompression.FLAG_PROPERTY);
            } else {
                System.setProperty(RegionCompression.FLAG_PROPERTY, held);
            }
        }

        // The byte-identity: the default save IS the pure serialization
        WorldDeltaSnapshot snap = workedSnapshot(registry);
        Path file = dir.resolve("default.bin");
        DeltaWorldStorage storage = new DeltaWorldStorage(file, resolver(registry));
        storage.save(snap);
        assertArrayEquals(WorldDeltaSerializer.serialize(snap), Files.readAllBytes(file),
                "the default answers the historical raw bytes — no envelope byte");
        assertRoundTrips(storage, snap);
    }

    /**
     * SEAT 2 — THE ENVELOPE LAW: every armed algorithm writes
     * {@code [ordinal][compressed payload]}, the payload is NOT the raw
     * bytes (compression really happened), and the load round-trips the
     * worked snapshot exactly — the four codecs the pair ships (GZIP the
     * region format's own legacy byte, ZLIB the patch default's, LZ4 the
     * pure-Java block codec, ZSTD the 0215 addition).
     */
    @Test
    void theEnvelopeRoundTripsEveryAlgorithm() throws Exception {
        BlockRegistry registry = registry();
        WorldDeltaSnapshot snap = workedSnapshot(registry);
        byte[] raw = WorldDeltaSerializer.serialize(snap);
        for (RegionCompression.Algorithm algorithm : RegionCompression.Algorithm.values()) {
            if (algorithm == RegionCompression.Algorithm.NONE) {
                continue; // seat 1's own law
            }
            Path file = dir.resolve("envelope-" + algorithm.name().toLowerCase() + ".bin");
            DeltaWorldStorage storage = new DeltaWorldStorage(file, resolver(registry), algorithm);
            storage.save(snap);

            byte[] written = Files.readAllBytes(file);
            assertEquals(algorithm.ordinal(), written[0] & 0xFF,
                    algorithm + " writes its own ordinal as the type byte");
            assertNotEquals(raw.length + 1, written.length,
                    algorithm + " really compressed the payload (the size moved)");
            assertRoundTrips(storage, snap);
        }
    }

    /**
     * SEAT 3 — THE READER'S DISPATCH LAW: the load dispatches on the
     * FILE's own first byte, never the operator's flag — the region
     * reader's own law (every file reads back whatever any configuration
     * wrote). A ZSTD-written file loads through a NONE storage; a raw
     * historical file loads through an armed storage; an explicit NONE
     * envelope (never written by this engine) loads too.
     */
    @Test
    void theReaderDispatchesOnTheFileByteNeverTheFlag() throws Exception {
        BlockRegistry registry = registry();
        WorldDeltaSnapshot snap = workedSnapshot(registry);

        // A ZSTD file, read through a NONE-flagged storage (the fresh boot
        // after the operator retires the flag): loads whole.
        Path zstdFile = dir.resolve("cross-zstd.bin");
        new DeltaWorldStorage(zstdFile, resolver(registry), RegionCompression.Algorithm.ZSTD).save(snap);
        assertRoundTrips(new DeltaWorldStorage(zstdFile, resolver(registry)), snap);

        // A raw historical file, read through an armed storage (the boot
        // after the operator first arms the flag): loads whole.
        Path rawFile = dir.resolve("cross-raw.bin");
        new DeltaWorldStorage(rawFile, resolver(registry)).save(snap);
        assertRoundTrips(
                new DeltaWorldStorage(rawFile, resolver(registry), RegionCompression.Algorithm.LZ4),
                snap);

        // An explicit NONE envelope (byte 0 + payload — never written by
        // this engine, accepted for completeness like the region reader's
        // every-known-ordinal law): loads whole.
        Path explicitNone = dir.resolve("explicit-none.bin");
        byte[] rawBytes = WorldDeltaSerializer.serialize(snap);
        byte[] enveloped = new byte[rawBytes.length + 1];
        enveloped[0] = (byte) RegionCompression.Algorithm.NONE.ordinal();
        System.arraycopy(rawBytes, 0, enveloped, 1, rawBytes.length);
        Files.write(explicitNone, enveloped);
        assertRoundTrips(
                new DeltaWorldStorage(explicitNone, resolver(registry), RegionCompression.Algorithm.GZIP),
                snap);
    }

    /**
     * SEAT 4 — THE CORRUPTION LAW: an unknown type byte is a corrupt
     * file — the storage's quarantine walk preserves it beside the store
     * (the .corrupt sibling) and answers empty, never silently replacing
     * the evidence (§31.6's law; the envelope never weakens it).
     */
    @Test
    void theUnknownTypeByteQuarantines() throws Exception {
        BlockRegistry registry = registry();
        Path file = dir.resolve("unknown-type.bin");
        byte[] rawBytes = WorldDeltaSerializer.serialize(workedSnapshot(registry));
        byte[] garbage = new byte[rawBytes.length + 1];
        garbage[0] = 7; // beyond every known ordinal
        System.arraycopy(rawBytes, 0, garbage, 1, rawBytes.length);
        Files.write(file, garbage);

        DeltaWorldStorage storage = new DeltaWorldStorage(file, resolver(registry));
        assertTrue(storage.load().isEmpty(), "the unknown type byte answers empty");
        assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")),
                "the unreadable evidence is preserved beside the store");
    }

    /**
     * SEAT 5 — THE PIPELINE EQUIVALENCE OVER THE ENVELOPE: the async save
     * path (the compute pool's serialization + the I/O lane's publication)
     * wraps through the same choke point — the landed file carries the
     * envelope's ordinal, the load round-trips, and the bytes match the
     * synchronous save's own file exactly (the two paths' equivalence,
     * Wave S1's own proof shape, now over the armed envelope).
     */
    @Test
    void thePipelineEquivalenceOverTheEnvelope() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("rc:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            BlockRegistry registry = registry();
            WorldDeltaSnapshot snap = workedSnapshot(registry);

            Path asyncFile = dir.resolve("async-lz4.bin");
            DeltaWorldStorage asyncStorage =
                    new DeltaWorldStorage(asyncFile, resolver(registry), RegionCompression.Algorithm.LZ4);
            CountDownLatch done = pipeline.submitSave(asyncStorage, snap);
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
            assertTrue(done.await(0, TimeUnit.MILLISECONDS), "the async save landed");

            byte[] asyncBytes = Files.readAllBytes(asyncFile);
            assertEquals(RegionCompression.Algorithm.LZ4.ordinal(), asyncBytes[0] & 0xFF,
                    "the pipeline's publication wrapped through the storage's own choke point");
            assertRoundTrips(asyncStorage, snap);

            // The synchronous save's own file: the same bytes (the two
            // paths' equivalence over the envelope)
            Path syncFile = dir.resolve("sync-lz4.bin");
            new DeltaWorldStorage(syncFile, resolver(registry), RegionCompression.Algorithm.LZ4).save(snap);
            assertArrayEquals(Files.readAllBytes(syncFile), asyncBytes,
                    "the async and synchronous paths land identical bytes (LZ4 is deterministic)");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }

    /**
     * SEAT 6 — THE GZIP/ZLIB PAYLOAD DECODE LAW: the envelope's codec
     * streams are the patch's own — the ZLIB arm is the JDK
     * DeflaterOutputStream (the region format's own ZLIB byte), and the
     * wrapped bytes decode through the patch's reader shape back to the
     * raw payload (the wrap/unwrap round-trip at the byte level, not
     * only through the storage).
     */
    @Test
    void theWrappedBytesDecodeThroughTheReaderShape() throws Exception {
        BlockRegistry registry = registry();
        byte[] raw = WorldDeltaSerializer.serialize(workedSnapshot(registry));
        for (RegionCompression.Algorithm algorithm : RegionCompression.Algorithm.values()) {
            byte[] wrapped = RegionCompression.wrap(algorithm, raw);
            if (algorithm == RegionCompression.Algorithm.NONE) {
                assertArrayEquals(raw, wrapped, "NONE answers the raw bytes whole");
            } else {
                assertEquals(algorithm.ordinal(), wrapped[0] & 0xFF,
                        algorithm + " carries its ordinal head");
                assertNotEquals(raw.length, wrapped.length,
                        algorithm + " moved the size");
            }
            byte[] decoded = RegionCompression.unwrap(wrapped).readAllBytes();
            assertArrayEquals(raw, decoded,
                    algorithm + " round-trips at the byte level through the reader shape");
        }
    }
}
