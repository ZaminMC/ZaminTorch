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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The serialized-column memoization's numbers — the same convention as the
 * T-2/T-5 benches: the timing rows PRINT for the ledger (a slow host never
 * fails the gate); every arm asserts the byte law instead.
 *
 * <p>The measured shape is the ML-024 re-stream storm's own: one full view
 * at view-distance 4 (81 columns), re-serialized the way a respawn's
 * {@code sent.clear() + sendInitial} does — against the attribution pass's
 * measured 0.4-0.5 ms per column and ~32 ms per re-stream cycle for the
 * W2 storm's 4.5-5.3 ms/tick wirePublish row. Three arms over the same
 * columns: COLD (every populated section mutated between serializes — the
 * pre-memo cost, every walk runs), WARM (no mutations — the memo's pure
 * re-serve, the respawn re-stream of an unchanged view), and PARTIAL (one
 * section per column mutated — the one-block-then-die shape).</p>
 */
class WireColumnMemoBenchmarkTest {

    private static final int VIEW_DISTANCE = 4;
    private static final int COLUMNS = (VIEW_DISTANCE * 2 + 1) * (VIEW_DISTANCE * 2 + 1); // 81

    /** The measured iteration counts (a stable mean on a dev host). */
    private static final int COLD_REPS = 40;
    private static final int WARM_REPS = 400;
    private static final int PARTIAL_REPS = 120;

    /** The warmup share — the JIT's first walks are not the serializer's face. */
    private static final double WARMUP_SHARE = 0.2;

    private static FrozenBlockRegistry registry;
    private static FlatWorldGenerator generator;
    private static net.zaminmc.torch.block.BlockType stone;
    private static net.zaminmc.torch.block.BlockType glass;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        generator = new FlatWorldGenerator(registry, 4);
        stone = registry.require(Identifier.parse("minecraft:stone"));
        glass = registry.require(Identifier.parse("minecraft:glass"));
    }

    @Test
    void respawnRestreamCostsAcrossTheMemoArms() {
        List<EngineChunk> columns = new ArrayList<>(COLUMNS);
        int i = 0;
        for (int dx = -VIEW_DISTANCE; dx <= VIEW_DISTANCE; dx++) {
            for (int dz = -VIEW_DISTANCE; dz <= VIEW_DISTANCE; dz++) {
                columns.add(varietyChunk(dx, dz, i++));
            }
        }
        // The byte law first: warm re-serializes equal the cold output.
        byte[][] coldOutputs = new byte[COLUMNS][];
        for (int c = 0; c < COLUMNS; c++) {
            coldOutputs[c] = ChunkSerializer18.serialize(columns.get(c), true);
        }
        for (int c = 0; c < COLUMNS; c++) {
            assertArrayEquals(coldOutputs[c], ChunkSerializer18.serialize(columns.get(c), true),
                    "warm column " + c);
        }

        long coldNanos = runArm("cold (all sections changed)", COLD_REPS, () -> {
            for (EngineChunk column : columns) {
                touchEverySection(column);
                ChunkSerializer18.serialize(column, true);
            }
        });
        long warmNanos = runArm("warm (the respawn re-stream)", WARM_REPS, () -> {
            for (EngineChunk column : columns) {
                ChunkSerializer18.serialize(column, true);
            }
        });
        long partialNanos = runArm("partial (one section changed)", PARTIAL_REPS, () -> {
            for (EngineChunk column : columns) {
                touchOneSection(column);
                ChunkSerializer18.serialize(column, true);
            }
        });

        System.out.printf(
                "[wire-column-memo] view %d = %d columns (3 populated sections + 1 light-only each)%n",
                VIEW_DISTANCE, COLUMNS);
        report("cold   ", coldNanos, COLD_REPS);
        report("warm   ", warmNanos, WARM_REPS);
        report("partial", partialNanos, PARTIAL_REPS);
        double warmPerColumnUs = perColumnMicros(warmNanos, WARM_REPS);
        System.out.printf(
                "[wire-column-memo] the re-stream cycle (81 columns, warm): %.3f ms"
                        + " vs the ML-024 measured ~32 ms/cycle pre-memo%n",
                warmPerColumnUs * COLUMNS / 1000.0);
        System.out.printf(
                "[wire-column-memo] per column: cold %.1f us / warm %.1f us / partial %.1f us"
                        + " (the attribution's pre-memo per-column: 400-500 us)%n",
                perColumnMicros(coldNanos, COLD_REPS), warmPerColumnUs,
                perColumnMicros(partialNanos, PARTIAL_REPS));
        // The storm's REAL shape: a respawn re-stream where ONE column in the
        // view changed since the last send (the death site) and the other 80
        // are unchanged — the mixed cycle the arms compose.
        System.out.printf(
                "[wire-column-memo] the mixed cycle (one changed column + %d unchanged): %.3f ms"
                        + " vs the pre-memo ~32 ms (every column re-walked)%n",
                COLUMNS - 1,
                (perColumnMicros(partialNanos, PARTIAL_REPS)
                        + (COLUMNS - 1) * warmPerColumnUs) / 1000.0);
    }

    /** Runs the arm, returning measured nanos with the warmup share discarded. */
    private static long runArm(String label, int reps, Runnable body) {
        // Warmup on the arm's own shape.
        int warmup = (int) (reps * WARMUP_SHARE);
        for (int i = 0; i < warmup; i++) {
            body.run();
        }
        long start = System.nanoTime();
        for (int i = 0; i < reps; i++) {
            body.run();
        }
        return System.nanoTime() - start;
    }

    private static void report(String label, long nanos, int reps) {
        System.out.printf("[wire-column-memo] %s: total %d reps in %.1f ms (%.3f ms/rep)%n",
                label, reps, nanos / 1_000_000.0, nanos / 1_000_000.0 / reps);
    }

    private static double perColumnMicros(long nanos, int reps) {
        return nanos / 1000.0 / reps / COLUMNS;
    }

    /** Mutates one cell in EVERY populated section — the next serialize walks all. */
    private static void touchEverySection(EngineChunk column) {
        flip(column, 1, 3);
        flip(column, 17, 3);
        flip(column, 33, 3);
    }

    /** Mutates one cell in ONE populated section — the one-block-then-die shape. */
    private static void touchOneSection(EngineChunk column) {
        flip(column, 17, 3);
    }

    private static void flip(EngineChunk column, int y, int localX) {
        var current = column.getBlock(localX, y, 3);
        column.setBlock(localX, y, 3,
                current.identifier().equals(stone.identifier()) ? glass : stone);
    }

    /** Three populated sections (ground + two scatters) + one light-only. */
    private static EngineChunk varietyChunk(int dx, int dz, int salt) {
        EngineChunk chunk = new EngineChunk(new ChunkPosition(dx, dz),
                registry.require(Identifier.parse("minecraft:air")));
        generator.generate(chunk);
        for (int i = 0; i < 24; i++) {
            chunk.setBlock((i * 7 + salt) & 0xF, 16 + ((i * 5) & 0xF), (i * 3) & 0xF,
                    (i & 1) == 0 ? stone : glass);
        }
        chunk.setBlock(0, 32, 0, stone);
        chunk.setBlock(15, 47, 15, glass);
        chunk.setBlockLight(8, 100, 8, 14);
        return chunk;
    }
}
