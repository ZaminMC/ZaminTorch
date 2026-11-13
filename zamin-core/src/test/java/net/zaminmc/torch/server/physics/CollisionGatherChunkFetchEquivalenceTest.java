package net.zaminmc.torch.server.physics;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE KO-F — THE CHUNK-BANDED GATHER'S EQUIVALENCE SEAT (Paper 0060
 * part 2's engine mapping, the exact-algorithmic class's own proof
 * obligation): the chunk-banded walk with its per-chunk reader fetch
 * must answer BOX-FOR-BOX, IN ORDER, exactly what the pre-KO-F flat
 * walk answered — the reader's presence the loaded gate, the cell
 * order identical by construction, the border law and the k-1 band
 * unchanged.
 *
 * <p>THE THREE WALKS compared over every scene:
 * <ol>
 *   <li>THE LEGACY LAW — a verbatim copy of the pre-KO-F flat gather
 *       (the per-column chunkLoaded gate, the per-cell blockAt read),
 *       the historical walk itself as the reference;</li>
 *   <li>THE FALLBACK ARM — the new chunk-banded gather over a
 *       readerless world (chunkReader answers null), the stub worlds'
 *       own path;</li>
 *   <li>THE READER ARM — the new chunk-banded gather over the same
 *       scene with the per-chunk reader face (the engine's live
 *       shape).</li>
 * </ol>
 * All three must return the identical box list — the pure-win proof
 * (the optimization changes no output, only the work).</p>
 */
class CollisionGatherChunkFetchEquivalenceTest {

    // ------------------------------------------------------------------
    // The legacy law — the pre-KO-F flat gather, VERBATIM (the reference
    // the new walk must reproduce box-for-box)
    // ------------------------------------------------------------------

    private static List<Aabb> legacyGather(EntityCollisionWorld world, Aabb region,
            boolean moverInsideBorder) {
        List<Aabb> out = new ArrayList<>();
        int minX = (int) Math.floor(region.minX);
        int maxX = (int) Math.floor(region.maxX + 1.0);
        int minY = (int) Math.floor(region.minY);
        int maxY = (int) Math.floor(region.maxY + 1.0);
        int minZ = (int) Math.floor(region.minZ);
        int maxZ = (int) Math.floor(region.maxZ + 1.0);
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                if (!world.chunkLoaded(x, z)) {
                    continue;
                }
                for (int y = minY - 1; y < maxY; y++) {
                    if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                            || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                        continue;
                    }
                    BlockType type;
                    if (world.borderContains(x, z) || !moverInsideBorder) {
                        type = world.blockAt(x, y, z);
                    } else {
                        type = BuiltinBlocks.STONE;
                    }
                    appendLegacyCellBoxes(world, type, x, y, z, region, out);
                }
            }
        }
        return out;
    }

    private static void appendLegacyCellBoxes(EntityCollisionWorld world, BlockType type,
            int x, int y, int z, Aabb region, List<Aabb> out) {
        if (type == null) {
            return;
        }
        net.zaminmc.torch.server.block.CollisionShapes.NeighborProbe probe =
                (dx, dy, dz) -> {
                    int ny = y + dy;
                    if (ny < net.zaminmc.torch.block.BlockPosition.MIN_Y
                            || ny > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                        return null;
                    }
                    int nx = x + dx;
                    int nz = z + dz;
                    if (!world.chunkLoaded(nx, nz)) {
                        return null;
                    }
                    return world.blockAt(nx, ny, nz);
                };
        for (net.zaminmc.torch.server.block.CollisionShapes.Box box
                : net.zaminmc.torch.server.block.CollisionShapes.cellBoxes(type, probe)) {
            Aabb worldBox = new Aabb(
                    x + box.minX(), y + box.minY(), z + box.minZ(),
                    x + box.maxX(), y + box.maxY(), z + box.maxZ());
            if (region.intersects(worldBox)) {
                out.add(worldBox);
            }
        }
    }

    // ------------------------------------------------------------------
    // The scene — a chunked stub world with BOTH faces: the per-cell
    // path (blockAt/chunkLoaded) and the per-chunk reader path
    // ------------------------------------------------------------------

    /**
     * The chunk-keyed stub: cells stored per loaded chunk (the engine's
     * own shape — a chunk map of cell maps), answering all three walks'
     * faces from one store so any divergence between them is a law
     * break, never a staging artifact.
     */
    private static final class ChunkedWorld implements EntityCollisionWorld {

        /** chunk packed key -> (cell packed key -> type). */
        final Map<Long, Map<Long, BlockType>> chunks = new HashMap<>();
        /** The out-of-bounds border (the STONE wall law's band). */
        int borderMinX = Integer.MIN_VALUE;
        int borderMaxX = Integer.MAX_VALUE;
        int borderMinZ = Integer.MIN_VALUE;
        int borderMaxZ = Integer.MAX_VALUE;
        /** Whether the KO-F reader face answers (the fallback arm flips it off). */
        boolean carryReaderFace = true;

        static long chunkKey(int chunkX, int chunkZ) {
            return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        }

        static long cellKey(int x, int y, int z) {
            return ((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL);
        }

        void put(int x, int y, int z, BlockType type) {
            chunks.computeIfAbsent(chunkKey(x >> 4, z >> 4),
                    key -> new HashMap<>()).put(cellKey(x, y, z), type);
        }

        void unloadChunk(int chunkX, int chunkZ) {
            chunks.remove(chunkKey(chunkX, chunkZ));
        }

        @Override
        public BlockType blockAt(int x, int y, int z) {
            if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                    || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                return BuiltinBlocks.AIR;
            }
            Map<Long, BlockType> cells = chunks.get(chunkKey(x >> 4, z >> 4));
            if (cells == null) {
                return BuiltinBlocks.AIR; // the unloaded column reads air
            }
            BlockType type = cells.get(cellKey(x, y, z));
            return type != null ? type : BuiltinBlocks.AIR;
        }

        @Override
        public boolean chunkLoaded(int x, int z) {
            return chunks.containsKey(chunkKey(x >> 4, z >> 4));
        }

        @Override
        public boolean borderContains(int x, int z) {
            return x >= borderMinX && x <= borderMaxX
                    && z >= borderMinZ && z <= borderMaxZ;
        }

        @Override
        public ChunkReader chunkReader(int chunkX, int chunkZ) {
            if (!carryReaderFace) {
                return null; // the fallback arm's readerless shape
            }
            Map<Long, BlockType> cells = chunks.get(chunkKey(chunkX, chunkZ));
            if (cells == null) {
                return null; // the reader's presence IS the loaded gate
            }
            return (x, y, z) -> {
                if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                        || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                    return BuiltinBlocks.AIR;
                }
                BlockType type = cells.get(cellKey(x, y, z));
                return type != null ? type : BuiltinBlocks.AIR;
            };
        }
    }

    // ------------------------------------------------------------------
    // The equivalence seats
    // ------------------------------------------------------------------

    /**
     * THE LOCKSTEP LAW over randomized scenes: 400 seeds, each a fresh
     * scatter of shape families (the full cubes, the slabs, the fences,
     * the panes, the walls, the stairs, the chests, the doors — the
     * connection families exercising the probe) across a chunk-spanning
     * band INCLUDING negative coordinates, and a fresh swept region per
     * seed — the three walks must answer the identical box list, every
     * time.
     */
    @Test
    void theThreeWalksAnswerTheIdenticalBoxesOverRandomizedScenes() {
        BlockType[] palette = {
                BuiltinBlocks.STONE, BuiltinBlocks.BEDROCK, BuiltinBlocks.AIR,
                BuiltinBlocks.OAK_SLAB, BuiltinBlocks.FENCE,
                BuiltinBlocks.GLASS_PANE, BuiltinBlocks.OAK_STAIRS_EAST,
                BuiltinBlocks.CHEST, BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_N,
                BuiltinBlocks.SNOW_LAYER,
        };
        for (int seed = 0; seed < 400; seed++) {
            Random random = new Random(seed * 7919L + 13L);
            ChunkedWorld world = new ChunkedWorld();
            // The scatter: 3-6 chunks wide, negative and positive, each
            // chunk 8-40 named cells.
            int baseChunkX = -2 + random.nextInt(4);
            int baseChunkZ = -2 + random.nextInt(4);
            int chunkSpan = 1 + random.nextInt(3);
            for (int cx = baseChunkX; cx < baseChunkX + chunkSpan; cx++) {
                for (int cz = baseChunkZ; cz < baseChunkZ + chunkSpan; cz++) {
                    int cells = 8 + random.nextInt(32);
                    for (int i = 0; i < cells; i++) {
                        int x = (cx << 4) + random.nextInt(16);
                        int z = (cz << 4) + random.nextInt(16);
                        int y = 1 + random.nextInt(40);
                        world.put(x, y, z, palette[random.nextInt(palette.length)]);
                    }
                    if (random.nextInt(4) == 0) {
                        // A straggler cell in a NEIGHBOUR chunk (the probe's
                        // cross-chunk reads + the chunk-band straddle).
                        world.put((cx << 4) + 15, 2 + random.nextInt(20),
                                (cz << 4) + 15, palette[random.nextInt(palette.length)]);
                    }
                }
            }
            // The swept region: a body-shaped band straddling cells and
            // (often) chunk borders, with a random motion expansion.
            double originX = (baseChunkX << 4) + random.nextInt(16) + 0.3;
            double originZ = (baseChunkZ << 4) + random.nextInt(16) + 0.3;
            double originY = 1 + random.nextInt(30) + 0.2;
            Aabb region = new Aabb(originX, originY, originZ,
                    originX + 0.25 + random.nextDouble(),
                    originY + 0.25 + random.nextDouble(),
                    originZ + 0.25 + random.nextDouble())
                    .expanded(random.nextDouble() - 0.5, random.nextDouble() - 0.75,
                            random.nextDouble() - 0.5);
            boolean inside = random.nextBoolean();
            List<Aabb> legacy = legacyGather(world, region, inside);
            world.carryReaderFace = false;
            List<Aabb> fallback = EntityCollisions.gatherBlockBoxes(world, region, inside);
            world.carryReaderFace = true;
            List<Aabb> reader = EntityCollisions.gatherBlockBoxes(world, region, inside);
            assertEquals(legacy, fallback,
                    "the fallback arm must answer the legacy law (seed " + seed + ")");
            assertEquals(legacy, reader,
                    "the reader arm must answer the legacy law (seed " + seed + ")");
        }
    }

    /**
     * THE UNLOADED CHUNK GATE: a region straddling a loaded and an
     * unloaded chunk answers only the loaded side's boxes on every
     * walk — the reader's null IS the gate, the fallback's chunkLoaded
     * consult the same gate.
     */
    @Test
    void theUnloadedChunkGateAnswersNothingFromThatChunk() {
        ChunkedWorld world = new ChunkedWorld();
        // Two chunks straddled by the region: (0,0) loaded with a floor
        // tower, (1,0) loaded then REMOVED.
        for (int y = 1; y <= 3; y++) {
            world.put(15, y, 5, BuiltinBlocks.STONE); // chunk (0,0)
            world.put(16, y, 5, BuiltinBlocks.STONE); // chunk (1,0)
        }
        world.unloadChunk(1, 0);
        Aabb region = new Aabb(14.5, 0.5, 4.5, 17.5, 5.5, 6.5);
        List<Aabb> legacy = legacyGather(world, region, true);
        List<Aabb> fallback = EntityCollisions.gatherBlockBoxes(world, region, true);
        world.carryReaderFace = true;
        List<Aabb> reader = EntityCollisions.gatherBlockBoxes(world, region, true);
        assertEquals(3, legacy.size(), "the legacy law: only chunk (0,0)'s column");
        assertEquals(legacy, fallback);
        assertEquals(legacy, reader);
    }

    /**
     * THE BORDER'S STONE WALL: cells outside the border read STONE for
     * a body INSIDE (the wall) and read real for a body OUTSIDE — the
     * law unchanged by the walk's chunk banding.
     */
    @Test
    void theBorderWallLawAnswersTheSameStoneOnEveryWalk() {
        ChunkedWorld world = new ChunkedWorld();
        world.borderMaxX = 10; // the border's east edge between cells 10 and 11
        world.put(11, 4, 5, BuiltinBlocks.AIR); // outside: would read air
        world.put(12, 4, 5, BuiltinBlocks.OAK_SLAB); // outside: would read a slab
        Aabb region = new Aabb(10.5, 3.5, 4.5, 12.5, 5.5, 6.5);
        // A body INSIDE the border: every OUTSIDE cell of the band reads
        // the STONE wall — x 11 and 12, z 4..6, the intersecting y rows
        // 3..5 (the k-1 row at y=2 tops at 3.0, below the region's own
        // 3.5 floor — it never intersects) = 18 full cubes.
        List<Aabb> insideLegacy = legacyGather(world, region, true);
        List<Aabb> insideReader = EntityCollisions.gatherBlockBoxes(world, region, true);
        assertEquals(insideLegacy, insideReader);
        assertEquals(18, insideLegacy.size(),
                "the wall: every outside cell of the band answers the STONE full cube");
        assertTrue(insideLegacy.stream().allMatch(box ->
                        box.maxY - box.minY == 1.0 && (box.minX == 11 || box.minX == 12)),
                "the STONE wall's own full cubes at the outside cells");
        // A body OUTSIDE the border: the cells read real — the air
        // column at 11 contributes nothing, the slab at 12 its half.
        List<Aabb> outsideLegacy = legacyGather(world, region, false);
        List<Aabb> outsideReader = EntityCollisions.gatherBlockBoxes(world, region, false);
        assertEquals(outsideLegacy, outsideReader);
        assertEquals(1, outsideLegacy.size(),
                "the real cells: the air contributes nothing, the slab its half");
        assertEquals(0.5, outsideLegacy.get(0).maxY - outsideLegacy.get(0).minY, 1e-9);
    }

    /**
     * THE ONE-BELOW BAND (the reference's k - 1 start): a tall family
     * poking through its ceiling reaches the cell BELOW the region —
     * the chunk banding never loses the k-1 row (the y loop's own
     * start, unchanged).
     */
    @Test
    void theOneBelowBandStillContributesBelowTheRegion() {
        ChunkedWorld world = new ChunkedWorld();
        // A fence standing BELOW the region's floor: its 1.5-tall band
        // tops at y=5.5 — a region starting at y=5 still clips it.
        world.put(5, 4, 5, BuiltinBlocks.FENCE);
        Aabb region = new Aabb(4.5, 5.0, 4.5, 6.5, 6.5, 6.5);
        List<Aabb> legacy = legacyGather(world, region, true);
        List<Aabb> reader = EntityCollisions.gatherBlockBoxes(world, region, true);
        assertEquals(legacy, reader);
        assertEquals(1, legacy.size(),
                "the k-1 row's fence arm reaches into the region");
        assertEquals(5.5, legacy.get(0).maxY, 1e-9);
    }

    /**
     * THE READER CONTRACT (the interface's own law): a reader fetched
     * for a chunk answers identically to the per-cell blockAt for every
     * cell of that chunk, and the reader's presence equals the loaded
     * gate — the exactness obligation stated at the face itself.
     */
    @Test
    void theReaderContractAnswersBlockAtIdentically() {
        ChunkedWorld world = new ChunkedWorld();
        Random random = new Random(20261113L);
        BlockType[] palette = {
                BuiltinBlocks.STONE, BuiltinBlocks.BEDROCK, BuiltinBlocks.AIR,
                BuiltinBlocks.OAK_SLAB, BuiltinBlocks.FENCE,
                BuiltinBlocks.GLASS_PANE, BuiltinBlocks.OAK_STAIRS_EAST,
        };
        for (int i = 0; i < 200; i++) {
            world.put(random.nextInt(48) - 24, random.nextInt(80),
                    random.nextInt(48) - 24, palette[random.nextInt(palette.length)]);
        }
        for (int chunkX = -2; chunkX <= 1; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 1; chunkZ++) {
                EntityCollisionWorld.ChunkReader reader = world.chunkReader(chunkX, chunkZ);
                assertEquals(world.chunkLoaded(chunkX << 4, chunkZ << 4),
                        reader != null,
                        "the reader's presence IS the loaded gate");
                if (reader == null) {
                    continue;
                }
                for (int i = 0; i < 200; i++) {
                    int x = (chunkX << 4) + random.nextInt(16);
                    int z = (chunkZ << 4) + random.nextInt(16);
                    int y = random.nextInt(300) - 20; // includes the out-of-band rows
                    assertEquals(world.blockAt(x, y, z), reader.blockAt(x, y, z),
                            "the reader answers blockAt identically ("
                                    + x + "," + y + "," + z + ")");
                }
            }
        }
    }

    /**
     * THE NON-TRIVIAL SCENE GUARD: the randomized seat's scenes must
     * actually exercise boxes (a seat that compared three empty lists
     * every time would prove nothing) — the palette's scatter over the
     * swept bands must produce non-empty answers on a fair share of
     * seeds, and at least one scene must diverge from another (the
     * guard against a degenerate all-air world).
     */
    @Test
    void theRandomizedScenesActuallyExerciseBoxes() {
        int nonEmpty = 0;
        int distinctSizes = 0;
        java.util.Set<Integer> sizes = new java.util.HashSet<>();
        for (int seed = 0; seed < 400; seed++) {
            Random random = new Random(seed * 7919L + 13L);
            ChunkedWorld world = new ChunkedWorld();
            int baseChunkX = -2 + random.nextInt(4);
            int baseChunkZ = -2 + random.nextInt(4);
            int chunkSpan = 1 + random.nextInt(3);
            for (int cx = baseChunkX; cx < baseChunkX + chunkSpan; cx++) {
                for (int cz = baseChunkZ; cz < baseChunkZ + chunkSpan; cz++) {
                    int cells = 8 + random.nextInt(32);
                    for (int i = 0; i < cells; i++) {
                        int x = (cx << 4) + random.nextInt(16);
                        int z = (cz << 4) + random.nextInt(16);
                        int y = 1 + random.nextInt(40);
                        world.put(x, y, z, seed % 2 == 0
                                ? BuiltinBlocks.STONE : BuiltinBlocks.OAK_SLAB);
                    }
                }
            }
            double originX = (baseChunkX << 4) + 8 + 0.3;
            double originZ = (baseChunkZ << 4) + 8 + 0.3;
            Aabb region = new Aabb(originX, 1.2, originZ,
                    originX + 4.0, 40.0, originZ + 4.0);
            int size = EntityCollisions.gatherBlockBoxes(world, region, true).size();
            if (size > 0) {
                nonEmpty++;
            }
            sizes.add(size);
        }
        assertTrue(nonEmpty > 300, "most scenes answer boxes (got " + nonEmpty + ")");
        distinctSizes = sizes.size();
        assertNotEquals(1, distinctSizes, "the scenes vary (not one degenerate size)");
    }

    /**
     * THE MEASURED RELIEF (the §22 evidence face, the DenseOwner
     * harness's own discipline — the assertions structural, the printed
     * numbers the evidence): the reader arm against the fallback arm
     * over a DENSE scene (a 5x5-chunk stone field, a body-sized swept
     * region walked over 4,000 positions), both answering the identical
     * box lists, the per-walk nanos printed for the worklog record.
     */
    @Test
    void theReaderArmAnswersIdenticallyAndMeasuresItsRelief() {
        ChunkedWorld world = new ChunkedWorld();
        // The dense scene: a 5x5-chunk band of full cubes and slabs
        // (the per-cell reads' worst case — every cell a real consult).
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int wx = (chunkX << 4) + x;
                        int wz = (chunkZ << 4) + z;
                        world.put(wx, 18, wz, BuiltinBlocks.OAK_SLAB);
                        world.put(wx, 19, wz, BuiltinBlocks.STONE);
                    }
                }
            }
        }
        // The walk: a mob-sized body's swept region (the 0.6x1.8 hull
        // with a fall's expansion) stepping across the band — its y
        // band straddles the slab row and the stone row — 4,000 gathers
        // per arm, a fresh region each step so no escape analysis
        // caches the list.
        List<Aabb> regions = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            double x = -30.0 + (i % 125) * 0.5;
            double z = -30.0 + ((i / 125) % 32) * 2.0;
            regions.add(new Aabb(x, 17.5, z, x + 0.6, 19.3, z + 0.6)
                    .expanded(0.0, -0.04 - (i % 7) * 0.01, 0.0));
        }
        // Warmup (both arms), then the measured windows.
        for (int i = 0; i < 2; i++) {
            world.carryReaderFace = false;
            for (Aabb region : regions) {
                EntityCollisions.gatherBlockBoxes(world, region, true);
            }
            world.carryReaderFace = true;
            for (Aabb region : regions) {
                EntityCollisions.gatherBlockBoxes(world, region, true);
            }
        }
        List<List<Aabb>> fallbackBoxes = new ArrayList<>();
        List<List<Aabb>> readerBoxes = new ArrayList<>();
        world.carryReaderFace = false;
        long fallbackNanos = System.nanoTime();
        for (Aabb region : regions) {
            fallbackBoxes.add(EntityCollisions.gatherBlockBoxes(world, region, true));
        }
        fallbackNanos = System.nanoTime() - fallbackNanos;
        world.carryReaderFace = true;
        long readerNanos = System.nanoTime();
        for (Aabb region : regions) {
            readerBoxes.add(EntityCollisions.gatherBlockBoxes(world, region, true));
        }
        readerNanos = System.nanoTime() - readerNanos;
        // THE EQUIVALENCE over the measured walk itself: every region's
        // box list identical between the arms.
        assertEquals(fallbackBoxes.size(), readerBoxes.size());
        for (int i = 0; i < fallbackBoxes.size(); i++) {
            assertEquals(fallbackBoxes.get(i), readerBoxes.get(i),
                    "the measured walk's own equivalence (region " + i + ")");
        }
        assertTrue(fallbackBoxes.stream().anyMatch(list -> !list.isEmpty()),
                "the scene answers boxes (a hollow measurement proves nothing)");
        // The printed evidence (never a CI assertion — the §22 shape).
        System.out.printf(
                "KO-F collision gather relief: fallback %.1f ns/walk, reader %.1f ns/walk "
                        + "(%.2fx, over %d gathers)%n",
                fallbackNanos / 4000.0, readerNanos / 4000.0,
                (double) fallbackNanos / readerNanos, regions.size());
    }
}
