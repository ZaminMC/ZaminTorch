package net.zaminmc.torch.server.world.light;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockLightTable;
import net.zaminmc.torch.server.world.ChunkLoadListener;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The light simulation (§475/§476): block light and skylight as derived world
 * state, recomputed from blocks, never persisted.
 *
 * <p>Model (the historical one, community-verified values from the same
 * dataset the behavior table embeds): block light radiates from emitting
 * blocks (torch 14) and spreads cell to cell paying {@code max(1, filter)}
 * per step; skylight additionally has the vertical direct rule — a cell with
 * an unobstructed vertical path to the sky through filter-0 cells holds level
 * 15, and level 15 propagates <b>downward without decrement</b> through
 * filter-0 cells. Every other step pays the standard decrement. Opaque cells
 * (filter 15) store no light. Glass, chests and the unlit furnace pass light
 * paying only the standard step (dataset {@code filterLight 0}).</p>
 *
 * <p>Correctness first (§476): the INCREMENTAL update (a committed block
 * change) runs synchronously inside the world's mutation on the owning tick
 * thread — the standard add/remove BFS pair, no concurrency, no batching
 * heuristics. This is deliberately NOT the first concurrency project (§476
 * verbatim).</p>
 *
 * <p>THE ACCESS SEAM (the §22 offload gate's own requirement): every
 * computation above is specified ONCE as a static routine over a
 * {@link LightAccess} state view. The live routing (the unpublished chunk
 * plus the world) is one implementation; a detached run over an immutable
 * {@link LightRegion} is the other. The two produce identical state by
 * construction — the gate's equivalence test proves it empirically — so the
 * initial per-chunk compute can leave the owning lane in the random-tick
 * picks' own compute-then-apply shape WITHOUT a second copy of the algorithm
 * that could drift.</p>
 *
 * <p>Incremental update (a committed block change): remove the light the old
 * state contributed — the unlight BFS strips strictly-decreasing levels and
 * collects the surviving sources — then re-propagate from those survivors and
 * from the new state. Skylight's direct columns get the two special cases the
 * historical model needs: removal cascades down equal-15 columns, and a newly
 * opened column refills with direct 15 before the lateral BFS runs.</p>
 *
 * <p>Initial compute (a freshly generated, still unpublished chunk): clear,
 * fill direct sky columns, radiate emitters, spread lateral shade within the
 * block span, then reconcile the borders of already-loaded neighbors — light
 * flows in from them, and their border cells re-derive against the new
 * terrain's shadows. Every touched chunk column lands in the relight queue;
 * {@link #flushRelight} hands them to the wire (protocol 47 has no
 * light-only packet — re-sending the chunk column is the transport). The
 * unpublished chunk itself is excluded: its first chunk packet already
 * carries the computed light.</p>
 */
public final class LightEngine implements WorldChangeListener, ChunkLoadListener {

    private final EngineWorld world;
    private final Set<Long> relightQueue = new LinkedHashSet<>();
    private final LiveAccess live = new LiveAccess();

    /** One BFS cell: world position plus the level carried into it. */
    private record Cell(int x, int y, int z, int level) {
    }

    public LightEngine(EngineWorld world) {
        this.world = world;
    }

    // ------------------------------------------------------------------ world change hook

    @Override
    public void onBlockChanged(EngineWorld world, BlockPosition position, BlockType type) {
        updateBlockChannel(live, position, BlockLightTable.emission(type));
        updateSkyChannel(live, position, BlockLightTable.filter(type));
    }

    // ------------------------------------------------------------------ chunk load hook

    /**
     * THE WIRING (the §22 lighting gate's verdict): the initial compute now
     * rides the MEASURED region path — capture the 3x3 read set, run the
     * same algorithms over the region routing, apply the recorded writes —
     * a 0.46-0.59x routing win over the live path per chunk (the pregen
     * batch 3.9-5.4x), with semantics identical by the proven twin-world
     * equivalence. The compute stays synchronous on the calling (owning)
     * lane; the pool dispatch is deferred to the batch shape (the pipelined
     * capture would see pre-apply neighbor states — the staleness protocol
     * is that step's gate, recorded with the numbers).
     */
    @Override
    public void onChunkGenerated(EngineWorld world, EngineChunk chunk) {
        applyInitial(InitialLightCompute.compute(LightRegion.capture(world, chunk)), chunk);
    }

    // ------------------------------------------------------------------ initial compute (the live entry)

    /**
     * Computes the initial light of a freshly generated (still unpublished)
     * chunk on the caller's (owning) lane. Only already-loaded neighbors are
     * touched (peek, never generate — the re-entrancy guard that keeps chunk
     * generation acyclic).
     */
    public void computeInitial(EngineChunk chunk) {
        live.adopt(chunk);
        try {
            computeInitial(live);
        } finally {
            live.release();
        }
    }

    // ------------------------------------------------------------------ the shared computations (§6.5: specified once)

    /**
     * The initial compute over any state view. The live entry drives it with
     * the unpublished routing; the detached offload gate drives the SAME code
     * over a captured region — the equivalence the gate proves.
     */
    static void computeInitial(LightAccess access) {
        access.clearFreshLight();
        int baseX = access.freshBaseX();
        int baseZ = access.freshBaseZ();
        int maxY = access.highestRelevantY();

        // 1. Direct sky columns: unobstructed vertical paths hold 15; every
        //    cell from the first filter>0 cell down is shaded (0) unless lateral
        //    BFS re-lights it. Writes skip cells already at their target, so a
        //    fully open column materializes nothing and an all-air chunk keeps
        //    every section absent.
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = baseX + lx;
                int z = baseZ + lz;
                int y = BlockPosition.MAX_Y;
                for (; y >= 0; y--) {
                    if (access.filterAt(x, y, z) != 0) {
                        break;
                    }
                    if (access.skyLightAt(x, y, z) < 15) {
                        access.setSkyLight(x, y, z, 15);
                    }
                }
                for (; y >= 0; y--) {
                    if (access.skyLightAt(x, y, z) != 0) {
                        access.setSkyLight(x, y, z, 0);
                    }
                }
            }
        }

        // 2. Lateral spread into shade and 3. emitters, within the relevant span.
        List<Cell> skySeeds = new ArrayList<>();
        for (int y = 0; y <= maxY; y++) {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int x = baseX + lx;
                    int z = baseZ + lz;
                    int sky = access.skyLightAt(x, y, z);
                    if (sky > 1 && feedsShade(access, x, y, z)) {
                        skySeeds.add(new Cell(x, y, z, sky));
                    }
                    int emission = access.emissionAt(x, y, z);
                    if (emission > 0) {
                        access.setBlockLight(x, y, z, emission);
                        addBlock(access, new Cell(x, y, z, emission));
                    }
                }
            }
        }
        for (Cell seed : skySeeds) {
            addSky(access, seed);
        }

        // 4. Border reconciliation with already-loaded neighbors: their border
        //    cells re-derive (the new terrain can shadow them), and their
        //    stored light flows into this chunk.
        for (int[] dir : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            ChunkPosition neighbor = access.neighborAt(dir[0], dir[1]);
            if (neighbor == null) {
                continue;
            }
            int nbx = neighbor.minBlockX();
            int nbz = neighbor.minBlockZ();
            // The neighbor's layer facing this chunk (its border strip).
            for (int i = 0; i < 16; i++) {
                for (int y = 0; y <= maxY; y++) {
                    int nx = dir[0] == 1 ? nbx : dir[0] == -1 ? nbx + 15 : nbx + i;
                    int nz = dir[1] == 1 ? nbz : dir[1] == -1 ? nbz + 15 : nbz + i;
                    rederiveBorderCell(access, nx, y, nz);
                    int block = access.blockLightAt(nx, y, nz);
                    if (block > 1) {
                        addBlock(access, new Cell(nx, y, nz, block));
                    }
                    int sky = access.skyLightAt(nx, y, nz);
                    if (sky > 1) {
                        addSky(access, new Cell(nx, y, nz, sky));
                    }
                }
            }
        }
    }

    /**
     * Re-derives one border cell of an already-loaded neighbor: its stored
     * light was computed when this chunk was air, and the new terrain can only
     * have darkened it. The incremental remove-then-re-add pass on the cell's
     * current state fixes the shadow (the fast path — nothing stored — covers
     * every fully dark cell, i.e. the flat world's whole underground border).
     */
    private static void rederiveBorderCell(LightAccess access, int x, int y, int z) {
        BlockPosition position = new BlockPosition(x, y, z);
        updateBlockChannel(access, position, access.emissionAt(x, y, z));
        updateSkyChannel(access, position, access.filterAt(x, y, z));
    }

    /** @return whether any 6-neighbor of the cell holds less than the cell's level minus a step. */
    private static boolean feedsShade(LightAccess access, int x, int y, int z) {
        int level = access.skyLightAt(x, y, z);
        boolean[] feeds = {false};
        forEachNeighbor(x, y, z, (nx, ny, nz) -> {
            if (!feeds[0] && access.skyLightAt(nx, ny, nz) < level - 1) {
                feeds[0] = true;
            }
        });
        return feeds[0];
    }

    // ------------------------------------------------------------------ block light channel

    private static void updateBlockChannel(LightAccess access, BlockPosition position, int emission) {
        Set<Cell> survivors = new LinkedHashSet<>();
        int current = access.blockLightAt(position.x(), position.y(), position.z());
        if (current > 0) {
            access.setBlockLight(position.x(), position.y(), position.z(), 0);
            unlightBlock(access, position.x(), position.y(), position.z(), current, survivors);
        }
        if (emission > 0) {
            access.setBlockLight(position.x(), position.y(), position.z(), emission);
            addBlock(access, new Cell(position.x(), position.y(), position.z(), emission));
        }
        readdFrom(survivors, cell -> access.blockLightAt(cell.x(), cell.y(), cell.z()),
                cell -> addBlock(access, cell),
                () -> readdNeighbors(access, position.x(), position.y(), position.z(),
                        cell -> access.blockLightAt(cell.x(), cell.y(), cell.z()),
                        cell -> addBlock(access, cell)));
    }

    /**
     * Strips every level downstream of the cell's old value. Neighbors holding
     * a level >= the removed one are independent sources that survive the
     * removal; their POSITIONS go out as re-add candidates.
     *
     * <p>Seeds carry positions only: a cell captured as a survivor early in
     * the sweep can still be removed by a later branch of the same sweep, so
     * its level is re-read only after the sweep finished — re-adding a stale
     * captured level would resurrect ghost light (found by the removal tests:
     * a seed at level 11 re-lit a cell the sweep had already stripped).</p>
     */
    private static void unlightBlock(LightAccess access, int x, int y, int z, int oldLevel,
                                     Set<Cell> survivors) {
        ArrayDeque<Cell> removal = new ArrayDeque<>();
        removal.push(new Cell(x, y, z, oldLevel));
        while (!removal.isEmpty()) {
            Cell cell = removal.pop();
            forEachNeighbor(cell.x(), cell.y(), cell.z(), (nx, ny, nz) -> {
                int neighborLevel = access.blockLightAt(nx, ny, nz);
                if (neighborLevel != 0 && neighborLevel < cell.level()) {
                    access.setBlockLight(nx, ny, nz, 0);
                    removal.push(new Cell(nx, ny, nz, neighborLevel));
                } else if (neighborLevel >= cell.level()) {
                    survivors.add(new Cell(nx, ny, nz, 0));
                }
            });
        }
    }

    private static void addBlock(LightAccess access, Cell origin) {
        if (origin.level() <= 1) {
            return;
        }
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.push(origin);
        while (!queue.isEmpty()) {
            Cell cell = queue.pop();
            forEachNeighbor(cell.x(), cell.y(), cell.z(), (nx, ny, nz) -> {
                int target = cell.level() - stepCost(access, nx, ny, nz);
                if (target > access.blockLightAt(nx, ny, nz)) {
                    access.setBlockLight(nx, ny, nz, target);
                    if (target > 1) {
                        queue.push(new Cell(nx, ny, nz, target));
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ sky channel

    private static void updateSkyChannel(LightAccess access, BlockPosition position, int filter) {
        Set<Cell> survivors = new LinkedHashSet<>();

        // A more-opaque cell kills the light it held and everything downstream,
        // including the equal-15 direct column beneath it (handled inside the
        // unlight pass).
        int current = access.skyLightAt(position.x(), position.y(), position.z());
        if (current > 0 && filter > 0) {
            access.setSkyLight(position.x(), position.y(), position.z(), 0);
            unlightSky(access, position.x(), position.y(), position.z(), current, survivors);
        }

        // A fully transparent cell with a clear vertical path receives direct
        // skylight again: refill the column downward with 15 (the historical
        // no-decrement downward rule) before the lateral BFS runs. Refilled
        // cells are seeds like any survivor.
        if (filter == 0 && verticalPathClear(access, position.x(), position.y() + 1, position.z())) {
            int y = position.y();
            while (y >= 0 && access.filterAt(position.x(), y, position.z()) == 0) {
                if (access.skyLightAt(position.x(), y, position.z()) < 15) {
                    access.setSkyLight(position.x(), y, position.z(), 15);
                    survivors.add(new Cell(position.x(), y, position.z(), 0));
                }
                y--;
            }
        }

        readdFrom(survivors, cell -> access.skyLightAt(cell.x(), cell.y(), cell.z()),
                cell -> addSky(access, cell),
                () -> readdNeighbors(access, position.x(), position.y(), position.z(),
                        cell -> access.skyLightAt(cell.x(), cell.y(), cell.z()),
                        cell -> addSky(access, cell)));
    }

    /**
     * Skylight's removal BFS: identical to block light's except for the direct
     * column rule — when a removed cell held the full 15, the cell straight
     * below holding 15 was fed by it (no decrement downward) and must be
     * removed even though its level is not strictly less.
     */
    private static void unlightSky(LightAccess access, int x, int y, int z, int oldLevel,
                                   Set<Cell> survivors) {
        ArrayDeque<Cell> removal = new ArrayDeque<>();
        removal.push(new Cell(x, y, z, oldLevel));
        while (!removal.isEmpty()) {
            Cell cell = removal.pop();
            forEachNeighbor(cell.x(), cell.y(), cell.z(), (nx, ny, nz) -> {
                int neighborLevel = access.skyLightAt(nx, ny, nz);
                boolean fedByDirectColumn =
                        cell.level() == 15 && neighborLevel == 15 && ny == cell.y() - 1;
                if (neighborLevel != 0 && (neighborLevel < cell.level() || fedByDirectColumn)) {
                    access.setSkyLight(nx, ny, nz, 0);
                    removal.push(new Cell(nx, ny, nz, neighborLevel));
                } else if (neighborLevel >= cell.level()) {
                    // No level gate here (unlike a 14-capped block source): a
                    // surviving 15 cell is a legitimate re-add source — it
                    // lights the diagonal cells under a newly placed roof that
                    // nothing else can reach.
                    survivors.add(new Cell(nx, ny, nz, 0));
                }
            });
        }
    }

    private static void addSky(LightAccess access, Cell origin) {
        if (origin.level() <= 1) {
            return;
        }
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.push(origin);
        while (!queue.isEmpty()) {
            Cell cell = queue.pop();
            forEachNeighbor(cell.x(), cell.y(), cell.z(), (nx, ny, nz) -> {
                // The downward-15 rule: direct skylight falls through filter-0
                // cells without paying the step.
                boolean directFall = cell.level() == 15 && ny == cell.y() - 1
                        && access.filterAt(nx, ny, nz) == 0;
                int target = directFall ? 15 : cell.level() - stepCost(access, nx, ny, nz);
                if (target > access.skyLightAt(nx, ny, nz)) {
                    access.setSkyLight(nx, ny, nz, target);
                    if (target > 1) {
                        queue.push(new Cell(nx, ny, nz, target));
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ shared re-add plumbing

    /**
     * Re-propagates from a removal sweep's surviving sources: each candidate
     * position's level is re-read AFTER the sweep (zero means a later branch
     * of the same sweep removed it — not a source). When nothing survived,
     * light flows back from the changed cell's own neighbors instead.
     */
    private static void readdFrom(Set<Cell> candidates,
                                  java.util.function.ToIntFunction<Cell> reader,
                                  Consumer<Cell> add, Runnable fallback) {
        boolean any = false;
        for (Cell candidate : candidates) {
            int level = reader.applyAsInt(candidate);
            if (level > 1) {
                any = true;
                add.accept(new Cell(candidate.x(), candidate.y(), candidate.z(), level));
            }
        }
        if (!any) {
            fallback.run();
        }
    }

    /** Seeds re-add from the changed cell's six neighbors (post-removal inflow). */
    private static void readdNeighbors(LightAccess access, int x, int y, int z,
                                       java.util.function.ToIntFunction<Cell> reader,
                                       Consumer<Cell> add) {
        forEachNeighbor(x, y, z, (nx, ny, nz) -> {
            int level = reader.applyAsInt(new Cell(nx, ny, nz, 0));
            if (level > 1) {
                add.accept(new Cell(nx, ny, nz, level));
            }
        });
    }

    // ------------------------------------------------------------------ storage accessors

    private static int stepCost(LightAccess access, int x, int y, int z) {
        return Math.max(1, access.filterAt(x, y, z));
    }

    private static boolean verticalPathClear(LightAccess access, int x, int yAbove, int z) {
        for (int y = Math.max(yAbove, 0); y <= BlockPosition.MAX_Y; y++) {
            if (access.filterAt(x, y, z) != 0) {
                return false;
            }
        }
        return true;
    }

    private static void forEachNeighbor(int x, int y, int z, NeighborAction action) {
        if (y < BlockPosition.MAX_Y) {
            action.accept(x, y + 1, z);
        }
        if (y > BlockPosition.MIN_Y) {
            action.accept(x, y - 1, z);
        }
        action.accept(x + 1, y, z);
        action.accept(x - 1, y, z);
        action.accept(x, y, z + 1);
        action.accept(x, y, z - 1);
    }

    private interface NeighborAction {
        void accept(int x, int y, int z);
    }

    // ------------------------------------------------------------------ relight queue (the wire transport)

    /** Drains the pending chunk-column resends into the consumer (tick thread). */
    public void flushRelight(Consumer<ChunkPosition> out) {
        for (Long packed : relightQueue) {
            out.accept(ChunkPosition.unpack(packed));
        }
        relightQueue.clear();
    }

    /** Queues the column's chunk (the live setter's own tail). */
    private void markRelightLive(int x, int z) {
        relightQueue.add(ChunkPosition.ofBlock(x, z).packed());
    }

    // ------------------------------------------------------------------ the offload's apply half

    /**
     * Applies a detached initial-compute result on the owning lane: replays
     * the fresh chunk's writes in order through the same guarded setters the
     * live path used (so light-section materialization matches by
     * construction), then replays the neighbor writes through the loaded
     * chunks — each landing marks its column relight, exactly as the live
     * compute's own writes would have.
     *
     * <p>Stale arm: a neighbor write whose target chunk is no longer loaded is
     * dropped (the live setter's own unloaded behavior — never a crash).</p>
     */
    public void applyInitial(InitialLightResult result, EngineChunk fresh) {
        for (int i = 0; i < result.freshWriteCount(); i++) {
            int w = result.freshWrite(i);
            int localIndex = (w >> 5) & 0xFFFF;
            int channel = (w >> 4) & 0x1;
            int level = w & 0xF;
            int lx = localIndex & 0xF;
            int lz = (localIndex >> 4) & 0xF;
            int y = (localIndex >> 8) & 0xFF;
            if (channel == InitialLightResult.CHANNEL_BLOCK) {
                fresh.setBlockLight(lx, y, lz, level);
            } else {
                fresh.setSkyLight(lx, y, lz, level);
            }
        }
        for (int i = 0; i < result.neighborWriteCount(); i++) {
            int w = result.neighborWrite(i);
            int slot = (w >> 21) & 0xF;
            int localIndex = (w >> 5) & 0xFFFF;
            int channel = (w >> 4) & 0x1;
            int level = w & 0xF;
            ChunkPosition target = result.neighborPosition(slot);
            int x = target.minBlockX() + (localIndex & 0xF);
            int z = target.minBlockZ() + ((localIndex >> 4) & 0xF);
            int y = (localIndex >> 8) & 0xFF;
            EngineChunk chunk = world.peek(ChunkPosition.ofBlock(x, z));
            if (chunk == null) {
                continue; // unloaded at apply: dropped (the live setter's own behavior)
            }
            if (channel == InitialLightResult.CHANNEL_BLOCK) {
                chunk.setBlockLight(x & 0xF, y, z & 0xF, level);
            } else {
                chunk.setSkyLight(x & 0xF, y, z & 0xF, level);
            }
            markRelightLive(x, z);
        }
    }

    // ------------------------------------------------------------------ the live routing

    /**
     * The live state view: the fresh chunk routes through the unpublished
     * fields (the chunk is not yet in the world map, so every read/write
     * inside its 16x256x16 bounds goes through it instead — §344: publication
     * stays atomic, the chunk appears fully generated AND fully lit); loaded
     * neighbors route through the world map; writes into loaded neighbors
     * mark their columns relight.
     */
    private final class LiveAccess implements LightAccess {

        // Set only during computeInitial: the freshly generated chunk is not yet
        // in the world map, so every read/write inside its 16x256x16 bounds goes
        // through it instead of the world map (§344: publication stays atomic —
        // the chunk appears fully generated AND fully lit).
        private EngineChunk unpublishedChunk;
        private int unpublishedBaseX;
        private int unpublishedBaseZ;

        void adopt(EngineChunk chunk) {
            this.unpublishedChunk = chunk;
            this.unpublishedBaseX = chunk.position().minBlockX();
            this.unpublishedBaseZ = chunk.position().minBlockZ();
        }

        void release() {
            this.unpublishedChunk = null;
        }

        private boolean inFresh(int x, int z) {
            return unpublishedChunk != null
                    && x >= unpublishedBaseX && x < unpublishedBaseX + 16
                    && z >= unpublishedBaseZ && z < unpublishedBaseZ + 16;
        }

        @Override
        public int filterAt(int x, int y, int z) {
            return BlockLightTable.filter(blockAt(x, y, z));
        }

        @Override
        public int emissionAt(int x, int y, int z) {
            return BlockLightTable.emission(blockAt(x, y, z));
        }

        private BlockType blockAt(int x, int y, int z) {
            if (inFresh(x, z)) {
                return unpublishedChunk.getBlock(x - unpublishedBaseX, y, z - unpublishedBaseZ);
            }
            return world.getBlock(new BlockPosition(x, y, z));
        }

        @Override
        public int blockLightAt(int x, int y, int z) {
            if (inFresh(x, z)) {
                return unpublishedChunk.blockLight(x - unpublishedBaseX, y, z - unpublishedBaseZ);
            }
            EngineChunk chunk = world.peek(ChunkPosition.ofBlock(x, z));
            return chunk == null ? LightSection.DEFAULT_BLOCK
                    : chunk.blockLight(x & 0xF, y, z & 0xF);
        }

        @Override
        public int skyLightAt(int x, int y, int z) {
            if (inFresh(x, z)) {
                return unpublishedChunk.skyLight(x - unpublishedBaseX, y, z - unpublishedBaseZ);
            }
            EngineChunk chunk = world.peek(ChunkPosition.ofBlock(x, z));
            return chunk == null ? LightSection.DEFAULT_SKY
                    : chunk.skyLight(x & 0xF, y, z & 0xF);
        }

        @Override
        public void setBlockLight(int x, int y, int z, int level) {
            if (inFresh(x, z)) {
                unpublishedChunk.setBlockLight(x - unpublishedBaseX, y, z - unpublishedBaseZ, level);
                return;
            }
            EngineChunk chunk = world.peek(ChunkPosition.ofBlock(x, z));
            if (chunk == null) {
                return; // light cannot spread into unloaded space
            }
            chunk.setBlockLight(x & 0xF, y, z & 0xF, level);
            markRelight(x, z);
        }

        @Override
        public void setSkyLight(int x, int y, int z, int level) {
            if (inFresh(x, z)) {
                unpublishedChunk.setSkyLight(x - unpublishedBaseX, y, z - unpublishedBaseZ, level);
                return;
            }
            EngineChunk chunk = world.peek(ChunkPosition.ofBlock(x, z));
            if (chunk == null) {
                return; // light cannot spread into unloaded space
            }
            chunk.setSkyLight(x & 0xF, y, z & 0xF, level);
            markRelight(x, z);
        }

        @Override
        public void markRelight(int x, int z) {
            if (inFresh(x, z)) {
                return; // the unpublished chunk's first packet carries its light
            }
            markRelightLive(x, z);
        }

        @Override
        public void clearFreshLight() {
            if (unpublishedChunk != null) {
                unpublishedChunk.clearLight();
            }
        }

        @Override
        public int highestRelevantY() {
            if (unpublishedChunk == null) {
                return 0;
            }
            return highestLightRelevantY(unpublishedChunk);
        }

        @Override
        public int freshBaseX() {
            return unpublishedBaseX;
        }

        @Override
        public int freshBaseZ() {
            return unpublishedBaseZ;
        }

        @Override
        public ChunkPosition neighborAt(int dx, int dz) {
            ChunkPosition at = unpublishedChunk.position();
            return world.peek(new ChunkPosition(at.x() + dx, at.z() + dz)) != null
                    ? new ChunkPosition(at.x() + dx, at.z() + dz)
                    : null;
        }

        private int highestLightRelevantY(EngineChunk chunk) {
            int highest = 0;
            for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
                var section = chunk.section(sectionY);
                if ((section != null && !section.isEmpty()) || chunk.sectionNeedsLightWire(sectionY)) {
                    highest = Math.max(highest, (sectionY << 4) + 15);
                }
            }
            return Math.min(highest + 1, BlockPosition.MAX_Y);
        }
    }
}
