package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * R-5 of the vanilla parity remainder: the worldgen deterministic-seed
 * equivalence run — same seed → same world — executed end-to-end and
 * compared at the byte level, in the engine's own end-to-end form.
 *
 * <p><b>The comparison level, chosen from the actual save path</b>: the
 * item's original wording assumed anvil region files, but the engine's
 * region files are the IMPORT side only (AnvilWorldImport, read-only —
 * region/*.mca never written); the native save path is the delta
 * pipeline (WorldSavePipeline/WorldDeltaSerializer), which is vacuous
 * for freshly generated chunks — no player change, no delta row. The
 * strongest non-vacuous end-to-end form is therefore the chunk's own
 * serialized bytes — {@link ChunkSerializer18#serialize} over the full
 * ground-up walk (the block states, the section bitmask, the light, the
 * biomes: everything the wire form carries) — the same form a real
 * client receives. Region-level inapplicability is recorded here, not
 * forced.</p>
 *
 * <p><b>The seed law</b> (NormalWorldGenerator line 113):
 * {@code 0x5A4D494E5C00L ^ worldName.hashCode() * 0x9E3779B97F4L} —
 * the seed derives deterministically from the level name, so two
 * engines booted with the SAME world name in different data directories
 * run the same generator seed through two independent instances. The
 * run's shape: a 5x5 chunk square around the origin (25 columns), each
 * requested through the engine's own on-demand load
 * ({@code requestChunkLoad}), awaited to publication, then compared
 * column-by-column: identical section bitmask AND identical serialized
 * bytes in both engines. Wall time is measured and reported in the
 * output.</p>
 *
 * <p><b>Known in-process limitation, recorded not hidden</b>:
 * NormalWorldGenerator's construction calls the STATIC
 * {@code Layer.init(seed)} (line 124) — two generators in one process
 * share that static. With the same seed (this test's shape) the
 * re-initialization is idempotent; two DIFFERENT seeds in one process
 * would clobber each other's layer state. That is an architectural
 * constraint of the in-process design, not a determinism defect — but
 * it means this run proves same-seed determinism, and a future
 * cross-process run (two JVMs) would be needed to also prove
 * different-seed isolation end-to-end.</p>
 */
class WorldgenDeterminismRunTest {

    @TempDir
    Path dataDirA;

    @TempDir
    Path dataDirB;

    private static final String WORLD_NAME = "r5determinism";
    private static final int RADIUS = 2; // 5x5 = 25 columns

    net.zaminmc.torch.server.EngineServer engineA;
    net.zaminmc.torch.server.EngineServer engineB;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig configA = new EngineConfig("127.0.0.1", 0, WORLD_NAME, "r5",
                20, 2, 20, dataDirA.toString(), net.zaminmc.torch.GameMode.SURVIVAL);
        EngineConfig configB = new EngineConfig("127.0.0.1", 0, WORLD_NAME, "r5",
                20, 2, 20, dataDirB.toString(), net.zaminmc.torch.GameMode.SURVIVAL);
        engineA = new net.zaminmc.torch.server.EngineServer(configA);
        engineA.start();
        engineB = new net.zaminmc.torch.server.EngineServer(configB);
        engineB.start();
    }

    @AfterEach
    void stop() {
        if (engineA != null) {
            engineA.shutdown(null);
        }
        if (engineB != null) {
            engineB.shutdown(null);
        }
    }

    private static void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out awaiting: " + description);
            }
            Thread.sleep(10);
        }
    }

    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    @Test
    void theSameSeedGeneratesByteIdenticalWorlds() throws Exception {
        List<ChunkPosition> columns = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                columns.add(new ChunkPosition(dx, dz));
            }
        }
        long startNanos = System.nanoTime();
        for (ChunkPosition position : columns) {
            engineA.requestChunkLoad(position, chunk -> { });
            engineB.requestChunkLoad(position, chunk -> { });
        }
        for (ChunkPosition position : columns) {
            ChunkPosition pinned = position;
            await(() -> engineA.world().peek(pinned) != null,
                    "engine A published " + pinned);
            await(() -> engineB.world().peek(pinned) != null,
                    "engine B published " + pinned);
        }
        long wallMillis = (System.nanoTime() - startNanos) / 1_000_000;

        int compared = 0;
        for (ChunkPosition position : columns) {
            var chunkA = engineA.world().peek(position);
            var chunkB = engineB.world().peek(position);
            assertNotNull(chunkA, "A holds " + position);
            assertNotNull(chunkB, "B holds " + position);
            assertEquals(ChunkSerializer18.sectionBitmask(chunkA),
                    ChunkSerializer18.sectionBitmask(chunkB),
                    "the section bitmask differs at " + position);
            assertArrayEquals(ChunkSerializer18.serialize(chunkA, true),
                    ChunkSerializer18.serialize(chunkB, true),
                    "the serialized column differs at " + position);
            compared++;
        }
        assertEquals(25, compared, "the run covered the full 5x5 square");
        System.out.println("R-5 determinism run: seed derived from world name '"
                + WORLD_NAME + "' (NormalWorldGenerator line 113's own law), 25 columns "
                + "(the boot-generated spawn square) over two independent engines, "
                + "byte-identical, " + wallMillis + " ms compare wall");
    }
}
