package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The serialized-column memoization's cross-lane smoke: the serialize walk
 * runs on the netty event loop (join/movement) AND the tick lane (respawn,
 * relight), while mutations land on the owner thread — the volatile
 * revision/memo protocol ({@code ChunkSection}'s javadoc) is what makes a
 * stale serve impossible. This test cannot prove the JMM (no unit test
 * can); it race-exercises the real interleavings — concurrent miss/store
 * pairs, mutation-during-walk, memo replacement mid-read — asserting the
 * protocol's two checkable laws: it never throws, and once the mutators
 * QUIET, the memoized output is byte-identical to a fresh verbatim walk.
 */
class SectionPackMemoConcurrencyTest {

    private static FrozenBlockRegistry registry;
    private static FlatWorldGenerator generator;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        generator = new FlatWorldGenerator(registry, 4);
    }

    @Test
    void concurrentMutationAndSerializeNeverServeStaleBytesOnceQuiet() throws Exception {
        EngineChunk chunk = populatedChunk(0, 0);
        var stone = registry.require(Identifier.parse("minecraft:stone"));
        var glass = registry.require(Identifier.parse("minecraft:glass"));

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // Two mutator lanes (the owner-thread shape — setBlock is the one
        // engine-wide mutation path) hammering alternating cells of the SAME
        // sections the serializers walk.
        List<Thread> mutators = new java.util.ArrayList<>();
        for (int m = 0; m < 2; m++) {
            final int lane = m;
            Thread mutator = new Thread(() -> {
                try {
                    int i = 0;
                    while (running.get()) {
                        int x = (i * 5) & 0xF;
                        int z = (i * 7) & 0xF;
                        // Section 0 (ground) and section 1 (the scatter):
                        // both ride the wire, both must invalidate.
                        chunk.setBlock(x, 4, z, (i & 1) == 0 ? stone : glass);
                        chunk.setBlock(x, 16 + (z & 0xF), z, lane == 0 ? stone : glass);
                        i++;
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }, "memo-mutator-" + m);
            mutator.setDaemon(true);
            mutator.start();
            mutators.add(mutator);
        }

        // Two serializer lanes (the netty + tick shape) looping the FULL and
        // the partial forms over the same chunk.
        List<Thread> serializers = new java.util.ArrayList<>();
        for (int s = 0; s < 2; s++) {
            Thread serializer = new Thread(() -> {
                try {
                    while (running.get()) {
                        byte[] full = ChunkSerializer18.serialize(chunk, true);
                        // The output must always be a LEGAL column: the
                        // grouped layout's size law (the mask's population
                        // times the per-section groups plus the biome row).
                        int mask = ChunkSerializer18.sectionBitmask(chunk);
                        int sections = Integer.bitCount(mask);
                        if (full.length != sections * (8192 + 2048 * 2) + 256) {
                            throw new IllegalStateException(
                                    "torn column: " + full.length + " bytes, mask " + mask);
                        }
                        byte[] partial = ChunkSerializer18.serialize(chunk, false, 1 << 1);
                        if (partial.length != 8192 + 2048 * 2) {
                            throw new IllegalStateException("torn partial: " + partial.length);
                        }
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }, "memo-serializer-" + s);
            serializer.setDaemon(true);
            serializer.start();
            serializers.add(serializer);
        }

        // Race for a bounded window (long enough for real interleavings on
        // any host, short enough to stay inside the suite's budget).
        Thread.sleep(1_500);
        running.set(false);
        for (Thread t : mutators) {
            t.join(5_000);
        }
        for (Thread t : serializers) {
            t.join(5_000);
        }
        assertTrue(failure.get() == null, "no lane may throw: " + failure.get());

        // The deterministic post-condition: with the mutators QUIET, the
        // memoized output equals a fresh verbatim walk of the final state —
        // no stale pack may survive the quieting.
        byte[] memoized = ChunkSerializer18.serialize(chunk, true);
        byte[] verbatim = verbatimWalk(chunk, ChunkSerializer18.sectionBitmask(chunk));
        assertArrayEquals(verbatim, memoized, "the quieted state serves its true bytes");
    }

    private EngineChunk populatedChunk(int x, int z) {
        EngineChunk chunk = new EngineChunk(new ChunkPosition(x, z),
                registry.require(Identifier.parse("minecraft:air")));
        generator.generate(chunk);
        var stone = registry.require(Identifier.parse("minecraft:stone"));
        var glass = registry.require(Identifier.parse("minecraft:glass"));
        for (int i = 0; i < 30; i++) {
            chunk.setBlock((i * 7) & 0xF, 16 + ((i * 5) & 0xF), (i * 3) & 0xF,
                    (i & 1) == 0 ? stone : glass);
        }
        chunk.setBlockLight(8, 100, 8, 14); // a light-only section above
        return chunk;
    }

    /** The pre-memo per-block law (same walk as SectionPackMemoTest's guard). */
    private static byte[] verbatimWalk(EngineChunk chunk, int sectionMask) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(
                Integer.bitCount(sectionMask) * (8192 + 2048 * 2) + 256);
        for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
            if ((sectionMask & (1 << sectionY)) == 0) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int stateId = ChunkSerializer18.packedStateId(
                                chunk.getBlock(x, (sectionY << 4) | y, z));
                        out.write(stateId & 0xFF);
                        out.write((stateId >> 8) & 0xFF);
                    }
                }
            }
        }
        for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
            if ((sectionMask & (1 << sectionY)) != 0) {
                out.write(chunk.blockLightArray(sectionY), 0, 2048);
            }
        }
        for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
            if ((sectionMask & (1 << sectionY)) != 0) {
                out.write(chunk.skyLightArray(sectionY), 0, 2048);
            }
        }
        out.write(chunk.biomeArray(), 0, 256);
        return out.toByteArray();
    }
}
