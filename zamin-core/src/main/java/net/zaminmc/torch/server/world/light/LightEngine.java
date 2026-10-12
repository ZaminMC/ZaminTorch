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
 * <p>Correctness first (§476): updates run synchronously inside the world's
 * mutation on the owning tick thread — the standard add/remove BFS pair, no
 * concurrency, no batching heuristics. Localized propagation, batched updates
 * and parallel chunk work remain documented follow-up investigations. This is
 * deliberately NOT the first concurrency project (§476 verbatim).</p>
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

    // Set only during computeInitial: the freshly generated chunk is not yet
    // in the world map, so every read/write inside its 16x256x16 bounds goes
    // through it instead of the world map (§344: publication stays atomic —
    // the chunk appears fully generated AND fully lit).
    private EngineChunk unpublishedChunk;
    private int unpublishedBaseX;
    private int unpublishedBaseZ;

    /** One BFS cell: world position plus the level carried into it. */
    private record Cell(int x, int y, int z, int level) {
    }

    public LightEngine(EngineWorld world) {
        this.world = world;
    }

    // ------------------------------------------------------------------ world change hook

    @Override
    public void onBlockChanged(EngineWorld world, BlockPosition position, BlockType type) {
        updateBlockChannel(position, type);
        updateSkyChannel(position, type);
    }

    // ------------------------------------------------------------------ chunk load hook

    @Override
    public void onChunkGenerated(EngineWorld world, EngineChunk chunk) {
        computeInitial(chunk);
    }

    // ------------------------------------------------------------------ block light channel

    private void updateBlockChannel(BlockPosition position, BlockType newType) {
        Set<Cell> survivors = new LinkedHashSet<>();
        int current = blockLightAt(position.x(), position.y(), position.z());
        if (current > 0) {
            setBlockLight(position.x(), position.y(), position.z(), 0);
            unlightBlock(position.x(), position.y(), position.z(), current, survivors);
        }
        int emission = BlockLightTable.emission(newType);
        if (emission > 0) {
            setBlockLight(position.x(), position.y(), position.z(), emission);
            addBlock(new Cell(position.x(), position.y(), position.z(), emission));
        }
        readdFrom(survivors, cell -> blockLightAt(cell.x(), cell.y(), cell.z()),
                this::addBlock, () ->
                readdNeighbors(position.x(), position.y(), position.z(),
                        cell -> blockLightAt(cell.x(), cell.y(), cell.z()), this::addBlock));
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
    private void unlightBlock(int x, int y, int z, int oldLevel, Set<Cell> survivors) {
        ArrayDeque<Cell> removal = new ArrayDeque<>();
        removal.push(new Cell(x, y, z, oldLevel));
        while (!removal.isEmpty()) {
            Cell cell = removal.pop();
            forEachNeighbor(cell.x(), cell.y(), cell.z(), (nx, ny, nz) -> {
                int neighborLevel = blockLightAt(nx, ny, nz);
                if (neighborLevel != 0 && neighborLevel < cell.level()) {
                    setBlockLight(nx, ny, nz, 0);
                    removal.push(new Cell(nx, ny, nz, neighborLevel));
                } else if (neighborLevel >= cell.level()) {
                    survivors.add(new Cell(nx, ny, nz, 0));
                }
            });
        }
    }

    private void addBlock(Cell origin) {
        if (origin.level() <= 1) {
            return;
        }
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.push(origin);
        while (!queue.isEmpty()) {
            Cell cell = queue.pop();
            forEachNeighbor(cell.x(), cell.y(), cell.z(), (nx, ny, nz) -> {
                int target = cell.level() - stepCost(nx, ny, nz);
                if (target > blockLightAt(nx, ny, nz)) {
                    setBlockLight(nx, ny, nz, target);
                    if (target > 1) {
                        queue.push(new Cell(nx, ny, nz, target));
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ sky channel

    private void updateSkyChannel(BlockPosition position, BlockType newType) {
        int filter = BlockLightTable.filter(newType);
        Set<Cell> survivors = new LinkedHashSet<>();

        // A more-opaque cell kills the light it held and everything downstream,
        // including the equal-15 direct column beneath it (handled inside the
        // unlight pass).
        int current = skyLightAt(position.x(), position.y(), position.z());
        if (current > 0 && filter > 0) {
            setSkyLight(position.x(), position.y(), position.z(), 0);
            unlightSky(position.x(), position.y(), position.z(), current, survivors);
        }

        // A fully transparent cell with a clear vertical path receives direct
        // skylight again: refill the column downward with 15 (the historical
        // no-decrement downward rule) before the lateral BFS runs. Refilled
        // cells are seeds like any survivor.
        if (filter == 0 && verticalPathClear(position.x(), position.y() + 1, position.z())) {
            int y = position.y();
            while (y >= 0 && BlockLightTable.filter(blockAt(position.x(), y, position.z())) == 0) {
                if (skyLightAt(position.x(), y, position.z()) < 15) {
                    setSkyLight(position.x(), y, position.z(), 15);
                    survivors.add(new Cell(position.x(), y, position.z(), 0));
                }
                y--;
            }
        }

        readdFrom(survivors, cell -> skyLightAt(cell.x(), cell.y(), cell.z()),
                this::addSky, () ->
                readdNeighbors(position.x(), position.y(), position.z(),
                        cell -> skyLightAt(cell.x(), cell.y(), cell.z()), this::addSky));
    }

    /**
     * Skylight's removal BFS: identical to block light's except for the direct
     * column rule — when a removed cell held the full 15, the cell straight
     * below holding 15 was fed by it (no decrement downward) and must be
     * removed even though its level is not strictly less.
     */
    private void unlightSky(int x, int y, int z, int oldLevel, Set<Cell> survivors) {
        ArrayDeque<Cell> removal = new ArrayDeque<>();
        removal.push(new Cell(x, y, z, oldLevel));
        while (!removal.isEmpty()) {
            Cell cell = removal.pop();
            forEachNeighbor(cell.x(), cell.y(), cell.z(), (nx, ny, nz) -> {
                int neighborLevel = skyLightAt(nx, ny, nz);
                boolean fedByDirectColumn =
                        cell.level() == 15 && neighborLevel == 15 && ny == cell.y() - 1;
                if (neighborLevel != 0 && (neighborLevel < cell.level() || fedByDirectColumn)) {
                    setSkyLight(nx, ny, nz, 0);
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

    private void addSky(Cell origin) {
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
                        && BlockLightTable.filter(blockAt(nx, ny, nz)) == 0;
                int target = directFall ? 15 : cell.level() - stepCost(nx, ny, nz);
                if (target > skyLightAt(nx, ny, nz)) {
                    setSkyLight(nx, ny, nz, target);
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
    private void readdFrom(Set<Cell> candidates, java.util.function.ToIntFunction<Cell> reader,
                           java.util.function.Consumer<Cell> add, Runnable fallback) {
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
    private void readdNeighbors(int x, int y, int z,
                                java.util.function.ToIntFunction<Cell> reader,
                                java.util.function.Consumer<Cell> add) {
        forEachNeighbor(x, y, z, (nx, ny, nz) -> {
            int level = reader.applyAsInt(new Cell(nx, ny, nz, 0));
            if (level > 1) {
                add.accept(new Cell(nx, ny, nz, level));
            }
        });
    }

    // ------------------------------------------------------------------ initial compute

    /**
     * Computes the initial light of a freshly generated (still unpublished)
     * chunk. Only already-loaded neighbors are touched (peek, never generate —
     * the re-entrancy guard that keeps chunk generation acyclic).
     */
    public void computeInitial(EngineChunk chunk) {
        unpublishedChunk = chunk;
        unpublishedBaseX = chunk.position().minBlockX();
        unpublishedBaseZ = chunk.position().minBlockZ();
        try {
            computeInitialInner(chunk);
        } finally {
            unpublishedChunk = null;
        }
    }

    private void computeInitialInner(EngineChunk chunk) {
        chunk.clearLight();
        ChunkPosition at = chunk.position();
        int baseX = at.minBlockX();
        int baseZ = at.minBlockZ();
        int maxY = highestLightRelevantY(chunk);

        // 1. Direct sky columns: unobstructed vertical paths hold 15; every
        //    cell from the first filter>0 cell down is shaded (0) unless lateral
        //    BFS re-lights it. Writes skip cells already at their target, so a
        //    fully open column materializes nothing and an all-air chunk keeps
        //    every section absent.
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int y = BlockPosition.MAX_Y;
                for (; y >= 0; y--) {
                    if (BlockLightTable.filter(blockAt(baseX + lx, y, baseZ + lz)) != 0) {
                        break;
                    }
                    if (chunk.skyLight(lx, y, lz) < 15) {
                        chunk.setSkyLight(lx, y, lz, 15);
                    }
                }
                for (; y >= 0; y--) {
                    if (chunk.skyLight(lx, y, lz) != 0) {
                        chunk.setSkyLight(lx, y, lz, 0);
                    }
                }
            }
        }

        // 2. Lateral spread into shade and 3. emitters, within the relevant span.
        List<Cell> skySeeds = new ArrayList<>();
        for (int y = 0; y <= maxY; y++) {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int sky = chunk.skyLight(lx, y, lz);
                    if (sky > 1 && feedsShade(baseX + lx, y, baseZ + lz)) {
                        skySeeds.add(new Cell(baseX + lx, y, baseZ + lz, sky));
                    }
                    int emission = BlockLightTable.emission(blockAt(baseX + lx, y, baseZ + lz));
                    if (emission > 0) {
                        chunk.setBlockLight(lx, y, lz, emission);
                        addBlock(new Cell(baseX + lx, y, baseZ + lz, emission));
                    }
                }
            }
        }
        for (Cell seed : skySeeds) {
            addSky(seed);
        }

        // 4. Border reconciliation with already-loaded neighbors: their border
        //    cells re-derive (the new terrain can shadow them), and their
        //    stored light flows into this chunk.
        for (int[] dir : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            EngineChunk neighbor = world.peek(new ChunkPosition(at.x() + dir[0], at.z() + dir[1]));
            if (neighbor == null) {
                continue;
            }
            int nbx = neighbor.position().minBlockX();
            int nbz = neighbor.position().minBlockZ();
            // The neighbor's layer facing this chunk (its border strip).
            for (int i = 0; i < 16; i++) {
                for (int y = 0; y <= maxY; y++) {
                    int nx = dir[0] == 1 ? nbx : dir[0] == -1 ? nbx + 15 : nbx + i;
                    int nz = dir[1] == 1 ? nbz : dir[1] == -1 ? nbz + 15 : nbz + i;
                    rederiveBorderCell(nx, y, nz);
                    int block = blockLightAt(nx, y, nz);
                    if (block > 1) {
                        addBlock(new Cell(nx, y, nz, block));
                    }
                    int sky = skyLightAt(nx, y, nz);
                    if (sky > 1) {
                        addSky(new Cell(nx, y, nz, sky));
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
    private void rederiveBorderCell(int x, int y, int z) {
        BlockType type = blockAt(x, y, z);
        BlockPosition position = new BlockPosition(x, y, z);
        updateBlockChannel(position, type);
        updateSkyChannel(position, type);
    }

    /** @return whether any 6-neighbor of the cell holds less than the cell's level minus a step. */
    private boolean feedsShade(int x, int y, int z) {
        int level = skyLightAt(x, y, z);
        boolean[] feeds = {false};
        forEachNeighbor(x, y, z, (nx, ny, nz) -> {
            if (!feeds[0] && skyLightAt(nx, ny, nz) < level - 1) {
                feeds[0] = true;
            }
        });
        return feeds[0];
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

    // ------------------------------------------------------------------ relight queue (the wire transport)

    /** Drains the pending chunk-column resends into the consumer (tick thread). */
    public void flushRelight(Consumer<ChunkPosition> out) {
        for (Long packed : relightQueue) {
            out.accept(ChunkPosition.unpack(packed));
        }
        relightQueue.clear();
    }

    private void markRelight(int x, int z) {
        if (unpublishedChunk != null && x >= unpublishedBaseX && x < unpublishedBaseX + 16
                && z >= unpublishedBaseZ && z < unpublishedBaseZ + 16) {
            return; // the unpublished chunk's first packet carries its light
        }
        relightQueue.add(ChunkPosition.ofBlock(x, z).packed());
    }

    // ------------------------------------------------------------------ storage accessors

    private int stepCost(int x, int y, int z) {
        return Math.max(1, BlockLightTable.filter(blockAt(x, y, z)));
    }

    private BlockType blockAt(int x, int y, int z) {
        if (unpublishedChunk != null
                && x >= unpublishedBaseX && x < unpublishedBaseX + 16
                && z >= unpublishedBaseZ && z < unpublishedBaseZ + 16) {
            return unpublishedChunk.getBlock(x - unpublishedBaseX, y, z - unpublishedBaseZ);
        }
        return world.getBlock(new BlockPosition(x, y, z));
    }

    private int blockLightAt(int x, int y, int z) {
        if (unpublishedChunk != null
                && x >= unpublishedBaseX && x < unpublishedBaseX + 16
                && z >= unpublishedBaseZ && z < unpublishedBaseZ + 16) {
            return unpublishedChunk.blockLight(x - unpublishedBaseX, y, z - unpublishedBaseZ);
        }
        EngineChunk chunk = world.peek(ChunkPosition.ofBlock(x, z));
        return chunk == null ? LightSection.DEFAULT_BLOCK
                : chunk.blockLight(x & 0xF, y, z & 0xF);
    }

    private int skyLightAt(int x, int y, int z) {
        if (unpublishedChunk != null
                && x >= unpublishedBaseX && x < unpublishedBaseX + 16
                && z >= unpublishedBaseZ && z < unpublishedBaseZ + 16) {
            return unpublishedChunk.skyLight(x - unpublishedBaseX, y, z - unpublishedBaseZ);
        }
        EngineChunk chunk = world.peek(ChunkPosition.ofBlock(x, z));
        return chunk == null ? LightSection.DEFAULT_SKY
                : chunk.skyLight(x & 0xF, y, z & 0xF);
    }

    private void setBlockLight(int x, int y, int z, int level) {
        if (unpublishedChunk != null
                && x >= unpublishedBaseX && x < unpublishedBaseX + 16
                && z >= unpublishedBaseZ && z < unpublishedBaseZ + 16) {
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

    private void setSkyLight(int x, int y, int z, int level) {
        if (unpublishedChunk != null
                && x >= unpublishedBaseX && x < unpublishedBaseX + 16
                && z >= unpublishedBaseZ && z < unpublishedBaseZ + 16) {
            unpublishedChunk.setSkyLight(x - unpublishedBaseX, y, z - unpublishedBaseZ, level);
            return;
        }
        EngineChunk chunk = world.peek(ChunkPosition.ofBlock(x, z));
        if (chunk == null) {
            return;
        }
        chunk.setSkyLight(x & 0xF, y, z & 0xF, level);
        markRelight(x, z);
    }

    private boolean verticalPathClear(int x, int yAbove, int z) {
        for (int y = Math.max(yAbove, 0); y <= BlockPosition.MAX_Y; y++) {
            if (BlockLightTable.filter(blockAt(x, y, z)) != 0) {
                return false;
            }
        }
        return true;
    }

    private void forEachNeighbor(int x, int y, int z, NeighborAction action) {
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
}
